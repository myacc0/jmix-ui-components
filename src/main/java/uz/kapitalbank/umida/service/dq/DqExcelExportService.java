package uz.kapitalbank.umida.service.dq;

import io.jmix.core.CoreProperties;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.dto.dq.DqReconciliation;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.service.dq.DqViolationsService.ViolationsHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Exports what a check found to an Excel workbook: the rows that violated the rule of a check result,
 * or, for a cross-source rule, which finds no rows, the values the check compared.
 * <p>
 * The violating rows are read live, like those shown on screen, and written as the data source
 * streams them: a streaming workbook keeps only a window of rows in memory, so the size of the export
 * is bounded by a sheet (1 048 575 rows), not by the heap. The workbook goes to a temporary
 * file, which {@link DqExcelExport#openStream()} deletes once it is read.
 */
@Service
public class DqExcelExportService {

    /** The most data rows a sheet holds below its header row. */
    public static final int MAX_ROWS = SpreadsheetVersion.EXCEL2007.getMaxRows() - 1;

    private static final String MESSAGE_GROUP = "uz.kapitalbank.umida.view.dq/";
    /** The longest text a cell holds. */
    private static final int MAX_CELL_TEXT = SpreadsheetVersion.EXCEL2007.getMaxTextLength();
    /** The most significant digits a number keeps in a cell, which stores it as a double. */
    private static final int MAX_NUMBER_PRECISION = 15;
    /** How many rows the streaming workbook keeps in memory before flushing them to disk. */
    private static final int ROW_WINDOW = 100;
    private static final int FIRST_EXCEL_YEAR = 1900;
    private static final int MIN_COLUMN_CHARS = 10;
    private static final int MAX_COLUMN_CHARS = 60;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DataManager dataManager;
    private final Messages messages;
    private final CoreProperties coreProperties;
    private final DqViolationsService violationsService;
    private final DqCrossSourceReconciler crossSourceReconciler;

    public DqExcelExportService(DataManager dataManager, Messages messages, CoreProperties coreProperties,
                                DqViolationsService violationsService,
                                DqCrossSourceReconciler crossSourceReconciler) {
        this.dataManager = dataManager;
        this.messages = messages;
        this.coreProperties = coreProperties;
        this.violationsService = violationsService;
        this.crossSourceReconciler = crossSourceReconciler;
    }

    /**
     * Exports the rows the violations query of the result currently returns, one column per column
     * the query selects. Past {@link #MAX_ROWS} rows the export is cut short and marked
     * {@link DqExcelExport#truncated()}.
     *
     * @throws IllegalStateException when the result has no violations query to run
     */
    public DqExcelExport exportViolations(UUID checkResultId) {
        DqCheckRunResult result = loadResult(checkResultId);
        long[] rowCount = new long[1];
        Path file = writeWorkbook(message("dqIssueDetailView.violationsHeader"), sheet -> {
            // one row past the limit tells a cut-short export from one that just fits
            violationsService.readViolations(checkResultId, MAX_ROWS + 1, new ViolationsHandler() {
                @Override
                public void columns(List<String> columns) {
                    sheet.header(columns);
                }

                @Override
                public void row(List<Object> values) {
                    if (rowCount[0]++ < MAX_ROWS) {
                        sheet.row(values);
                    }
                }
            });
        });
        boolean truncated = rowCount[0] > MAX_ROWS;
        return new DqExcelExport(file, fileName(result, "violations"), Math.min(rowCount[0], MAX_ROWS), truncated);
    }

    /**
     * Exports the reconciliation stored on the result of a cross-source rule: a row per pair of
     * compared columns, as the reconciliation on screen shows it.
     *
     * @throws IllegalStateException when the result keeps no reconciliation
     */
    public DqExcelExport exportReconciliation(UUID checkResultId) {
        DqCheckRunResult result = loadResult(checkResultId);
        DqReconciliation reconciliation = crossSourceReconciler.fromJson(result.getReconciliation());
        if (reconciliation == null) {
            throw new IllegalStateException("Check result " + checkResultId + " has no reconciliation");
        }

        Path file = writeWorkbook(message("dqReconciliationFragment.header"), sheet -> {
            sheet.header(List.of(
                    message("dqReconciliationFragment.primaryAlias"),
                    message("dqReconciliationFragment.primaryValue"),
                    message("dqReconciliationFragment.secondaryAlias"),
                    message("dqReconciliationFragment.secondaryValue"),
                    message("dqReconciliationFragment.difference"),
                    message("dqReconciliationFragment.result")));
            for (DqReconciliation.Pair pair : reconciliation.getPairs()) {
                List<Object> values = new ArrayList<>(6);
                values.add(pair.getPrimaryAlias());
                values.add(pair.getPrimaryValue());
                values.add(pair.getSecondaryAlias());
                values.add(pair.getSecondaryValue());
                values.add(pair.difference());
                values.add(message(pair.isMatched()
                        ? "dqReconciliationFragment.matched"
                        : "dqReconciliationFragment.mismatched"));
                sheet.row(values);
            }
        });
        return new DqExcelExport(file, fileName(result, "reconciliation"), reconciliation.getPairs().size(), false);
    }

    private DqCheckRunResult loadResult(UUID checkResultId) {
        return dataManager.load(DqCheckRunResult.class)
                .id(checkResultId)
                .fetchPlan(fp -> fp.add("reconciliation")
                        .add("rule", rule -> rule.add("code"))
                        .add("checkRun", run -> run.add("startedAt")))
                .one();
    }

    /** The code of the rule, what is exported and when the check ran: {@code DQ-12-violations-20261023-101500.xlsx}. */
    private static String fileName(DqCheckRunResult result, String what) {
        StringBuilder name = new StringBuilder();
        if (result.getRule() != null && result.getRule().getCode() != null) {
            name.append(result.getRule().getCode()).append('-');
        }
        name.append(what);
        OffsetDateTime startedAt = result.getCheckRun() == null ? null : result.getCheckRun().getStartedAt();
        if (startedAt != null) {
            name.append('-').append(FILE_TIMESTAMP.format(startedAt));
        }
        return name.append(".xlsx").toString();
    }

    private String message(String key) {
        return messages.getMessage(MESSAGE_GROUP + key);
    }

    /** Writes a workbook of one sheet to a temporary file; the file is removed again if writing fails. */
    private Path writeWorkbook(String sheetName, Consumer<SheetWriter> content) {
        Path file;
        try {
            Path tempDir = Files.createDirectories(Path.of(coreProperties.getTempDir()));
            file = Files.createTempFile(tempDir, "dq-export-", ".xlsx");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create a temporary file for the export", e);
        }

        // closing the workbook also removes the temporary files the flushed rows were kept in
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW);
             OutputStream out = Files.newOutputStream(file)) {
            workbook.setCompressTempFiles(true);
            SheetWriter sheet = new SheetWriter(workbook, WorkbookUtil.createSafeSheetName(sheetName));
            content.accept(sheet);
            sheet.finish();
            workbook.write(out);
            return file;
        } catch (IOException e) {
            deleteQuietly(file);
            throw new UncheckedIOException("Cannot write the export", e);
        } catch (RuntimeException e) {
            deleteQuietly(file);
            throw e;
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // a temporary file, cleaned up with the temporary directory anyway
        }
    }

    /** Fills one sheet: a bold, frozen header row with a filter, then the data rows below it. */
    private static class SheetWriter {

        private final SXSSFSheet sheet;
        private final CellStyle headerStyle;
        private final CellStyle dateStyle;
        private final CellStyle dateTimeStyle;
        private int columnCount;
        private int nextRow;

        SheetWriter(SXSSFWorkbook workbook, String name) {
            sheet = workbook.createSheet(name);

            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle = workbook.createCellStyle();
            headerStyle.setFont(bold);

            short dateFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd");
            dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(dateFormat);

            short dateTimeFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss");
            dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(dateTimeFormat);
        }

        /** The header row; the width of each column follows its caption, within bounds. */
        void header(List<String> columns) {
            columnCount = columns.size();
            Row row = sheet.createRow(nextRow++);
            for (int i = 0; i < columns.size(); i++) {
                String caption = columns.get(i) == null ? "" : columns.get(i);
                Cell cell = row.createCell(i);
                cell.setCellValue(caption);
                cell.setCellStyle(headerStyle);
                int chars = Math.max(MIN_COLUMN_CHARS, Math.min(MAX_COLUMN_CHARS, caption.length() + 2));
                sheet.setColumnWidth(i, chars * 256);
            }
        }

        void row(List<Object> values) {
            Row row = sheet.createRow(nextRow++);
            for (int i = 0; i < values.size(); i++) {
                setValue(row.createCell(i), values.get(i));
            }
        }

        void finish() {
            if (columnCount > 0) {
                sheet.createFreezePane(0, 1);
                sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, nextRow - 1), 0, columnCount - 1));
            }
        }

        /**
         * Numbers, dates and booleans keep their type, so that they can be summed, sorted and filtered
         * as such. A number with more digits than a cell keeps, and anything else, goes in as text.
         */
        private void setValue(Cell cell, @Nullable Object value) {
            switch (value) {
                case null -> cell.setBlank();
                case Boolean b -> cell.setCellValue(b);
                case Integer n -> cell.setCellValue(n);
                case Long n when Math.abs(n) < (1L << 53) -> cell.setCellValue(n);
                case Short n -> cell.setCellValue(n);
                case Byte n -> cell.setCellValue(n);
                case Double n when Double.isFinite(n) -> cell.setCellValue(n);
                case Float n when Float.isFinite(n) -> cell.setCellValue(n);
                case BigDecimal n when n.precision() <= MAX_NUMBER_PRECISION -> cell.setCellValue(n.doubleValue());
                case BigDecimal n -> setText(cell, n.toPlainString());
                case BigInteger n when n.bitLength() < 53 -> cell.setCellValue(n.doubleValue());
                case java.sql.Timestamp t -> setDate(cell, t.toLocalDateTime(), dateTimeStyle);
                case java.sql.Date d -> setDate(cell, d.toLocalDate().atStartOfDay(), dateStyle);
                case LocalDateTime t -> setDate(cell, t, dateTimeStyle);
                case LocalDate d -> setDate(cell, d.atStartOfDay(), dateStyle);
                default -> setText(cell, String.valueOf(value));
            }
        }

        /** A date before the first one a cell can hold, 1900-01-01, goes in as text. */
        private static void setDate(Cell cell, LocalDateTime value, CellStyle style) {
            if (value.getYear() < FIRST_EXCEL_YEAR) {
                setText(cell, value.toString());
                return;
            }
            cell.setCellValue(value);
            cell.setCellStyle(style);
        }

        private static void setText(Cell cell, String text) {
            cell.setCellValue(text.length() > MAX_CELL_TEXT ? text.substring(0, MAX_CELL_TEXT) : text);
        }
    }
}
