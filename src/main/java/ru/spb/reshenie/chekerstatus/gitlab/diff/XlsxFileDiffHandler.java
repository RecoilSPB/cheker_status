package ru.spb.reshenie.chekerstatus.gitlab.diff;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(20)
public class XlsxFileDiffHandler extends AbstractLineFileDiffHandler {

    public XlsxFileDiffHandler() {
        super("SEMANTIC", "XLSX");
    }

    @Override
    public boolean supports(FileDiffSource source) {
        return FileTypeClassifier.isXlsx(source.getPrimaryPath())
                && (hasBytes(source.getBefore()) || hasBytes(source.getAfter()));
    }

    @Override
    public ExtractedFileContent extract(FileDiffSource.Version version) {
        if (!hasBytes(version)) {
            return ExtractedFileContent.available(List.of());
        }
        List<String> lines = new ArrayList<String>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(version.readableBytes()))) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                XSSFSheet sheet = workbook.getSheetAt(sheetIndex);
                int sheetLines = lines.size();
                for (Row row : sheet) {
                    short lastCell = row.getLastCellNum();
                    if (lastCell < 0) {
                        continue;
                    }
                    for (int columnIndex = 0; columnIndex < lastCell; columnIndex++) {
                        Cell cell = row.getCell(columnIndex);
                        if (cell == null) {
                            continue;
                        }
                        String renderedValue = renderValue(formatter, evaluator, cell);
                        String formula = cell.getCellType() == CellType.FORMULA ? cell.getCellFormula() : null;
                        if ((renderedValue == null || renderedValue.isBlank())
                                && (formula == null || formula.isBlank())) {
                            continue;
                        }
                        String reference = new CellReference(row.getRowNum(), columnIndex).formatAsString();
                        StringBuilder line = new StringBuilder();
                        line.append(sheet.getSheetName()).append('!').append(reference);
                        if (formula != null && !formula.isBlank()) {
                            line.append(" formula=").append(formula);
                        }
                        line.append(" value=").append(renderedValue == null ? "" : renderedValue);
                        lines.add(line.toString());
                    }
                }
                if (lines.size() == sheetLines) {
                    lines.add("Sheet " + sheet.getSheetName() + ": <empty>");
                }
            }
            return ExtractedFileContent.available(lines);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot extract XLSX structured diff", e);
        }
    }

    private boolean hasBytes(FileDiffSource.Version version) {
        return version != null && version.readableBytes() != null;
    }

    private String renderValue(DataFormatter formatter, FormulaEvaluator evaluator, Cell cell) {
        try {
            return formatter.formatCellValue(cell, evaluator);
        } catch (Exception e) {
            return formatter.formatCellValue(cell);
        }
    }
}
