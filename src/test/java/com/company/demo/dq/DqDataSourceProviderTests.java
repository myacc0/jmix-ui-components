package com.company.demo.dq;

import com.company.demo.dto.DqDataSource;
import com.company.demo.dto.SelectDto;
import com.company.demo.service.DqDataSourceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class DqDataSourceProviderTests {

    @Autowired
    DqDataSourceProvider dqDataSourceProvider;

    @Test
    void test_get_data_source_list() {
        List<SelectDto> items = dqDataSourceProvider.getDataSourceList();
        System.out.println("size: " + items.size());
        items.forEach(System.out::println);
    }

    @Test
    void test_get_data_sources() {
        List<DqDataSource> items = dqDataSourceProvider.getDataSources();
        System.out.println("size: " + items.size());
        items.forEach(System.out::println);
    }
}
