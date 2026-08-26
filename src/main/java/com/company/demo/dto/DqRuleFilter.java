package com.company.demo.dto;

import com.company.demo.entity.dq.DqDataDomain;
import com.company.demo.entity.dq.DqDataProduct;
import com.company.demo.entity.dq.DqRuleGroup;
import com.company.demo.entity.User;
import com.company.demo.enums.DqDimension;
import com.company.demo.enums.DqRuleType;
import com.company.demo.enums.DqSeverity;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;

import java.util.UUID;

/**
 * Non-persistent model of the rule filter on the "run check" page. Only {@code dataSource}
 * is mandatory: the check run is always executed against a single data source, the remaining
 * attributes narrow down the rules taken from it.
 */
@JmixEntity(name = "demo_DqRuleFilter", annotatedPropertiesOnly = true)
public class DqRuleFilter {

    @JmixId
    @JmixGeneratedValue
    @JmixProperty(mandatory = true)
    private UUID id;

    @JmixProperty(mandatory = true)
    private String dataSource;

    @JmixProperty
    private DqRuleGroup group;

    @JmixProperty
    private DqDataDomain domain;

    @JmixProperty
    private DqDataProduct dataProduct;

    @JmixProperty
    private DqDimension dimension;

    @JmixProperty
    private DqSeverity severity;

    @JmixProperty
    private DqRuleType ruleType;

    @JmixProperty
    private String tableName;

    @JmixProperty
    private String columnName;

    @JmixProperty
    private User owner;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public DqRuleGroup getGroup() {
        return group;
    }

    public void setGroup(DqRuleGroup group) {
        this.group = group;
    }

    public DqDataDomain getDomain() {
        return domain;
    }

    public void setDomain(DqDataDomain domain) {
        this.domain = domain;
    }

    public DqDataProduct getDataProduct() {
        return dataProduct;
    }

    public void setDataProduct(DqDataProduct dataProduct) {
        this.dataProduct = dataProduct;
    }

    public DqDimension getDimension() {
        return dimension;
    }

    public void setDimension(DqDimension dimension) {
        this.dimension = dimension;
    }

    public DqSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(DqSeverity severity) {
        this.severity = severity;
    }

    public DqRuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(DqRuleType ruleType) {
        this.ruleType = ruleType;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }
}
