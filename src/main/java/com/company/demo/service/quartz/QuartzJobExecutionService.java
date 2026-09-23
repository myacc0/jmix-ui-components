package com.company.demo.service.quartz;

import com.company.demo.entity.quartz.QuartzJobExecution;
import com.company.demo.enums.quartz.QuartzJobExecutionStatus;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Stores the outcome of every Quartz job run so the history survives the log rotation.
 */
@Service
public class QuartzJobExecutionService {
    private static final Logger log = LoggerFactory.getLogger(QuartzJobExecutionService.class);

    private static final int JOB_PARAMS_MAX_LENGTH = 4000;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 4000;

    private final UnconstrainedDataManager dataManager;
    private final SystemAuthenticator systemAuthenticator;

    public QuartzJobExecutionService(UnconstrainedDataManager dataManager,
                                     SystemAuthenticator systemAuthenticator) {
        this.dataManager = dataManager;
        this.systemAuthenticator = systemAuthenticator;
    }

    /**
     * Writes one history record for a finished execution. The scheduler thread carries no
     * authentication, so the save runs as the system user.
     * <p>
     * Never throws: a job that did its work must not be reported as broken because the bookkeeping
     * failed, and a listener throwing back into Quartz would disturb the scheduler.
     */
    public void registerExecution(JobExecutionContext context, @Nullable JobExecutionException jobException) {
        try {
            systemAuthenticator.runWithSystem(() -> save(context, jobException));
        } catch (Exception e) {
            log.error("Cannot store execution history for job [{}]",
                    context.getJobDetail().getKey(), e);
        }
    }

    private void save(JobExecutionContext context, @Nullable JobExecutionException jobException) {
        QuartzJobExecution execution = dataManager.create(QuartzJobExecution.class);

        execution.setJobName(truncate(context.getJobDetail().getKey().getName(), 255));
        execution.setJobGroup(truncate(context.getJobDetail().getKey().getGroup(), 255));
        execution.setJobClass(truncate(context.getJobDetail().getJobClass().getName(), 500));

        Trigger trigger = context.getTrigger();
        if (trigger != null) {
            execution.setTriggerName(truncate(trigger.getKey().getName(), 255));
            execution.setTriggerGroup(truncate(trigger.getKey().getGroup(), 255));
        }
        execution.setFireInstanceId(truncate(context.getFireInstanceId(), 100));

        LocalDateTime finishedAt = LocalDateTime.now();
        execution.setStartedAt(toLocalDateTime(context.getFireTime(), finishedAt));
        execution.setFinishedAt(finishedAt);
        // Quartz reports -1 while the run time is still unknown
        execution.setDurationMs(context.getJobRunTime() >= 0 ? context.getJobRunTime() : null);

        execution.setStatus(jobException == null
                ? QuartzJobExecutionStatus.SUCCESS
                : QuartzJobExecutionStatus.FAILED);
        execution.setJobParams(formatJobParams(context.getMergedJobDataMap()));
        execution.setErrorMessage(formatError(jobException));

        // a history record is write-once and nothing reads the saved instance back
        dataManager.saveWithoutReload(execution);
    }

    private LocalDateTime toLocalDateTime(@Nullable Date date, LocalDateTime fallback) {
        return date == null
                ? fallback
                : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Renders the merged job data map as {@code key=value} pairs — for the multi-task sync jobs this
     * is what tells which tasks the run actually covered.
     */
    @Nullable
    private String formatJobParams(JobDataMap jobDataMap) {
        if (jobDataMap.isEmpty()) {
            return null;
        }
        String params = new TreeMap<>(jobDataMap.getWrappedMap()).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        return truncate(params, JOB_PARAMS_MAX_LENGTH);
    }

    @Nullable
    private String formatError(@Nullable JobExecutionException jobException) {
        if (jobException == null) {
            return null;
        }
        StringBuilder message = new StringBuilder(String.valueOf(jobException.getMessage()));
        Throwable cause = jobException.getCause();
        while (cause != null && cause != cause.getCause()) {
            message.append("\nCaused by: ").append(cause.getClass().getName())
                    .append(": ").append(cause.getMessage());
            cause = cause.getCause();
        }
        return truncate(message.toString(), ERROR_MESSAGE_MAX_LENGTH);
    }

    @Nullable
    private String truncate(@Nullable String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
