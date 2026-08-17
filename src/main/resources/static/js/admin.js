/*
=====================================================
        StudyFlow Admin Dashboard JavaScript
        Initializes the Chart.js charts from the
        aggregated statistics provided by Thymeleaf.
        No sensitive data is exposed here — only
        counts and chart labels.
=====================================================
*/

(function () {

    "use strict";

    document.addEventListener("DOMContentLoaded", function () {

        const chartData = window.adminChartData || null;

        if (!chartData || !window.Chart) {
            return;
        }

        const trendCanvas = document.getElementById("registrationTrendChart");

        if (trendCanvas) {

            new Chart(trendCanvas, {

                type: "line",

                data: {

                    labels: chartData.trendLabels || [],

                    datasets: [{

                        label: "New registrations",
                        data: chartData.trendCounts || [],
                        borderColor: "#8B5CF6",
                        backgroundColor: "rgba(139,92,246,.16)",
                        fill: true,
                        tension: .4,
                        borderWidth: 2,
                        pointBackgroundColor: "#A78BFA",
                        pointBorderColor: "#A78BFA",
                        pointRadius: 4,
                        pointHoverRadius: 6

                    }]

                },

                options: {

                    responsive: true,
                    maintainAspectRatio: false,

                    plugins: {

                        legend: {
                            labels: { color: "#94A3B8", usePointStyle: true }
                        }

                    },

                    scales: {

                        x: {
                            grid: { color: "rgba(255,255,255,.05)" },
                            ticks: { color: "#94A3B8" }
                        },

                        y: {
                            beginAtZero: true,
                            grid: { color: "rgba(255,255,255,.05)" },
                            ticks: { color: "#94A3B8", precision: 0 }
                        }

                    }

                }

            });

        }

        const roleCanvas = document.getElementById("roleDistributionChart");

        if (roleCanvas) {

            new Chart(roleCanvas, {

                type: "doughnut",

                data: {

                    labels: chartData.roleLabels || [],

                    datasets: [{

                        data: chartData.roleCounts || [],
                        backgroundColor: ["#8B5CF6", "#38BDF8"],
                        borderColor: "#1E293B",
                        borderWidth: 3,
                        hoverOffset: 8

                    }]

                },

                options: {

                    responsive: true,
                    maintainAspectRatio: false,
                    cutout: "68%",

                    plugins: {

                        legend: {
                            position: "bottom",
                            labels: { color: "#94A3B8", padding: 18, usePointStyle: true }
                        },

                        tooltip: {

                            callbacks: {

                                label: function (context) {

                                    const total = context.dataset.data.reduce(
                                        (sum, value) => sum + value, 0
                                    );

                                    const count = context.parsed;
                                    const percent = total > 0
                                        ? Math.round((count / total) * 100)
                                        : 0;

                                    return context.label + ": " + count + " (" + percent + "%)";

                                }

                            }

                        }

                    }

                }

            });

        }

    });

})();