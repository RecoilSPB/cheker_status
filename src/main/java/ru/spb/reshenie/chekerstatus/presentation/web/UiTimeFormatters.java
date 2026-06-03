package ru.spb.reshenie.chekerstatus.presentation.web;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component("uiTimeFormatters")
public class UiTimeFormatters {

    private static final Locale RU_LOCALE = Locale.forLanguageTag("ru-RU");
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("UTC");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", RU_LOCALE).withZone(FALLBACK_ZONE);
    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", RU_LOCALE);

    public String formatUtcDateTime(OffsetDateTime value) {
        if (value == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(value.toInstant()).replace(".", "");
    }

    public String formatDuration(Long durationMs) {
        if (durationMs == null) {
            return "";
        }

        long remaining = Math.abs(durationMs);
        long hours = remaining / 3_600_000;
        remaining %= 3_600_000;
        long minutes = remaining / 60_000;
        remaining %= 60_000;
        long seconds = remaining / 1_000;
        long milliseconds = remaining % 1_000;

        StringBuilder result = new StringBuilder();
        appendDurationPart(result, hours, "час", "часа", "часов");
        appendDurationPart(result, minutes, "минута", "минуты", "минут");
        appendDurationPart(result, seconds, "секунда", "секунды", "секунд");
        appendDurationPart(result, milliseconds, "миллисекунда", "миллисекунды", "миллисекунд");

        if (result.length() == 0) {
            return "0 миллисекунд";
        }
        return durationMs < 0 ? "-" + result : result.toString();
    }

    public String formatLocalDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return LOCAL_DATE_TIME_FORMATTER.format(value).replace(".", "");
    }

    private void appendDurationPart(StringBuilder result, long value, String one, String few, String many) {
        if (value <= 0) {
            return;
        }
        if (result.length() > 0) {
            result.append(' ');
        }
        result.append(value).append(' ').append(plural(value, one, few, many));
    }

    private String plural(long value, String one, String few, String many) {
        long mod10 = value % 10;
        long mod100 = value % 100;
        if (mod10 == 1 && mod100 != 11) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return few;
        }
        return many;
    }
}
