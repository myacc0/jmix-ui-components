package com.company.demo.service;

import com.company.demo.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the sample violating rows recorded by {@link DqCheckExecutorService} back into the tabular
 * shape they were collected in. The columns are whatever the rule's samples query selected, so they
 * are only known when the stored text is read.
 */
@Service
public class DqSampleViolationsService {

    private static final Logger log = LoggerFactory.getLogger(DqSampleViolationsService.class);

    private static final TypeReference<List<Map<String, Object>>> ROWS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses the stored JSON into one map per violating row, keyed by column label.
     *
     * @return the rows, or an empty list when nothing was recorded or the text is not the expected
     *         array of flat objects — an older or hand-edited value still has to be displayable
     */
    public List<Map<String, Object>> parseRows(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, ROWS_TYPE);
            return rows == null ? List.of() : rows;
        } catch (Exception e) {
            log.debug("Sample violations cannot be read as a row set", e);
            return List.of();
        }
    }

    /**
     * Collects the column labels of the parsed rows, keeping the order the samples query returned
     * them in and tolerating a row that omits a column.
     */
    public List<String> columns(List<Map<String, Object>> rows) {
        Set<String> columns = new LinkedHashSet<>();
        rows.forEach(row -> columns.addAll(row.keySet()));
        return List.copyOf(columns);
    }

    /**
     * Re-formats the stored JSON for reading.
     *
     * @return the indented text, {@code null} when nothing was recorded, or the text unchanged when
     *         it is not valid JSON
     */
    @Nullable
    public String prettify(@Nullable String json) {
        return JsonUtils.prettify(json, objectMapper);
    }
}
