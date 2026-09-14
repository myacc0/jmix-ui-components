package com.company.demo.entity.tablesync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TableColConfig {
    private List<TableCol> columns;

    public List<TableCol> getColumns() {
        return columns;
    }

    public void setColumns(List<TableCol> columns) {
        this.columns = columns;
    }

    @Override
    public String toString() {
        return "TableColConfig{" +
                "columns=" + columns +
                '}';
    }
}
