package com.company.demo.entity.dq;

import com.company.demo.enums.dq.DqAuditEntityType;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "DEMO_DQ_AUDIT_LOG")
@Entity(name = "demo_DqAuditLog")
public class DqAuditLog {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "ENTITY_TYPE", nullable = false, updatable = false, length = 50)
    @NotNull
    private String entityType;

    @Column(name = "ENTITY_ID", nullable = false, updatable = false)
    @NotNull
    private UUID entityId;

    @InstanceName
    @Column(name = "ACTION_", nullable = false, updatable = false, length = 50)
    @NotNull
    private String action;

    @Column(name = "USERNAME", updatable = false, length = 200)
    private String username;

    @Column(name = "USER_IP", updatable = false, length = 64)
    private String user_ip;

    @Column(name = "CHANGES", updatable = false, columnDefinition = "jsonb")
    private String changes;

    @Column(name = "CREATED_AT", nullable = false, columnDefinition = "TIMESTAMP")
    @NotNull
    private LocalDateTime createdAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getChanges() {
        return changes;
    }

    public void setChanges(String changes) {
        this.changes = changes;
    }

    public String getUser_ip() {
        return user_ip;
    }

    public void setUser_ip(String user_ip) {
        this.user_ip = user_ip;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public DqAuditEntityType getEntityType() {
        return entityType == null ? null : DqAuditEntityType.fromId(entityType);
    }

    public void setEntityType(DqAuditEntityType entityType) {
        this.entityType = entityType == null ? null : entityType.getId();
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

}