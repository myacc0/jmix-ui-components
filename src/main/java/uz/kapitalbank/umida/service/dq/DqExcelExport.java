package uz.kapitalbank.umida.service.dq;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A workbook written by {@link DqExcelExportService}, kept in a temporary file until it is read.
 *
 * @param file      the temporary file holding the workbook
 * @param fileName  the name to offer the file under
 * @param rows      the number of data rows written, the header row aside
 * @param truncated whether there were more rows than a sheet holds, so that only the first ones were
 *                  written
 */
public record DqExcelExport(Path file, String fileName, long rows, boolean truncated) {

    /** Opens the workbook for reading. The file is deleted once the stream is closed: it is read once. */
    public InputStream openStream() {
        try {
            return new FileInputStream(file.toFile()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        Files.deleteIfExists(file);
                    }
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the exported workbook " + file, e);
        }
    }
}
