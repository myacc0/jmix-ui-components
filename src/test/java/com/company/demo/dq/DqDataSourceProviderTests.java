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
