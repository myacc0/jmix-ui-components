package com.company.demo.entity.tablesync;

import com.company.demo.utils.JsonbStringConverter;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.eclipse.persistence.annotations.Convert;
import org.eclipse.persistence.annotations.Converter;

import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "DEMO_TABLE_SYNCHRONIZER")
@Entity(name = "demo_TableSynchronizer")
public class TableSynchronizer {

    /** Rows copied per batch when the synchronizer runs, unless the user sets another size. */
    public static final int DEFAULT_BATCH_SIZE = 500;

    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @InstanceName
    @Column(name = "DIRECTION", nullable = false, length = 200)
    @NotNull
    private String direction;

    @Column(name = "TARGET_TABLE_NAME", nullable = false)
    @NotNull
    private String targetTableName;

    @Column(name = "SOURCE_TABLE_NAME", nullable = false)
    @NotNull
    private String sourceTableName;

    @Column(name = "BATCH_SIZE", nullable = false)
    @NotNull
    private Integer batchSize = DEFAULT_BATCH_SIZE;

    @Column(name = "TABLE_COL_CONFIG", nullable = false, columnDefinition = "jsonb")
    @NotNull
    @Converter(name = "tableColConfigJsonbConverter", converterClass = JsonbStringConverter.class)
    @Convert("tableColConfigJsonbConverter")
    private String tableColConfig;

    @Column(name = "CREATED_AT", nullable = false, columnDefinition = "TIMESTAMP")
    @NotNull
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", columnDefinition = "TIMESTAMP")
    private LocalDateTime updatedAt;

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

    public TableSyncDirection getDirection() {
        return direction == null ? null : TableSyncDirection.fromId(direction);
    }

    public void setDirection(TableSyncDirection direction) {
        this.direction = direction == null ? null : direction.getId();
    }

    public String getTableColConfig() {
        return tableColConfig;
    }

    public void setTableColConfig(String ruleConfig) {
        this.tableColConfig = ruleConfig;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public String getSourceTableName() {
        return sourceTableName;
    }

    public void setSourceTableName(String sourceTableName) {
        this.sourceTableName = sourceTableName;
    }

    public String getTargetTableName() {
        return targetTableName;
    }

    public void setTargetTableName(String targetTableName) {
        this.targetTableName = targetTableName;
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