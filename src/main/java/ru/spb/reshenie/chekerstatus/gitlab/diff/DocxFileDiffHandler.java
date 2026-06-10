package ru.spb.reshenie.chekerstatus.gitlab.diff;

import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(10)
public class DocxFileDiffHandler extends AbstractLineFileDiffHandler {

    public DocxFileDiffHandler() {
        super("SEMANTIC", "DOCX");
    }

    @Override
    public boolean supports(FileDiffSource source) {
        return FileTypeClassifier.isDocx(source.getPrimaryPath())
                && (hasBytes(source.getBefore()) || hasBytes(source.getAfter()));
    }

    @Override
    public ExtractedFileContent extract(FileDiffSource.Version version) {
        if (!hasBytes(version)) {
            return ExtractedFileContent.available(List.of());
        }
        List<String> lines = new ArrayList<String>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(version.readableBytes()))) {
            int paragraphIndex = 0;
            int tableIndex = 0;
            for (IBodyElement bodyElement : document.getBodyElements()) {
                if (bodyElement.getElementType() == BodyElementType.PARAGRAPH) {
                    String text = normalize(((XWPFParagraph) bodyElement).getText());
                    if (!text.isEmpty()) {
                        paragraphIndex++;
                        lines.add("Paragraph " + paragraphIndex + ": " + text);
                    }
                    continue;
                }
                if (bodyElement.getElementType() == BodyElementType.TABLE) {
                    tableIndex++;
                    XWPFTable table = (XWPFTable) bodyElement;
                    int rowIndex = 0;
                    for (XWPFTableRow row : table.getRows()) {
                        rowIndex++;
                        List<String> cells = new ArrayList<String>();
                        row.getTableCells().forEach(cell -> cells.add(normalize(cell.getText())));
                        lines.add("Table " + tableIndex + " Row " + rowIndex + ": " + String.join(" | ", cells));
                    }
                }
            }
            return ExtractedFileContent.available(lines);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot extract DOCX structured diff", e);
        }
    }

    private boolean hasBytes(FileDiffSource.Version version) {
        return version != null && version.readableBytes() != null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
