package com.company.demo.entity.quartz;

import com.company.demo.enums.quartz.QuartzJobExecutionStatus;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One finished run of a Quartz job, written by {@code JobExecutionListener} after every execution.
 * A history record is never edited afterwards, so the entity carries no version or audit columns.
 */
@JmixEntity
@Table(name = "DEMO_QUARTZ_JOB_EXECUTION")
@Entity(name = "demo_QuartzJobExecution")
public class QuartzJobExecution {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "JOB_NAME", nullable = false)
    @NotNull
    private String jobName;

    @Column(name = "JOB_GROUP", nullable = false)
    @NotNull
    private String jobGroup;

    @Column(name = "JOB_CLASS", length = 500)
    private String jobClass;

    @Column(name = "TRIGGER_NAME")
    private String triggerName;

    @Column(name = "TRIGGER_GROUP")
    private String triggerGroup;

    /** Quartz id of this single execution — unique per fire, the key to correlate with scheduler logs. */
    @Column(name = "FIRE_INSTANCE_ID", length = 100)
    private String fireInstanceId;

    @Column(name = "STARTED_AT", nullable = false, columnDefinition = "TIMESTAMP")
    @NotNull
    private LocalDateTime startedAt;

    @Column(name = "FINISHED_AT", nullable = false, columnDefinition = "TIMESTAMP")
    @NotNull
    private LocalDateTime finishedAt;

    @Column(name = "DURATION_MS")
    private Long durationMs;

    @Column(name = "STATUS", nullable = false, length = 20)
    @NotNull
    private String status;

    @Column(name = "JOB_PARAMS", columnDefinition = "text")
    private String jobParams;

    @Column(name = "ERROR_MESSAGE", columnDefinition = "text")
    private String errorMessage;

    @InstanceName
    @DependsOnProperties({"jobName", "startedAt"})
    public String getInstanceName() {
        return jobName + " " + startedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getJobParams() {
        return jobParams;
    }

    public void setJobParams(String jobParams) {
        this.jobParams = jobParams;
    }

    public QuartzJobExecutionStatus getStatus() {
        return status == null ? null : QuartzJobExecutionStatus.fromId(status);
    }

    public void setStatus(QuartzJobExecutionStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
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

    public String getFireInstanceId() {
        return fireInstanceId;
    }

    public void setFireInstanceId(String fireInstanceId) {
        this.fireInstanceId = fireInstanceId;
    }

    public String getTriggerGroup() {
        return triggerGroup;
    }

    public void setTriggerGroup(String triggerGroup) {
        this.triggerGroup = triggerGroup;
    }

    public String getTriggerName() {
        return triggerName;
    }

    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
    }

    public String getJobClass() {
        return jobClass;
    }

    public void setJobClass(String jobClass) {
        this.jobClass = jobClass;
    }

    public String getJobGroup() {
        return jobGroup;
    }

    public void setJobGroup(String jobGroup) {
        this.jobGroup = jobGroup;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
