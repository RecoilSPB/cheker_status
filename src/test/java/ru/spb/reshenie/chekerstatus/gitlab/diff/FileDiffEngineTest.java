package ru.spb.reshenie.chekerstatus.gitlab.diff;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentSnapshot;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileDiffEngineTest {

    private final FileDiffEngine engine = new FileDiffEngine(List.of(
            new DocxFileDiffHandler(),
            new XlsxFileDiffHandler(),
            new PdfFileDiffHandler(),
            new TextFileDiffHandler(new ObjectMapper()),
            new BinaryFallbackFileDiffHandler()
    ));

    @Test
    void routesXmlToTextHandlerAndBuildsStructuredRows() {
        FileDiffSource source = new FileDiffSource(
                1L,
                2L,
                "sample.xml",
                "sample.xml",
                "sample.xml",
                "modified",
                new GitFileContentSnapshot("<root><value>1</value></root>", null, "sha-before", 28L,
                        "<root><value>1</value></root>".getBytes(StandardCharsets.UTF_8)),
                new GitFileContentSnapshot("<root><value>2</value></root>", null, "sha-after", 28L,
                        "<root><value>2</value></root>".getBytes(StandardCharsets.UTF_8))
        );

        FileDiffHandler handler = engine.selectHandler(source);
        StructuredFileDiffArtifact artifact = engine.buildDiff(source);

        assertThat(handler).isInstanceOf(TextFileDiffHandler.class);
        assertThat(artifact.getDiffType()).isEqualTo("TEXT");
        assertThat(artifact.getFormatFamily()).isEqualTo("TEXT");
        assertThat(artifact.getRows()).isNotEmpty();
        assertThat(artifact.getSummary().getModifiedLines()).isGreaterThan(0);
    }

    @Test
    void fallsBackToMetadataDiffForUnknownBinaryFiles() {
        FileDiffSource source = new FileDiffSource(
                1L,
                2L,
                "archive.bin",
                "archive.bin",
                "archive.bin",
                "modified",
                new GitFileContentSnapshot(null, null, "sha-before", 3L, new byte[] {0, 1, 2}),
                new GitFileContentSnapshot(null, null, "sha-after", 3L, new byte[] {0, 2, 3})
        );

        FileDiffHandler handler = engine.selectHandler(source);
        StructuredFileDiffArtifact artifact = engine.buildDiff(source);

        assertThat(handler).isInstanceOf(BinaryFallbackFileDiffHandler.class);
        assertThat(artifact.getDiffType()).isEqualTo("METADATA");
        assertThat(artifact.getMetadataEntries()).isNotEmpty();
        assertThat(artifact.getSummary().getMetadataChanges()).isGreaterThan(0);
    }

    @Test
    void normalizesJsonBeforeDiffing() {
        FileDiffSource source = new FileDiffSource(
                1L,
                2L,
                "sample.json",
                "sample.json",
                "sample.json",
                "modified",
                new GitFileContentSnapshot("{\"b\":1,\"a\":2}", null, "sha-before", 13L,
                        "{\"b\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8)),
                new GitFileContentSnapshot("{\"a\":2,\"b\":1}", null, "sha-after", 13L,
                        "{\"a\":2,\"b\":1}".getBytes(StandardCharsets.UTF_8))
        );

        StructuredFileDiffArtifact artifact = engine.buildDiff(source);

        assertThat(artifact.getSummary().getAddedLines()).isZero();
        assertThat(artifact.getSummary().getRemovedLines()).isZero();
        assertThat(artifact.getSummary().getModifiedLines()).isZero();
    }

    @Test
    void routesSchematronFilesToTextHandlerAndNormalizesXml() {
        FileDiffSource source = new FileDiffSource(
                1L,
                2L,
                "rules/sample.sch",
                "rules/sample.sch",
                "rules/sample.sch",
                "modified",
                new GitFileContentSnapshot("<schema><pattern><rule context=\"root\"/></pattern></schema>",
                        null, "sha-before", 58L,
                        "<schema><pattern><rule context=\"root\"/></pattern></schema>"
                                .getBytes(StandardCharsets.UTF_8)),
                new GitFileContentSnapshot("<schema>\n  <pattern><rule context=\"root\"/></pattern>\n</schema>",
                        null, "sha-after", 63L,
                        "<schema>\n  <pattern><rule context=\"root\"/></pattern>\n</schema>"
                                .getBytes(StandardCharsets.UTF_8))
        );

        FileDiffHandler handler = engine.selectHandler(source);
        StructuredFileDiffArtifact artifact = engine.buildDiff(source);

        assertThat(handler).isInstanceOf(TextFileDiffHandler.class);
        assertThat(artifact.getDiffType()).isEqualTo("TEXT");
        assertThat(artifact.getFormatFamily()).isEqualTo("TEXT");
        assertThat(artifact.getRows()).isNotEmpty();
    }

    @Test
    void decodesWindows1251TextBeforeBuildingStructuredDiff() {
        Charset windows1251 = Charset.forName("windows-1251");
        FileDiffSource source = new FileDiffSource(
                1L,
                2L,
                "xpath/CDA_ПЕРВИЧНЫЙ_ОСМОТР_ВРАЧА_Р1.csv",
                "xpath/CDA_ПЕРВИЧНЫЙ_ОСМОТР_ВРАЧА_Р1.csv",
                "xpath/CDA_ПЕРВИЧНЫЙ_ОСМОТР_ВРАЧА_Р1.csv",
                "modified",
                new GitFileContentSnapshot(null, null, "sha-before", 40L,
                        "№;Xpath;Кардинальность".getBytes(windows1251)),
                new GitFileContentSnapshot(null, null, "sha-after", 47L,
                        "№;Xpath;Номер требования".getBytes(windows1251))
        );

        StructuredFileDiffArtifact artifact = engine.buildDiff(source);

        assertThat(artifact.getRows()).hasSize(1);
        assertThat(artifact.getRows().getFirst().getBeforeText()).isEqualTo("№;Xpath;Кардинальность");
        assertThat(artifact.getRows().getFirst().getAfterText()).isEqualTo("№;Xpath;Номер требования");
        assertThat(artifact.getSummary().getModifiedLines()).isEqualTo(1);
    }
}
