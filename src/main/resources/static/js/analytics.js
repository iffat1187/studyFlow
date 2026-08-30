/*
=====================================================
        StudyFlow Analytics JavaScript
        Initializes the Chart.js charts from the
        aggregated statistics provided by Thymeleaf.
        No sensitive data is exposed here — only
        minutes, session counts and chart labels.
=====================================================
*/

(function () {

    "use strict";

    document.addEventListener("DOMContentLoaded", function () {

        const chartData = window.analyticsChartData || null;

        if (!chartData || !window.Chart) {
            return;
        }

        // Display-only helper mirroring the server formatter.
        // The chart VALUES always come from the backend.
        function formatMinutes(total) {

            const minutes = Math.round(total);

            if (minutes < 60) {
                return minutes + "m";
            }

            const hours = Math.floor(minutes / 60);
            const rest = minutes % 60;
            const label = hours + "h";
            return rest === 0 ? label : label + " " + rest + "m";

        }

        const theme = {
            text: "#94A3B8",
            grid: "rgba(255,255,255,.05)",
            card: "#1E293B",
            violet: "#8B5CF6",
            violetSoft: "rgba(139,92,246,.55)"
        };

        // ------------------------------------------------------
        // Daily Study Trend (bar)
        // ------------------------------------------------------

        const trendCanvas = document.getElementById("dailyTrendChart");

        if (trendCanvas) {

            new Chart(trendCanvas, {

                type: "bar",

                data: {

                    labels: chartData.dailyLabels || [],

                    datasets: [{

                        label: "Study minutes",
                        data: chartData.dailyMinutes || [],
                        backgroundColor: theme.violetSoft,
                        borderColor: theme.violet,
                        borderWidth: 1,
                        borderRadius: 6,
                        hoverBackgroundColor: "#A78BFA"

                    }]

                },

                options: {

                    responsive: true,
                    maintainAspectRatio: false,

                    plugins: {

                        legend: { display: false },

                        tooltip: {

                            callbacks: {

                                label: function (context) {

                                    return "Study time: " + formatMinutes(context.parsed.y);

                                }

                            }

                        }

                    },

                    scales: {

                        x: {
                            grid: { display: false },
                            ticks: { color: theme.text }
                        },

                        y: {
                            beginAtZero: true,
                            grid: { color: theme.grid },
                            ticks: { color: theme.text, precision: 0 }
                        }

                    }

                }

            });

        }

        // ------------------------------------------------------
        // Subject Distribution (doughnut)
        // ------------------------------------------------------

        const subjectCanvas = document.getElementById("subjectDistributionChart");

        if (subjectCanvas) {

            new Chart(subjectCanvas, {

                type: "doughnut",

                data: {

                    labels: chartData.subjectLabels || [],

                    datasets: [{

                        data: chartData.subjectMinutes || [],
                        backgroundColor: chartData.subjectColors || [],
                        borderColor: theme.card,
                        borderWidth: 3,
                        hoverOffset: 8

                    }]

                },

                options: {

                    responsive: true,
                    maintainAspectRatio: false,
                    cutout: "66%",

                    plugins: {

                        legend: {
                            position: "bottom",
                            labels: { color: theme.text, padding: 16, usePointStyle: true }
                        },

                        tooltip: {

                            callbacks: {

                                label: function (context) {

                                    const total = context.dataset.data.reduce(
                                        (sum, value) => sum + value, 0
                                    );

                                    const minutes = context.parsed;
                                    const percent = total > 0
                                        ? Math.round((minutes / total) * 100)
                                        : 0;

                                    return context.label + ": " + formatMinutes(minutes)
                                        + " (" + percent + "%)";

                                }

                            }

                        }

                    }

                }

            });

        }

        // ------------------------------------------------------
        // Session Type Distribution (doughnut)
        // ------------------------------------------------------

        const typeCanvas = document.getElementById("sessionTypeDistributionChart");

        if (typeCanvas) {

            new Chart(typeCanvas, {

                type: "doughnut",

                data: {

                    labels: chartData.typeLabels || [],

                    datasets: [{

                        data: chartData.typeMinutes || [],
                        backgroundColor: chartData.typeColors || [],
                        borderColor: theme.card,
                        borderWidth: 3,
                        hoverOffset: 8

                    }]

                },

                options: {

                    responsive: true,
                    maintainAspectRatio: false,
                    cutout: "66%",

                    plugins: {

                        legend: {
                            position: "bottom",
                            labels: { color: theme.text, padding: 16, usePointStyle: true }
                        },

                        tooltip: {

                            callbacks: {

                                label: function (context) {

                                    const total = context.dataset.data.reduce(
                                        (sum, value) => sum + value, 0
                                    );

                                    const minutes = context.parsed;
                                    const percent = total > 0
                                        ? Math.round((minutes / total) * 100)
                                        : 0;

                                    return context.label + ": " + formatMinutes(minutes)
                                        + " (" + percent + "%)";

                                }

                            }

                        }

                    }

                }

            });

        }

    });

})();