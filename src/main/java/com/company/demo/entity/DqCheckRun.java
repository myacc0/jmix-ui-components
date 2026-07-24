package com.company.demo.entity;

import com.company.demo.enums.DqCheckRunStatus;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.Composition;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JmixEntity
@Table(name = "DEMO_DQ_CHECK_RUN")
@Entity(name = "demo_DqCheckRun")
public class DqCheckRun {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "DATA_SOURCE", nullable = false, length = 100)
    @NotNull
    private String dataSource;

    @Column(name = "TRIGGERED_USERNAME", nullable = false, length = 200)
    @NotNull
    private String triggeredUsername;

    @Column(name = "STARTED_AT", nullable = false, columnDefinition = "TIMESTAMP")
    @NotNull
    private LocalDateTime startedAt;

    @Column(name = "FINISHED_AT", columnDefinition = "TIMESTAMP")
    private LocalDateTime finishedAt;

    @Column(name = "STATUS", nullable = false, length = 20)
    @NotNull
    private String status;

    @NotNull
    @NumberFormat(pattern = "#")
    @Column(name = "RULES_TOTAL", nullable = false)
    private Integer rulesTotal;

    @NumberFormat(pattern = "#")
    @Column(name = "RULES_PASSED")
    private Integer rulesPassed;

    @NumberFormat(pattern = "#")
    @Column(name = "RULES_FAILED")
    private Integer rulesFailed;

    @NumberFormat(pattern = "#")
    @Column(name = "RULES_SKIPPED")
    private Integer rulesSkipped;

    @Column(name = "DQ_SCORE", precision = 5, scale = 2)
    private BigDecimal dqScore;

    @Column(name = "ERROR_MESSAGE", columnDefinition = "text")
    private String errorMessage;

    @Composition
    @OneToMany(mappedBy = "checkRun")
    private List<DqCheckRunResult> checkResults;

    public List<DqCheckRunResult> getCheckResults() {
        return checkResults;
    }

    public void setCheckResults(List<DqCheckRunResult> checkResults) {
        this.checkResults = checkResults;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public BigDecimal getDqScore() {
        return dqScore;
    }

    public void setDqScore(BigDecimal dqScore) {
        this.dqScore = dqScore;
    }

    public Integer getRulesSkipped() {
        return rulesSkipped;
    }

    public void setRulesSkipped(Integer rulesSkipped) {
        this.rulesSkipped = rulesSkipped;
    }

    public Integer getRulesFailed() {
        return rulesFailed;
    }

    public void setRulesFailed(Integer rulesFailed) {
        this.rulesFailed = rulesFailed;
    }

    public Integer getRulesPassed() {
        return rulesPassed;
    }

    public void setRulesPassed(Integer rulesPassed) {
        this.rulesPassed = rulesPassed;
    }

    public Integer getRulesTotal() {
        return rulesTotal;
    }

    public void setRulesTotal(Integer rulesTotal) {
        this.rulesTotal = rulesTotal;
    }

    public DqCheckRunStatus getStatus() {
        return status == null ? null : DqCheckRunStatus.fromId(status);
    }

    public void setStatus(DqCheckRunStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public String getTriggeredUsername() {
        return triggeredUsername;
    }

    public void setTriggeredUsername(String triggeredUsername) {
        this.triggeredUsername = triggeredUsername;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}