/*
=====================================================
        StudyFlow - Dashboard JavaScript
=====================================================
Modules:
  1. Setup Progress Animation
=====================================================
This file handles small UI interactions only. All
statistics come from the server; no data is generated
or hard-coded on the client.
=====================================================
*/

(function () {

    "use strict";

    /*
    =====================================================
     1. Setup Progress Animation
     The server renders the final width and the target
     percentage in data-percent. On load we animate the
     bar from 0 to that real value. If scripting is
     disabled the server-rendered width is still correct.
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

        // Start from 0 for a clean entrance animation.
        fill.style.width = "0%";
        fill.classList.add("is-animating");

        // Force a reflow so the transition fires from 0.
        void fill.offsetWidth;

        fill.style.width = targetWidth;

    }


    /*
    =====================================================
     Bootstrap
    =====================================================
    */

    document.addEventListener("DOMContentLoaded", () => {
        initializeSetupProgress();
    });

})();
