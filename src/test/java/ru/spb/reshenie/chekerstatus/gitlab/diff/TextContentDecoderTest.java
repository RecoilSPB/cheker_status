package ru.spb.reshenie.chekerstatus.gitlab.diff;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

class TextContentDecoderTest {

    @Test
    void decodesWindows1251CsvUsingCyrillicPathHint() {
        byte[] bytes = "№;Xpath;Кардинальность;Номер требования"
                .getBytes(Charset.forName("windows-1251"));

        String decoded = TextContentDecoder.decode(bytes, "xpath/CDA_ПЕРВИЧНЫЙ_ОСМОТР_ВРАЧА_Р1.csv");

        assertThat(decoded).isEqualTo("№;Xpath;Кардинальность;Номер требования");
    }

    @Test
    void decodesRealSemdCsvHeaderAndRow() {
        byte[] bytes = ("№;0;1;2;3;4;5;6;7;8;Xpath;Кардинальность;Номер требования\n"
                + "1;Указание на область применения документа (РФ) ;;;;;;;;;ClinicalDocument/realmCode; R [1..1];У1-1")
                .getBytes(Charset.forName("windows-1251"));

        String decoded = TextContentDecoder.decode(bytes, "xpath/CDA_ПЕРВИЧНЫЙ_ОСМОТР_ВРАЧА_Р1.csv");

        assertThat(decoded).contains("Кардинальность");
        assertThat(decoded).contains("Указание на область применения документа");
        assertThat(decoded).doesNotContain("��������");
    }
}
