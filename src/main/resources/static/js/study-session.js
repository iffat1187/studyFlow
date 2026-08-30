/*
=========================================================
 STUDYFLOW — Study Session Module JavaScript
 Study Session-specific UI interactions only.
 No backend business logic lives here.
=========================================================
*/

(function () {

    "use strict";

    var MIN_DURATION = 15;
    var MAX_DURATION = 600;

    var topicsBySubject = window.studySessionTopics || {};

    var addOverlay = document.querySelector(".session-modal-overlay");
    var confirmOverlay = document.getElementById("sessionConfirmOverlay");
    var editForm = document.querySelector("[data-session-edit-form]");

    var addSubjectSelect = null;
    var addTopicSelect = null;
    var lastTrigger = null;
    var confirmLastTrigger = null;


    /* =============================
       Focus trap
    ============================= */

    function trapFocus(event, root) {
        if (event.key !== "Tab") {
            return;
        }
        var focusables = root.querySelectorAll(
            'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled])'
        );
        if (focusables.length === 0) {
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


    /* =============================
       Duration & end-time controls
    ============================= */

    function initDuration(root) {
        var durationInput = root.querySelector("[data-duration-input]");
        if (!durationInput) {
            return;
        }
        var durationDisplay = root.querySelector("[data-duration-display]");
        var durationPills = root.querySelectorAll("[data-duration]");
        var durationSteppers = root.querySelectorAll("[data-duration-step]");
        var startInput = root.querySelector("[data-session-start-input]");
        var endHelper = root.querySelector("[data-session-end-helper]");

        function clampDuration(value) {
            if (Number.isNaN(value)) {
                return MIN_DURATION;
            }
            return Math.min(MAX_DURATION, Math.max(MIN_DURATION, value));
        }

        function getDuration() {
            return clampDuration(parseInt(durationInput.value, 10) || 60);
        }

        function formatMinutes(minutes) {
            if (minutes < 60) {
                return minutes + "m";
            }
            var hours = Math.floor(minutes / 60);
            var rest = minutes % 60;
            return rest === 0 ? hours + "h" : hours + "h " + rest + "m";
        }

        function toTwelveHour(totalMinutes) {
            var rolled = false;
            if (totalMinutes >= 1440) {
                rolled = true;
                totalMinutes = totalMinutes % 1440;
            }
            var period = totalMinutes >= 720 ? "PM" : "AM";
            var h = Math.floor(totalMinutes / 60) % 12;
            if (h === 0) {
                h = 12;
            }
            var m = String(totalMinutes % 60).padStart(2, "0");
            return h + ":" + m + " " + period + (rolled ? " (next day)" : "");
        }

        function updateEndTime() {
            if (!startInput || !endHelper) {
                return;
            }
            var raw = startInput.value;
            if (!raw) {
                endHelper.textContent = "Pick a start time to see when this session ends.";
                endHelper.classList.remove("has-end-time");
                return;
            }
            var parts = raw.split(":");
            var minutes = parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);
            if (Number.isNaN(minutes)) {
                endHelper.textContent = "Pick a start time to see when this session ends.";
                endHelper.classList.remove("has-end-time");
                return;
            }
            endHelper.textContent = "Ends at " + toTwelveHour(minutes + getDuration());
            endHelper.classList.add("has-end-time");
        }

        function syncDuration() {
            var minutes = getDuration();
            durationInput.value = minutes;
            if (durationDisplay) {
                durationDisplay.textContent = formatMinutes(minutes);
            }
            durationPills.forEach(function (pill) {
                pill.classList.toggle("active", parseInt(pill.getAttribute("data-duration"), 10) === minutes);
            });
            updateEndTime();
        }

        durationSteppers.forEach(function (button) {
            button.addEventListener("click", function () {
                var delta = parseInt(button.getAttribute("data-duration-step"), 10);
                durationInput.value = clampDuration(getDuration() + delta);
                syncDuration();
            });
        });

        durationPills.forEach(function (pill) {
            pill.addEventListener("click", function () {
                durationInput.value = clampDuration(parseInt(pill.getAttribute("data-duration"), 10));
                syncDuration();
            });
        });

        if (startInput) {
            startInput.addEventListener("change", updateEndTime);
            startInput.addEventListener("input", updateEndTime);
        }

        // Keep display/pills/end-time in sync with the bound duration value.
        syncDuration();
    }


    /* =============================
       Subject → Topic behavior
       (options come from the topics
       embedded server-side, scoped
       to the current user)
    ============================= */

    function populateTopics(topicSelect, subjectId, keepSelectedId) {
        if (!topicSelect) {
            return;
        }

        // Remove previously added topic options (keep "No specific topic").
        topicSelect.querySelectorAll("option[data-session-topic]").forEach(function (option) {
            option.remove();
        });

        var topics = (subjectId && topicsBySubject[String(subjectId)]) || [];

        topics.forEach(function (topic) {
            var option = document.createElement("option");
            option.value = String(topic.id);
            option.textContent = topic.name;
            option.setAttribute("data-session-topic", "");
            topicSelect.appendChild(option);
        });

        if (keepSelectedId == null || String(keepSelectedId) === "null") {
            topicSelect.value = "";
            return;
        }

        var found = false;
        for (var i = 0; i < topicSelect.options.length; i++) {
            if (String(topicSelect.options[i].value) === String(keepSelectedId)) {
                found = true;
                break;
            }
        }
        topicSelect.value = found ? String(keepSelectedId) : "";
    }


    /* =============================
       Start Study Session modal
    ============================= */

    if (addOverlay) {
        var addModal = addOverlay.querySelector(".session-modal");
        addSubjectSelect = addOverlay.querySelector("#sessionSubject");
        addTopicSelect = addOverlay.querySelector("#sessionTopic");

        function openModal(trigger) {
            lastTrigger = trigger;
            addOverlay.classList.add("open");
            document.body.classList.add("session-modal-open");
            requestAnimationFrame(function () {
                if (addSubjectSelect) {
                    addSubjectSelect.focus();
                }
            });
        }

        function closeModal() {
            addOverlay.classList.remove("open");
            document.body.classList.remove("session-modal-open");
            if (lastTrigger && lastTrigger.focus) {
                lastTrigger.focus();
            }
        }

        // Open buttons (page header + empty state)
        document.querySelectorAll("[data-session-open]").forEach(function (button) {
            button.addEventListener("click", function () {
                openModal(button);
            });
        });

        // Close buttons + Cancel
        addOverlay.querySelectorAll("[data-session-modal-close]").forEach(function (button) {
            button.addEventListener("click", closeModal);
        });

        // Backdrop click (clicking the dialog itself does not close)
        addOverlay.addEventListener("click", function (event) {
            if (event.target === addOverlay) {
                closeModal();
            }
        });

        // Focus trap while open
        addModal.addEventListener("keydown", function (event) {
            trapFocus(event, addModal);
        });

        // Subject changes refresh the available topic options.
        if (addSubjectSelect) {
            addSubjectSelect.addEventListener("change", function () {
                populateTopics(addTopicSelect, addSubjectSelect.value, null);
            });
        }

        // Duration + end-time controls inside the modal
        initDuration(addOverlay);

        // Auto-open the modal when the server re-renders with validation errors.
        if (addOverlay.classList.contains("open")) {
            openModal(null);
        }

        // Render topics for a pre-selected subject (deep link or error re-render).
        if (addSubjectSelect && addSubjectSelect.value) {
            populateTopics(addTopicSelect, addSubjectSelect.value, window.studySessionSelectedTopic);
        }
    }


    /* =============================
       Delete Study Session
       confirmation (same overlay
       system as the Add modal — no
       second framework.)
    ============================= */

    if (confirmOverlay) {
        var confirmModal = confirmOverlay.querySelector(".session-modal");
        var confirmName = confirmOverlay.querySelector("[data-session-confirm-name]");
        var deleteForm = document.getElementById("sessionDeleteForm");

        function openConfirm(trigger) {
            confirmLastTrigger = trigger;
            confirmOverlay.classList.add("open");
            document.body.classList.add("session-modal-open");
            requestAnimationFrame(function () {
                var deleteButton = deleteForm && deleteForm.querySelector("[type='submit']");
                if (deleteButton) {
                    deleteButton.focus();
                }
            });
        }

        function closeConfirm() {
            confirmOverlay.classList.remove("open");
            document.body.classList.remove("session-modal-open");
            if (confirmLastTrigger && confirmLastTrigger.focus) {
                confirmLastTrigger.focus();
            }
        }

        // Delete buttons on the session cards
        document.querySelectorAll("[data-session-delete]").forEach(function (button) {
            button.addEventListener("click", function () {
                if (confirmName) {
                    confirmName.textContent = button.getAttribute("data-session-name") || "this study session";
                }
                if (deleteForm) {
                    deleteForm.setAttribute("action", "/study-sessions/delete/" + button.getAttribute("data-session-id"));
                }
                openConfirm(button);

                // Escaping focus into the dialog ensures the delete target is
                // always the session the user clicked.
                requestAnimationFrame(function () {
                    var deleteButton = deleteForm && deleteForm.querySelector("[type='submit']");
                    if (deleteButton) {
                        deleteButton.focus();
                    }
                });
            });
        });

        // Close buttons + Cancel
        confirmOverlay.querySelectorAll("[data-session-confirm-close]").forEach(function (button) {
            button.addEventListener("click", closeConfirm);
        });

        // Backdrop click
        confirmOverlay.addEventListener("click", function (event) {
            if (event.target === confirmOverlay) {
                closeConfirm();
            }
        });

        // Focus trap while open
        confirmModal.addEventListener("keydown", function (event) {
            trapFocus(event, confirmModal);
        });
    }


    /* =============================
       Edit Study Session page
       (duration control + subject →
       topic wiring; no overlay on
       this page.)
    ============================= */

    if (editForm) {
        var editSubjectSelect = editForm.querySelector("#sessionSubject");
        var editTopicSelect = editForm.querySelector("#sessionTopic");

        initDuration(editForm);

        if (editSubjectSelect && editTopicSelect) {
            editSubjectSelect.addEventListener("change", function () {
                populateTopics(editTopicSelect, editSubjectSelect.value, null);
            });
        }

        // Render topics for the pre-selected subject, keeping the bound topic
        // (also covers a validation re-render after a failed submit).
        if (editSubjectSelect && editSubjectSelect.value) {
            populateTopics(editTopicSelect, editSubjectSelect.value, window.studySessionEditSelectedTopic);
        }
    }


    /* =============================
       Escape closes any open dialog
    ============================= */

    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape") {
            return;
        }
        if (confirmOverlay && confirmOverlay.classList.contains("open")) {
            closeConfirm();
        } else if (addOverlay && addOverlay.classList.contains("open")) {
            closeModal();
        }
    });

    // Shared with the Focus Timer (sub-phase F): subject → topic repopulation
    // is single-sourced here instead of being duplicated per module.
    window.studySessionPopulateTopics = populateTopics;

})();