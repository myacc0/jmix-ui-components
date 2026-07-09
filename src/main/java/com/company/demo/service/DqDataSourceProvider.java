package com.company.demo.service;

import com.company.demo.dto.SelectDto;
import io.jmix.core.Messages;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DqDataSourceProvider {
    private final Environment env;
    private final Messages messages;
    private final Map<String, DataSource> dataSourcesByBeanName;

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

    public List<String> getDataSourceTables(String dataSourceName) {
        DataSource dataSource = resolveDataSource(dataSourceName);
        List<String> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get tables for data source: " + dataSourceName, e);
        }
        return tables;
    }

    public List<String> getTableColumns(String dataSourceName, String tableName) {
        DataSource dataSource = resolveDataSource(dataSourceName);
        List<String> columns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String normalizedTableName = normalizeIdentifier(metaData, tableName);
            try (ResultSet rs = metaData.getColumns(connection.getCatalog(), connection.getSchema(), normalizedTableName, "%")) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get columns for table: " + tableName + " in data source: " + dataSourceName, e);
        }
        return columns;
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

}
