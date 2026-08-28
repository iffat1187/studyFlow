/*
=====================================================
        StudyFlow - Task Module JavaScript
=====================================================
Modules:
  1. Task Progress Slider
  2. Task Completion
  3. Form Enhancements
=====================================================
*/

(function () {

    "use strict";

    // Get CSRF token from meta tags
    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
    const csrfParameter = document.querySelector('meta[name="_csrf_parameter"]').content;

    function getCsrfBody() {
        // Include CSRF token in request body for Spring Security CsrfTokenRequestAttributeHandler
        if (csrfToken && csrfParameter) {
            return `${csrfParameter}=${encodeURIComponent(csrfToken)}`;
        }
        return "";
    }

    function getHeaders() {
        const headers = {
            "Content-Type": "application/x-www-form-urlencoded",
            "X-Requested-With": "XMLHttpRequest"
        };
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }
        return headers;
    }

    /*
    =====================================================
     1. Task Progress Slider
    =====================================================
    */

    function initializeTaskProgress() {

        const sliders = document.querySelectorAll(".progress-slider");

        if (sliders.length === 0) {
            return;
        }

        sliders.forEach((slider) => {

            const taskId = slider.dataset.taskId;

            if (!taskId) {
                return;
            }

            // Debounce the progress update
            let updateTimer;
            const debouncedUpdate = (value) => {
                clearTimeout(updateTimer);
                updateTimer = setTimeout(() => {
                    updateTaskProgress(taskId, value);
                }, 400);
            };

            slider.addEventListener("input", function () {
                const value = parseInt(this.value, 10);
                debouncedUpdate(value);
            });

            // Also handle change event for keyboard/mobile
            slider.addEventListener("change", function () {
                clearTimeout(updateTimer);
                const value = parseInt(this.value, 10);
                updateTaskProgress(taskId, value);
            });

        });

    }

    async function updateTaskProgress(taskId, progress) {

        try {
            const response = await fetch(`/tasks/${taskId}/progress`, {
                method: "POST",
                headers: getHeaders(),
                body: `progress=${progress}&${getCsrfBody()}`
            });

            if (!response.ok) {
                throw new Error("Failed to update progress");
            }

            const data = await response.json();

            // Update the UI with returned data
            const card = document.querySelector(`.progress-slider[data-task-id="${taskId}"]`).closest(".task-card");

            if (card) {
                const progressBar = card.querySelector(".progress-bar");
                const progressText = card.querySelector(".progress-text");
                const statusBadge = card.querySelector(".status-badge");

                if (progressBar && progressText) {
                    progressBar.style.width = data.progress + "%";
                    progressText.textContent = data.progress + "%";

                    if (data.completed) {
                        progressBar.classList.add("completed");
                        if (statusBadge) {
                            statusBadge.classList.remove("pending");
                            statusBadge.classList.add("completed");
                            statusBadge.textContent = "Completed";
                            statusBadge.innerHTML = '<i class="bi bi-check-circle-fill"></i> Completed';
                        }

                        // Show congratulations
                        showCongratulations(data.title || "Task");
                    }
                }
            }

        } catch (err) {
            console.error("Progress update failed:", err);
            showToast("Failed to update progress", "error");

            // Reset slider to previous value
            const slider = document.querySelector(`.progress-slider[data-task-id="${taskId}"]`);
            if (slider) {
                // We don't know the previous value, so just refresh the page or show error
            }
        }

    }

/*
=====================================================
     2. Task Completion
=====================================================
*/

function initializeTaskCompletion() {

    // Mark Complete button handler
    document.addEventListener("click", function (event) {
        const completeBtn = event.target.closest(".action-btn.complete");
        if (!completeBtn) {
            return;
        }

        const taskId = completeBtn.dataset.taskId;
        if (!taskId) {
            return;
        }

        completeTask(taskId, completeBtn);
    });

    // Also handle progress slider reaching 100%
    document.addEventListener("change", function (event) {
        if (event.target.classList.contains("progress-slider")) {
            const slider = event.target;
            const taskId = slider.dataset.taskId;
            const progress = parseInt(slider.value, 10);

            if (progress >= 100 && taskId) {
                completeTaskViaSlider(taskId, slider);
            }
        }
    });
}

async function completeTask(taskId, button) {

        try {
            const response = await fetch(`/tasks/${taskId}/complete`, {
                method: "POST",
                headers: getHeaders(),
                body: getCsrfBody()
            });

        if (!response.ok) {
            throw new Error("Failed to complete task");
        }

        const data = await response.json();

        // Update the UI with returned data
        const card = button.closest(".task-card");

        if (card) {
            updateTaskCardOnComplete(card, data);

            // Show congratulations
            showCongratulations(data.title || "Task");
        }

    } catch (err) {
        console.error("Task completion failed:", err);
        showToast("Failed to complete task", "error");
    }
}

async function completeTaskViaSlider(taskId, slider) {

        try {
            const response = await fetch(`/tasks/${taskId}/complete`, {
                method: "POST",
                headers: getHeaders(),
                body: getCsrfBody()
            });

        if (!response.ok) {
            throw new Error("Failed to complete task");
        }

        const data = await response.json();

        // Update the UI with returned data
        const card = slider.closest(".task-card");

        if (card) {
            updateTaskCardOnComplete(card, data);

            // Show congratulations
            showCongratulations(data.title || "Task");
        }

    } catch (err) {
        console.error("Task completion failed:", err);
        showToast("Failed to complete task", "error");
    }
}

function updateTaskCardOnComplete(card, data) {

    const progressBar = card.querySelector(".progress-bar");
    const progressText = card.querySelector(".progress-text");
    const completeBtn = card.querySelector(".action-btn.complete");
    const slider = card.querySelector(".progress-slider");

    if (progressBar && progressText) {
        progressBar.style.width = "100%";
        progressBar.classList.add("completed");
        progressText.textContent = "100%";
    }

    // Hide the complete button
    if (completeBtn) {
        completeBtn.style.display = "none";
    }

    // Disable the slider
    if (slider) {
        slider.disabled = true;
        slider.classList.add("disabled");
    }

    // Add completed animation
    card.classList.add("task-completed");
    setTimeout(() => card.classList.remove("task-completed"), 1000);
}

/*
=====================================================
     3. Form Enhancements
=====================================================
*/

    function initializeTaskForm() {

        // Set default due date to tomorrow if not set
        const dueDateInput = document.getElementById("dueDate");
        if (dueDateInput && !dueDateInput.value) {
            const tomorrow = new Date();
            tomorrow.setDate(tomorrow.getDate() + 1);
            dueDateInput.value = tomorrow.toISOString().split("T")[0];
        }

        // Ensure recurrence type defaults to ONCE
        const recurrenceSelect = document.getElementById("recurrenceType");
        if (recurrenceSelect && !recurrenceSelect.value) {
            recurrenceSelect.value = "ONCE";
        }

        // Ensure type select defaults to first option
        const typeSelect = document.getElementById("type");
        if (typeSelect && !typeSelect.value) {
            typeSelect.value = "ASSIGNMENT";
        }

    }

    /*
    =====================================================
     4. Congratulations Feedback
    =====================================================
    */

    function showCongratulations(taskTitle) {

        // Create a small celebration toast
        const messages = [
            `🎉 Great job! "${taskTitle}" completed.`,
            `✨ Well done! Task finished.`,
            `🎊 Excellent! One more task down.`,
            `🌟 Awesome! Keep the momentum going.`
        ];

        const randomMessage = messages[Math.floor(Math.random() * messages.length)];

        showToast(randomMessage, "success", { duration: 4000 });

        // Add a subtle animation to the completed card
        const card = document.querySelector(`.progress-slider[data-task-id]`).closest(".task-card");
        if (card) {
            card.classList.add("task-completed");
            setTimeout(() => card.classList.remove("task-completed"), 1000);
        }
    }

    /*
    =====================================================
     Bootstrap
    =====================================================
    */

    document.addEventListener("DOMContentLoaded", () => {

        initializeTaskProgress();
        initializeTaskCompletion();
        initializeTaskForm();

    });

})();