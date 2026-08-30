/*
=========================================================
 STUDYFLOW — Focus Timer (Study Session Sub-Phase F + G)
 Timer UI (F) + persistence of completed runs (G).

 The timer is ONLY a UI mechanism for creating a StudySession:
   Focus Timer UI
     → Timer JavaScript (active elapsed, start/end times)
     → POST /study-sessions/from-timer
     → StudySessionController → StudySessionService → MySQL

 Security/business rules live on the server: the authenticated
 user comes from Spring Security and the submitted subject/topic
 ids are re-verified by the service. JavaScript only computes the
 active study time and the wall-clock start/end.
=========================================================
*/

(function () {

    "use strict";

    var MIN_MINUTES = 5;
    var MAX_MINUTES = 180;
    var MAX_DURATION_MINUTES = 1440;
    var SAVE_ENDPOINT = "/study-sessions/from-timer";

    // Single source of truth for every label shown in the UI.
    var STATE_STATUS = {
        READY: "Ready",
        RUNNING: "Focus in progress",
        PAUSED: "Session paused",
        COMPLETED: "Focus session complete",
        STOPPED: "Session stopped"
    };

    // Guarded transitions — invalid moves are ignored.
    var ALLOWED = {
        READY: ["RUNNING"],
        RUNNING: ["PAUSED", "STOPPED", "COMPLETED"],
        PAUSED: ["RUNNING", "STOPPED"],
        COMPLETED: ["READY"],
        STOPPED: ["READY"]
    };

    var overlay = document.getElementById("focusTimerOverlay");
    if (!overlay) {
        return;
    }

    var state = "READY";
    var durationMinutes = 25;
    var remainingSeconds = durationMinutes * 60;
    var endTimestamp = null;
    var tickId = null;
    var lastStatus = "";
    var lastTrigger = null;

    // Persistence bookkeeping (Sub-Phase G).
    var sessionStartedAt = null; // exact Date when RUNNING was first entered
    var saved = false;           // true once this run has been persisted
    var saving = false;          // a save request is currently in flight

    var body = overlay.querySelector(".focus-timer-body");
    var subjectSelect = overlay.querySelector("[data-focus-subject]");
    var topicSelect = overlay.querySelector("[data-focus-topic]");
    var typeSelect = overlay.querySelector("[data-focus-type]");
    var durationInput = overlay.querySelector("[data-focus-timer-duration]");
    var durationDisplay = overlay.querySelector("[data-focus-timer-duration-display]");
    var durationSteps = overlay.querySelectorAll("[data-focus-timer-step]");
    var durationPresets = overlay.querySelectorAll("[data-focus-timer-preset]");
    var display = overlay.querySelector("[data-focus-timer-display]");
    var statusEl = overlay.querySelector("[data-focus-timer-status]");
    var liveRegion = overlay.querySelector("[data-focus-timer-live]");
    var ringProgress = overlay.querySelector("[data-focus-timer-ring]");
    var completeBox = overlay.querySelector("[data-focus-timer-complete]");
    var resultEl = overlay.querySelector("[data-focus-timer-result]");
    var saveErrorEl = overlay.querySelector("[data-focus-timer-save-error]");
    var saveErrorText = overlay.querySelector("[data-focus-timer-save-error-text]");
    var retryBtn = overlay.querySelector("[data-focus-timer-retry]");
    var startBtn = overlay.querySelector("[data-focus-timer-start]");
    var pauseBtn = overlay.querySelector("[data-focus-timer-pause]");
    var resumeBtn = overlay.querySelector("[data-focus-timer-resume]");
    var stopBtn = overlay.querySelector("[data-focus-timer-stop]");
    var resetBtn = overlay.querySelector("[data-focus-timer-reset]");

    var confirmOverlay = document.getElementById("focusTimerConfirmOverlay");
    var confirmModal = confirmOverlay && confirmOverlay.querySelector(".session-modal");

    // Shared subject → topic repopulation (single-sourced in study-session.js).
    var populateTopics = window.studySessionPopulateTopics || function () {};

    function ringRadius() {
        var r = parseFloat(ringProgress.getAttribute("r"));
        return Number.isFinite(r) ? r : 100;
    }

    var ringCircumference = 2 * Math.PI * ringRadius();

    function formatTime(totalSeconds) {
        var t = Math.max(0, Math.floor(totalSeconds));
        var h = Math.floor(t / 3600);
        var m = Math.floor((t % 3600) / 60);
        var s = t % 60;
        var mm = String(m).padStart(2, "0");
        var ss = String(s).padStart(2, "0");
        return h > 0 ? h + ":" + mm + ":" + ss : mm + ":" + ss;
    }

    function announce(message) {
        if (message === lastStatus) {
            return;
        }
        lastStatus = message;
        if (statusEl) {
            statusEl.textContent = message;
        }
        // Aria-live communicates state changes only — never every second.
        if (liveRegion && message !== "") {
            liveRegion.textContent = message + ".";
        }
    }

    function changeState(nextState) {
        if (!ALLOWED[state] || ALLOWED[state].indexOf(nextState) === -1) {
            return false;
        }
        state = nextState;
        return true;
    }

    function isRunning() {
        return state === "RUNNING" || state === "PAUSED";
    }

    /**
     * Actual active study time in seconds.
     *
     * Because the remaining countdown only decreases while RUNNING
     * (pause freezes it, resume continues from the frozen point), the
     * selected total minus the remaining time is EXACTLY the active study
     * time — paused wall-clock time is never included.
     */
    function activeElapsedSeconds() {
        return Math.max(0, durationMinutes * 60 - remainingSeconds);
    }

    /**
     * Project duration rule: round active seconds to the nearest whole
     * minute (half up), with a minimum of 1 so the stored value is always
     * valid for the NOT NULL duration_minutes column. The selected preset
     * is never persisted as-is.
     */
    function roundedDurationMinutes(elapsedSeconds) {
        return Math.max(1, Math.round(elapsedSeconds / 60));
    }

    function clearTick() {
        if (tickId !== null) {
            clearInterval(tickId);
            tickId = null;
        }
    }

    function tick() {
        if (state !== "RUNNING") {
            return;
        }
        remainingSeconds = Math.max(0, Math.ceil((endTimestamp - Date.now()) / 1000));
        render();
        if (remainingSeconds <= 0) {
            clearTick();
            if (changeState("COMPLETED")) {
                announce("Focus session complete");
            }
            render();
            // Timer finished on its own → persist the completed session once.
            persist(); // no-op when subject missing / already saved / saving
        }
    }

    function beginTicking() {
        clearTick();
        tickId = setInterval(tick, 250);
        tick();
    }

    function renderRing() {
        var total = durationMinutes * 60;
        var fraction = total > 0 ? remainingSeconds / total : 0;
        var offset = ringCircumference * (1 - fraction);
        ringProgress.style.strokeDasharray = String(ringCircumference) + "px";
        ringProgress.style.strokeDashoffset = String(Math.max(0, Math.min(ringCircumference, offset))) + "px";
    }

    function render() {
        if (display) {
            display.textContent = formatTime(remainingSeconds);
        }
        renderRing();

        var locked = isRunning() || saving;
        [subjectSelect, topicSelect, typeSelect].forEach(function (el) {
            if (el) {
                el.disabled = locked;
            }
        });
        durationSteps.forEach(function (btn) {
            btn.disabled = locked;
        });
        durationPresets.forEach(function (pill) {
            pill.disabled = locked;
            var isActive = parseInt(pill.getAttribute("data-focus-timer-preset"), 10) === durationMinutes;
            pill.classList.toggle("active", isActive);
        });

        startBtn.disabled = saving;
        pauseBtn.disabled = saving || state !== "RUNNING";
        resumeBtn.disabled = saving || state !== "PAUSED";
        stopBtn.disabled = saving;
        resetBtn.disabled = saving;

        startBtn.hidden = state !== "READY";
        pauseBtn.hidden = state !== "RUNNING";
        resumeBtn.hidden = state !== "PAUSED";
        stopBtn.hidden = state !== "RUNNING" && state !== "PAUSED";
        resetBtn.hidden = state !== "COMPLETED" && state !== "STOPPED";
        completeBox.hidden = state !== "COMPLETED";

        if (resultEl) {
            resultEl.textContent = saved
                ? "Saved to your study history."
                : "Great work.";
        }

        announce(STATE_STATUS[state] || "");

        body.classList.toggle("is-running", state === "RUNNING");
        body.classList.toggle("is-paused", state === "PAUSED");
        body.classList.toggle("is-finished", state === "COMPLETED" || state === "STOPPED");
    }

    function showSaveError(message) {
        if (saveErrorText) {
            saveErrorText.textContent = message;
        }
        if (saveErrorEl) {
            saveErrorEl.hidden = false;
        }
        // A completed-but-unsaved session gets a direct retry action.
        if (retryBtn) {
            retryBtn.hidden = !(state === "COMPLETED" && !saved);
        }
    }

    function clearSaveError() {
        if (saveErrorText) {
            saveErrorText.textContent = "";
        }
        if (saveErrorEl) {
            saveErrorEl.hidden = true;
        }
        if (retryBtn) {
            retryBtn.hidden = true;
        }
    }

    function setDuration(minutes) {
        if (isRunning() || saving) {
            return;
        }
        var clamped = Math.min(MAX_MINUTES, Math.max(MIN_MINUTES, Math.round(minutes)));
        durationMinutes = clamped;
        durationInput.value = String(clamped);
        remainingSeconds = clamped * 60;
        if (durationDisplay) {
            durationDisplay.textContent = clamped < 60
                ? clamped + " min"
                : (clamped % 60 === 0 ? clamped / 60 + "h" : Math.floor(clamped / 60) + "h " + (clamped % 60) + "m");
        }
        render();
    }

    function start() {
        if (state !== "READY" || saving) {
            return;
        }
        if (!subjectSelect.value) {
            showSaveError("Select a subject to start your focus session.");
            subjectSelect.focus();
            return;
        }
        clearSaveError();
        // Begin a new timer run: forget any previous run's persistence state.
        sessionStartedAt = new Date();
        saved = false;
        saving = false;
        endTimestamp = Date.now() + remainingSeconds * 1000;
        if (!changeState("RUNNING")) {
            return;
        }
        announce("Focus in progress");
        beginTicking();
        render();
    }

    function pause() {
        if (state !== "RUNNING" || saving) {
            return;
        }
        remainingSeconds = Math.max(0, Math.ceil((endTimestamp - Date.now()) / 1000));
        clearTick();
        changeState("PAUSED");
        announce("Session paused");
        render();
    }

    function resume() {
        if (state !== "PAUSED" || saving) {
            return;
        }
        endTimestamp = Date.now() + remainingSeconds * 1000;
        changeState("RUNNING");
        announce("Focus in progress");
        beginTicking();
        render();
    }

    // Discard: intentionally finished WITHOUT saving (existing F behavior).
    function discard() {
        clearTick();
        changeState("STOPPED");
        announce("Session stopped");
        render();
    }

    function reset() {
        if (saving) {
            return;
        }
        clearTick();
        state = "READY";
        sessionStartedAt = null;
        saved = false;
        clearSaveError();
        remainingSeconds = durationMinutes * 60;
        announce("Ready");
        render();
    }

    // Stop/Finish is confirmed through the StudyFlow overlay system.
    function askStop() {
        if (saving) {
            return;
        }
        if (!confirmOverlay) {
            discard();
            return;
        }
        confirmOverlay.classList.add("open");
    }

    function closeConfirm() {
        confirmOverlay.classList.remove("open");
    }

    /* ================================================================
       Persistence helpers (Sub-Phase G)
       ================================================================ */

    function pad(n) {
        return String(n).padStart(2, "0");
    }

    function toDateString(date) {
        return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate());
    }

    function toTimeString(date) {
        return pad(date.getHours()) + ":" + pad(date.getMinutes()) + ":" + pad(date.getSeconds());
    }

    function newClientKey() {
        if (window.crypto && typeof window.crypto.randomUUID === "function") {
            return window.crypto.randomUUID();
        }
        return "timer-" + Date.now() + "-" + Math.floor(Math.random() * 1e9);
    }

    function buildTimerPayload(endMoment) {
        var start = sessionStartedAt || new Date();
        return {
            subjectId: subjectSelect.value ? parseInt(subjectSelect.value, 10) : null,
            topicId: topicSelect.value ? parseInt(topicSelect.value, 10) : null,
            sessionType: typeSelect.value || "FOCUS",
            sessionDate: toDateString(start),
            startTime: toTimeString(start),
            endTime: toTimeString(endMoment),
            durationMinutes: roundedDurationMinutes(activeElapsedSeconds()),
            notes: null,
            clientKey: newClientKey()
        };
    }

    /**
     * Send the completed run to the backend. Follows the established
     * StudyFlow AJAX convention from task.js: CSRF token is read from the
     * layout meta tags and sent as a request header (CSRF stays enabled).
     */
    function doSave(payload) {
        var metaToken = document.querySelector('meta[name="_csrf"]');
        var metaHeader = document.querySelector('meta[name="_csrf_header"]');
        var headers = {
            "Content-Type": "application/json",
            "X-Requested-With": "XMLHttpRequest"
        };
        if (metaToken && metaHeader && metaToken.content) {
            headers[metaHeader.content] = metaToken.content;
        }

        return fetch(SAVE_ENDPOINT, {
            method: "POST",
            headers: headers,
            body: JSON.stringify(payload)
        }).then(function (response) {
            return response.json().catch(function () {
                return { success: false, message: "Unable to save study session." };
            }).then(function (data) {
                return { ok: response.ok, status: response.status, data: data };
            });
        });
    }

    function handleSaveResult(result) {
        // 200 → saved. 409 → already saved (duplicate replay) → same outcome.
        if (result.ok || result.status === 409) {
            saved = true;
            clearSaveError();
            showToast("Study session saved successfully!", "success");
            return { saved: true };
        }
        var message = (result.data && result.data.message)
            ? result.data.message
            : "Unable to save study session.";
        showSaveError(message);
        showToast(message, "error");
        return { saved: false };
    }

    /**
     * Attempt to persist the current run. Used by:
     *  - timer reaching 00:00 (auto-save of a completed session)
     *  - the explicit "Save Session" action after Stop
     *  - the Retry Save button on a failed completion save
     */
    function persist() {
        if (saving || saved) {
            return;
        }
        if (!subjectSelect.value) {
            showSaveError("Select a subject to save your session.");
            return;
        }
        var elapsed = activeElapsedSeconds();
        if (elapsed <= 0 || state === "READY") {
            return;
        }

        saving = true;
        render();

        doSave(buildTimerPayload(new Date()))
            .then(function (result) {
                saving = false;
                var outcome = handleSaveResult(result);
                persistFinished(outcome.saved);
                render();
            })
            .catch(function () {
                saving = false;
                handleSaveResult({ ok: false, status: 500, data: { message: "Unable to save study session." } });
                persistFinished(false);
                render();
            });
    }

    /**
     * What happens to the timer when a save attempt ends.
     *
     * Success always lands the run in STOPPED (completed-in-modal runs were
     * already in COMPLETED). Failure leaves the timer untouched so the user
     * can retry immediately — nothing is silently assumed to be stored.
     */
    function persistFinished(outcomeSaved) {
        if (!outcomeSaved) {
            return; // stay RUNNING/PAUSED (explicit stop) or COMPLETED (retry)
        }
        if (state === "RUNNING" || state === "PAUSED") {
            clearTick();
            if (changeState("STOPPED")) {
                announce("Session stopped");
            }
        }
    }

    function openTimer(trigger) {
        lastTrigger = trigger;
        overlay.classList.add("open");
        document.body.classList.add("session-modal-open");
        clearSaveError();
        // Keep any previously prepared selection consistent with the page.
        if (subjectSelect && subjectSelect.value) {
            populateTopics(topicSelect, subjectSelect.value, null);
        }
        requestAnimationFrame(function () {
            if (subjectSelect) {
                subjectSelect.focus();
            }
        });
    }

    function closeTimer() {
        if (saving) {
            return;
        }
        // Never leave an orphaned interval behind after navigation/close.
        if (isRunning()) {
            clearTick();
            state = "READY";
            remainingSeconds = durationMinutes * 60;
            sessionStartedAt = null;
            saved = false;
        }
        clearSaveError();
        closeConfirm();
        overlay.classList.remove("open");
        document.body.classList.remove("session-modal-open");
        if (lastTrigger && lastTrigger.focus) {
            lastTrigger.focus();
        }
        render();
    }

    // Entry point
    document.querySelectorAll("[data-focus-timer-open]").forEach(function (button) {
        button.addEventListener("click", function () {
            openTimer(button);
        });
    });

    // Close controls
    overlay.querySelectorAll("[data-focus-timer-close]").forEach(function (button) {
        button.addEventListener("click", closeTimer);
    });
    overlay.addEventListener("click", function (event) {
        if (event.target === overlay) {
            closeTimer();
        }
    });

    // Subject → Topic (topics embedded server-side, scoped to the user)
    if (subjectSelect && topicSelect) {
        subjectSelect.addEventListener("change", function () {
            populateTopics(topicSelect, subjectSelect.value, null);
        });
    }

    // Duration controls (disabled while running/paused/saving)
    durationSteps.forEach(function (btn) {
        btn.addEventListener("click", function () {
            var delta = parseInt(btn.getAttribute("data-focus-timer-step"), 10);
            setDuration(durationMinutes + delta);
        });
    });
    durationPresets.forEach(function (pill) {
        pill.addEventListener("click", function () {
            setDuration(parseInt(pill.getAttribute("data-focus-timer-preset"), 10));
        });
    });

    // Primary actions
    startBtn.addEventListener("click", start);
    pauseBtn.addEventListener("click", pause);
    resumeBtn.addEventListener("click", resume);
    stopBtn.addEventListener("click", askStop);
    resetBtn.addEventListener("click", reset);
    retryBtn.addEventListener("click", persist);

    // Finish confirmation: Discard vs Save Session.
    if (confirmOverlay) {
        confirmOverlay.querySelectorAll("[data-focus-timer-confirm-discard]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                closeConfirm();
                discard();
            });
        });
        confirmOverlay.querySelector("[data-focus-timer-confirm-save]").addEventListener("click", function () {
            closeConfirm();
            persist();
        });
        confirmOverlay.addEventListener("click", function (event) {
            if (event.target === confirmOverlay) {
                closeConfirm();
            }
        });
        if (confirmModal) {
            confirmModal.addEventListener("keydown", function (event) {
                if (event.key === "Tab") {
                    var focusables = confirmModal.querySelectorAll(
                        'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled])'
                    );
                    if (!focusables.length) {
                        return;
                    }
                    var first = focusables[0];
                    var last = focusables[focusables.length - 1];
                    if (event.shiftKey && document.activeElement === first) {
                        event.preventDefault();
                        last.focus();
                    } else if (!event.shiftKey && document.activeElement === last) {
                        event.preventDefault();
                        first.focus();
                    }
                }
            });
        }
    }

    // Tab visibility: recalibrate immediately when the tab becomes visible.
    document.addEventListener("visibilitychange", function () {
        if (!document.hidden && state === "RUNNING") {
            tick();
        }
    });

    // Leaving the page while a session is active (or a save is in flight)
    // is confirmed by the browser.
    window.addEventListener("beforeunload", function (event) {
        if (isRunning() || saving) {
            event.preventDefault();
            event.returnValue = "";
        }
    });

    // Escape closes the stop-confirmation first, then the timer.
    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape") {
            return;
        }
        if (confirmOverlay && confirmOverlay.classList.contains("open")) {
            closeConfirm();
        } else if (overlay.classList.contains("open") && !saving) {
            closeTimer();
        }
    });

    // Initial paint: READY, 25:00.
    setDuration(25);
    render();

})();