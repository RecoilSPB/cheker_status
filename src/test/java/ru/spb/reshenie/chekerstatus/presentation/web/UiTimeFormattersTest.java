package ru.spb.reshenie.chekerstatus.presentation.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiTimeFormattersTest {

    private final UiTimeFormatters formatters = new UiTimeFormatters();

    @Test
    void formatsDurationWithRussianWordForms() {
        assertThat(formatters.formatDuration(0L)).isEqualTo("0 миллисекунд");
        assertThat(formatters.formatDuration(45L)).isEqualTo("45 миллисекунд");
        assertThat(formatters.formatDuration(1_000L)).isEqualTo("1 секунда");
        assertThat(formatters.formatDuration(70_100L)).isEqualTo("1 минута 10 секунд 100 миллисекунд");
        assertThat(formatters.formatDuration(3_661_001L)).isEqualTo("1 час 1 минута 1 секунда 1 миллисекунда");
    }
}
