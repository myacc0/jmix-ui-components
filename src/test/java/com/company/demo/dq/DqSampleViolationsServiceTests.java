package com.company.demo.dq;

import com.company.demo.service.DqSampleViolationsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored samples drive a grid whose columns are only known at read time, so parsing has to
 * survive anything the column ever held — including a value written before or outside the executor.
 */
public class DqSampleViolationsServiceTests {

    private final DqSampleViolationsService service = new DqSampleViolationsService();

    @Test
    void rowsAreReadWithTheColumnsOfTheSamplesQuery() {
        List<Map<String, Object>> rows = service.parseRows(
                "[{\"id\": 1, \"name\": \"a\"}, {\"id\": 2, \"name\": null}]");

        assertEquals(2, rows.size());
        assertEquals(List.of("id", "name"), service.columns(rows), "the select order is kept");
        assertEquals("a", rows.get(0).get("name"));
        assertNull(rows.get(1).get("name"));
    }

    @Test
    void columnsOfAllRowsAreCollectedOnce() {
        List<Map<String, Object>> rows = service.parseRows(
                "[{\"id\": 1}, {\"id\": 2, \"email\": \"b\"}]");

        assertEquals(List.of("id", "email"), service.columns(rows),
                "a column absent from the first row is still a column of the grid");
    }

    @Test
    void nothingRecordedYieldsNoRows() {
        assertTrue(service.parseRows(null).isEmpty());
        assertTrue(service.parseRows("  ").isEmpty());
    }

    @Test
    void textThatIsNotARowSetYieldsNoRows() {
        assertTrue(service.parseRows("not json").isEmpty(), "a grid cannot be built from it");
        assertTrue(service.parseRows("{\"id\": 1}").isEmpty(), "a single object is not an array of rows");
        assertTrue(service.parseRows("[1, 2]").isEmpty(), "scalars are not rows");
    }

    @Test
    void storedJsonIsIndentedForReading() {
        String prettified = service.prettify("[{\"id\":1}]");

        assertTrue(prettified.contains(System.lineSeparator()) || prettified.contains("\n"),
                "the fallback editor shows the value on several lines: " + prettified);
        assertEquals("[{\"id\":1}]", prettified.replaceAll("\\s", ""));
    }

    @Test
    void textThatIsNotJsonIsShownUnchanged() {
        assertEquals("not json", service.prettify("not json"));
        assertNull(service.prettify(null));
    }
}
