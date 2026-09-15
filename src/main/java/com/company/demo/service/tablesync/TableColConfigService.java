package com.company.demo.service.tablesync;

import com.company.demo.entity.tablesync.TableCol;
import com.company.demo.entity.tablesync.TableColConfig;
import com.company.demo.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the {@code tableColConfig} JSON of a synchronizer to {@link TableCol} records and back.
 * The column list is stored as a single jsonb column, so the UI edits records and this service
 * owns the conversion in both directions.
 */
@Service
public class TableColConfigService {

    private static final Logger log = LoggerFactory.getLogger(TableColConfigService.class);

    /** Java types offered by the column editor; the field also accepts a custom value. */
    public static final List<String> JAVA_TYPES = List.of(
            "java.lang.String",
            "java.lang.Boolean",
            "java.lang.Short",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.math.BigDecimal",
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.OffsetDateTime",
            "java.util.UUID",
            "byte[]"
    );

    /** JDBC types offered by the column editor, in the order they are shown. */
    private static final Map<Integer, String> SQL_TYPES = sqlTypes();

    /** Default JDBC type suggested when a java type is picked and no JDBC type is set yet. */
    private static final Map<String, Integer> DEFAULT_SQL_TYPES = defaultSqlTypes();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The columns stored in the given config JSON. Returns an empty list for a null, blank or
     * unparseable value — a synchronizer whose config cannot be read is still editable, the user
     * simply starts from an empty column list.
     */
    public List<TableCol> parseColumns(@Nullable String json) {
        TableColConfig config = JsonUtils.parseConfig(json, TableColConfig.class, objectMapper);
        if (config == null) {
            if (json != null && !json.isBlank()) {
                log.warn("Cannot parse tableColConfig, falling back to an empty column list");
            }
            return List.of();
        }
        return config.getColumns() == null ? List.of() : List.copyOf(config.getColumns());
    }

    /** The JSON to store for the given columns, always a {@link TableColConfig} object. */
    public String toJson(List<TableCol> columns) {
        TableColConfig config = new TableColConfig();
        config.setColumns(List.copyOf(columns));
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize table column config", e);
        }
    }

    /** JDBC type code to its {@link Types} constant name, for the column editor's type picker. */
    public Map<Integer, String> getSqlTypes() {
        return SQL_TYPES;
    }

    /** The JDBC type that usually carries the given java type, or null when there is no obvious one. */
    @Nullable
    public Integer suggestSqlType(@Nullable String javaType) {
        return javaType == null ? null : DEFAULT_SQL_TYPES.get(javaType);
    }

    private static Map<Integer, String> sqlTypes() {
        Map<Integer, String> types = new LinkedHashMap<>();
        types.put(Types.VARCHAR, "VARCHAR");
        types.put(Types.LONGVARCHAR, "LONGVARCHAR");
        types.put(Types.CHAR, "CHAR");
        types.put(Types.BOOLEAN, "BOOLEAN");
        types.put(Types.SMALLINT, "SMALLINT");
        types.put(Types.INTEGER, "INTEGER");
        types.put(Types.BIGINT, "BIGINT");
        types.put(Types.DECIMAL, "DECIMAL");
        types.put(Types.NUMERIC, "NUMERIC");
        types.put(Types.DOUBLE, "DOUBLE");
        types.put(Types.FLOAT, "FLOAT");
        types.put(Types.REAL, "REAL");
        types.put(Types.DATE, "DATE");
        types.put(Types.TIME, "TIME");
        types.put(Types.TIMESTAMP, "TIMESTAMP");
        types.put(Types.TIMESTAMP_WITH_TIMEZONE, "TIMESTAMP_WITH_TIMEZONE");
        types.put(Types.BINARY, "BINARY");
        types.put(Types.VARBINARY, "VARBINARY");
        types.put(Types.OTHER, "OTHER");
        // unmodifiableMap, not Map.copyOf: the picker shows the types in this order
        return Collections.unmodifiableMap(types);
    }

    private static Map<String, Integer> defaultSqlTypes() {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("java.lang.String", Types.VARCHAR);
        types.put("java.lang.Boolean", Types.BOOLEAN);
        types.put("java.lang.Short", Types.SMALLINT);
        types.put("java.lang.Integer", Types.INTEGER);
        types.put("java.lang.Long", Types.BIGINT);
        types.put("java.lang.Double", Types.DOUBLE);
        types.put("java.math.BigDecimal", Types.DECIMAL);
        types.put("java.time.LocalDate", Types.DATE);
        types.put("java.time.LocalDateTime", Types.TIMESTAMP);
        types.put("java.time.OffsetDateTime", Types.TIMESTAMP_WITH_TIMEZONE);
        types.put("java.util.UUID", Types.OTHER);
        types.put("byte[]", Types.VARBINARY);
        return Map.copyOf(types);
    }
}
