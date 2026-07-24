package com.company.demo.entity;

import com.company.demo.enums.DqIssueStatus;
import com.company.demo.enums.DqSeverity;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "DEMO_DQ_ISSUE", indexes = {
        @Index(name = "IDX_DEMO_DQ_ISSUE_CHECK_RESULT", columnList = "CHECK_RESULT_ID"),
        @Index(name = "IDX_DEMO_DQ_ISSUE_RULE", columnList = "RULE_ID")
})
@Entity(name = "demo_DqIssue")
public class DqIssue {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @JoinColumn(name = "CHECK_RESULT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private DqCheckRunResult checkResult;

    @NotNull
    @JoinColumn(name = "RULE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DqRule rule;

    @NotNull
    @Column(name = "DATA_SOURCE", nullable = false, length = 100)
    private String dataSource;

    @NotNull
    @Column(name = "STATUS", nullable = false, length = 40, columnDefinition = "VARCHAR(40) DEFAULT 'OPEN'")
    private String status;

    @NotNull
    @Column(name = "SEVERITY", nullable = false, length = 20)
    private String severity;

    @NotNull
    @InstanceName
    @Column(name = "TITLE", nullable = false, length = 500)
    private String title;

    @Column(name = "DESCRIPTION", columnDefinition = "text")
    private String description;

    @Column(name = "ASSIGNE_NAME", length = 200)
    private String assigneName;

    @Column(name = "ASSIGNEE_EMAIL", length = 200)
    private String assigneeEmail;

    @Column(name = "ASSIGNEE_PHONE", length = 200)
    private String assigneePhone;

    @Column(name = "DUE_DATE")
    private LocalDate dueDate;

    @Column(name = "RESOLVED_AT", columnDefinition = "TIMESTAMP")
    private LocalDateTime resolvedAt;

    @Column(name = "RESOLUTION_NOTES", columnDefinition = "text")
    private String resolutionNotes;

    @NumberFormat(pattern = "#")
    @Column(name = "AFFECTED_ROWS", precision = 19)
    private BigInteger affectedRows;

    @NotNull
    @Column(name = "CREATED_AT", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
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

    public BigInteger getAffectedRows() {
        return affectedRows;
    }

    public void setAffectedRows(BigInteger affectedRows) {
        this.affectedRows = affectedRows;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getAssigneePhone() {
        return assigneePhone;
    }

    public void setAssigneePhone(String assigneePhone) {
        this.assigneePhone = assigneePhone;
    }

    public String getAssigneeEmail() {
        return assigneeEmail;
    }

    public void setAssigneeEmail(String assigneeEmail) {
        this.assigneeEmail = assigneeEmail;
    }

    public String getAssigneName() {
        return assigneName;
    }

    public void setAssigneName(String assigneName) {
        this.assigneName = assigneName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DqSeverity getSeverity() {
        return severity == null ? null : DqSeverity.fromId(severity);
    }

    public void setSeverity(DqSeverity severity) {
        this.severity = severity == null ? null : severity.getId();
    }

    public DqIssueStatus getStatus() {
        return status == null ? null : DqIssueStatus.fromId(status);
    }

    public void setStatus(DqIssueStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public DqRule getRule() {
        return rule;
    }

    public void setRule(DqRule rule) {
        this.rule = rule;
    }

    public DqCheckRunResult getCheckResult() {
        return checkResult;
    }

    public void setCheckResult(DqCheckRunResult checkResult) {
        this.checkResult = checkResult;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}