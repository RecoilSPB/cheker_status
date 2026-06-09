(function (global) {
    "use strict";

    var REFRESH_INTERVAL_MS = 500;
    var MAX_RECONNECT_MS = 30000;
    var SCROLL_TOLERANCE_PX = 2;
    var timers = {};
    var lastRefreshAt = {};
    var fragmentRequests = {};
    var queuedFragments = {};
    var progressStates = {};
    var reconnectAttempt = 0;
    var socket = null;

    function byId(id) {
        return document.getElementById(id);
    }

    function sameRun(element, syncRunId) {
        if (!element || syncRunId == null) {
            return false;
        }
        return String(element.dataset.syncRunId) === String(syncRunId);
    }

    function scheduleRefresh(key, callback) {
        var now = Date.now();
        var last = lastRefreshAt[key] || 0;
        var wait = Math.max(0, REFRESH_INTERVAL_MS - (now - last));
        if (wait === 0) {
            if (timers[key]) {
                clearTimeout(timers[key]);
                delete timers[key];
            }
            lastRefreshAt[key] = now;
            callback();
            return;
        }
        if (timers[key]) {
            return;
        }
        timers[key] = setTimeout(function () {
            delete timers[key];
            lastRefreshAt[key] = Date.now();
            callback();
        }, wait);
    }

    function fragmentUrl(element) {
        if (!element || !element.dataset.liveFragmentUrl) {
            return null;
        }
        var url = element.dataset.liveFragmentUrl;
        if (element.id === "sync-runs-panel" && global.location.search) {
            url += global.location.search;
        }
        return url;
    }

    function fragmentRequestKey(element) {
        if (!element) {
            return null;
        }
        return element.id || element.dataset.liveFragmentUrl;
    }

    function currentScroll() {
        var scrollRoot = document.scrollingElement || document.documentElement;
        return {
            x: global.scrollX || scrollRoot.scrollLeft || 0,
            y: global.scrollY || scrollRoot.scrollTop || 0
        };
    }

    function captureViewport() {
        return currentScroll();
    }

    function viewportMoved(snapshot) {
        var scroll = currentScroll();
        return Math.abs(scroll.x - snapshot.x) > SCROLL_TOLERANCE_PX
            || Math.abs(scroll.y - snapshot.y) > SCROLL_TOLERANCE_PX;
    }

    function restoreViewport(snapshot) {
        (global.requestAnimationFrame || global.setTimeout)(function () {
            global.scrollTo(snapshot.x, snapshot.y);
        });
    }

    function replaceFragment(element) {
        var url = fragmentUrl(element);
        if (!url) {
            return Promise.resolve();
        }
        var key = fragmentRequestKey(element);
        var elementId = element.id;
        if (key && fragmentRequests[key]) {
            queuedFragments[key] = true;
            return fragmentRequests[key];
        }
        var previousProgress = captureProgressStates(element);
        var viewport = captureViewport();

        var request = fetch(url, {
            cache: "no-store",
            credentials: "same-origin",
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            }
        }).then(function (response) {
            if (!response.ok) {
                throw new Error("Fragment request failed: " + response.status);
            }
            return response.text();
        }).then(function (html) {
            var template = document.createElement("template");
            template.innerHTML = html.trim();
            var next = template.content.firstElementChild;
            if (!next) {
                return;
            }
            var shouldRestoreViewport = !viewportMoved(viewport);
            element.replaceWith(next);
            applyFormatting(next);
            initializeProgress(next, previousProgress);
            updateCountdowns();
            updateManualSyncButton(next);
            if (shouldRestoreViewport) {
                restoreViewport(viewport);
            }
        }).catch(function () {
            // The WebSocket reconnect loop will retry; keep UI stable on transient failures.
        }).finally(function () {
            if (!key) {
                return;
            }
            delete fragmentRequests[key];
            if (queuedFragments[key]) {
                delete queuedFragments[key];
                replaceFragment(byId(elementId));
            }
        });
        if (key) {
            fragmentRequests[key] = request;
        }
        return request;
    }

    function clampPercent(value) {
        var number = Number(value);
        if (Number.isNaN(number)) {
            return 0;
        }
        return Math.max(0, Math.min(100, number));
    }

    function progressWidgets(root) {
        return Array.prototype.slice.call((root || document).querySelectorAll(".progress-widget[data-progress-key]"));
    }

    function progressKey(widget) {
        return widget.dataset.progressKey;
    }

    function progressTarget(widget) {
        return clampPercent(widget.dataset.progressValue);
    }

    function progressLabel(widget) {
        return widget.querySelector(".progress-meta strong");
    }

    function progressBar(widget) {
        return widget.querySelector("[role='progressbar']");
    }

    function progressFill(widget) {
        return widget.querySelector(".progress-fill");
    }

    function progressDisplayed(widget) {
        if (widget.dataset.progressDisplay) {
            return clampPercent(widget.dataset.progressDisplay);
        }
        var fill = progressFill(widget);
        if (fill && fill.style.width) {
            return clampPercent(fill.style.width.replace("%", ""));
        }
        return progressTarget(widget);
    }

    function setProgress(widget, value) {
        var percent = Math.round(clampPercent(value));
        var fill = progressFill(widget);
        var bar = progressBar(widget);
        var label = progressLabel(widget);
        widget.dataset.progressDisplay = String(percent);
        if (fill) {
            fill.style.width = percent + "%";
        }
        if (bar) {
            bar.setAttribute("aria-valuenow", String(percent));
        }
        if (label) {
            label.textContent = percent + "%";
        }
        progressStates[progressKey(widget)] = percent;
    }

    function captureProgressStates(root) {
        var states = {};
        progressWidgets(root).forEach(function (widget) {
            states[progressKey(widget)] = progressDisplayed(widget);
        });
        return states;
    }

    function initializeProgress(root, previousProgress) {
        progressWidgets(root).forEach(function (widget) {
            var key = progressKey(widget);
            var target = progressTarget(widget);
            var running = widget.dataset.progressRunning === "true";
            var start = previousProgress && previousProgress[key] != null
                ? previousProgress[key]
                : progressStates[key];
            if (start == null) {
                start = target;
            }
            if (running && start > target) {
                target = start;
            }
            setProgress(widget, start);
            if (Math.round(start) !== Math.round(target)) {
                (global.requestAnimationFrame || global.setTimeout)(function () {
                    setProgress(widget, target);
                });
            }
        });
    }

    function applyLiveProgress(syncRunId, progressPercent) {
        if (syncRunId == null || progressPercent == null) {
            return;
        }
        var key = "run-" + syncRunId;
        progressWidgets(document).forEach(function (widget) {
            if (progressKey(widget) !== key) {
                return;
            }
            var target = clampPercent(progressPercent);
            var current = progressDisplayed(widget);
            if (widget.dataset.progressRunning === "true") {
                target = Math.max(current, target);
            }
            widget.dataset.progressValue = String(target);
            setProgress(widget, target);
        });
    }

    function updateManualSyncButton(summary) {
        var summaryElement = summary && summary.id === "dashboard-summary" ? summary : byId("dashboard-summary");
        var button = byId("sync-start-button");
        if (!summaryElement || !button) {
            return;
        }
        var running = summaryElement.dataset.liveStatus === "RUNNING";
        button.disabled = running;
        if (running) {
            button.setAttribute("title", "Синхронизация уже выполняется");
        } else {
            button.removeAttribute("title");
        }
    }

    function refreshDashboard() {
        scheduleRefresh("dashboard", function () {
            var summary = byId("dashboard-summary");
            var runs = byId("sync-runs-panel");
            if (summary) {
                replaceFragment(summary);
            }
            if (runs) {
                replaceFragment(runs);
            }
        });
    }

    function refreshRun(syncRunId) {
        var detail = byId("sync-run-detail");
        if (!sameRun(detail, syncRunId)) {
            return;
        }
        scheduleRefresh("sync-run-" + syncRunId, function () {
            replaceFragment(byId("sync-run-detail"));
        });
    }

    function refreshLog(syncRunId) {
        var log = byId("sync-run-log");
        if (!sameRun(log, syncRunId)) {
            return;
        }
        scheduleRefresh("sync-run-log-" + syncRunId, function () {
            replaceFragment(byId("sync-run-log"));
        });
    }

    function handleLiveMessage(message) {
        var type = message.type;
        var syncRunId = message.syncRunId;
        applyLiveProgress(syncRunId, message.progressPercent);

        if (type === "dashboard.changed" || type === "scheduler.changed") {
            refreshDashboard();
            return;
        }
        if (type === "syncRun.changed") {
            refreshDashboard();
            refreshRun(syncRunId);
            refreshLog(syncRunId);
            return;
        }
        if (type === "syncRun.log.changed") {
            refreshLog(syncRunId);
        }
    }

    function connect() {
        if (!("WebSocket" in global)) {
            return;
        }
        var protocol = global.location.protocol === "https:" ? "wss:" : "ws:";
        var url = protocol + "//" + global.location.host + "/ws/live";
        socket = new WebSocket(url);
        socket.onopen = function () {
            reconnectAttempt = 0;
            refreshVisibleFragments();
        };
        socket.onmessage = function (event) {
            try {
                handleLiveMessage(JSON.parse(event.data));
            } catch (e) {
                // Ignore malformed messages from stale tabs or proxy glitches.
            }
        };
        socket.onclose = function () {
            scheduleReconnect();
        };
        socket.onerror = function () {
            if (socket) {
                socket.close();
            }
        };
    }

    function scheduleReconnect() {
        reconnectAttempt++;
        var delay = Math.min(MAX_RECONNECT_MS, Math.pow(2, reconnectAttempt) * 1000);
        setTimeout(connect, delay);
    }

    function refreshVisibleFragments() {
        refreshDashboard();
        var detail = byId("sync-run-detail");
        if (detail) {
            refreshRun(detail.dataset.syncRunId);
        }
        var log = byId("sync-run-log");
        if (log) {
            refreshLog(log.dataset.syncRunId);
        }
    }

    function applyFormatting(root) {
        if (global.clientDateFormat && global.clientDateFormat.applyClientFormatting) {
            global.clientDateFormat.applyClientFormatting(root || document);
        }
    }

    function formatCountdown(milliseconds) {
        var rounded = Math.max(0, Math.floor(milliseconds / 1000) * 1000);
        if (global.clientDateFormat && global.clientDateFormat.formatDurationShort) {
            return global.clientDateFormat.formatDurationShort(rounded);
        }
        return Math.ceil(rounded / 1000) + " сек";
    }

    function updateCountdowns() {
        document.querySelectorAll("[data-countdown-target]").forEach(function (element) {
            var target = new Date(element.dataset.countdownTarget);
            if (Number.isNaN(target.getTime())) {
                return;
            }
            var remaining = target.getTime() - Date.now();
            element.textContent = remaining <= 0
                ? "следующий запуск скоро"
                : "следующий запуск через " + formatCountdown(remaining);
        });
    }

    function shouldIgnoreRowNavigation(target) {
        if (!target || !target.closest) {
            return true;
        }
        return !!target.closest("a, button, input, select, textarea, label, summary");
    }

    function openClickableRow(row) {
        if (row && row.dataset.rowHref) {
            global.location.href = row.dataset.rowHref;
        }
    }

    function bindClickableRows() {
        document.addEventListener("click", function (event) {
            var row = event.target.closest("[data-row-href]");
            if (!row || shouldIgnoreRowNavigation(event.target)) {
                return;
            }
            openClickableRow(row);
        });

        document.addEventListener("keydown", function (event) {
            if (event.key !== "Enter" && event.key !== " ") {
                return;
            }
            var row = event.target.closest("[data-row-href]");
            if (!row || shouldIgnoreRowNavigation(event.target)) {
                return;
            }
            event.preventDefault();
            openClickableRow(row);
        });
    }

    function start() {
        applyFormatting(document);
        initializeProgress(document, {});
        updateCountdowns();
        updateManualSyncButton();
        bindClickableRows();
        connect();
        setInterval(updateCountdowns, 1000);
    }

    if (typeof document !== "undefined") {
        document.addEventListener("DOMContentLoaded", start);
    }
})(window);
