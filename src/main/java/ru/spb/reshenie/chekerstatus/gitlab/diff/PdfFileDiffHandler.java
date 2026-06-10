package ru.spb.reshenie.chekerstatus.gitlab.diff;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(30)
public class PdfFileDiffHandler extends AbstractLineFileDiffHandler {

    public PdfFileDiffHandler() {
        super("SEMANTIC", "PDF");
    }

    @Override
    public boolean supports(FileDiffSource source) {
        return FileTypeClassifier.isPdf(source.getPrimaryPath())
                && (hasBytes(source.getBefore()) || hasBytes(source.getAfter()));
    }

    @Override
    public ExtractedFileContent extract(FileDiffSource.Version version) {
        if (!hasBytes(version)) {
            return ExtractedFileContent.available(List.of());
        }
        List<String> lines = new ArrayList<String>();
        try (PDDocument document = Loader.loadPDF(version.readableBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).replace("\r\n", "\n").replace('\r', '\n');
                String[] pageLines = pageText.split("\n");
                boolean hasContent = false;
                for (String pageLine : pageLines) {
                    String normalized = pageLine.trim();
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    hasContent = true;
                    lines.add("Page " + page + ": " + normalized);
                }
                if (!hasContent) {
                    lines.add("Page " + page + ": <empty>");
                }
            }
            return ExtractedFileContent.available(lines);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot extract PDF structured diff", e);
        }
    }

    private boolean hasBytes(FileDiffSource.Version version) {
        return version != null && version.readableBytes() != null;
    }
}
