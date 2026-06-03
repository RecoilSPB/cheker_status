(function (global) {
    "use strict";

    var LOCALE = "ru-RU";
    var DEFAULT_TIME_ZONE = "UTC";

    function getClientTimeZone() {
        var options = Intl.DateTimeFormat().resolvedOptions();
        return options.timeZone || DEFAULT_TIME_ZONE;
    }

    function parseDate(value) {
        if (!value) {
            return null;
        }

        var date = new Date(value);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function formatterOptions(timeZone) {
        return {
            day: "2-digit",
            month: "short",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false,
            timeZone: timeZone || getClientTimeZone()
        };
    }

    function partValue(parts, type) {
        var part = parts.find(function (item) {
            return item.type === type;
        });
        return part == null ? "" : part.value;
    }

    function formatDateTime(value, timeZone) {
        var date = parseDate(value);
        if (date == null) {
            return "";
        }

        var parts = new Intl.DateTimeFormat(LOCALE, formatterOptions(timeZone)).formatToParts(date);
        var month = partValue(parts, "month").replace(".", "");
        return [
            partValue(parts, "day"),
            month,
            partValue(parts, "year") + ",",
            partValue(parts, "hour") + ":" + partValue(parts, "minute") + ":" + partValue(parts, "second")
        ].join(" ");
    }

    function formatDateTimeTitle(value, timeZone) {
        var date = parseDate(value);
        if (date == null) {
            return "";
        }

        return new Intl.DateTimeFormat(LOCALE, {
            weekday: "long",
            day: "numeric",
            month: "long",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            timeZoneName: "short",
            timeZone: timeZone || getClientTimeZone()
        }).format(date);
    }

    function plural(value, one, few, many) {
        var mod10 = value % 10;
        var mod100 = value % 100;
        if (mod10 === 1 && mod100 !== 11) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return few;
        }
        return many;
    }

    function appendDurationPart(parts, value, one, few, many) {
        if (value > 0) {
            parts.push(value + " " + plural(value, one, few, many));
        }
    }

    function formatDuration(durationMs) {
        var value = Number(durationMs);
        if (!Number.isFinite(value)) {
            return "";
        }

        var sign = value < 0 ? "-" : "";
        var remaining = Math.abs(Math.trunc(value));
        var hours = Math.floor(remaining / 3600000);
        remaining %= 3600000;
        var minutes = Math.floor(remaining / 60000);
        remaining %= 60000;
        var seconds = Math.floor(remaining / 1000);
        var milliseconds = remaining % 1000;

        var parts = [];
        appendDurationPart(parts, hours, "час", "часа", "часов");
        appendDurationPart(parts, minutes, "минута", "минуты", "минут");
        appendDurationPart(parts, seconds, "секунда", "секунды", "секунд");
        appendDurationPart(parts, milliseconds, "миллисекунда", "миллисекунды", "миллисекунд");

        if (parts.length === 0) {
            return "0 миллисекунд";
        }
        return sign + parts.join(" ");
    }

    function appendDurationShortPart(parts, value, unit) {
        if (value > 0) {
            parts.push(value + " " + unit);
        }
    }

    function formatDurationShort(durationMs) {
        var value = Number(durationMs);
        if (!Number.isFinite(value)) {
            return "";
        }

        var sign = value < 0 ? "-" : "";
        var remaining = Math.abs(Math.trunc(value));
        var hours = Math.floor(remaining / 3600000);
        remaining %= 3600000;
        var minutes = Math.floor(remaining / 60000);
        remaining %= 60000;
        var seconds = Math.floor(remaining / 1000);
        var milliseconds = remaining % 1000;

        var parts = [];
        appendDurationShortPart(parts, hours, "ч");
        appendDurationShortPart(parts, minutes, "мин");
        appendDurationShortPart(parts, seconds, "сек");
        appendDurationShortPart(parts, milliseconds, "мс");

        if (parts.length === 0) {
            return "0 мс";
        }
        return sign + parts.join(" ");
    }

    function fillClientTimeZone(root, timeZone) {
        var scope = root || document;
        var value = timeZone || getClientTimeZone();
        scope.querySelectorAll("input[name='tz'], input[name='clientTimeZone']").forEach(function (input) {
            input.value = value;
        });
    }

    function applyClientFormatting(root) {
        var scope = root || document;
        var timeZone = getClientTimeZone();

        fillClientTimeZone(scope, timeZone);

        scope.querySelectorAll("[data-utc]").forEach(function (element) {
            var source = element.dataset.utc;
            var formatted = formatDateTime(source, timeZone);
            if (!formatted) {
                return;
            }

            if (element.tagName === "TIME") {
                var date = parseDate(source);
                if (date != null) {
                    element.setAttribute("datetime", date.toISOString());
                }
            }
            element.textContent = formatted;
            element.title = formatDateTimeTitle(source, timeZone);
        });

        scope.querySelectorAll("[data-duration-ms]").forEach(function (element) {
            var full = formatDuration(element.dataset.durationMs);
            var compact = formatDurationShort(element.dataset.durationMs);
            if (full) {
                element.title = full;
            }
            if (element.dataset.durationFormat === "compact" && compact) {
                element.textContent = compact;
            } else if (full) {
                element.textContent = full;
            }
        });
    }

    global.clientDateFormat = {
        getClientTimeZone: getClientTimeZone,
        formatDateTime: formatDateTime,
        formatDuration: formatDuration,
        formatDurationShort: formatDurationShort,
        fillClientTimeZone: fillClientTimeZone,
        applyClientFormatting: applyClientFormatting
    };

    if (typeof document !== "undefined") {
        document.addEventListener("DOMContentLoaded", function () {
            applyClientFormatting(document);
        });
    }
})(typeof window !== "undefined" ? window : globalThis);
