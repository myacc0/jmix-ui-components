package com.company.demo.dq;

import com.company.demo.dto.SelectDto;
import com.company.demo.service.DqDataSourceProvider;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqDataSourceProviderTests {

    @Autowired
    DqDataSourceProvider dqDataSourceProvider;

    @Test
    void test_get_data_source_list() {
        List<SelectDto> items = dqDataSourceProvider.getDataSourceList();
        System.out.println("size: " + items.size());
        items.forEach(System.out::println);
    }

    @ParameterizedTest
    @CsvSource({"main", "dwh", "dwholap"})
    void test_get_data_source_schemas(String dataSourceId) {
        List<String> schemas = dqDataSourceProvider.getDataSourceSchemas(dataSourceId);
        System.out.println("size: " + schemas.size());
        schemas.forEach(System.out::println);

        assertFalse(schemas.isEmpty());
        // the system schemas of the driver are filtered out
        assertTrue(schemas.stream().noneMatch(s -> s.startsWith("pg_") || s.equals("information_schema")));
    }

    @ParameterizedTest
    @CsvSource({"main,public", "dwh,public", "dwholap,public"})
    void test_get_schema_tables(String dataSourceId, String schema) {
        List<String> tables = dqDataSourceProvider.getDataSourceTables(dataSourceId, schema);
        System.out.println("size: " + tables.size());
        tables.forEach(System.out::println);

        assertFalse(tables.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"main,public,employee", "dwh,public,orders"})
    void test_get_schema_table_columns(String dataSourceId, String schema, String tableName) {
        List<String> columns = dqDataSourceProvider.getTableColumns(dataSourceId, schema, tableName);
        System.out.println("size: " + columns.size());
        columns.forEach(System.out::println);

        assertFalse(columns.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"main", "dwh", "dwholap"})
    void test_get_data_source_tables(String dataSourceId) {
        List<String> tables = dqDataSourceProvider.getDataSourceTables(dataSourceId);
        System.out.println("size: " + tables.size());
        tables.forEach(System.out::println);

        assertFalse(tables.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "main,employee",
            "dwh,product_categories",
            "dwh,products", "dwh,orders"
    })
    void test_get_table_columns(String dataSourceId, String tableName) {
        List<String> columns = dqDataSourceProvider.getTableColumns(dataSourceId, tableName);
        System.out.println("size: " + columns.size());
        columns.forEach(System.out::println);

        assertFalse(columns.isEmpty());
    }

}
