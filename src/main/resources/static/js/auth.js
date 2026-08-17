/*
=====================================================
        StudyFlow - Authentication Pages JavaScript
=====================================================
Module:
  1. Password visibility toggle (show/hide eye button)

Reused by the login and registration pages.
Does not interfere with Spring Security form processing,
validation or submission.
=====================================================
*/

(function () {

    "use strict";

    /*
     1. Password Visibility Toggle
     Toggles a password input between type="password" and
     type="text", synchronizes the eye icon and updates
     accessibility attributes.
    */

    function togglePassword(button) {

        const wrapper = button.closest(".password-wrapper");

        if (!wrapper) {
            return;
        }

        const input = wrapper.querySelector("input");
        const icon = button.querySelector("i");

        if (!input || !icon) {
            return;
        }

        const isVisible = input.type === "text";
        const nextVisible = !isVisible;

        input.type = nextVisible ? "text" : "password";
        icon.className = nextVisible ? "bi bi-eye-slash" : "bi bi-eye";

        button.setAttribute("aria-pressed", String(nextVisible));

        const label = nextVisible
            ? button.dataset.labelHide
            : button.dataset.labelShow;

        if (label) {
            button.setAttribute("aria-label", label);
        }

    }

    function initializePasswordToggles() {

        const toggles = document.querySelectorAll(".password-toggle");

        toggles.forEach((button) => {
            button.addEventListener("click", () => togglePassword(button));
        });

    }

    /*
     Bootstrap
    */

    document.addEventListener("DOMContentLoaded", initializePasswordToggles);

})();