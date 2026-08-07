/* =========================================================
   StudyFlow — Landing Page Scripts (standalone, vanilla JS)
   - Sticky navbar with blur on scroll
   - Mobile hamburger menu
   - Scroll reveal animations
   - Animated statistics counters
   - FAQ accordion
   - Active navigation highlighting
========================================================== */

(function () {
  'use strict';

  document.documentElement.classList.add('js');

  var $ = function (selector, scope) {
    return (scope || document).querySelector(selector);
  };

  var $$ = function (selector, scope) {
    return Array.prototype.slice.call((scope || document).querySelectorAll(selector));
  };

  /* ---------------------------------------------------------
     Sticky navbar — toggle blur/background on scroll
  --------------------------------------------------------- */

  var navbar = $('#landingNavbar');

  function onScroll() {
    if (!navbar) return;
    var scrolled = window.scrollY > 24;
    navbar.classList.toggle('landing-navbar--scrolled', scrolled);
  }

  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  /* ---------------------------------------------------------
     Mobile hamburger menu
  --------------------------------------------------------- */

  var hamburger = $('#landingHamburger');
  var navMenu = $('#landingNav');

  function closeMenu() {
    if (!hamburger || !navMenu) return;
    hamburger.classList.remove('landing-hamburger--open');
    navMenu.classList.remove('landing-nav--open');
    hamburger.setAttribute('aria-expanded', 'false');
  }

  if (hamburger && navMenu) {

    hamburger.addEventListener('click', function () {
      var isOpen = navMenu.classList.toggle('landing-nav--open');
      hamburger.classList.toggle('landing-hamburger--open', isOpen);
      hamburger.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
    });

    // Close the menu after choosing a destination.
    $$('.landing-menu__link', navMenu).forEach(function (link) {
      link.addEventListener('click', closeMenu);
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') closeMenu();
    });

    // Keep the menu tidy when the viewport is resized back to desktop.
    window.addEventListener('resize', function () {
      if (window.innerWidth > 860) closeMenu();
    });

  }

  /* ---------------------------------------------------------
     Scroll reveal animations
  --------------------------------------------------------- */

  var revealElements = $$('.reveal');

  if ('IntersectionObserver' in window && revealElements.length) {

    var revealObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('revealed');
          revealObserver.unobserve(entry.target);
        }
      });
    }, { threshold: 0.14, rootMargin: '0px 0px -40px 0px' });

    revealElements.forEach(function (element) {
      revealObserver.observe(element);
    });

  } else {

    // Fallback: show everything immediately.
    revealElements.forEach(function (element) {
      element.classList.add('revealed');
    });

  }

  /* ---------------------------------------------------------
     Active navigation highlighting
  --------------------------------------------------------- */

  var menuLinks = $$('.landing-menu__link');
  var sectionIds = menuLinks
    .map(function (link) {
      var hash = link.getAttribute('href');
      return hash ? hash.replace('#', '') : null;
    })
    .filter(function (id) { return id && $( '#' + id); });

  if ('IntersectionObserver' in window && sectionIds.length) {

    var sectionObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;

        menuLinks.forEach(function (link) {
          var isActive = link.getAttribute('href') === '#' + entry.target.id;
          link.classList.toggle('landing-menu__link--active', isActive);
        });
      });
    }, { rootMargin: '-40% 0px -55% 0px' });

    sectionIds.forEach(function (id) {
      sectionObserver.observe(document.getElementById(id));
    });

  }

  /* ---------------------------------------------------------
     Animated statistics counters
  --------------------------------------------------------- */

  var counters = $$('.landing-stat__number');

  function formatNumber(value, element) {
    var prefix = element.getAttribute('data-prefix') || '';
    var suffix = element.getAttribute('data-suffix') || '';

    // Special case: decimal-style counters (e.g. rating "4.9").
    if (prefix === '.') {
      return (value / 10).toFixed(1);
    }

    return Math.round(value).toLocaleString('en-US') + suffix;
  }

  function animateCounter(element) {
    var target = parseInt(element.getAttribute('data-count'), 10) || 0;
    var duration = 1800;
    var startTime = null;

    function step(timestamp) {
      if (startTime === null) startTime = timestamp;
      var progress = Math.min((timestamp - startTime) / duration, 1);
      // Ease-out for a natural finish.
      var eased = 1 - Math.pow(1 - progress, 3);

      element.textContent = formatNumber(target * eased, element);

      if (progress < 1) {
        window.requestAnimationFrame(step);
      } else {
        element.textContent = formatNumber(target, element);
      }
    }

    window.requestAnimationFrame(step);
  }

  if ('IntersectionObserver' in window && counters.length) {

    var counterObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          animateCounter(entry.target);
          counterObserver.unobserve(entry.target);
        }
      });
    }, { threshold: 0.4 });

    counters.forEach(function (counter) {
      counterObserver.observe(counter);
    });

  } else {

    counters.forEach(function (counter) {
      counter.textContent = formatNumber(parseInt(counter.getAttribute('data-count'), 10) || 0, counter);
    });

  }

  /* ---------------------------------------------------------
     FAQ accordion
  --------------------------------------------------------- */

  var faqQuestions = $$('.landing-faq__question');

  faqQuestions.forEach(function (question) {

    question.addEventListener('click', function () {
      var item = question.closest('.landing-faq__item');
      var isOpen = item.classList.contains('landing-faq__item--open');

      // Close every item, then open the clicked one.
      $$('.landing-faq__item').forEach(function (other) {
        other.classList.remove('landing-faq__item--open');
        var btn = other.querySelector('.landing-faq__question');
        if (btn) btn.setAttribute('aria-expanded', 'false');
      });

      if (!isOpen) {
        item.classList.add('landing-faq__item--open');
        question.setAttribute('aria-expanded', 'true');
      }
    });

  });

})();
