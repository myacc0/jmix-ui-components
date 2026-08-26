package com.company.demo.entity.dq;

import com.company.demo.entity.Department;
import com.company.demo.entity.Employee;
import com.company.demo.entity.User;
import com.company.demo.enums.DqDimension;
import com.company.demo.enums.DqRuleType;
import com.company.demo.enums.DqSeverity;
import com.company.demo.utils.JsonbStringConverter;
import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.eclipse.persistence.annotations.Convert;
import org.eclipse.persistence.annotations.Converter;

import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "DEMO_DQ_RULE", indexes = {
        @Index(name = "IDX_DEMO_DQ_RULE_OWNER", columnList = "OWNER_ID"),
        @Index(name = "IDX_DEMO_DQ_RULE_DOMAIN", columnList = "DOMAIN_ID"),
        @Index(name = "IDX_DEMO_DQ_RULE_DATA_PRODUCT", columnList = "DATA_PRODUCT_ID"),
        @Index(name = "IDX_DEMO_DQ_RULE_GROUP", columnList = "GROUP_ID"),
        @Index(name = "IDX_DEMO_DQ_RULE_ASSIGNEE_SUBDIVISION", columnList = "ASSIGNEE_SUBDIVISION_ID"),
        @Index(name = "IDX_DEMO_DQ_RULE_ASSIGNEE_EMPLOYEE", columnList = "ASSIGNEE_EMPLOYEE_ID")
})
@Entity(name = "demo_DqRule")
public class DqRule {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @InstanceName
    @Column(name = "NAME", nullable = false)
    @NotNull
    private String name;

    @Column(name = "DESCRIPTION", columnDefinition = "text")
    private String description;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "GROUP_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private DqRuleGroup group;

    @Column(name = "DATA_SOURCE", nullable = false, length = 100)
    @NotNull
    private String dataSource;

    @Column(name = "DB_SCHEMA")
    private String dbSchema;

    @Column(name = "TABLE_NAME")
    private String tableName;

    @Column(name = "COLUMN_NAME")
    private String columnName;

    @Column(name = "DIMENSION", nullable = false, length = 50)
    @NotNull
    private String dimension;

    @Column(name = "RULE_TYPE", nullable = false, length = 50)
    @NotNull
    private String ruleType;

    @Column(name = "RULE_CONFIG", nullable = false, columnDefinition = "jsonb")
    @NotNull
    @Converter(name = "ruleConfigJsonbConverter", converterClass = JsonbStringConverter.class)
    @Convert("ruleConfigJsonbConverter")
    private String ruleConfig;

    @Column(name = "SEVERITY", nullable = false, length = 50)
    @NotNull
    private String severity;

    @Column(name = "ACTIVE", columnDefinition = "boolean default true")
    private Boolean active = false;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "OWNER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private User owner;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "DOMAIN_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private DqDataDomain domain;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "DATA_PRODUCT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private DqDataProduct dataProduct;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "ASSIGNEE_SUBDIVISION_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Department assigneeSubdivision;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "ASSIGNEE_EMPLOYEE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee assigneeEmployee;

    @Column(name = "VERSION", nullable = false)
    @Version
    private Integer version;

    @Column(name = "CREATED_AT", nullable = false, columnDefinition = "TIMESTAMP")
    @NotNull
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", columnDefinition = "TIMESTAMP")
    private LocalDateTime updatedAt;

    public Employee getAssigneeEmployee() {
        return assigneeEmployee;
    }

    public void setAssigneeEmployee(Employee assigneeEmployee) {
        this.assigneeEmployee = assigneeEmployee;
    }

    public Department getAssigneeSubdivision() {
        return assigneeSubdivision;
    }

    public void setAssigneeSubdivision(Department subdivision) {
        this.assigneeSubdivision = subdivision;
    }

    public DqRuleGroup getGroup() {
        return group;
    }

    public void setGroup(DqRuleGroup group) {
        this.group = group;
    }

    public String getDbSchema() {
        return dbSchema;
    }

    public void setDbSchema(String schema) {
        this.dbSchema = schema;
    }

    public DqDataProduct getDataProduct() {
        return dataProduct;
    }

    public void setDataProduct(DqDataProduct dataProduct) {
        this.dataProduct = dataProduct;
    }

    public DqDataDomain getDomain() {
        return domain;
    }

    public void setDomain(DqDataDomain domain) {
        this.domain = domain;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public DqSeverity getSeverity() {
        return severity == null ? null : DqSeverity.fromId(severity);
    }

    public void setSeverity(DqSeverity severity) {
        this.severity = severity == null ? null : severity.getId();
    }

    public String getRuleConfig() {
        return ruleConfig;
    }

    public void setRuleConfig(String ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    public DqRuleType getRuleType() {
        return ruleType == null ? null : DqRuleType.fromId(ruleType);
    }

    public void setRuleType(DqRuleType ruleType) {
        this.ruleType = ruleType == null ? null : ruleType.getId();
    }

    public DqDimension getDimension() {
        return dimension == null ? null : DqDimension.fromId(dimension);
    }

    public void setDimension(DqDimension dimension) {
        this.dimension = dimension == null ? null : dimension.getId();
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}