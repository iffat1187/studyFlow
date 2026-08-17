/*
=====================================================
        StudyFlow - Admin User Management JS
=====================================================
Module:
  1. Delete User Confirmation (modal based)

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
     Bootstrap
    =====================================================
    */

    document.addEventListener("DOMContentLoaded", () => {
        initializeAdminDeleteForms();
    });

})();