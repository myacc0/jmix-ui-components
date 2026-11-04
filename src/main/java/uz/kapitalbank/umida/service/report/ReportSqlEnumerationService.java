package uz.kapitalbank.umida.service.report;

import io.jmix.core.Messages;
import io.jmix.core.Stores;
import io.jmix.reports.entity.ReportInputParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uz.kapitalbank.umida.entity.report.ReportInputParameterCustom;
import uz.kapitalbank.umida.enums.report.ReportCustomParameterType;
import uz.kapitalbank.umida.service.dq.DqCustomSql;
import uz.kapitalbank.umida.service.dq.DqDataSourceProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The values of a {@link ReportCustomParameterType#SQL_ENUMERATION} report parameter: the rows of the query of
 * the parameter, run in its data store.
 *
 * <p>The first column of a row is the value of the parameter, the second one, when the query returns it, is
 * the caption shown in the list; a query of one column shows the values themselves. The values are texts, as
 * the parameter is a {@code TEXT} one for the add-on. Rows with no value are left out, and a value that comes
 * again keeps its first caption.
 *
 * <p>The query is held to a single read query, {@code SELECT} or {@code WITH}, and runs in a read-only
 * transaction, as the custom SQL of the data quality rules does: see {@link DqCustomSql} and
 * {@link DqDataSourceProvider#queryReadOnly}.
 */
@Service
public class ReportSqlEnumerationService {

    private static final Logger log = LoggerFactory.getLogger(ReportSqlEnumerationService.class);

    /** The most values a list gets: the rest of the rows are not read. */
    public static final int MAX_OPTIONS = 1000;

    static final int QUERY_TIMEOUT_SECONDS = 30;

    private final DqDataSourceProvider dataSourceProvider;
    private final Stores stores;
    private final Messages messages;

    public ReportSqlEnumerationService(DqDataSourceProvider dataSourceProvider, Stores stores, Messages messages) {
        this.dataSourceProvider = dataSourceProvider;
        this.stores = stores;
        this.messages = messages;
    }

    /** The data stores a query can run in: the main one first, then the additional ones. */
    public List<String> getDataStores() {
        List<String> dataStores = new ArrayList<>();
        dataStores.add(Stores.MAIN);
        dataStores.addAll(stores.getAdditional());
        return dataStores;
    }

    public boolean isSqlEnumeration(@Nullable ReportInputParameter parameter) {
        return parameter instanceof ReportInputParameterCustom custom
                && custom.getCustomType() == ReportCustomParameterType.SQL_ENUMERATION;
    }

    /** The caption of the type of a parameter: the custom type when it has one, otherwise the standard one. */
    public String getTypeCaption(ReportInputParameter parameter) {
        if (parameter instanceof ReportInputParameterCustom custom && custom.getCustomType() != null) {
            return messages.getMessage(custom.getCustomType());
        }
        return parameter.getType() != null ? messages.getMessage(parameter.getType()) : "";
    }

    /** Whether the parameter has both a data store and a query, so that its values can be loaded. */
    public boolean isConfigured(ReportInputParameterCustom parameter) {
        return StringUtils.hasText(parameter.getSqlDataStore()) && StringUtils.hasText(parameter.getSqlQuery());
    }

    /**
     * The values of the parameter by value, with their captions, in the order of the query.
     *
     * @throws SqlEnumerationException with a message for the user when the query is not acceptable or fails
     */
    public Map<String, String> loadOptions(ReportInputParameterCustom parameter) {
        return loadOptions(parameter.getSqlDataStore(), parameter.getSqlQuery());
    }

    /**
     * The values a query returns by value, with their captions, in the order of the query.
     *
     * @throws SqlEnumerationException with a message for the user when there is no data store, the query is
     *                                 not a single read query or the data store fails to run it
     */
    public Map<String, String> loadOptions(@Nullable String dataStore, @Nullable String sql) {
        String error = checkQuery(dataStore, sql);
        if (error != null) {
            throw new SqlEnumerationException(error, null);
        }
        try {
            return dataSourceProvider.queryReadOnly(dataStore, DqCustomSql.normalize(sql), optionsExtractor(),
                    QUERY_TIMEOUT_SECONDS);
        } catch (DqDataSourceProvider.QueryFailedException e) {
            throw new SqlEnumerationException(message("sqlEnumeration.queryFailed", e.getMessage()), e);
        } catch (RuntimeException e) {
            log.warn("The values of a SQL enumeration parameter could not be loaded from data store '{}'",
                    dataStore, e);
            throw new SqlEnumerationException(message("sqlEnumeration.dataStoreFailed", dataStore), e);
        }
    }

    /**
     * Checks the settings of a parameter before it is saved: a data store, a single read query, and a query
     * the data store runs. A data store that cannot be reached does not stop the save, it may be down for a
     * while; the run of the report then shows the problem.
     *
     * @return the message for the user, or null when the settings are acceptable
     */
    @Nullable
    public String validate(@Nullable String dataStore, @Nullable String sql) {
        String error = checkQuery(dataStore, sql);
        if (error != null) {
            return error;
        }
        try {
            dataSourceProvider.queryReadOnly(dataStore, DqCustomSql.normalize(sql), optionsExtractor(),
                    QUERY_TIMEOUT_SECONDS);
        } catch (DqDataSourceProvider.QueryFailedException e) {
            return message("sqlEnumeration.queryFailed", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("The query of a SQL enumeration parameter could not be checked in data store '{}'",
                    dataStore, e);
        }
        return null;
    }

    /** The settings checked without running the query; null when they are acceptable. */
    @Nullable
    private String checkQuery(@Nullable String dataStore, @Nullable String sql) {
        if (!StringUtils.hasText(dataStore)) {
            return message("sqlEnumeration.dataStoreRequired");
        }
        if (!getDataStores().contains(dataStore)) {
            return message("sqlEnumeration.unknownDataStore", dataStore);
        }
        DqCustomSql.Violation violation = DqCustomSql.check(sql);
        if (violation == null) {
            return null;
        }
        return switch (violation.problem()) {
            case BLANK -> message("sqlEnumeration.queryRequired");
            case MULTIPLE_STATEMENTS -> message("sqlEnumeration.multipleStatements");
            case NOT_SELECT -> message("sqlEnumeration.notSelect");
            case MODIFYING_KEYWORD -> message("sqlEnumeration.modifiesData", violation.word());
        };
    }

    private ResultSetExtractor<Map<String, String>> optionsExtractor() {
        return rs -> {
            boolean hasCaptions = rs.getMetaData().getColumnCount() > 1;
            Map<String, String> options = new LinkedHashMap<>();
            while (options.size() < MAX_OPTIONS && rs.next()) {
                String value = rs.getString(1);
                if (value == null) {
                    continue;
                }
                String caption = hasCaptions ? rs.getString(2) : null;
                options.putIfAbsent(value, caption != null ? caption : value);
            }
            return options;
        };
    }

    private String message(String key, Object... params) {
        return messages.formatMessage(ReportSqlEnumerationService.class, key, params);
    }

    /** The values of a parameter cannot be loaded; the message is for the user. */
    public static class SqlEnumerationException extends RuntimeException {
        public SqlEnumerationException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }
}
