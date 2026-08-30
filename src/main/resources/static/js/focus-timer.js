/*
=========================================================
 STUDYFLOW — Focus Timer (Study Session Sub-Phase F)
 Client-side timer state management only.

 No persistence: this phase deliberately does NOT write to
 StudySession. Subject/Topic/Type selections are UI-state
 preparation for the future persistence phase.
=========================================================
*/

(function () {

    "use strict";

    var MIN_MINUTES = 5;
    var MAX_MINUTES = 180;

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

        var locked = isRunning();
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

        startBtn.hidden = state !== "READY";
        pauseBtn.hidden = state !== "RUNNING";
        resumeBtn.hidden = state !== "PAUSED";
        stopBtn.hidden = state !== "RUNNING" && state !== "PAUSED";
        resetBtn.hidden = state !== "COMPLETED" && state !== "STOPPED";
        completeBox.hidden = state !== "COMPLETED";

        announce(STATE_STATUS[state] || "");

        body.classList.toggle("is-running", state === "RUNNING");
        body.classList.toggle("is-paused", state === "PAUSED");
        body.classList.toggle("is-finished", state === "COMPLETED" || state === "STOPPED");
    }

    function setDuration(minutes) {
        if (isRunning()) {
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
        if (state !== "READY") {
            return;
        }
        endTimestamp = Date.now() + remainingSeconds * 1000;
        if (!changeState("RUNNING")) {
            return;
        }
        announce("Focus in progress");
        beginTicking();
        render();
    }

    function pause() {
        if (state !== "RUNNING") {
            return;
        }
        remainingSeconds = Math.max(0, Math.ceil((endTimestamp - Date.now()) / 1000));
        clearTick();
        changeState("PAUSED");
        announce("Session paused");
        render();
    }

    function resume() {
        if (state !== "PAUSED") {
            return;
        }
        endTimestamp = Date.now() + remainingSeconds * 1000;
        changeState("RUNNING");
        announce("Focus in progress");
        beginTicking();
        render();
    }

    function doStop() {
        clearTick();
        changeState("STOPPED");
        announce("Session stopped");
        render();
    }

    function reset() {
        clearTick();
        state = "READY";
        remainingSeconds = durationMinutes * 60;
        announce("Ready");
        render();
    }

    // Stop is confirmed through the StudyFlow overlay system (no confirm()).
    function askStop() {
        if (!confirmOverlay) {
            doStop();
            return;
        }
        confirmOverlay.classList.add("open");
    }

    function closeConfirm() {
        confirmOverlay.classList.remove("open");
    }

    function openTimer(trigger) {
        lastTrigger = trigger;
        overlay.classList.add("open");
        document.body.classList.add("session-modal-open");
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
        // Never leave an orphaned interval behind after navigation/close.
        if (isRunning()) {
            clearTick();
            state = "READY";
            remainingSeconds = durationMinutes * 60;
        }
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

    // Duration controls (disabled while running/paused)
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

    // Stop confirmation
    if (confirmOverlay) {
        confirmOverlay.querySelectorAll("[data-focus-timer-confirm-cancel]").forEach(function (btn) {
            btn.addEventListener("click", closeConfirm);
        });
        confirmOverlay.querySelector("[data-focus-timer-confirm-stop]").addEventListener("click", function () {
            closeConfirm();
            doStop();
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

    // Leaving the page while a session is active is confirmed by the browser.
    window.addEventListener("beforeunload", function (event) {
        if (isRunning()) {
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
        } else if (overlay.classList.contains("open")) {
            closeTimer();
        }
    });

    // Initial paint: READY, 25:00.
    setDuration(25);
    render();

})();