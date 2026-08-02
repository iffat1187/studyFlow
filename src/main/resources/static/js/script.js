/*
=====================================================
                StudyFlow JavaScript
=====================================================
*/

document.addEventListener("DOMContentLoaded", () => {

    initializeSidebar();

    initializeColorPicker();

    initializeDeleteConfirmation();

    initializeSearch();

});


/*
=====================================================
 Sidebar Toggle
=====================================================
*/

function initializeSidebar() {

    const menuToggle = document.getElementById("menuToggle");
    const sidebar = document.querySelector(".sidebar");

    if (!menuToggle || !sidebar) {
        return;
    }

    menuToggle.addEventListener("click", () => {
        sidebar.classList.toggle("active");
    });

}


/*
=====================================================
 Subject Color Picker
=====================================================
*/

function initializeColorPicker() {

    const colorButtons = document.querySelectorAll(".color-circle");
    const selectedColorInput = document.getElementById("selectedColor");

    if (!selectedColorInput || colorButtons.length === 0) {
        return;
    }

    // Highlight selected color on Edit page
    colorButtons.forEach(button => {

        if (button.dataset.color === selectedColorInput.value) {
            button.classList.add("selected");
        }

    });

    // Change color on click
    colorButtons.forEach(button => {

        button.addEventListener("click", () => {

            colorButtons.forEach(btn =>
                btn.classList.remove("selected")
            );

            button.classList.add("selected");

            selectedColorInput.value = button.dataset.color;

        });

    });

}


/*
=====================================================
 Delete Confirmation
=====================================================
*/

function initializeDeleteConfirmation() {

    const deleteButtons = document.querySelectorAll(".delete");

    deleteButtons.forEach(button => {

        button.addEventListener("click", function (event) {

            const confirmed = confirm(
                "Are you sure you want to delete this item?"
            );

            if (!confirmed) {
                event.preventDefault();
            }

        });

    });

}


/*
=====================================================
 Subject Search (Future Ready)
=====================================================
*/

function initializeSearch() {

    const searchInput = document.getElementById("subjectSearch");

    if (!searchInput) {
        return;
    }

    searchInput.addEventListener("keyup", function () {

        const keyword = this.value.toLowerCase();

        const cards = document.querySelectorAll(".subject-card");

        cards.forEach(card => {

            const text = card.innerText.toLowerCase();

            if (text.includes(keyword)) {
                card.style.display = "";
            } else {
                card.style.display = "none";
            }

        });

    });

}


/*
=====================================================
 Toast Notification
=====================================================
*/

function showToast(message, type = "success") {

    const toastContainer = document.querySelector(".toast-container");

    if (!toastContainer) {
        return;
    }

    const icon =
        type === "success"
            ? "bi-check-circle-fill"
            : "bi-exclamation-circle-fill";

    const toast = document.createElement("div");

    toast.className = `toast ${type}`;

    toast.innerHTML = `
        <i class="bi ${icon}"></i>
        <span>${message}</span>
    `;

    toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.classList.add("show");
    }, 100);

    setTimeout(() => {

        toast.classList.remove("show");

        setTimeout(() => {
            toast.remove();
        }, 300);

    }, 3000);

}

/* ==========================
   Scroll Progress
========================== */

const progress = document.getElementById("scrollProgress");

window.addEventListener("scroll", () => {

    const total =
        document.documentElement.scrollHeight -
        document.documentElement.clientHeight;

    const percent =
        (window.scrollY / total) * 100;

    progress.style.width = percent + "%";

});