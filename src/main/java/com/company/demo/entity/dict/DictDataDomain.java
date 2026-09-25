package com.company.demo.entity.dict;

import com.company.demo.entity.orgstructure.Department;
import io.jmix.core.DeletePolicy;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@JmixEntity
@Table(name = "DEMO_DICT_DATA_DOMAIN", indexes = {
        @Index(name = "IDX_DEMO_DICT_DATA_DOMAIN_PARENT", columnList = "PARENT_ID"),
        @Index(name = "IDX_DEMO_DICT_DATA_DOMAIN_BUSINESS_OWNER", columnList = "BUSINESS_OWNER_ID")
})
@Entity(name = "demo_DictDataDomain")
public class DictDataDomain {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private Integer id;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "PARENT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private DictDataDomain parent;

    @Column(name = "CODE", nullable = false, length = 500)
    @NotNull
    private String code;

    @Column(name = "SHORT_NAME_RU", nullable = false)
    @NotNull
    private String shortNameRu;

    @NotNull
    @Column(name = "SHORT_NAME_UZ", nullable = false, length = 500)
    private String shortNameUz;

    @Column(name = "LONG_NAME_RU", length = 1000)
    private String longNameRu;

    @Column(name = "LONG_NAME_UZ", length = 1000)
    private String longNameUz;

    @NotNull
    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "BUSINESS_OWNER_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Department businessOwner;

    @Column(name = "ASSIGN_DATE", nullable = false)
    @NotNull
    private LocalDate assignDate;

    @Column(name = "DESCRIPTION_RU", length = 2000)
    private String descriptionRu;

    @Column(name = "DESCRIPTION_UZ", length = 2000)
    private String descriptionUz;

    @Column(name = "VERSION", nullable = false)
    @Version
    private Integer version;

    @DeletedBy
    @Column(name = "DELETED_BY")
    private String deletedBy;

    @DeletedDate
    @Column(name = "DELETED_DATE")
    private OffsetDateTime deletedDate;

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

    @InstanceName
    @DependsOnProperties({"shortNameRu", "shortNameUz"})
    public String getLocaledShortName() {
        return shortNameRu;
    }

    public String getDescriptionUz() {
        return descriptionUz;
    }

    public void setDescriptionUz(String descriptionUz) {
        this.descriptionUz = descriptionUz;
    }

    public String getDescriptionRu() {
        return descriptionRu;
    }

    public void setDescriptionRu(String description) {
        this.descriptionRu = description;
    }

    public LocalDate getAssignDate() {
        return assignDate;
    }

    public void setAssignDate(LocalDate assignDate) {
        this.assignDate = assignDate;
    }

    public Department getBusinessOwner() {
        return businessOwner;
    }

    public void setBusinessOwner(Department businessOwner) {
        this.businessOwner = businessOwner;
    }

    public String getLongNameUz() {
        return longNameUz;
    }

    public void setLongNameUz(String longNameUz) {
        this.longNameUz = longNameUz;
    }

    public String getLongNameRu() {
        return longNameRu;
    }

    public void setLongNameRu(String longName) {
        this.longNameRu = longName;
    }

    public String getShortNameUz() {
        return shortNameUz;
    }

    public void setShortNameUz(String shortNameUz) {
        this.shortNameUz = shortNameUz;
    }

    public String getShortNameRu() {
        return shortNameRu;
    }

    public void setShortNameRu(String shortName) {
        this.shortNameRu = shortName;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public DictDataDomain getParent() {
        return parent;
    }

    public void setParent(DictDataDomain parent) {
        this.parent = parent;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

}