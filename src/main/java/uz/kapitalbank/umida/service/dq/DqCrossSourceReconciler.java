package uz.kapitalbank.umida.service.dq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uz.kapitalbank.umida.dto.dq.DqReconciliation;
import uz.kapitalbank.umida.dto.dq.DqRuleConfig;
import uz.kapitalbank.umida.dto.dq.DqRuleConfig.CrossColumnPair;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.utils.JsonUtils;

import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures a {@link DqRuleType#CROSS_SOURCE_AGGREGATED} rule: runs one aggregating query against the
 * rule's data source and another against a second data source, each returning a single row of numbers,
 * and compares the columns the rule pairs by their aliases. The rule passes when every pair holds the
 * same value.
 * <p>
 * The two rows live in different databases, so the comparison is made here rather than by a query, and
 * there is no query left to read the "violating rows" with later: what was compared is kept as a
 * {@link DqReconciliation} instead. Both statements are the user's own and run read-only, like the one of
 * a {@code CUSTOM_SQL} rule (see {@link DqDataSourceProvider#queryReadOnly}).
 */
@Service
public class DqCrossSourceReconciler {

    /** Rows read from a query: one is expected, a second one only proves there are too many. */
    private static final int ROWS_TO_READ = 2;

    private final DqDataSourceProvider dataSourceProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DqCrossSourceReconciler(DqDataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    /**
     * The two statements of a rule, where they run, and the columns compared.
     *
     * @throws IllegalArgumentException when the configuration lacks any of them, or a statement is not a
     *                                  single read query
     */
    public Queries queries(DqRule rule) {
        DqRuleConfig config = JsonUtils.parseConfig(rule.getRuleConfig(), DqRuleConfig.class, objectMapper);
        if (config == null) {
            throw new IllegalArgumentException("Rule configuration is missing or is not valid JSON for rule: "
                    + rule.getName());
        }
        if (!StringUtils.hasText(rule.getDataSource())) {
            throw new IllegalArgumentException("The first data source is not set for rule: " + rule.getName());
        }
        if (!StringUtils.hasText(config.getCrossDataSource())) {
            throw new IllegalArgumentException("The second data source is not set for rule: " + rule.getName());
        }

        List<CrossColumnPair> pairs = config.getCrossColumnPairs() == null ? List.of() : config.getCrossColumnPairs();
        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("No columns to compare are set for rule: " + rule.getName());
        }
        for (CrossColumnPair pair : pairs) {
            if (!StringUtils.hasText(pair.getPrimaryAlias()) || !StringUtils.hasText(pair.getSecondaryAlias())) {
                throw new IllegalArgumentException("A pair of compared columns lacks an alias in rule: "
                        + rule.getName());
            }
        }

        return new Queries(rule.getDataSource(), readQuery(rule, "first", config.getCrossPrimarySql()),
                config.getCrossDataSource().trim(), readQuery(rule, "second", config.getCrossDataSourceSql()),
                pairs.stream()
                        .map(pair -> new CrossColumnPair(pair.getPrimaryAlias().trim(), pair.getSecondaryAlias().trim()))
                        .toList());
    }

    /**
     * Runs both statements and compares the paired columns.
     *
     * @throws IllegalStateException                     when a statement returns no row or more than one
     * @throws IllegalArgumentException                  when a paired column is missing from its row or
     *                                                   does not hold a number
     * @throws DqDataSourceProvider.QueryFailedException when a data source rejects its statement
     */
    public DqReconciliation reconcile(Queries queries) {
        Row primary = singleRow(queries.primaryDataSource(), queries.primarySql());
        Row secondary = singleRow(queries.secondaryDataSource(), queries.secondarySql());

        DqReconciliation reconciliation = new DqReconciliation();
        reconciliation.setPrimary(new DqReconciliation.Source(queries.primaryDataSource(), primary.asMap()));
        reconciliation.setSecondary(new DqReconciliation.Source(queries.secondaryDataSource(), secondary.asMap()));
        List<DqReconciliation.Pair> pairs = new ArrayList<>();
        for (CrossColumnPair pair : queries.pairs()) {
            pairs.add(new DqReconciliation.Pair(
                    pair.getPrimaryAlias(), primary.number(pair.getPrimaryAlias(), queries.primaryDataSource()),
                    pair.getSecondaryAlias(), secondary.number(pair.getSecondaryAlias(), queries.secondaryDataSource())));
        }
        reconciliation.setPairs(pairs);
        return reconciliation;
    }

    public String toJson(DqReconciliation reconciliation) {
        try {
            return objectMapper.writeValueAsString(reconciliation);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize the reconciliation", e);
        }
    }

    /** The reconciliation stored on a check result, or null when there is none or it cannot be read. */
    @Nullable
    public DqReconciliation fromJson(@Nullable String json) {
        return JsonUtils.parseConfig(json, DqReconciliation.class, objectMapper);
    }

    private String readQuery(DqRule rule, String which, @Nullable String sql) {
        DqCustomSql.Violation violation = DqCustomSql.check(sql);
        if (violation != null) {
            throw new IllegalArgumentException("The SQL of the " + which + " data source of rule '" + rule.getName()
                    + "' is not a single read query: " + violation.problem()
                    + (violation.word() != null ? " (" + violation.word() + ")" : ""));
        }
        return DqCustomSql.normalize(sql);
    }

    private Row singleRow(String dataSource, String sql) {
        List<Row> rows = dataSourceProvider.queryReadOnly(dataSource, sql, ROWS_EXTRACTOR, 0);
        if (rows.size() != 1) {
            throw new IllegalStateException("The query of data source '" + dataSource + "' must return exactly one row, "
                    + (rows.isEmpty() ? "it returned none" : "it returned more"));
        }
        return rows.get(0);
    }

    /** Reads the rows by position: a query may give two columns the same label. */
    private static final ResultSetExtractor<List<Row>> ROWS_EXTRACTOR = rs -> {
        ResultSetMetaData metaData = rs.getMetaData();
        List<String> labels = new ArrayList<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            labels.add(metaData.getColumnLabel(i));
        }
        List<Row> rows = new ArrayList<>();
        while (rows.size() < ROWS_TO_READ && rs.next()) {
            List<Object> values = new ArrayList<>(labels.size());
            for (int i = 1; i <= labels.size(); i++) {
                values.add(snapshotValue(rs.getObject(i)));
            }
            rows.add(new Row(labels, values));
        }
        return rows;
    };

    /** A number as a {@link BigDecimal}, anything else as its text: the row is stored as JSON. */
    @Nullable
    private static Object snapshotValue(@Nullable Object value) {
        if (value == null || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString());
            } catch (NumberFormatException e) {
                // NaN, Infinity
                return number.toString();
            }
        }
        return value.toString();
    }

    /**
     * The statements of a rule.
     *
     * @param primaryDataSource   the rule's own data source
     * @param secondaryDataSource the data source compared against
     */
    public record Queries(String primaryDataSource, String primarySql,
                          String secondaryDataSource, String secondarySql,
                          List<CrossColumnPair> pairs) {

        /** Both statements, each headed by the data source it runs against, as stored on the result. */
        public String toDisplayString() {
            return "-- " + primaryDataSource + "\n" + primarySql + "\n\n-- " + secondaryDataSource + "\n" + secondarySql;
        }
    }

    private record Row(List<String> labels, List<Object> values) {

        /** Label to value; of two columns with the same label, the first one. */
        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < labels.size(); i++) {
                map.putIfAbsent(labels.get(i), values.get(i));
            }
            return map;
        }

        /**
         * The value of the column with the alias. The database may have folded an unquoted alias to
         * upper or lower case, so a column whose label differs only in case is taken when no label
         * matches exactly.
         */
        @Nullable
        BigDecimal number(String alias, String dataSource) {
            int index = labels.indexOf(alias);
            if (index < 0) {
                for (int i = 0; i < labels.size() && index < 0; i++) {
                    if (labels.get(i).equalsIgnoreCase(alias)) {
                        index = i;
                    }
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("The query of data source '" + dataSource
                        + "' returns no column '" + alias + "', only " + labels);
            }
            Object value = values.get(index);
            if (value != null && !(value instanceof BigDecimal)) {
                throw new IllegalArgumentException("Column '" + alias + "' of the query of data source '"
                        + dataSource + "' is not a number: " + value);
            }
            return (BigDecimal) value;
        }
    }
}
