package uz.kapitalbank.umida.service.dq;

import uz.kapitalbank.umida.dto.SelectDto;
import uz.kapitalbank.umida.enums.dq.DqSqlDialect;
import io.jmix.core.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DqDataSourceProvider {
    private static final Logger log = LoggerFactory.getLogger(DqDataSourceProvider.class);

    private final Environment env;
    private final Messages messages;
    private final Map<String, DataSource> dataSourcesByBeanName;
    private final Map<String, DqSqlDialect> dialectCache = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplateCache = new ConcurrentHashMap<>();

    public DqDataSourceProvider(Environment env, Messages messages, Map<String, DataSource> dataSourcesByBeanName) {
        this.env = env;
        this.messages = messages;
        this.dataSourcesByBeanName = dataSourcesByBeanName;
    }

    public List<SelectDto> getDataSourceList() {
        String prop = env.getProperty("jmix.core.additional-stores");
        if (StringUtils.hasText(prop)) {
            List<SelectDto> dataSources = new ArrayList<>();
            SelectDto mainDs = new SelectDto();
            mainDs.setId("main");
            mainDs.setName(messages.getMessage("io.jmix.reportsflowui.view.report/bandsTab.dataSet.dataStoreMain"));
            dataSources.add(mainDs);

            for (String s : prop.split(",")) {
                SelectDto ds = new SelectDto();
                ds.setId(s.trim());
                ds.setName(s.trim());
                dataSources.add(ds);
            }
            return dataSources;
        }
        return List.of();
    }

    /**
     * The schemas of a data source that hold user data, in the order the JDBC driver reports them
     * (alphabetical). System schemas — the driver's own catalog and {@code information_schema} —
     * are left out: a rule is never defined over them.
     */
    public List<String> getDataSourceSchemas(String dataSourceName) {
        DataSource dataSource = resolveDataSource(dataSourceName);
        List<String> schemas = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getSchemas(connection.getCatalog(), null)) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    if (!isSystemSchema(schema)) {
                        schemas.add(schema);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get schemas for data source: " + dataSourceName, e);
        }
        return schemas;
    }

    /** The tables of the connection's default schema. */
    public List<String> getDataSourceTables(String dataSourceName) {
        return getDataSourceTables(dataSourceName, null);
    }

    /**
     * The tables of one schema of a data source.
     *
     * @param schema the schema to list, or null for the connection's default schema
     */
    public List<String> getDataSourceTables(String dataSourceName, @Nullable String schema) {
        DataSource dataSource = resolveDataSource(dataSourceName);
        List<String> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String schemaPattern = resolveSchemaPattern(connection, metaData, schema);
            try (ResultSet rs = metaData.getTables(connection.getCatalog(), schemaPattern, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get tables for data source: " + dataSourceName, e);
        }
        return tables;
    }

    /** The columns of a table in the connection's default schema. */
    public List<String> getTableColumns(String dataSourceName, String tableName) {
        return getTableColumns(dataSourceName, null, tableName);
    }

    /**
     * The columns of a table in one schema of a data source.
     *
     * @param schema the schema the table belongs to, or null for the connection's default schema
     */
    public List<String> getTableColumns(String dataSourceName, @Nullable String schema, String tableName) {
        DataSource dataSource = resolveDataSource(dataSourceName);
        List<String> columns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String normalizedTableName = normalizeIdentifier(metaData, tableName);
            String schemaPattern = resolveSchemaPattern(connection, metaData, schema);
            try (ResultSet rs = metaData.getColumns(connection.getCatalog(), schemaPattern, normalizedTableName, "%")) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get columns for table: " + tableName + " in data source: " + dataSourceName, e);
        }
        return columns;
    }

    /**
     * The type of one column of a table, as the JDBC driver reports it.
     *
     * @param schema the schema the table belongs to, or null for the connection's default schema
     * @return the column type, or null when the table has no such column
     */
    @Nullable
    public ColumnType getColumnType(String dataSourceName, @Nullable String schema, String tableName,
                                    String columnName) {
        DataSource dataSource = resolveDataSource(dataSourceName);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String normalizedTableName = normalizeIdentifier(metaData, tableName);
            String normalizedColumnName = normalizeIdentifier(metaData, columnName);
            String schemaPattern = resolveSchemaPattern(connection, metaData, schema);
            try (ResultSet rs = metaData.getColumns(connection.getCatalog(), schemaPattern, normalizedTableName,
                    normalizedColumnName)) {
                while (rs.next()) {
                    // the lookup takes patterns, in which "_" matches any character
                    if (normalizedTableName.equals(rs.getString("TABLE_NAME"))
                            && normalizedColumnName.equals(rs.getString("COLUMN_NAME"))) {
                        return new ColumnType(rs.getInt("DATA_TYPE"), rs.getString("TYPE_NAME"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get the type of column: " + tableName + "." + columnName
                    + " in data source: " + dataSourceName, e);
        }
        return null;
    }

    /**
     * Determines the SQL dialect of a data source from its JDBC metadata. The result is cached:
     * a data source cannot change its database product while the application is running.
     *
     * @throws IllegalStateException when the database product is not one of the supported dialects
     */
    public DqSqlDialect resolveDialect(String dataSourceName) {
        return dialectCache.computeIfAbsent(dataSourceName, name -> {
            DataSource dataSource = resolveDataSource(name);
            String productName;
            try (Connection connection = dataSource.getConnection()) {
                productName = connection.getMetaData().getDatabaseProductName();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read metadata of data source: " + name, e);
            }
            DqSqlDialect dialect = DqSqlDialect.fromProductName(productName);
            if (dialect == null) {
                throw new IllegalStateException("Unsupported database '" + productName
                        + "' of data source: " + name);
            }
            return dialect;
        });
    }

    /**
     * A template over a data source, for running the statements that measure a rule. Cached: a
     * {@code JdbcTemplate} is thread-safe once configured and holds no connection of its own.
     *
     * @throws IllegalArgumentException when the data source is not one of the configured stores
     */
    public JdbcTemplate getJdbcTemplate(String dataSourceName) {
        return jdbcTemplateCache.computeIfAbsent(dataSourceName, name -> new JdbcTemplate(resolveDataSource(name)));
    }

    /**
     * Runs a user-written read query in a read-only transaction, which is rolled back afterwards: the
     * database itself then refuses whatever the statement would write. A driver that cannot switch a
     * connection to read-only runs it as is — the statement is still held to a single read query by
     * {@link DqCustomSql#check}.
     * <p>
     * The statement is sent as plain text, not prepared, so a {@code ?} in it stays an operator.
     *
     * @param timeoutSeconds the longest the statement may run, or 0 for no limit
     * @throws QueryFailedException when the database rejects or aborts the statement
     * @throws IllegalStateException when no connection to the data source can be opened
     */
    public <T> T queryReadOnly(String dataSourceName, String sql, ResultSetExtractor<T> extractor,
                               int timeoutSeconds) {
        return inReadOnlyTransaction(dataSourceName, connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(timeoutSeconds);
                try (ResultSet rs = statement.executeQuery(sql)) {
                    return extractor.extractData(rs);
                }
            }
        });
    }

    /**
     * The columns a user-written read query returns, by label and type, without reading its rows. The database describes
     * a prepared statement without running it where the driver supports that (PostgreSQL); elsewhere
     * the query is run wrapped into a sub-query that selects nothing.
     *
     * @param timeoutSeconds the longest the statement may run, or 0 for no limit
     * @throws QueryFailedException when the database rejects the statement
     * @throws IllegalStateException when no connection to the data source can be opened
     */
    public List<QueryColumn> describeQuery(String dataSourceName, String sql, int timeoutSeconds) {
        return inReadOnlyTransaction(dataSourceName, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(timeoutSeconds);
                ResultSetMetaData metaData = statement.getMetaData();
                if (metaData != null) {
                    return columnTypes(metaData);
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(timeoutSeconds);
                try (ResultSet rs = statement.executeQuery("SELECT * FROM (" + sql + ") dq_query WHERE 1 = 0")) {
                    return columnTypes(rs.getMetaData());
                }
            }
        });
    }

    private List<QueryColumn> columnTypes(ResultSetMetaData metaData) throws SQLException {
        List<QueryColumn> columns = new ArrayList<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            columns.add(new QueryColumn(metaData.getColumnLabel(i),
                    new ColumnType(metaData.getColumnType(i), metaData.getColumnTypeName(i))));
        }
        return columns;
    }

    private <T> T inReadOnlyTransaction(String dataSourceName, ConnectionWork<T> work) {
        DataSource dataSource = resolveDataSource(dataSourceName);
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to connect to data source: " + dataSourceName, e);
        }
        try (connection) {
            boolean autoCommit = connection.getAutoCommit();
            boolean readOnly = connection.isReadOnly();
            // PostgreSQL applies read-only to explicit transactions only
            connection.setAutoCommit(false);
            try {
                connection.setReadOnly(true);
            } catch (SQLException e) {
                log.warn("Data source '{}' cannot switch a connection to read-only, the query runs as is",
                        dataSourceName, e);
            }
            try {
                return work.run(connection);
            } finally {
                connection.rollback();
                connection.setReadOnly(readOnly);
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new QueryFailedException(e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface ConnectionWork<T> {
        T run(Connection connection) throws SQLException;
    }

    /** The database rejected or aborted a user-written query; the message is the database's own. */
    public static class QueryFailedException extends RuntimeException {
        public QueryFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Metadata lookups take a schema PATTERN, so the caller's schema is normalized the same way a
     * table name is; a caller that named no schema keeps the connection's own schema.
     */
    @Nullable
    private String resolveSchemaPattern(Connection connection, DatabaseMetaData metaData, @Nullable String schema)
            throws SQLException {
        return StringUtils.hasText(schema) ? normalizeIdentifier(metaData, schema.trim()) : connection.getSchema();
    }

    private boolean isSystemSchema(@Nullable String schema) {
        if (!StringUtils.hasText(schema)) {
            return true;
        }
        String name = schema.toLowerCase();
        return name.startsWith("pg_") || name.equals("information_schema") || name.equals("sys");
    }

    private String normalizeIdentifier(DatabaseMetaData metaData, String identifier) throws SQLException {
        if (metaData.storesLowerCaseIdentifiers()) {
            return identifier.toLowerCase();
        } else if (metaData.storesUpperCaseIdentifiers()) {
            return identifier.toUpperCase();
        }
        return identifier;
    }

    private DataSource resolveDataSource(String dataSourceName) {
        String beanName = "main".equalsIgnoreCase(dataSourceName) ? "dataSource" : dataSourceName + "DataSource";
        DataSource dataSource = dataSourcesByBeanName.get(beanName);
        if (dataSource == null) {
            throw new IllegalArgumentException("Unknown data source: " + dataSourceName);
        }
        return dataSource;
    }

    /**
     * A column type from the JDBC metadata.
     *
     * @param jdbcType one of {@link java.sql.Types}
     * @param typeName the database's own name of the type, e.g. {@code varchar} or {@code uuid}
     */
    public record ColumnType(int jdbcType, String typeName) {
    }

    /**
     * A column of a query result.
     *
     * @param label the name the query gives the column, its alias when it has one, as the database
     *              reports it (an unquoted alias may come back folded to upper or lower case)
     */
    public record QueryColumn(String label, ColumnType type) {
    }
}
