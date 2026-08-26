package com.company.demo.entity.dq;

import com.company.demo.enums.DqCheckResultStatus;
import com.company.demo.utils.JsonbStringConverter;
import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.eclipse.persistence.annotations.Convert;
import org.eclipse.persistence.annotations.Converter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "DEMO_DQ_CHECK_RUN_RESULT", indexes = {
        @Index(name = "IDX_DEMO_DQ_CHECK_RUN_RESULT_CHECK_RUN", columnList = "CHECK_RUN_ID"),
        @Index(name = "IDX_DEMO_DQ_CHECK_RUN_RESULT_RULE", columnList = "RULE_ID")
})
@Entity(name = "demo_DqCheckRunResult")
public class DqCheckRunResult {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "CHECK_RUN_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DqCheckRun checkRun;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "RULE_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DqRule rule;

    @Column(name = "STATUS", nullable = false, length = 20)
    @NotNull
    private String status;

    @NumberFormat(pattern = "#")
    @Column(name = "TOTAL_RECORDS", precision = 19)
    private BigInteger totalRecords;

    @NumberFormat(pattern = "#")
    @Column(name = "FAILED_RECORDS", precision = 19)
    private BigInteger failedRecords;

    @Column(name = "PASS_RATE", precision = 5, scale = 2)
    private BigDecimal passRate;

    @NumberFormat(pattern = "#")
    @Column(name = "EXECUTION_MS")
    private Long executionMs;

    @Column(name = "SAMPLE_VIOLATIONS", columnDefinition = "jsonb")
    @Converter(name = "sampleViolationsJsonbConverter", converterClass = JsonbStringConverter.class)
    @Convert("sampleViolationsJsonbConverter")
    private String sampleViolations;

    @Column(name = "ERROR_MESSAGE", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "EXECUTED_QUERY", columnDefinition = "text")
    private String executedQuery;

    @Column(name = "CREATED_AT", nullable = false, columnDefinition = "TIMESTAMP")
    @NotNull
    private LocalDateTime createdAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getExecutedQuery() {
        return executedQuery;
    }

    public void setExecutedQuery(String executedQuery) {
        this.executedQuery = executedQuery;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSampleViolations() {
        return sampleViolations;
    }

    public void setSampleViolations(String sampleViolations) {
        this.sampleViolations = sampleViolations;
    }

    public Long getExecutionMs() {
        return executionMs;
    }

    public void setExecutionMs(Long executionMs) {
        this.executionMs = executionMs;
    }

    public BigDecimal getPassRate() {
        return passRate;
    }

    public void setPassRate(BigDecimal passRate) {
        this.passRate = passRate;
    }

    public BigInteger getFailedRecords() {
        return failedRecords;
    }

    public void setFailedRecords(BigInteger failedRecords) {
        this.failedRecords = failedRecords;
    }

    public BigInteger getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(BigInteger totalRecords) {
        this.totalRecords = totalRecords;
    }

    public DqCheckResultStatus getStatus() {
        return status == null ? null : DqCheckResultStatus.fromId(status);
    }

    public void setStatus(DqCheckResultStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public DqRule getRule() {
        return rule;
    }

    public void setRule(DqRule rule) {
        this.rule = rule;
    }

    public DqCheckRun getCheckRun() {
        return checkRun;
    }

    public void setCheckRun(DqCheckRun checkRun) {
        this.checkRun = checkRun;
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