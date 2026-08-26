package com.company.demo.entity.dq;

import com.company.demo.enums.dq.DqDimension;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@JmixEntity
@Table(name = "DEMO_DQ_METRICS_HISTORY")
@Entity(name = "demo_DqMetricsHistory")
public class DqMetricsHistory {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @NotNull
    @Column(name = "DATA_SOURCE", nullable = false, length = 100)
    private String dataSource;

    @Column(name = "DIMENSION", length = 50)
    private String dimension;

    @NotNull
    @Column(name = "METRIC_DATE", nullable = false)
    private LocalDate metricDate;

    @NotNull
    @Column(name = "DQ_SCORE", nullable = false, precision = 5, scale = 2)
    private BigDecimal dqScore;

    @NumberFormat(pattern = "#")
    @Column(name = "RULES_CHECKED")
    private Integer rulesChecked;

    @NumberFormat(pattern = "#")
    @Column(name = "RULES_PASSED")
    private Integer rulesPassed;

    @NumberFormat(pattern = "#")
    @Column(name = "ISSUES_OPEN")
    private Integer issuesOpen;

    @NotNull
    @Column(name = "CREATED_AT", nullable = false, columnDefinition = "TIMESTAMP")
    private LocalTime createdAt;

    public LocalTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getIssuesOpen() {
        return issuesOpen;
    }

    public void setIssuesOpen(Integer issuesOpen) {
        this.issuesOpen = issuesOpen;
    }

    public Integer getRulesPassed() {
        return rulesPassed;
    }

    public void setRulesPassed(Integer rulesPassed) {
        this.rulesPassed = rulesPassed;
    }

    public Integer getRulesChecked() {
        return rulesChecked;
    }

    public void setRulesChecked(Integer rulesChecked) {
        this.rulesChecked = rulesChecked;
    }

    public BigDecimal getDqScore() {
        return dqScore;
    }

    public void setDqScore(BigDecimal dqScore) {
        this.dqScore = dqScore;
    }

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public void setMetricDate(LocalDate metricDate) {
        this.metricDate = metricDate;
    }

    public DqDimension getDimension() {
        return dimension == null ? null : DqDimension.fromId(dimension);
    }

    public void setDimension(DqDimension dimension) {
        this.dimension = dimension == null ? null : dimension.getId();
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

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalTime.now();
    }

}