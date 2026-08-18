/*
=====================================================
        StudyFlow - Admin User Management JS
=====================================================
Modules:
  1. Delete User Confirmation (modal based)
  2. Generic Sensitive-Action Confirmation (modal based)

Reuses the global toast/modal systems from script.js
(window.showToast / window.confirmDialog). Admin forms
post with CSRF, so we intercept submit and confirm
before letting the browser proceed.
=====================================================
*/

(function () {

    "use strict";

    /*
    =====================================================
     1. Delete User Confirmation
    =====================================================
    */

    function initializeAdminDeleteForms() {

        const deleteForms = document.querySelectorAll(".admin-delete-form");

        deleteForms.forEach((form) => {

            form.addEventListener("submit", (event) => {

                event.preventDefault();

                const userName = form.dataset.userName || "this user";

                window.confirmDialog({
                    title: "Delete User?",
                    message: `Delete "${userName}"? This action cannot be undone.`,
                    confirmText: "Delete",
                    cancelText: "Cancel",
                    danger: true
                }).then((confirmed) => {

                    if (confirmed) {
                        form.submit();
                    }

                });

            });

        });

    }


    /*
    =====================================================
     2. Generic Sensitive-Action Confirmation
     Forms carrying class "admin-confirm-form" with a
     "data-confirm-message" attribute are confirmed via the
     shared modal before their CSRF POST is submitted.
    =====================================================
    */

    function initializeAdminConfirmForms() {

        const confirmForms = document.querySelectorAll(".admin-confirm-form");

        confirmForms.forEach((form) => {

            form.addEventListener("submit", (event) => {

                event.preventDefault();

                const action = form.dataset.action || "";
                const name = form.dataset.userName || "this user";

                if (action === "enable") {
                    form.submit();
                    return;
                }

                const options = {};

                if (action === "disable") {
                    options.title = "Disable User?";
                    options.message = `Disable "${name}"? They will not be able to log in until re-enabled.`;
                    options.confirmText = "Disable";
                    options.danger = true;
                } else if (action === "role") {
                    options.title = "Change Role?";
                    options.message = `Change the role of "${name}"?`;
                    options.confirmText = "Change Role";
                    options.danger = true;
                } else {
                    form.submit();
                    return;
                }

                window.confirmDialog(options).then((confirmed) => {
                    if (confirmed) {
                        form.submit();
                    }
                });

            });

        });

    }


    /*
    =====================================================
     Bootstrap
    =====================================================
    */

    document.addEventListener("DOMContentLoaded", () => {
        initializeAdminDeleteForms();
        initializeAdminConfirmForms();
    });

})();