package uz.kapitalbank.umida.entity.dq;

import io.jmix.core.DeletePolicy;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.eclipse.persistence.annotations.Convert;
import org.eclipse.persistence.annotations.Converter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.dq.DqDimension;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSeverity;
import uz.kapitalbank.umida.utils.JsonbStringConverter;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "UMIDA_DQ_RULE", indexes = {
        @Index(name = "IDX_UMIDA_DQ_RULE_DOMAIN", columnList = "DOMAIN_ID"),
        @Index(name = "IDX_UMIDA_DQ_RULE_DATA_PRODUCT", columnList = "DATA_PRODUCT_ID"),
        @Index(name = "IDX_UMIDA_DQ_RULE_GROUP", columnList = "GROUP_ID"),
        @Index(name = "IDX_UMIDA_DQ_RULE_ASSIGNE", columnList = "ASSIGNEE_ID")
})
@Entity(name = "umida_DqRule")
public class DqRule {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "CODE", nullable = false, length = 50)
    @NotNull
    private String code;

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
    @JoinColumn(name = "DOMAIN_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private DictDataDomain domain;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "DATA_PRODUCT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private DictDataProduct dataProduct;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "ASSIGNEE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructureSubdivision assignee;

    @NumberFormat(pattern = "#")
    @Column(name = "DUE_DAYS")
    private Integer dueDays;

    @Column(name = "KEY_FIELDS_CHANGED_AT", nullable = false)
    @NotNull
    private OffsetDateTime keyFieldsChangedAt;

    @Column(name = "VERSION", nullable = false)
    @Version
    private Integer version;

    @CreatedBy
    @Column(name = "CREATED_BY")
    private String createdBy;

    @CreatedDate
    @Column(name = "CREATED_DATE")
    private OffsetDateTime createdDate;

    @LastModifiedBy
    @Column(name = "LAST_MODIFIED_BY")
    private String lastModifiedBy;

    @LastModifiedDate
    @Column(name = "LAST_MODIFIED_DATE")
    private OffsetDateTime lastModifiedDate;

    @DeletedBy
    @Column(name = "DELETED_BY")
    private String deletedBy;

    @DeletedDate
    @Column(name = "DELETED_DATE")
    private OffsetDateTime deletedDate;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getDueDays() {
        return dueDays;
    }

    public void setDueDays(Integer dueDays) {
        this.dueDays = dueDays;
    }

    public OffsetDateTime getKeyFieldsChangedAt() {
        return keyFieldsChangedAt;
    }

    public void setKeyFieldsChangedAt(OffsetDateTime keyFieldsChangedAt) {
        this.keyFieldsChangedAt = keyFieldsChangedAt;
    }

    public OrgStructureSubdivision getAssignee() {
        return assignee;
    }

    public void setAssignee(OrgStructureSubdivision assigne) {
        this.assignee = assigne;
    }

    public OffsetDateTime getDeletedDate() {
        return deletedDate;
    }

    public void setDeletedDate(OffsetDateTime deletedDate) {
        this.deletedDate = deletedDate;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public OffsetDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(OffsetDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

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

}