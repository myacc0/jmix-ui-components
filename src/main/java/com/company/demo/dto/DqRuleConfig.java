package com.company.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DqRuleConfig {
    // threshold default 100%
    private Double threshold;
    // if null, all selected
    private Integer thresholdSampleSize;

    // ----- RANGE -----
    private Double min;
    private Double max;
    private Double includeMin;
    private Double includeMax;

    // ----- REGEXP_PATTERN -----
    private String regexp;

    // ----- UNIQUENESS (multiple columns) -----
    private List<String> columns;

    // ----- REFERENCE_TABLE -----
    private String refTable;
    private String refColumn;
    private String allowNull;

    // ----- CUSTOM_SQL -----
    private String sql;

    // ----- CROSS_SOURCE -----
    private String secondaryDataSourceId;

    private String crossSourceSql;
    private String crossSourceTable;
    private String crossSourceColumn;
    private String crossSourceAllowNull;

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public Integer getThresholdSampleSize() {
        return thresholdSampleSize;
    }

    public void setThresholdSampleSize(Integer thresholdSampleSize) {
        this.thresholdSampleSize = thresholdSampleSize;
    }

    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    public Double getIncludeMin() {
        return includeMin;
    }

    public void setIncludeMin(Double includeMin) {
        this.includeMin = includeMin;
    }

    public Double getIncludeMax() {
        return includeMax;
    }

    public void setIncludeMax(Double includeMax) {
        this.includeMax = includeMax;
    }

    public String getRegexp() {
        return regexp;
    }

    public void setRegexp(String regexp) {
        this.regexp = regexp;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public String getRefTable() {
        return refTable;
    }

    public void setRefTable(String refTable) {
        this.refTable = refTable;
    }

    public String getRefColumn() {
        return refColumn;
    }

    public void setRefColumn(String refColumn) {
        this.refColumn = refColumn;
    }

    public String getAllowNull() {
        return allowNull;
    }

    public void setAllowNull(String allowNull) {
        this.allowNull = allowNull;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    @Override
    public String toString() {
        return "DqRuleConfig{" +
                "threshold=" + threshold +
                ", thresholdSampleSize=" + thresholdSampleSize +
                ", min=" + min +
                ", max=" + max +
                ", includeMin=" + includeMin +
                ", includeMax=" + includeMax +
                ", pattern='" + regexp + '\'' +
                ", columns=" + columns +
                ", refTable='" + refTable + '\'' +
                ", refColumn='" + refColumn + '\'' +
                ", allowNull='" + allowNull + '\'' +
                ", sql='" + sql + '\'' +
                '}';
    }
}
