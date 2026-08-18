/*
=====================================================
        StudyFlow - Global JavaScript Framework
=====================================================
Modules:
  1. Utility Helpers
  2. Sidebar Toggle
  3. Scroll Progress Bar
  4. Active Sidebar Link
  5. Theme Toggle (placeholder)
  6. Toast Notification System
  7. Modal System
  8. Delete Confirmation (modal based)
  9. Subject Color Picker
  10. Subject Search
=====================================================
*/

(function () {

    "use strict";

    /*
    =====================================================
     1. Utility Helpers
    =====================================================
    */

    const $ = (selector, context = document) => context.querySelector(selector);
    const $$ = (selector, context = document) => Array.from(context.querySelectorAll(selector));

    function debounce(fn, wait = 200) {
        let timer;
        return function (...args) {
            clearTimeout(timer);
            timer = setTimeout(() => fn.apply(this, args), wait);
        };
    }

    function escapeHTML(value) {
        const div = document.createElement("div");
        div.textContent = String(value ?? "");
        return div.innerHTML;
    }

    function clamp(value, min, max) {
        return Math.min(Math.max(value, min), max);
    }

    function formatDate(date, options = {}) {
        const d = (date instanceof Date) ? date : new Date(date);
        if (isNaN(d.getTime())) {
            return "";
        }
        return d.toLocaleDateString(undefined, options);
    }

    async function copyToClipboard(text) {
        try {
            await navigator.clipboard.writeText(text);
            showToast("Copied to clipboard", "success");
            return true;
        } catch (err) {
            showToast("Could not copy to clipboard", "error");
            return false;
        }
    }


    /*
    =====================================================
     2. Sidebar Toggle
    =====================================================
    */

    function initializeSidebar() {

        const menuToggle = document.getElementById("menuToggle");
        const sidebar = document.querySelector(".sidebar");

        if (!menuToggle || !sidebar) {
            return;
        }

        // Create mobile backdrop
        let backdrop = document.querySelector(".sidebar-backdrop");

        if (!backdrop) {
            backdrop = document.createElement("div");
            backdrop.className = "sidebar-backdrop";
            document.body.appendChild(backdrop);
        }

        const closeSidebar = () => {
            sidebar.classList.remove("active");
            backdrop.classList.remove("show");
            document.body.style.overflow = "";
        };

        const openSidebar = () => {
            sidebar.classList.add("active");
            backdrop.classList.add("show");
            document.body.style.overflow = "hidden";
        };

        menuToggle.addEventListener("click", () => {
            const isActive = sidebar.classList.toggle("active");
            backdrop.classList.toggle("show", isActive);
            document.body.style.overflow = isActive ? "hidden" : "";
        });

        backdrop.addEventListener("click", closeSidebar);

        // Close on Escape
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && sidebar.classList.contains("active")) {
                closeSidebar();
            }
        });

        // Close after navigation on mobile
        sidebar.querySelectorAll(".sidebar-menu a").forEach((link) => {
            link.addEventListener("click", () => {
                if (window.innerWidth <= 992) {
                    closeSidebar();
                }
            });
        });

    }


    /*
    =====================================================
     3. Scroll Progress Bar
    =====================================================
    */

    function initializeScrollProgress() {

        const progress = document.getElementById("scrollProgress");

        if (!progress) {
            return;
        }

        const update = () => {

            const total =
                document.documentElement.scrollHeight -
                document.documentElement.clientHeight;

            const percent = total > 0 ? (window.scrollY / total) * 100 : 0;

            progress.style.width = clamp(percent, 0, 100) + "%";

        };

        window.addEventListener("scroll", update, { passive: true });
        window.addEventListener("resize", update);
        update();

    }


    /*
    =====================================================
     4. Active Sidebar Link
    =====================================================
    */

    function initializeActiveSidebar() {

        const links = document.querySelectorAll(".sidebar-menu a[href]");

        if (links.length === 0) {
            return;
        }

        const currentPath = window.location.pathname.replace(/\/+$/, "");

        let matched = false;

        links.forEach((link) => {

            const href = (link.getAttribute("href") || "").split("?")[0].replace(/\/+$/, "");

            // Exact match (e.g. /admin/dashboard) or child-page match
            // (e.g. /admin/users/5 highlights the /admin/users link).
            const isActive = href.length > 1 &&
                (href === currentPath || currentPath.startsWith(href + "/"));

            if (isActive) {
                link.classList.add("active");
                matched = true;
            } else {
                link.classList.remove("active");
            }

        });

        // Keep hardcoded fallback when nothing matches
        if (!matched) {
            const fallback = document.querySelector(".sidebar-menu a.active");
            if (!fallback && links[0]) {
                links[0].classList.add("active");
            }
        }

    }


    /*
    =====================================================
     5. Theme Toggle (Placeholder)
     Toggle is wired up and persisted, but no light theme
     styles exist yet. Add CSS rules for body[data-theme="light"]
     later to enable the full theme.
    =====================================================
    */

    function initializeThemeToggle() {

        const toggle = document.getElementById("themeToggle");

        if (!toggle) {
            return;
        }

        const icon = toggle.querySelector("i");

        const applyTheme = (theme) => {
            document.documentElement.setAttribute("data-theme", theme);
            if (icon) {
                icon.className = theme === "light"
                    ? "bi bi-sun"
                    : "bi bi-moon-stars";
            }
        };

        const savedTheme = localStorage.getItem("studyflow-theme");

        if (savedTheme) {
            applyTheme(savedTheme);
        }

        toggle.addEventListener("click", () => {

            const current =
                document.documentElement.getAttribute("data-theme") === "light"
                    ? "light"
                    : "dark";

            const next = current === "light" ? "dark" : "light";

            applyTheme(next);

            localStorage.setItem("studyflow-theme", next);

            showToast(
                next === "light" ? "Light mode coming soon" : "Dark mode enabled",
                "info"
            );

        });

    }


    /*
    =====================================================
     6. Toast Notification System
    =====================================================
    */

    const TOAST_ICONS = {
        success: "bi-check-circle-fill",
        error: "bi-x-circle-fill",
        warning: "bi-exclamation-triangle-fill",
        info: "bi-info-circle-fill"
    };

    function showToast(message, type = "success", options = {}) {

        let container = document.querySelector(".toast-container");

        if (!container) {
            container = document.createElement("div");
            container.className = "toast-container";
            document.body.appendChild(container);
        }

        const duration = options.duration ?? 3000;

        const toast = document.createElement("div");

        toast.className = `toast ${type}`;

        toast.setAttribute("role", "status");

        toast.innerHTML = `
            <i class="bi ${TOAST_ICONS[type] || TOAST_ICONS.info} toast-icon"></i>
            <span class="toast-message">${escapeHTML(message)}</span>
            <button type="button" class="toast-close" aria-label="Dismiss notification">
                <i class="bi bi-x-lg"></i>
            </button>
            <span class="toast-progress"></span>
        `;

        container.appendChild(toast);

        const dismiss = () => {

            toast.classList.remove("show");

            setTimeout(() => toast.remove(), 350);

        };

        // Manual close
        toast.querySelector(".toast-close").addEventListener("click", dismiss);

        // Animate in
        requestAnimationFrame(() => {
            requestAnimationFrame(() => toast.classList.add("show"));
        });

        // Auto dismiss with progress bar
        const progressBar = toast.querySelector(".toast-progress");

        requestAnimationFrame(() => {
            progressBar.style.transitionDuration = duration + "ms";
            progressBar.style.width = "0%";
        });

        setTimeout(dismiss, duration);

    }


    /*
    =====================================================
     7. Modal System
    =====================================================
    */

    let modalOverlay = null;

    function ensureModalMarkup() {

        if (modalOverlay) {
            return modalOverlay;
        }

        modalOverlay = document.querySelector(".modal-overlay");

        if (!modalOverlay) {
            modalOverlay = document.createElement("div");
            modalOverlay.className = "modal-overlay";
            modalOverlay.innerHTML = `
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="sfModalTitle">
                    <div class="modal-header">
                        <h3 class="modal-title" id="sfModalTitle">
                            <span class="modal-icon"></span>
                            <span class="modal-title-text">Confirm</span>
                        </h3>
                        <button type="button" class="modal-close" aria-label="Close">
                            <i class="bi bi-x-lg"></i>
                        </button>
                    </div>
                    <div class="modal-body"></div>
                    <div class="modal-footer"></div>
                </div>
            `;
            document.body.appendChild(modalOverlay);
        }

        return modalOverlay;

    }

    function openModal(options = {}) {

        const overlay = ensureModalMarkup();

        const titleText = overlay.querySelector(".modal-title-text");
        const icon = overlay.querySelector(".modal-icon");
        const body = overlay.querySelector(".modal-body");
        const footer = overlay.querySelector(".modal-footer");

        const iconType = options.icon || "info";

        titleText.textContent = options.title || "Confirm";

        icon.className = `modal-icon ${iconType}`;
        icon.innerHTML = `<i class="bi ${options.iconClass || modalIconClass(iconType)}"></i>`;

        body.innerHTML = options.message
            ? `<p>${escapeHTML(options.message)}</p>`
            : "";

        // Build footer buttons
        footer.innerHTML = "";

        const cancelBtn = document.createElement("button");
        cancelBtn.type = "button";
        cancelBtn.className = "btn btn-secondary";
        cancelBtn.textContent = options.cancelText || "Cancel";
        footer.appendChild(cancelBtn);

        const confirmBtn = document.createElement("button");
        confirmBtn.type = "button";
        confirmBtn.className = options.danger
            ? "btn btn-danger"
            : "btn btn-primary";
        confirmBtn.textContent = options.confirmText || "Confirm";
        footer.appendChild(confirmBtn);

        overlay.classList.add("open");

        // Focus the primary action
        requestAnimationFrame(() => confirmBtn.focus());

        return { overlay, cancelBtn, confirmBtn };

    }

    function closeModal() {

        if (modalOverlay) {
            modalOverlay.classList.remove("open");
        }

    }

    function modalIconClass(type) {
        switch (type) {
            case "danger": return "bi-exclamation-triangle-fill";
            case "success": return "bi-check-circle-fill";
            case "warning": return "bi-exclamation-circle-fill";
            default: return "bi-info-circle-fill";
        }
    }

    function bindModalGlobalHandlers() {

        document.addEventListener("click", (event) => {

            const overlay = ensureModalMarkup();

            // Close button
            if (event.target.closest(".modal-close")) {
                closeModal();
            }

            // Backdrop click
            if (event.target === overlay) {
                closeModal();
            }

        });

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && modalOverlay && modalOverlay.classList.contains("open")) {
                closeModal();
            }
        });

    }


    /*
    =====================================================
     Confirmation Dialog (replaces browser confirm())
    =====================================================
    */

    function confirmDialog(options = {}) {

        const overlay = ensureModalMarkup();

        const closeButton = overlay.querySelector(".modal-close");

        const { cancelBtn, confirmBtn } = openModal({
            title: options.title || "Please Confirm",
            message: options.message || "Are you sure you want to continue?",
            confirmText: options.confirmText || "Confirm",
            cancelText: options.cancelText || "Cancel",
            danger: options.danger !== false,
            icon: options.danger === false ? "info" : "danger"
        });

        return new Promise((resolve) => {

            const cleanup = () => {
                cancelBtn.removeEventListener("click", onCancel);
                confirmBtn.removeEventListener("click", onConfirm);
                closeButton.removeEventListener("click", onCancel);
                overlay.removeEventListener("click", onOverlayClick);
            };

            const onConfirm = () => {
                closeModal();
                cleanup();
                resolve(true);
            };

            const onCancel = () => {
                closeModal();
                cleanup();
                resolve(false);
            };

            // Resolve when backdrop closes (treated as cancel)
            const onOverlayClick = (event) => {
                if (event.target === overlay) {
                    onCancel();
                }
            };

            cancelBtn.addEventListener("click", onCancel);
            confirmBtn.addEventListener("click", onConfirm);
            closeButton.addEventListener("click", onCancel);
            overlay.addEventListener("click", onOverlayClick);

        });

    }


    /*
    =====================================================
     8. Delete Confirmation (Modal based)
    =====================================================
    */

    function initializeDeleteConfirmation() {

        const deleteButtons = document.querySelectorAll(".delete");

        deleteButtons.forEach((button) => {

            button.addEventListener("click", function (event) {

                event.preventDefault();

                const href = this.getAttribute("href");

                confirmDialog({
                    title: "Delete Item?",
                    message: "This action cannot be undone. Are you sure you want to delete this item?",
                    confirmText: "Delete",
                    cancelText: "Cancel",
                    danger: true
                }).then((confirmed) => {

                    if (confirmed && href) {
                        window.location.href = href;
                    }

                });

            });

        });

    }


    /*
    =====================================================
     9. Subject Color Picker
    =====================================================
    */

    function initializeColorPicker() {

        const colorButtons = document.querySelectorAll(".color-circle");
        const selectedColorInput = document.getElementById("selectedColor");

        if (!selectedColorInput || colorButtons.length === 0) {
            return;
        }

        // Highlight selected color on Edit page
        colorButtons.forEach((button) => {

            if (button.dataset.color === selectedColorInput.value) {
                button.classList.add("selected");
            }

        });

        // Change color on click
        colorButtons.forEach((button) => {

            button.addEventListener("click", () => {

                colorButtons.forEach((btn) =>
                    btn.classList.remove("selected")
                );

                button.classList.add("selected");

                selectedColorInput.value = button.dataset.color;

            });

        });

    }


    /*
    =====================================================
     10. Subject Search
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

            cards.forEach((card) => {

                const text = card.innerText.toLowerCase();

                card.style.display = text.includes(keyword) ? "" : "none";

            });

        });

    }


    /*
    =====================================================
     Bootstrap
    =====================================================
    */

    document.addEventListener("DOMContentLoaded", () => {

        bindModalGlobalHandlers();

        initializeSidebar();
        initializeScrollProgress();
        initializeActiveSidebar();
        initializeThemeToggle();

        initializeColorPicker();
        initializeDeleteConfirmation();
        initializeSearch();

    });


    /*
    =====================================================
     Public API
    =====================================================
    */

    window.showToast = showToast;
    window.openModal = openModal;
    window.closeModal = closeModal;
    window.confirmDialog = confirmDialog;

    window.studyflow = {
        $,
        $$,
        debounce,
        escapeHTML,
        clamp,
        formatDate,
        copyToClipboard,
        showToast,
        openModal,
        closeModal,
        confirmDialog
    };

})();
