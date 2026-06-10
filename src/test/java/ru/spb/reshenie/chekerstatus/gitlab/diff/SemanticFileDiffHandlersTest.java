package ru.spb.reshenie.chekerstatus.gitlab.diff;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentSnapshot;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticFileDiffHandlersTest {

    @Test
    void docxHandlerExtractsParagraphsAndTables() throws Exception {
        DocxFileDiffHandler handler = new DocxFileDiffHandler();
        FileDiffSource source = new FileDiffSource(
                1L, 1L, "sample.docx", "sample.docx", "sample.docx", "modified",
                new GitFileContentSnapshot(null, null, "before", 1L, docxBytes("before", "left")),
                new GitFileContentSnapshot(null, null, "after", 1L, docxBytes("after", "right"))
        );

        StructuredFileDiffArtifact artifact = handler.buildDiff(source,
                handler.extract(source.getBefore()),
                handler.extract(source.getAfter()));

        assertThat(artifact.getDiffType()).isEqualTo("SEMANTIC");
        assertThat(artifact.getFormatFamily()).isEqualTo("DOCX");
        assertThat(artifact.getRows()).anyMatch(row -> text(row).contains("Paragraph 1"));
        assertThat(artifact.getRows()).anyMatch(row -> text(row).contains("Table 1 Row 1"));
    }

    @Test
    void xlsxHandlerExtractsCellsFormulasAndRenderedValues() throws Exception {
        XlsxFileDiffHandler handler = new XlsxFileDiffHandler();
        FileDiffSource source = new FileDiffSource(
                1L, 1L, "sample.xlsx", "sample.xlsx", "sample.xlsx", "modified",
                new GitFileContentSnapshot(null, null, "before", 1L, xlsxBytes(1)),
                new GitFileContentSnapshot(null, null, "after", 1L, xlsxBytes(2))
        );

        StructuredFileDiffArtifact artifact = handler.buildDiff(source,
                handler.extract(source.getBefore()),
                handler.extract(source.getAfter()));

        assertThat(artifact.getFormatFamily()).isEqualTo("XLSX");
        assertThat(artifact.getRows()).anyMatch(row -> text(row).contains("formula=A1*2"));
        assertThat(artifact.getSummary().getModifiedLines()).isGreaterThan(0);
    }

    @Test
    void pdfHandlerExtractsTextByPage() throws Exception {
        PdfFileDiffHandler handler = new PdfFileDiffHandler();
        FileDiffSource source = new FileDiffSource(
                1L, 1L, "sample.pdf", "sample.pdf", "sample.pdf", "modified",
                new GitFileContentSnapshot(null, null, "before", 1L, pdfBytes("before line")),
                new GitFileContentSnapshot(null, null, "after", 1L, pdfBytes("after line"))
        );

        StructuredFileDiffArtifact artifact = handler.buildDiff(source,
                handler.extract(source.getBefore()),
                handler.extract(source.getAfter()));

        assertThat(artifact.getFormatFamily()).isEqualTo("PDF");
        assertThat(artifact.getRows()).anyMatch(row -> text(row).contains("Page 1:"));
    }

    private byte[] docxBytes(String paragraphText, String cellText) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(paragraphText);
            XWPFTable table = document.createTable(1, 1);
            table.getRow(0).getCell(0).setText(cellText);
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] xlsxBytes(int value) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Sheet1");
            var row1 = sheet.createRow(0);
            row1.createCell(0).setCellValue(value);
            var row2 = sheet.createRow(1);
            row2.createCell(0, CellType.FORMULA).setCellFormula("A1*2");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private String text(StructuredDiffRow row) {
        String before = row.getBeforeText() == null ? "" : row.getBeforeText();
        String after = row.getAfterText() == null ? "" : row.getAfterText();
        return before + after;
    }
}
