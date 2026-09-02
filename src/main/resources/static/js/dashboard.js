/*
=====================================================
        StudyFlow - Dashboard JavaScript
=====================================================
Modules:
  1. Setup Progress Animation
  2. Dashboard Task Completion
=====================================================
This file handles small UI interactions only. All
statistics come from the server; no data is generated
or hard-coded on the client.
=====================================================
*/

(function () {

    "use strict";

    const csrfToken   = document.querySelector('meta[name="_csrf"]')?.content   || "";
    const csrfHeader  = document.querySelector('meta[name="_csrf_header"]')?.content  || "";
    const csrfParam   = document.querySelector('meta[name="_csrf_parameter"]')?.content || "";

    function getHeaders() {
        const h = {
            "Content-Type": "application/x-www-form-urlencoded",
            "X-Requested-With": "XMLHttpRequest"
        };
        if (csrfToken && csrfHeader) {
            h[csrfHeader] = csrfToken;
        }
        return h;
    }

    function getCsrfBody() {
        return csrfParam && csrfToken
            ? `${csrfParam}=${encodeURIComponent(csrfToken)}`
            : "";
    }


    /*
    =====================================================
     1. Setup Progress Animation
    =====================================================
    */

    function initializeSetupProgress() {

        const fill = document.querySelector(".setup-progress__fill");

        if (!fill) {
            return;
        }

        const target = parseInt(fill.getAttribute("data-percent"), 10);

        if (isNaN(target)) {
            return;
        }

        const targetWidth = Math.max(0, Math.min(100, target)) + "%";

        fill.style.width = "0%";
        fill.classList.add("is-animating");

        void fill.offsetWidth;

        fill.style.width = targetWidth;

    }


    /*
    =====================================================
     2. Dashboard Task Completion
     Reuses the same /tasks/{id}/complete endpoint as
     task.js. On success the checkbox row is visually
     updated: the checkbox hides, a filled check icon
     appears, the row gets the is-completed class, and
     the "X of Y done" header count, the Today's Tasks
     progress meter and the summary card are refreshed.
    =====================================================
    */

    function initializeDashboardTaskCompletion() {

        document.addEventListener("change", function (event) {

            const cb = event.target;
            if (!cb.classList.contains("dash-task-item__checkbox")) {
                return;
            }
            if (!cb.checked) {
                return;
            }

            const taskId = cb.dataset.taskId;
            if (!taskId) {
                return;
            }

            const item = cb.closest(".dash-task-item");
            if (!item) {
                return;
            }

            completeDashboardTask(taskId, item, cb);
        });

    }

    async function completeDashboardTask(taskId, item, checkbox) {

        try {
            const response = await fetch(`/tasks/${taskId}/complete`, {
                method: "POST",
                headers: getHeaders(),
                body: getCsrfBody()
            });

            if (!response.ok) {
                throw new Error("Failed to complete task");
            }

            // Update row visuals
            item.classList.add("is-completed");

            // Replace circle with filled check
            const checkArea = item.querySelector(".dash-task-item__check");
            if (checkArea && checkArea.contains(checkbox)) {
                checkArea.innerHTML =
                    '<i class="bi bi-check-circle-fill"></i>';
            }

            // Hide progress bar if present
            const progress = item.querySelector(".dash-task-item__progress");
            if (progress) {
                progress.style.display = "none";
            }

            // Refresh the "X of Y done" count, progress meter and
            // summary card in the Today's Tasks section
            refreshTodayTaskCount();

        } catch (err) {
            console.error("Dashboard task completion failed:", err);
            checkbox.checked = false;
        }

    }

    function refreshTodayTaskCount() {

        const header = document.querySelector("#tasksTitle");
        if (!header) {
            return;
        }
        const card = header.closest(".dash-card");
        if (!card) {
            return;
        }

        const all   = card.querySelectorAll(".dash-task-item");
        const total = all.length;
        const doneN = card.querySelectorAll(".dash-task-item.is-completed").length;
        const percent = total > 0 ? Math.round((doneN * 100) / total) : 0;

        const badge = document.getElementById("todayTaskCountBadge");
        if (badge) {
            badge.textContent = `${doneN} of ${total} done`;
        }

        const progressCount = document.querySelector(".dash-task-progress__count");
        if (progressCount) {
            progressCount.textContent = `${doneN} of ${total} completed`;
        }

        const fill = document.getElementById("todayTaskProgressFill");
        if (fill) {
            fill.style.width = percent + "%";
            fill.setAttribute("data-percent", percent);
            const track = fill.closest(".dash-task-progress__track");
            if (track) {
                track.setAttribute("aria-valuenow", percent);
            }
        }

        const summary = document.getElementById("summaryTasksValue");
        if (summary) {
            summary.textContent = `${doneN} / ${total}`;
            summary.setAttribute("data-done", doneN);
            summary.setAttribute("data-total", total);
        }
    }


    /*
    =====================================================
     Bootstrap
    =====================================================
    */

    document.addEventListener("DOMContentLoaded", () => {
        initializeSetupProgress();
        initializeDashboardTaskCompletion();
    });

})();
