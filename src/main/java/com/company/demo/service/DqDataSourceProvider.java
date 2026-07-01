package com.company.demo.service;

import com.company.demo.dto.DqDataSource;
import com.company.demo.dto.SelectDto;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DqDataSourceProvider {
    private final DataManager dataManager;

    public DqDataSourceProvider(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public List<SelectDto> getDataSourceList() {
        return List.of(
                new SelectDto("main", "Main"),
                new SelectDto("dwh", "Oracle dwh"),
                new SelectDto("b2", "Oracle b2")
        );
    }

    public List<DqDataSource> getDataSources() {
        DqDataSource ds1 = dataManager.create(DqDataSource.class);
        ds1.setId("main");
        ds1.setName("Main");

        DqDataSource ds2 = dataManager.create(DqDataSource.class);
        ds2.setId("dwh");
        ds2.setName("Oracle dwh");

        DqDataSource ds3 = dataManager.create(DqDataSource.class);
        ds3.setId("b2");
        ds3.setName("Oracle b2");
        return List.of(ds1, ds2, ds3);
    }
}
