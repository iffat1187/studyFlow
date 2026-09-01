/*
=====================================================
        StudyFlow - Notification Center JavaScript
=====================================================
Modules:
  1. Notification Bell Dropdown
  2. Mark as Read / Mark All as Read
  3. Dropdown Refresh (no polling)
=====================================================
The bell badge and list are rendered server-side on
every authenticated page load. This script only:
  - toggles the dropdown,
  - calls the mark-read / mark-all endpoints,
  - refreshes the badge + dropdown list after an action.

No polling, no timers. Data always comes from the server.
=====================================================
*/

(function () {

    "use strict";

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || "";
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "";
    const csrfParam = document.querySelector('meta[name="_csrf_parameter"]')?.content || "";

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

    function postForm(url) {
        return fetch(url, {
            method: "POST",
            headers: getHeaders(),
            body: getCsrfBody()
        });
    }

    /*
    =====================================================
     1. Notification Bell Dropdown
    =====================================================
    */

    function initializeDropdown() {

        const menu = document.getElementById("notificationMenu");
        const bell = document.getElementById("notificationBell");
        const dropdown = document.getElementById("notificationDropdown");

        if (!menu || !bell || !dropdown) {
            return;
        }

        bell.addEventListener("click", function (event) {
            event.stopPropagation();
            const open = menu.classList.toggle("is-open");
            bell.setAttribute("aria-expanded", open ? "true" : "false");

            if (open) {
                window.addEventListener("click", closeOnOutsideClick, true);
                window.addEventListener("keydown", closeOnEscape, true);
            }
        });

        function closeOnOutsideClick(event) {
            if (!menu.contains(event.target)) {
                closeDropdown();
            }
        }

        function closeOnEscape(event) {
            if (event.key === "Escape") {
                closeDropdown();
            }
        }

        function closeDropdown() {
            menu.classList.remove("is-open");
            bell.setAttribute("aria-expanded", "false");
            window.removeEventListener("click", closeOnOutsideClick, true);
            window.removeEventListener("keydown", closeOnEscape, true);
        }

        // Clicking any item in the dropdown marks it read and navigates.
        dropdown.addEventListener("click", function (event) {
            const link = event.target.closest(".notification-item__link");
            if (link) {
                event.preventDefault();
                handleItemClick(link, true);
            }
        });

        const markAll = document.getElementById("notificationMarkAll");
        if (markAll) {
            markAll.addEventListener("click", function (event) {
                event.stopPropagation();
                markAllRead();
            });
        }
    }

    /*
    =====================================================
     2. Mark as Read / Mark All as Read
    =====================================================
    */

    function handleItemClick(element, fromDropdown) {
        const id = element.getAttribute("data-notif-id");
        const href = element.getAttribute("data-notif-href") || "";
        const read = element.getAttribute("data-notif-read") === "true";

        if (!id) {
            return;
        }

        if (!read) {
            markRead(id).then(function () {
                if (href) {
                    window.location.href = href;
                } else if (fromDropdown) {
                    refreshDropdown();
                } else {
                    markReadInPage(element);
                    refreshNavbarBadge();
                }
            });
        } else if (href) {
            window.location.href = href;
        }
    }

    function markRead(id) {
        return postForm(`/notifications/read/${encodeURIComponent(id)}`)
            .then(function (response) {
                return response.ok;
            })
            .catch(function () {
                return false;
            });
    }

    function markAllRead() {
        postForm("/notifications/read-all")
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("Failed to mark all read");
                }
                refreshDropdown();
                refreshNavbarBadge();
                const pageMarkAll = document.getElementById("notificationsMarkAll");
                if (pageMarkAll) {
                    pageMarkAll.remove();
                }
                const summary = document.querySelector(".notification-summary");
                if (summary) {
                    summary.remove();
                }
                const pageList = document.querySelector(".notification-page-list");
                if (pageList) {
                    pageList.querySelectorAll(".notification-page-item").forEach(function (item) {
                        markReadInPage(item);
                    });
                }
            })
            .catch(function (err) {
                console.error("Mark all read failed:", err);
            });
    }

    // Apply read styling to an already-rendered element.
    function markReadInPage(element) {
        element.setAttribute("data-notif-read", "true");
        if (element.matches(".notification-page-item")) {
            element.classList.remove("is-unread");
            element.classList.add("is-read");
        }
        const dot = element.querySelector(".notification-dot");
        if (dot) {
            dot.remove();
        }
    }

    /*
    =====================================================
     3. Dropdown / Badge Refresh (only after actions)
    =====================================================
    */

    function refreshNavbarBadge() {
        const badge = document.getElementById("notificationBadge");
        if (!badge) {
            return;
        }
        fetch("/notifications/unread-count", { headers: getHeaders() })
            .then(function (response) { return response.ok ? response.json() : null; })
            .then(function (data) {
                const count = data ? Number(data.unreadCount) : 0;
                if (count > 0) {
                    badge.textContent = count;
                    badge.style.display = "flex";
                } else {
                    badge.remove();
                }
            })
            .catch(function () { /* leave badge unchanged */ });
    }

    function refreshDropdown() {
        const listEl = document.querySelector(".notification-list");
        if (!listEl) {
            return;
        }
        fetch("/notifications/recent", { headers: getHeaders() })
            .then(function (response) { return response.ok ? response.json() : []; })
            .then(function (items) {
                listEl.innerHTML = "";
                if (!items || items.length === 0) {
                    showDropdownEmpty(listEl);
                    return;
                }
                items.forEach(function (n) {
                    const li = document.createElement("li");
                    li.className = "notification-item" + (n.read ? "" : " is-unread");

                    const href = n.href || "/notifications";
                    const a = document.createElement("a");
                    a.className = "notification-item__link";
                    a.href = href;
                    a.setAttribute("data-notif-id", n.id);
                    a.setAttribute("data-notif-read", String(n.read));
                    a.setAttribute("data-notif-href", n.href || "");
                    a.setAttribute("data-notif-time", n.createdAt);

                    const icon = document.createElement("span");
                    icon.className = "notification-item__icon";
                    icon.innerHTML = `<i class="bi ${n.icon}"></i>`;

                    const body = document.createElement("span");
                    body.className = "notification-item__body";
                    const title = document.createElement("span");
                    title.className = "notification-item__title";
                    title.textContent = n.title;
                    const time = document.createElement("span");
                    time.className = "notification-item__time";
                    time.textContent = n.typeLabel;
                    body.appendChild(title);
                    body.appendChild(time);

                    a.appendChild(icon);
                    a.appendChild(body);

                    if (!n.read) {
                        const dot = document.createElement("span");
                        dot.className = "notification-item__dot";
                        a.appendChild(dot);
                    }

                    li.appendChild(a);
                    listEl.appendChild(li);
                });
                hideDropdownEmpty(listEl);
            })
            .catch(function () { /* leave list unchanged */ });
    }

    function showDropdownEmpty(listEl) {
        const menu = listEl.closest(".notification-dropdown");
        let empty = menu.querySelector(".notification-dropdown__empty");
        if (!empty) {
            empty = document.createElement("div");
            empty.className = "notification-dropdown__empty";
            empty.textContent = "No notifications yet.";
            menu.insertBefore(empty, menu.querySelector(".notification-dropdown__view-all"));
        }
        empty.style.display = "block";
    }

    function hideDropdownEmpty(listEl) {
        const menu = listEl.closest(".notification-dropdown");
        const empty = menu.querySelector(".notification-dropdown__empty");
        if (empty) {
            empty.style.display = "none";
        }
    }

    /*
    =====================================================
     Notifications Page
    =====================================================
    */

    function initializePage() {
        const pageList = document.querySelector(".notification-page-list");
        if (!pageList) {
            return;
        }

        pageList.querySelectorAll(".notification-page-item").forEach(function (item) {
            item.addEventListener("click", function () {
                const href = item.getAttribute("data-notif-href") || "";
                const read = item.getAttribute("data-notif-read") === "true";

                if (read) {
                    if (href) {
                        window.location.href = href;
                    }
                    return;
                }

                const id = item.getAttribute("data-notif-id");
                if (!id) {
                    return;
                }

                markRead(id).then(function (ok) {
                    if (ok) {
                        markReadInPage(item);
                        refreshNavbarBadge();
                        const pageMarkAll = document.getElementById("notificationsMarkAll");
                        const summary = document.querySelector(".notification-summary");
                        if (pageMarkAll && !pageList.querySelector(".notification-page-item.is-unread")) {
                            pageMarkAll.remove();
                            if (summary) {
                                summary.remove();
                            }
                        }
                    }
                    if (href) {
                        window.location.href = href;
                    }
                });
            });
        });

        const pageMarkAll = document.getElementById("notificationsMarkAll");
        if (pageMarkAll) {
            pageMarkAll.addEventListener("click", function () {
                markAllRead();
            });
        }
    }

    // Present all served timestamps as friendly relative times once on load.
    function initializeRelativeTimes() {
        document.querySelectorAll(".notification-page-item").forEach(function (el) {
            const iso = el.getAttribute("data-notif-time");
            const timeEl = el.querySelector(".notification-page-item__time");
            if (iso && timeEl) {
                timeEl.textContent = relativeTime(iso);
            }
        });
    }

    function relativeTime(iso) {
        const date = new Date(iso);
        if (isNaN(date.getTime())) {
            return iso;
        }
        const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
        if (seconds < 60) {
            return "just now";
        }
        const minutes = Math.floor(seconds / 60);
        if (minutes < 60) {
            return minutes + " min ago";
        }
        const hours = Math.floor(minutes / 60);
        if (hours < 24) {
            return hours + " hr ago";
        }
        const days = Math.floor(hours / 24);
        if (days < 7) {
            return days + " day" + (days === 1 ? "" : "s") + " ago";
        }
        return date.toLocaleDateString();
    }

    document.addEventListener("DOMContentLoaded", function () {
        initializeDropdown();
        initializePage();
        initializeRelativeTimes();
    });

})();
