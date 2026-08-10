package com.company.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DqRuleConfig {
    // threshold default 100%
    private Double threshold;
    // if null, all selected
    private Integer sampleSize;

    // ----- RANGE (number or date) -----
    // For RANGE_NUMBER holds a numeric bound; for RANGE_DATE holds an ISO-8601 date string.
    private Object min;
    private Object max;
    private Boolean minIncluded;
    private Boolean maxIncluded;

    // ----- REGEXP_PATTERN -----
    private String regexp;

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

    public Integer getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(Integer sampleSize) {
        this.sampleSize = sampleSize;
    }

    public Object getMin() {
        return min;
    }

    public void setMin(Object min) {
        this.min = min;
    }

    public Object getMax() {
        return max;
    }

    public void setMax(Object max) {
        this.max = max;
    }

    public Boolean getMinIncluded() {
        return minIncluded;
    }

    public void setMinIncluded(Boolean minIncluded) {
        this.minIncluded = minIncluded;
    }

    public Boolean getMaxIncluded() {
        return maxIncluded;
    }

    public void setMaxIncluded(Boolean maxIncluded) {
        this.maxIncluded = maxIncluded;
    }

    public String getRegexp() {
        return regexp;
    }

    public void setRegexp(String regexp) {
        this.regexp = regexp;
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

    public String getSecondaryDataSourceId() {
        return secondaryDataSourceId;
    }

    public void setSecondaryDataSourceId(String secondaryDataSourceId) {
        this.secondaryDataSourceId = secondaryDataSourceId;
    }

    public String getCrossSourceSql() {
        return crossSourceSql;
    }

    public void setCrossSourceSql(String crossSourceSql) {
        this.crossSourceSql = crossSourceSql;
    }

    public String getCrossSourceTable() {
        return crossSourceTable;
    }

    public void setCrossSourceTable(String crossSourceTable) {
        this.crossSourceTable = crossSourceTable;
    }

    public String getCrossSourceColumn() {
        return crossSourceColumn;
    }

    public void setCrossSourceColumn(String crossSourceColumn) {
        this.crossSourceColumn = crossSourceColumn;
    }

    public String getCrossSourceAllowNull() {
        return crossSourceAllowNull;
    }

    public void setCrossSourceAllowNull(String crossSourceAllowNull) {
        this.crossSourceAllowNull = crossSourceAllowNull;
    }

    @Override
    public String toString() {
        return "DqRuleConfig{" +
                "threshold=" + threshold +
                ", sampleSize=" + sampleSize +
                ", min=" + min +
                ", max=" + max +
                ", minIncluded=" + minIncluded +
                ", maxIncluded=" + maxIncluded +
                ", regexp='" + regexp + '\'' +
                ", refTable='" + refTable + '\'' +
                ", refColumn='" + refColumn + '\'' +
                ", allowNull='" + allowNull + '\'' +
                ", sql='" + sql + '\'' +
                '}';
    }
}
