package uz.kapitalbank.umida.dto.dq;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.dq.DqRuleGroup;
import uz.kapitalbank.umida.enums.dq.DqDimension;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSeverity;

import java.util.UUID;

/**
 * Non-persistent model of the rule filter of the dialog the rules of a check are picked in. Every
 * attribute is optional and only narrows down the rules offered: a check may hold rules of several
 * data sources, each rule being run against its own.
 */
@JmixEntity(name = "umida_DqRuleFilter", annotatedPropertiesOnly = true)
public class DqRuleFilter {

    @JmixId
    @JmixGeneratedValue
    @JmixProperty(mandatory = true)
    private UUID id;

    @JmixProperty
    private String dataSource;

    @JmixProperty
    private DqRuleGroup group;

    @JmixProperty
    private DictDataDomain domain;

    @JmixProperty
    private DictDataProduct dataProduct;

    @JmixProperty
    private String dimension;

    @JmixProperty
    private String severity;

    @JmixProperty
    private String ruleType;

    @JmixProperty
    private String tableName;

    @JmixProperty
    private String ruleCode;

    public void setDataProduct(DictDataProduct dataProduct) {
        this.dataProduct = dataProduct;
    }

    public DictDataProduct getDataProduct() {
        return dataProduct;
    }

    public void setDomain(DictDataDomain domain) {
        this.domain = domain;
    }

    public DictDataDomain getDomain() {
        return domain;
    }

    public void setDimension(DqDimension dimension) {
        this.dimension = dimension == null ? null : dimension.getId();
    }

    public DqDimension getDimension() {
        return dimension == null ? null : DqDimension.fromId(dimension);
    }

    public void setSeverity(DqSeverity severity) {
        this.severity = severity == null ? null : severity.getId();
    }

    public DqSeverity getSeverity() {
        return severity == null ? null : DqSeverity.fromId(severity);
    }

    public void setRuleType(DqRuleType ruleType) {
        this.ruleType = ruleType == null ? null : ruleType.getId();
    }

    public DqRuleType getRuleType() {
        return ruleType == null ? null : DqRuleType.fromId(ruleType);
    }

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

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

}
