/*
=========================================================
 STUDYFLOW — Exam Module JavaScript
 Exam-specific UI interactions only.
 No backend business logic lives here.
=========================================================
*/

(function () {

    "use strict";

    var MIN_DURATION = 15;
    var MAX_DURATION = 600;

    var addOverlay = document.querySelector(".exam-modal-overlay");
    var confirmOverlay = document.getElementById("examConfirmOverlay");
    var addLastTrigger = null;
    var confirmLastTrigger = null;


    /* =============================
       Focus trap (shared by modals)
    ============================= */

    function trapFocus(event, modal) {
        if (event.key !== "Tab") {
            return;
        }
        var focusables = modal.querySelectorAll(
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
       Shared by the Add Exam modal
       and the Edit Exam page.
    ============================= */

    function initDuration(root) {
        var durationInput = root.querySelector("[data-duration-input]");
        if (!durationInput) {
            return;
        }
        var durationDisplay = root.querySelector("[data-duration-display]");
        var durationPills = root.querySelectorAll("[data-duration]");
        var durationSteppers = root.querySelectorAll("[data-duration-step]");
        var timeInput = root.querySelector("[data-time-input]");
        var endHelper = root.querySelector("[data-end-helper]");

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
            if (!timeInput || !endHelper) {
                return;
            }
            var raw = timeInput.value;
            if (!raw) {
                endHelper.textContent = "Pick a time to see when this exam ends.";
                endHelper.classList.remove("has-end-time");
                return;
            }
            var parts = raw.split(":");
            var minutes = parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);
            if (Number.isNaN(minutes)) {
                endHelper.textContent = "Pick a time to see when this exam ends.";
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

        if (timeInput) {
            timeInput.addEventListener("change", updateEndTime);
            timeInput.addEventListener("input", updateEndTime);
        }

        // Keep display/pills/end-time in sync with the bound duration value.
        syncDuration();
    }


    /* =============================
       Add Exam modal
    ============================= */

    if (addOverlay) {
        var addModal = addOverlay.querySelector(".exam-modal");
        var nameInput = document.getElementById("examName");

        function openExamModal(trigger) {
            addLastTrigger = trigger;
            addOverlay.classList.add("open");
            document.body.classList.add("exam-modal-open");
            requestAnimationFrame(function () {
                if (nameInput) {
                    nameInput.focus();
                }
            });
        }

        function closeExamModal() {
            addOverlay.classList.remove("open");
            document.body.classList.remove("exam-modal-open");
            if (addLastTrigger && addLastTrigger.focus) {
                addLastTrigger.focus();
            }
        }

        // Open buttons
        document.querySelectorAll("[data-exam-open]").forEach(function (button) {
            button.addEventListener("click", function () {
                openExamModal(button);
            });
        });

        // Close buttons + Cancel
        addOverlay.querySelectorAll("[data-exam-modal-close]").forEach(function (button) {
            button.addEventListener("click", closeExamModal);
        });

        // Backdrop click (clicking the dialog itself does not close)
        addOverlay.addEventListener("click", function (event) {
            if (event.target === addOverlay) {
                closeExamModal();
            }
        });

        // Focus trap while open
        addModal.addEventListener("keydown", function (event) {
            trapFocus(event, addModal);
        });

        // Duration + end-time controls inside the modal
        initDuration(addOverlay);

        // Auto-open the modal when the server re-renders with validation errors.
        if (addOverlay.classList.contains("open")) {
            openExamModal(null);
        }
    }


    /* =============================
       Delete Exam confirmation
       (same overlay/modal system as
       the Add Exam modal — no second
       framework.)
    ============================= */

    if (confirmOverlay) {
        var confirmModal = confirmOverlay.querySelector(".exam-modal");
        var confirmName = confirmOverlay.querySelector("[data-exam-confirm-name]");
        var deleteForm = document.getElementById("examDeleteForm");

        function openConfirm(trigger) {
            confirmLastTrigger = trigger;
            confirmOverlay.classList.add("open");
            document.body.classList.add("exam-modal-open");
            requestAnimationFrame(function () {
                var deleteButton = deleteForm && deleteForm.querySelector("[type='submit']");
                if (deleteButton) {
                    deleteButton.focus();
                }
            });
        }

        function closeConfirm() {
            confirmOverlay.classList.remove("open");
            document.body.classList.remove("exam-modal-open");
            if (confirmLastTrigger && confirmLastTrigger.focus) {
                confirmLastTrigger.focus();
            }
        }

        // Delete buttons on the exam cards
        document.querySelectorAll("[data-exam-delete]").forEach(function (button) {
            button.addEventListener("click", function () {
                if (confirmName) {
                    confirmName.textContent = button.getAttribute("data-exam-name");
                }
                if (deleteForm) {
                    deleteForm.setAttribute("action", "/exams/delete/" + button.getAttribute("data-exam-id"));
                }
                openConfirm(button);

                // Escaping focus into the dialog ensures the delete target is
                // always the exam the user clicked.
                requestAnimationFrame(function () {
                    var deleteButton = deleteForm && deleteForm.querySelector("[type='submit']");
                    if (deleteButton) {
                        deleteButton.focus();
                    }
                });
            });
        });

        // Close buttons + Cancel
        confirmOverlay.querySelectorAll("[data-exam-confirm-close]").forEach(function (button) {
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
       Edit Exam page
       (duration controls only — no
       overlay on this page.)
    ============================= */

    var editForm = document.querySelector("[data-exam-edit-form]");
    if (editForm) {
        initDuration(editForm);
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
            closeExamModal();
        }
    });

})();