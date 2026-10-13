package uz.kapitalbank.umida.dto.dq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DqRuleConfig {
    // threshold default 100%
    private Double threshold;
    // columns projected by the samples query; if null or empty, the whole row is stored.
    // Wide tables would otherwise blow up the sample storage and make the samples hard to read.
    private List<String> samplesQueryColumns;

    // ----- RANGE (number or date) -----
    // For RANGE_NUMBER holds a numeric bound; for RANGE_DATE holds an ISO-8601 date string.
    private Object min;
    private Object max;
    private Boolean minIncluded;
    private Boolean maxIncluded;

    // ----- REGEXP_PATTERN -----
    private String regexp;

    // ----- REFERENTIAL -----
    // The reference ("dictionary") table the column values are looked up in; it lives in the same
    // data source as the checked table.
    private String refSchema;
    private String refTable;
    private String refColumn;
    // whether NULL in the checked column is a valid value; absent means true
    private Boolean allowNull;

    // ----- CUSTOM_SQL -----
    // one row of two numbers: the total and the failed record counts
    private String sql;
    // the failed records themselves, with the columns to show; paged when the violations are viewed
    private String violationsSql;

    // ----- CROSS_SOURCE -----
    private String crossPrimarySql;
    private String crossDataSource;
    private String crossDataSourceSql;

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public List<String> getSamplesQueryColumns() {
        return samplesQueryColumns;
    }

    public void setSamplesQueryColumns(List<String> samplesQueryColumns) {
        this.samplesQueryColumns = samplesQueryColumns;
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

    public String getRefSchema() {
        return refSchema;
    }

    public void setRefSchema(String refSchema) {
        this.refSchema = refSchema;
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

    public Boolean getAllowNull() {
        return allowNull;
    }

    public void setAllowNull(Boolean allowNull) {
        this.allowNull = allowNull;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getViolationsSql() {
        return violationsSql;
    }

    public void setViolationsSql(String violationsSql) {
        this.violationsSql = violationsSql;
    }

    public String getCrossPrimarySql() {
        return crossPrimarySql;
    }

    public void setCrossPrimarySql(String crossPrimarySql) {
        this.crossPrimarySql = crossPrimarySql;
    }

    public String getCrossDataSource() {
        return crossDataSource;
    }

    public void setCrossDataSource(String crossDataSource) {
        this.crossDataSource = crossDataSource;
    }

    public String getCrossDataSourceSql() {
        return crossDataSourceSql;
    }

    public void setCrossDataSourceSql(String crossDataSourceSql) {
        this.crossDataSourceSql = crossDataSourceSql;
    }

    @Override
    public String toString() {
        return "DqRuleConfig{" +
                "threshold=" + threshold +
                ", samplesQueryColumns=" + samplesQueryColumns +
                ", min=" + min +
                ", max=" + max +
                ", minIncluded=" + minIncluded +
                ", maxIncluded=" + maxIncluded +
                ", regexp='" + regexp + '\'' +
                ", refSchema='" + refSchema + '\'' +
                ", refTable='" + refTable + '\'' +
                ", refColumn='" + refColumn + '\'' +
                ", allowNull=" + allowNull +
                ", sql='" + sql + '\'' +
                '}';
    }
}
