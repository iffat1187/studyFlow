/*
=====================================================
        StudyFlow Calendar JavaScript
        Month / Week / Day views
        -------------------------------------------------
        Responsibilities:
          - current calendar date + view state
          - previous / next / Today navigation
          - view switching (month / week / day)
          - fetching visible-range events (GET /calendar/events)
          - rendering month, week and day layouts
          - event-type + subject filtering
          - opening the event detail modal
        No ownership/security logic lives here: the backend
        only returns the authenticated user's events.
=====================================================
*/

(function () {

    "use strict";

    const MINUTES_PER_HOUR = 60;
    const HOUR_HEIGHT = 60; // px per hour in timeline views
    const DAY_START_HOUR = 0;
    const DAY_END_HOUR = 24;

    // ---------- Date helpers (local, no DST surprises via UTC noon round-trip) ----------

    function toDate(str) {
        if (!str) {
            return null;
        }
        const parts = String(str).split("-").map(Number);
        if (parts.length < 3 || parts.some(function (n) { return Number.isNaN(n); })) {
            return null;
        }
        const d = new Date(parts[0], parts[1] - 1, parts[2]);
        if (Number.isNaN(d.getTime())) {
            return null;
        }
        return d;
    }

    function toISO(d) {
        const m = String(d.getMonth() + 1).padStart(2, "0");
        const day = String(d.getDate()).padStart(2, "0");
        return d.getFullYear() + "-" + m + "-" + day;
    }

    function addDays(d, n) {
        const copy = new Date(d.getTime());
        copy.setDate(copy.getDate() + n);
        return copy;
    }

    function startOfDay(d) {
        return new Date(d.getFullYear(), d.getMonth(), d.getDate());
    }

    const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
    const MONTHS = ["January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"];

    // Monday = 0 ... Sunday = 6
    function mondayIndex(d) {
        return (d.getDay() + 6) % 7;
    }

    function sameDay(a, b) {
        return a.getFullYear() === b.getFullYear()
            && a.getMonth() === b.getMonth()
            && a.getDate() === b.getDate();
    }

    function formatTimeMin(localTimeStr) {
        if (!localTimeStr) {
            return null;
        }
        const parts = String(localTimeStr).split(":");
        let hour = Number(parts[0]);
        const minute = Number(parts[1] || 0);
        const ampm = hour >= 12 ? "PM" : "AM";
        hour = hour % 12;
        hour = hour === 0 ? 12 : hour;
        return (minute > 0 ? hour + ":" + String(minute).padStart(2, "0") : hour) + " " + ampm;
    }

    function formatDuration(minutes) {
        if (minutes == null) {
            return null;
        }
        const m = Math.round(Number(minutes));
        if (m <= 0) {
            return "0m";
        }
        const h = Math.floor(m / 60);
        const r = m % 60;
        if (h === 0) {
            return r + "m";
        }
        return r === 0 ? h + "h" : h + "h " + r + "m";
    }

    function formatDayLabel(d) {
        return MONTHS[d.getMonth()].slice(0, 3) + " " + d.getDate() + ", " + d.getFullYear();
    }

    function minutesOfDay(localTimeStr) {
        if (!localTimeStr) {
            return null;
        }
        const parts = String(localTimeStr).split(":");
        return Number(parts[0]) * 60 + Number(parts[1] || 0);
    }

    // ---------- State ----------

    let state = {
        cursor: null,     // reference date within the current period
        view: "month",
        typeFilter: "all",
        subjectId: "all",
        events: [],       // currently loaded events (visible range)
        subjects: []
    };

    let els = {};
    let previousFocus = null; // saved for focus restore on modal close
    let requestCounter = 0;   // guards against stale async responses

    // ---------- Element cache ----------

    function initElements() {
        els.monthView = document.getElementById("calMonthView");
        els.weekView = document.getElementById("calWeekView");
        els.dayView = document.getElementById("calDayView");
        els.emptyState = document.getElementById("calEmptyState");
        els.monthGrid = document.getElementById("calMonthGrid");
        els.periodLabel = document.getElementById("calPeriodLabel");
        els.weekHead = document.getElementById("calWeekHead");
        els.timeCol = document.getElementById("calTimeCol");
        els.weekColumns = document.getElementById("calWeekColumns");
        els.dayHeader = document.getElementById("calDayHeader");
        els.dayTimeline = document.getElementById("calDayTimeline");
        els.dayEmpty = document.getElementById("calDayEmpty");
        els.dayPanel = document.getElementById("calDayPanel");
        els.dayPanelHead = document.getElementById("calDayPanelHead");
        els.dayPanelList = document.getElementById("calDayPanelList");
        els.dayPanelClose = document.getElementById("calDayPanelClose");
        els.overlay = document.getElementById("calDetailOverlay");
        els.detailTitle = document.getElementById("calDetailTitle");
        els.detailIcon = document.getElementById("calDetailIcon");
        els.detailBody = document.getElementById("calDetailBody");
        els.detailFooter = document.getElementById("calDetailFooter");
        els.detailClose = document.getElementById("calDetailClose");
        els.subjectFilter = document.getElementById("calSubjectFilter");
    }

    // ---------- Fetching (backend-authoritative, user-scoped) ----------

    async function loadRange(startDate, endDate) {
        const url = "/calendar/events?startDate=" + toISO(startDate)
            + "&endDate=" + toISO(endDate);
        try {
            const res = await fetch(url, { headers: { "Accept": "application/json" } });
            if (!res.ok) {
                throw new Error("Failed to load calendar events");
            }
            return await res.json();
        } catch (e) {
            console.error("Calendar: failed to fetch events", e);
            return [];
        }
    }

    // ---------- Range computation per view ----------

    function visibleRange(view, cursor) {
        const start = startOfDay(cursor);
        if (view === "day") {
            return { start: start, end: start };
        }
        if (view === "week") {
            const monday = addDays(start, -mondayIndex(start));
            return { start: monday, end: addDays(monday, 6) };
        }
        // month
        const first = new Date(start.getFullYear(), start.getMonth(), 1);
        const last = new Date(start.getFullYear(), start.getMonth() + 1, 0);
        return { start: first, end: last };
    }

    // ---------- Filtering ----------

    function applyFilters(events) {
        return events.filter(function (e) {
            const okType = state.typeFilter === "all" || e.eventType === state.typeFilter;
            const okSubject = state.subjectId === "all"
                || String(e.subjectId || "") === String(state.subjectId);
            return okType && okSubject;
        });
    }

    // ---------- Grouping ----------

    function eventsByDate(events) {
        const map = {};
        events.forEach(function (e) {
            const key = toISO(toDate(e.startDate));
            if (!map[key]) {
                map[key] = [];
            }
            map[key].push(e);
        });
        Object.keys(map).forEach(function (key) {
            map[key].sort(function (a, b) {
                const ta = minutesOfDay(a.startTime) == null ? -1 : minutesOfDay(a.startTime);
                const tb = minutesOfDay(b.startTime) == null ? -1 : minutesOfDay(b.startTime);
                return ta - tb;
            });
        });
        return map;
    }

    function pillIcon(type) {
        if (type === "TASK") {
            return '<i class="bi bi-check2-square"></i>';
        }
        if (type === "EXAM") {
            return '<i class="bi bi-mortarboard"></i>';
        }
        return '<i class="bi bi-stopwatch"></i>';
    }

    function pillLabel(e) {
        if (e.eventType === "TASK") {
            return e.title;
        }
        if (e.eventType === "EXAM") {
            return "Exam \u00B7 " + e.title;
        }
        // study session: type label, plus topic when present
        const type = e.sessionTypeLabel || "Study";
        return e.topicName ? (type + " \u00B7 " + e.topicName) : type;
    }

    // ---------- Month view ----------

    function renderMonth() {
        const cursor = state.cursor;
        const firstOfMonth = new Date(cursor.getFullYear(), cursor.getMonth(), 1);
        const daysInMonth = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0).getDate();
        const lead = mondayIndex(firstOfMonth); // empty leading cells
        const trail = (7 - ((lead + daysInMonth) % 7)) % 7; // empty trailing cells

        const filtered = applyFilters(state.events);
        const grouped = eventsByDate(filtered);
        const today = new Date();
        const todayISO = toISO(today);

        let html = "";

        // leading blanks
        for (let i = 0; i < lead; i++) {
            html += '<div class="cal-day-cell is-outside"></div>';
        }

        for (let d = 1; d <= daysInMonth; d++) {
            const date = new Date(cursor.getFullYear(), cursor.getMonth(), d);
            const iso = toISO(date);
            const dayEvents = grouped[iso] || [];
            const isToday = iso === todayISO;

            let eventsHtml = "";
            if (dayEvents.length > 0) {
                eventsHtml += '<span class="cal-day-count">' + dayEvents.length + "</span>";
            }
            const visible = dayEvents.slice(0, 3);
            visible.forEach(function (e) {
                const cls = "cal-event-pill cal-event-pill--" + e.eventType
                    + (e.completed ? " is-completed" : "");
                eventsHtml += '<button type="button" class="' + cls
                    + '" data-event-id="' + e.id + '" data-event-type="' + e.eventType + '">'
                    + pillIcon(e.eventType) + " " + escapeHtml(pillLabel(e)) + "</button>";
            });
            if (dayEvents.length > visible.length) {
                const rest = dayEvents.length - visible.length;
                eventsHtml += '<span class="cal-more" data-date="' + iso + '">+'
                    + rest + " more</span>";
            }

            html += '<div class="cal-day-cell' + (isToday ? " is-today" : "")
                + '" data-date="' + iso + '">'
                + '<span class="cal-day-num">' + d + "</span>"
                + '<div class="cal-day-events">' + eventsHtml + "</div>"
                + "</div>";
        }

        // trailing blanks
        for (let i = 0; i < trail; i++) {
            html += '<div class="cal-day-cell is-outside"></div>';
        }

        els.monthGrid.innerHTML = html;
    }

    // ---------- Week / Day timeline ----------

    function buildTimeSlots() {
        return Array.from({ length: DAY_END_HOUR - DAY_START_HOUR }, function (_, i) {
            return DAY_START_HOUR + i;
        });
    }

    function timeSlotsHtml() {
        const slots = buildTimeSlots();
        let html = "";
        slots.forEach(function (h) {
            html += '<div class="cal-time-slot">' + formatHour(h) + "</div>";
        });
        return html;
    }

    function formatHour(h) {
        const ampm = h >= 12 ? "PM" : "AM";
        let hh = h % 12;
        hh = hh === 0 ? 12 : hh;
        return hh + " " + ampm;
    }

    function dayColumnTimedEvents(dayEvents) {
        // separate all-day vs timed
        const timed = dayEvents.filter(function (e) {
            return e.startTime != null;
        });
        const allDay = dayEvents.filter(function (e) {
            return e.startTime == null;
        });

        let hourLines = "";
        buildTimeSlots().forEach(function (h) {
            hourLines += '<div class="cal-hour-line" style="top:' + (h - DAY_START_HOUR) * HOUR_HEIGHT + 'px"></div>';
        });

        // All-day band at the top of the column
        let allDayHtml = "";
        if (allDay.length > 0) {
            allDayHtml += '<div class="cal-allday-band">';
            allDay.forEach(function (e) {
                const cls = "cal-event-pill cal-event-pill--" + e.eventType
                    + (e.completed ? " is-completed" : "");
                allDayHtml += '<button type="button" class="' + cls + '"'
                    + ' data-event-id="' + e.id + '" data-event-type="' + e.eventType + '">'
                    + pillIcon(e.eventType) + " " + escapeHtml(pillLabel(e)) + "</button>";
            });
            allDayHtml += "</div>";
        }

        // Layout overlapping timed events in side-by-side columns
        var layout = layoutOverlapping(timed);

        let timedHtml = "";
        layout.forEach(function (item) {
            var e = item.event;
            var startM = minutesOfDay(e.startTime);
            let endM = null;
            if (e.eventType === "STUDY_SESSION" && e.endTime) {
                endM = minutesOfDay(e.endTime);
            } else if (e.durationMinutes) {
                endM = startM + Number(e.durationMinutes);
            }
            if (endM == null || endM <= startM) {
                endM = startM + MINUTES_PER_HOUR; // default 1h block
            }
            // clamp to 24h boundary
            if (endM > DAY_END_HOUR * 60) {
                endM = DAY_END_HOUR * 60;
            }
            const top = startM * (HOUR_HEIGHT / 60);
            const height = Math.max((endM - startM) * (HOUR_HEIGHT / 60), 22);
            const leftPct = (item.col / item.totalCols) * 100;
            const widthPct = (1 / item.totalCols) * 100;
            const cls = "cal-timed-event cal-event-pill--" + e.eventType
                + (e.completed ? " is-completed" : "");
            timedHtml += '<button type="button" class="' + cls + '"'
                + ' data-event-id="' + e.id + '" data-event-type="' + e.eventType + '"'
                + ' style="top:' + top + 'px;height:' + height + 'px;'
                + 'left:calc(' + leftPct + '% + 2px);'
                + 'width:calc(' + widthPct + '% - 4px)">'
                + '<div class="cal-timed-event__time">' + formatTimeMin(e.startTime) + "</div>"
                + escapeHtml(pillLabel(e)) + "</button>";
        });

        return '<div class="cal-week-col">'
            + allDayHtml
            + hourLines
            + timedHtml
            + "</div>";
    }

    /**
     * Layout overlapping timed events into side-by-side columns.
     * Returns an array of { event, col, totalCols }.
     */
    function layoutOverlapping(events) {
        if (events.length === 0) {
            return [];
        }

        // Sort by start time, then by duration (longer first)
        var sorted = events.slice().sort(function (a, b) {
            var sa = minutesOfDay(a.startTime) || 0;
            var sb = minutesOfDay(b.startTime) || 0;
            if (sa !== sb) {
                return sa - sb;
            }
            return (b.durationMinutes || 0) - (a.durationMinutes || 0);
        });

        // Assign columns
        var columns = []; // each column tracks the latest end minute
        var assignments = [];
        sorted.forEach(function (e) {
            var startM = minutesOfDay(e.startTime) || 0;
            var endM = startM;
            if (e.eventType === "STUDY_SESSION" && e.endTime) {
                endM = minutesOfDay(e.endTime) || startM;
            } else if (e.durationMinutes) {
                endM = startM + Number(e.durationMinutes);
            }
            if (endM <= startM) {
                endM = startM + MINUTES_PER_HOUR;
            }

            var placed = false;
            for (var c = 0; c < columns.length; c++) {
                if (columns[c] <= startM) {
                    columns[c] = endM;
                    assignments.push({ event: e, col: c });
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                columns.push(endM);
                assignments.push({ event: e, col: columns.length - 1 });
            }
        });

        // For each event, find the max columns in its overlap group
        // Two events overlap if their time ranges intersect
        var result = [];
        for (var i = 0; i < assignments.length; i++) {
            var a = assignments[i];
            var maxCols = 1;
            var aStart = minutesOfDay(a.event.startTime) || 0;
            var aEnd = aStart;
            if (a.event.eventType === "STUDY_SESSION" && a.event.endTime) {
                aEnd = minutesOfDay(a.event.endTime) || aStart;
            } else if (a.event.durationMinutes) {
                aEnd = aStart + Number(a.event.durationMinutes);
            }
            if (aEnd <= aStart) {
                aEnd = aStart + MINUTES_PER_HOUR;
            }

            for (var j = 0; j < assignments.length; j++) {
                if (i === j) {
                    continue;
                }
                var b = assignments[j];
                var bStart = minutesOfDay(b.event.startTime) || 0;
                var bEnd = bStart;
                if (b.event.eventType === "STUDY_SESSION" && b.event.endTime) {
                    bEnd = minutesOfDay(b.event.endTime) || bStart;
                } else if (b.event.durationMinutes) {
                    bEnd = bStart + Number(b.event.durationMinutes);
                }
                if (bEnd <= bStart) {
                    bEnd = bStart + MINUTES_PER_HOUR;
                }
                // Check overlap
                if (aStart < bEnd && bStart < aEnd) {
                    maxCols = Math.max(maxCols, Math.max(a.col, b.col) + 1);
                }
            }
            result.push({ event: a.event, col: a.col, totalCols: maxCols });
        }

        return result;
    }

    function renderWeek() {
        const range = visibleRange("week", state.cursor);
        const filtered = applyFilters(state.events);
        const grouped = eventsByDate(filtered);
        const today = new Date();
        const todayISO = toISO(today);

        // header
        let headHtml = '<div class="cal-week-head__corner"></div>';
        for (let i = 0; i < 7; i++) {
            const date = addDays(range.start, i);
            const iso = toISO(date);
            const isToday = iso === todayISO;
            headHtml += '<div class="cal-week-head__day' + (isToday ? " is-today" : "") + '">'
                + '<span class="dow">' + DAYS[i] + "</span>"
                + '<span class="dnum">' + date.getDate() + "</span>"
                + "</div>";
        }
        els.weekHead.innerHTML = headHtml;

        // time column
        els.timeCol.innerHTML = timeSlotsHtml();

        // columns
        let colsHtml = "";
        for (let i = 0; i < 7; i++) {
            const date = addDays(range.start, i);
            const iso = toISO(date);
            const dayEvents = grouped[iso] || [];
            const isToday = iso === todayISO;
            colsHtml += '<div class="cal-week-col' + (isToday ? " is-today" : "") + '"'
                + ' data-date="' + iso + '">'
                + dayColumnTimedEvents(dayEvents)
                + "</div>";
        }
        els.weekColumns.innerHTML = colsHtml;
    }

    function renderDay() {
        const cursor = startOfDay(state.cursor);
        const iso = toISO(cursor);
        const filtered = applyFilters(state.events);
        const grouped = eventsByDate(filtered);
        const dayEvents = grouped[iso] || [];
        const today = new Date();
        const isToday = sameDay(cursor, today);

        els.dayHeader.textContent = MONTHS[cursor.getMonth()] + " " + cursor.getDate() + ", "
            + cursor.getFullYear() + (isToday ? " \u00B7 Today" : "");

        const timed = dayEvents.filter(function (e) { return e.startTime != null; });
        const allDay = dayEvents.filter(function (e) { return e.startTime == null; });

        if (dayEvents.length === 0) {
            els.dayEmpty.style.display = "block";
            els.dayEmpty.innerHTML = '<div class="empty-state"><i class="bi bi-calendar-x"></i>'
                + "<h3>No events this day</h3>"
                + "<p>Nothing scheduled for " + formatDayLabel(cursor) + ".</p></div>";
            els.dayTimeline.style.display = "none";
            return;
        }

        els.dayEmpty.style.display = "none";
        els.dayTimeline.style.display = "block";

        let html = "";

        // all-day area
        if (allDay.length > 0) {
            html += '<div style="position:absolute;top:0;left:4px;right:8px;z-index:5;display:flex;flex-direction:column;gap:4px;">';
            allDay.forEach(function (e) {
                const cls = "cal-event-pill cal-event-pill--" + e.eventType
                    + (e.completed ? " is-completed" : "");
                html += '<button type="button" class="' + cls + '"'
                    + ' data-event-id="' + e.id + '" data-event-type="' + e.eventType + '">'
                    + pillIcon(e.eventType) + " All day \u00B7 " + escapeHtml(pillLabel(e)) + "</button>";
            });
            html += "</div>";
        }

        // timed events (overlapping layout)
        const layout = layoutOverlapping(timed);
        layout.forEach(function (item) {
            const e = item.event;
            const startM = minutesOfDay(e.startTime);
            let endM = null;
            if (e.eventType === "STUDY_SESSION" && e.endTime) {
                endM = minutesOfDay(e.endTime);
            } else if (e.durationMinutes) {
                endM = startM + Number(e.durationMinutes);
            }
            if (endM == null || endM <= startM) {
                endM = startM + MINUTES_PER_HOUR;
            }
            if (endM > DAY_END_HOUR * 60) {
                endM = DAY_END_HOUR * 60;
            }
            const top = startM * (HOUR_HEIGHT / 60);
            const height = Math.max((endM - startM) * (HOUR_HEIGHT / 60), 22);
            const leftPct = (item.col / item.totalCols) * 100;
            const widthPct = (1 / item.totalCols) * 100;
            const cls = "cal-timed-event cal-event-pill--" + e.eventType
                + (e.completed ? " is-completed" : "");
            html += '<button type="button" class="' + cls + '"'
                + ' data-event-id="' + e.id + '" data-event-type="' + e.eventType + '"'
                + ' style="top:' + top + "px;height:" + height + "px;"
                + 'left:calc(' + leftPct + '% + 2px);'
                + 'width:calc(' + widthPct + '% - 4px)">'
                + '<div class="cal-timed-event__time">' + formatTimeMin(e.startTime)
                + (formatTimeMin(e.endTime) ? " \u2013 " + formatTimeMin(e.endTime) : "") + "</div>"
                + escapeHtml(pillLabel(e)) + "</button>";
        });

        els.dayTimeline.innerHTML = html;
    }

    // ---------- Shared render dispatch ----------

    function updatePeriodLabel() {
        const c = state.cursor;
        if (state.view === "month") {
            els.periodLabel.textContent = MONTHS[c.getMonth()] + " " + c.getFullYear();
        } else if (state.view === "week") {
            const range = visibleRange("week", c);
            const sameMonth = range.start.getMonth() === range.end.getMonth();
            const startTxt = MONTHS[range.start.getMonth()].slice(0, 3) + " " + range.start.getDate();
            const endTxt = (sameMonth ? "" : MONTHS[range.end.getMonth()].slice(0, 3) + " ")
                + range.end.getDate() + ", " + range.end.getFullYear();
            els.periodLabel.textContent = startTxt + " \u2013 " + endTxt;
        } else {
            els.periodLabel.textContent = formatDayLabel(c);
        }
    }

    function updateViewTabs() {
        document.querySelectorAll(".view-switch__tab").forEach(function (tab) {
            const active = tab.getAttribute("data-view") === state.view;
            tab.classList.toggle("is-active", active);
            tab.setAttribute("aria-selected", active ? "true" : "false");
        });
    }

    function updateFiltersUI() {
        document.querySelectorAll(".cal-filter").forEach(function (btn) {
            const active = btn.getAttribute("data-type") === state.typeFilter;
            btn.classList.toggle("is-active", active);
        });
    }

    function showView() {
        els.monthView.hidden = state.view !== "month";
        els.weekView.hidden = state.view !== "week";
        els.dayView.hidden = state.view !== "day";

        const filtered = applyFilters(state.events);
        const hasEvents = filtered.length > 0;
        els.emptyState.hidden = !(hasEvents === false && state.view !== "day");
        els.monthView.style.display = state.view === "month" ? "" : "none";
        els.weekView.style.display = state.view === "week" ? "" : "none";
        els.dayView.style.display = state.view === "day" ? "" : "none";

        if (state.view === "month") {
            renderMonth();
        } else if (state.view === "week") {
            renderWeek();
        } else {
            renderDay();
        }
    }

    function render() {
        updatePeriodLabel();
        updateViewTabs();
        updateFiltersUI();
        showView();
        pushUrlState();
    }

    // ---------- URL state sync ----------

    function pushUrlState() {
        var iso = toISO(state.cursor);
        var url = "?date=" + iso + "&view=" + state.view;
        if (state.typeFilter !== "all") {
            url += "&type=" + state.typeFilter;
        }
        if (state.subjectId !== "all") {
            url += "&subject=" + state.subjectId;
        }
        history.replaceState(null, "", url);
    }

    // ---------- Clear filters ----------

    function clearFilters() {
        state.typeFilter = "all";
        state.subjectId = "all";
        var sel = els.subjectFilter;
        if (sel) {
            sel.value = "all";
        }
        render();
    }

    // ---------- Navigation ----------

    function shiftPeriod(dir) {
        const c = state.cursor;
        if (state.view === "day") {
            state.cursor = addDays(c, dir);
        } else if (state.view === "week") {
            state.cursor = addDays(c, dir * 7);
        } else {
            state.cursor = new Date(c.getFullYear(), c.getMonth() + dir, 1);
        }
        refreshCurrentView();
    }

    function goToday() {
        state.cursor = new Date();
        refreshCurrentView();
    }

    async function refreshCurrentView() {
        const range = visibleRange(state.view, state.cursor);
        const myRequest = ++requestCounter;
        const events = await loadRange(range.start, range.end);
        // Ignore stale responses: a newer navigation superseded this one
        if (myRequest !== requestCounter) {
            return;
        }
        state.events = events;
        render();
    }

    // ---------- Event detail modal ----------

    function findEvent(id, type) {
        return state.events.find(function (e) {
            return e.id === id && e.eventType === type;
        });
    }

    function statusBadge(e) {
        const map = {
            completed: "badge-success",
            overdue: "badge-danger",
            pending: "badge-warning",
            upcoming: "badge-info",
            past: "badge-plain",
            today: "badge-primary",
            logged: "badge-success"
        };
        const label = e.status ? e.status.charAt(0).toUpperCase() + e.status.slice(1) : "";
        return '<span class="badge ' + (map[e.status] || "badge-plain") + '">' + label + "</span>";
    }

    function detailItem(label, icon, value) {
        return '<div class="cal-detail-item">'
            + '<div class="cal-detail-item__label"><i class="bi ' + icon + '"></i>' + label + "</div>"
            + '<div class="cal-detail-item__value">' + (value || "\u2014") + "</div>"
            + "</div>";
    }

    function openDetail(e) {
        const typeCls = "cal-detail-type--" + e.eventType;
        let bodyHtml = "";

        bodyHtml += '<div class="cal-detail-type ' + typeCls + '">'
            + pillIcon(e.eventType) + " " + (e.typeLabel || e.eventType) + "</div>";
        bodyHtml += '<div class="cal-detail-title">' + escapeHtml(e.title) + "</div>";

        bodyHtml += '<div class="cal-detail-grid">';

        if (e.eventType === "TASK") {
            const status = statusBadge(e);
            bodyHtml += detailItem("Subject", "bi-book-half", escapeHtml(e.subjectName));
            bodyHtml += detailItem("Task type", "bi-tag", escapeHtml(e.subTypeLabel));
            bodyHtml += '<div class="cal-detail-item">'
                + '<div class="cal-detail-item__label"><i class="bi bi-check-circle"></i>Status</div>'
                + '<div class="cal-detail-item__value">' + status + "</div></div>";
            bodyHtml += detailItem("Due date", "bi-calendar-event",
                formatDayLabel(toDate(e.startDate)));
            bodyHtml += detailItem("Due time", "bi-clock",
                e.startTime ? formatTimeMin(e.startTime) : "\u2014");
            bodyHtml += '<div class="cal-detail-item">'
                + '<div class="cal-detail-item__label"><i class="bi bi-bar-chart"></i>Progress</div>'
                + '<div class="cal-progress">'
                + '<div class="cal-progress__bar"><div class="cal-progress__fill" style="width:'
                + (e.progress || 0) + '%"></div></div>'
                + '<span class="cal-progress__label">' + (e.progress || 0) + "%</span>"
                + "</div></div>";
        } else if (e.eventType === "EXAM") {
            const status = statusBadge(e);
            bodyHtml += detailItem("Subject", "bi-book-half", escapeHtml(e.subjectName));
            bodyHtml += detailItem("Exam type", "bi-tag", escapeHtml(e.subTypeLabel));
            bodyHtml += '<div class="cal-detail-item">'
                + '<div class="cal-detail-item__label"><i class="bi bi-check-circle"></i>Status</div>'
                + '<div class="cal-detail-item__value">' + status + "</div></div>";
            bodyHtml += detailItem("Date", "bi-calendar-event",
                formatDayLabel(toDate(e.startDate)));
            bodyHtml += detailItem("Time", "bi-clock",
                e.startTime ? formatTimeMin(e.startTime) : "\u2014");
            bodyHtml += detailItem("Duration", "bi-hourglass-split",
                formatDuration(e.durationMinutes));
        } else {
            // STUDY_SESSION
            bodyHtml += detailItem("Subject", "bi-book-half", escapeHtml(e.subjectName));
            bodyHtml += detailItem("Topic", "bi-journal-text",
                e.topicName ? escapeHtml(e.topicName) : "\u2014");
            bodyHtml += detailItem("Date", "bi-calendar-event",
                formatDayLabel(toDate(e.startDate)));
            bodyHtml += detailItem("Start", "bi-clock",
                e.startTime ? formatTimeMin(e.startTime) : "\u2014");
            bodyHtml += detailItem("End", "bi-clock-history",
                e.endTime ? formatTimeMin(e.endTime) : "\u2014");
            bodyHtml += detailItem("Duration", "bi-stopwatch",
                formatDuration(e.durationMinutes));
            bodyHtml += '<div class="cal-detail-item">'
                + '<div class="cal-detail-item__label"><i class="bi bi-tag"></i>Session type</div>'
                + '<div class="cal-detail-item__value">'
                + (e.sessionTypeLabel || e.typeLabel || "\u2014") + "</div></div>";
            if (e.notes) {
                bodyHtml += '<div class="cal-detail-item cal-detail-item--full">'
                    + '<div class="cal-detail-item__label"><i class="bi bi-journal-text"></i>Notes</div>'
                    + '<div class="cal-detail-item__value">' + escapeHtml(e.notes) + "</div></div>";
            }
        }

        bodyHtml += "</div>";

        els.detailTitle.textContent = (e.typeLabel || e.eventType) + " details";
        els.detailBody.innerHTML = bodyHtml;

        // footer actions (existing routes)
        let footer = "";
        if (e.eventType === "TASK") {
            footer += '<a class="btn btn-primary" href="/tasks/edit/' + e.id + '">'
                + '<i class="bi bi-pencil-square"></i> Edit Task</a>';
            footer += '<a class="btn btn-outline" href="/tasks">'
                + '<i class="bi bi-list-check"></i> All Tasks</a>';
        } else if (e.eventType === "EXAM") {
            footer += '<a class="btn btn-primary" href="/exams/edit/' + e.id + '">'
                + '<i class="bi bi-pencil-square"></i> Edit Exam</a>';
            footer += '<a class="btn btn-outline" href="/exams">'
                + '<i class="bi bi-mortarboard"></i> All Exams</a>';
        } else {
            footer += '<a class="btn btn-primary" href="/study-sessions/edit/' + e.id + '">'
                + '<i class="bi bi-pencil-square"></i> Edit Session</a>';
            footer += '<a class="btn btn-outline" href="/study-sessions">'
                + '<i class="bi bi-stopwatch"></i> All Sessions</a>';
        }
        els.detailFooter.innerHTML = footer;

        els.overlay.classList.add("open");
        previousFocus = document.activeElement;
        els.detailClose.focus();
    }

    function closeDetail() {
        els.overlay.classList.remove("open");
        if (previousFocus && typeof previousFocus.focus === "function") {
            previousFocus.focus();
            previousFocus = null;
        }
    }

    // ---------- Mobile day panel ----------

    function openDayPanel(date, dayEvents) {
        if (window.innerWidth > 768) {
            return false;
        }
        els.dayPanelHead.textContent = MONTHS[date.getMonth()] + " " + date.getDate() + ", "
            + date.getFullYear() + " \u00B7 " + dayEvents.length + " event"
            + (dayEvents.length === 1 ? "" : "s");

        let list = "";
        dayEvents.forEach(function (e) {
            const cls = "cal-event-pill cal-event-pill--" + e.eventType
                + (e.completed ? " is-completed" : "");
            list += '<button type="button" class="' + cls + '"'
                + ' data-event-id="' + e.id + '" data-event-type="' + e.eventType + '">'
                + pillIcon(e.eventType) + " " + escapeHtml(pillLabel(e)) + "</button>";
        });
        els.dayPanelList.innerHTML = list;
        els.dayPanel.hidden = false;
        return true;
    }

    function closeDayPanel() {
        els.dayPanel.hidden = true;
    }

    // ---------- Escape helper ----------

    function escapeHtml(str) {
        if (str == null) {
            return "";
        }
        return String(str)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    // ---------- Event delegation ----------

    function handleDocumentClick(e) {
        const target = e.target;

        // event pill / timed event click
        const eventBtn = target.closest("[data-event-id]");
        if (eventBtn) {
            e.preventDefault();
            const id = Number(eventBtn.getAttribute("data-event-id"));
            const type = eventBtn.getAttribute("data-event-type");
            const ev = findEvent(id, type);
            if (ev) {
                openDetail(ev);
            }
            return;
        }

        // "+N more" on a day
        const more = target.closest(".cal-more");
        if (more) {
            e.preventDefault();
            const dateStr = more.getAttribute("data-date");
            const date = toDate(dateStr);
            const dayEvents = (eventsByDate(applyFilters(state.events))[dateStr]) || [];
            openDayPanel(date, dayEvents) || (function () {
                state.cursor = date;
                state.view = "day";
                refreshCurrentView();
            })();
            return;
        }

        // month day cell click (desktop -> jump to day view)
        const cell = target.closest(".cal-day-cell[data-date]");
        if (cell && !target.closest("button")) {
            const dateStr = cell.getAttribute("data-date");
            const date = toDate(dateStr);
            const dayEvents = (eventsByDate(applyFilters(state.events))[dateStr]) || [];
            if (openDayPanel(date, dayEvents)) {
                return;
            }
            state.cursor = date;
            state.view = "day";
            refreshCurrentView();
            return;
        }
    }

    function handleViewTabClick(ev) {
        const view = ev.currentTarget.getAttribute("data-view");
        if (view === state.view) {
            return;
        }
        const base = startOfDay(state.cursor);
        // keep the same reference date; recompute cursor to a stable point
        if (view === "month") {
            state.cursor = new Date(base.getFullYear(), base.getMonth(), 1);
        } else if (view === "week") {
            state.cursor = addDays(base, -mondayIndex(base));
        } else {
            state.cursor = base;
        }
        state.view = view;
        refreshCurrentView();
    }

    function handleTypeFilterClick(ev) {
        const type = ev.currentTarget.getAttribute("data-type");
        state.typeFilter = type;
        render();
    }

    // ---------- Init ----------

    function init() {
        initElements();
        if (!els.monthGrid) {
            return;
        }

        const initial = window.calendarInitial || {};
        state.subjects = initial.subjects || [];
        state.events = initial.events || [];

        // Read URL params for deep-link state
        var params = new URLSearchParams(window.location.search);
        var urlDate = params.get("date");
        var urlView = params.get("view");
        var urlType = params.get("type");
        var urlSubject = params.get("subject");

        if (urlView && (urlView === "month" || urlView === "week" || urlView === "day")) {
            state.view = urlView;
        }
        if (urlType && ["TASK", "EXAM", "STUDY_SESSION"].indexOf(urlType) !== -1) {
            state.typeFilter = urlType;
        }
        if (urlSubject) {
            state.subjectId = urlSubject;
        }

        // cursor: prefer URL date, fall back to server-provided month start
        var cursorDate = urlDate ? toDate(urlDate) : toDate(initial.startDate);
        if (!cursorDate) {
            cursorDate = new Date();
        }
        if (state.view === "month") {
            state.cursor = new Date(cursorDate.getFullYear(), cursorDate.getMonth(), 1);
        } else if (state.view === "week") {
            state.cursor = addDays(startOfDay(cursorDate), -mondayIndex(startOfDay(cursorDate)));
        } else {
            state.cursor = startOfDay(cursorDate);
        }

        // Sync filter UI with URL state
        if (state.typeFilter !== "all") {
            document.querySelectorAll(".cal-filter").forEach(function (btn) {
                btn.classList.toggle("is-active", btn.getAttribute("data-type") === state.typeFilter);
            });
        }
        if (state.subjectId !== "all" && els.subjectFilter) {
            els.subjectFilter.value = state.subjectId;
        }

        // wire events
        document.getElementById("calPrev").addEventListener("click", function () { shiftPeriod(-1); });
        document.getElementById("calToday").addEventListener("click", goToday);
        document.getElementById("calNext").addEventListener("click", function () { shiftPeriod(1); });

        document.querySelectorAll(".view-switch__tab").forEach(function (tab) {
            tab.addEventListener("click", handleViewTabClick);
        });

        document.querySelectorAll(".cal-filter").forEach(function (btn) {
            btn.addEventListener("click", handleTypeFilterClick);
        });

        if (els.subjectFilter) {
            els.subjectFilter.addEventListener("change", function () {
                state.subjectId = els.subjectFilter.value;
                render();
            });
        }

        var clearBtn = document.getElementById("calClearFilters");
        if (clearBtn) {
            clearBtn.addEventListener("click", clearFilters);
        }

        document.addEventListener("click", handleDocumentClick);

        els.detailClose.addEventListener("click", closeDetail);
        els.overlay.addEventListener("click", function (e) {
            if (e.target === els.overlay) {
                closeDetail();
            }
        });
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") {
                closeDetail();
                closeDayPanel();
            }
            // Focus trap: keep Tab inside the modal when open
            if (e.key === "Tab" && els.overlay.classList.contains("open")) {
                var focusable = els.overlay.querySelectorAll(
                    'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])'
                );
                if (focusable.length === 0) {
                    return;
                }
                var first = focusable[0];
                var last = focusable[focusable.length - 1];
                if (e.shiftKey) {
                    if (document.activeElement === first) {
                        e.preventDefault();
                        last.focus();
                    }
                } else {
                    if (document.activeElement === last) {
                        e.preventDefault();
                        first.focus();
                    }
                }
            }
        });

        els.dayPanelClose.addEventListener("click", closeDayPanel);

        render();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }

})();
