package com.company.demo.jobs.starrocks;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.service.starrockssync.TableDynamicSynchronizeManager;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StarrocksToMainMultipleTaskDynamicSyncJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(StarrocksToMainMultipleTaskDynamicSyncJob.class);

    private static final Pattern PARAM_KEY_PATTERN = Pattern.compile("^(task\\d+)_(id|order|name)$");

    private static final String ID_PARAM = "id";
    private static final String ORDER_PARAM = "order";
    private static final String NAME_PARAM = "name";

    @Autowired
    private TableDynamicSynchronizeManager tableDynamicSynchronizeManager;

    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        try {
            List<DynamicSyncTask> dynamicSyncTasks = getSortedDynamicSyncTasks(jobDataMap);

            for (DynamicSyncTask task : dynamicSyncTasks) {
                SyncResult result = tableDynamicSynchronizeManager.sync(task.id());
                log.info("Task [{}] with id[{}] finished: {}", task.name(), task.id(), result);
            }
            log.info("StarrocksToMainMultipleTaskDynamicSyncJob finished, {} task(s) executed", dynamicSyncTasks.size());
        } catch (Exception e) {
            log.error("StarrocksToMainMultipleTaskDynamicSyncJob failed", e);
            throw new JobExecutionException("StarrocksToMainMultipleTaskDynamicSyncJob failed: " + e.getMessage(), e);
        }
    }

    private List<DynamicSyncTask> getSortedDynamicSyncTasks(JobDataMap jobDataMap) {
        Map<String, Map<String, String>> paramsByTask = groupParamsByTask(jobDataMap);
        if (paramsByTask.isEmpty()) {
            throw new IllegalArgumentException("no job params found, at least one taskN_id + taskN_order pair is required");
        }

        List<String> errors = new ArrayList<>();
        List<DynamicSyncTask> dynamicSyncTasks = new ArrayList<>();
        paramsByTask.forEach((taskKey, params) -> {
            UUID id = parseId(taskKey, params.get(ID_PARAM), errors);
            Integer ordNo = parseOrdNo(taskKey, params.get(ORDER_PARAM), errors);
            if (id != null && ordNo != null) {
                dynamicSyncTasks.add(new DynamicSyncTask(id, ordNo, params.get(NAME_PARAM)));
            }
        });
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("invalid job params: " + String.join("; ", errors));
        }

        dynamicSyncTasks.sort(Comparator.comparingInt(DynamicSyncTask::ordNo));
        return dynamicSyncTasks;
    }

    /**
     * Groups the job data map into {@code taskN -> (id|order|name) -> value}. Every key must match
     * {@link #PARAM_KEY_PATTERN}, an unexpected one fails the job instead of being ignored.
     */
    private Map<String, Map<String, String>> groupParamsByTask(JobDataMap jobDataMap) {
        Map<String, Map<String, String>> paramsByTask = new TreeMap<>();
        List<String> invalidKeys = new ArrayList<>();
        for (Map.Entry<String, Object> entry : jobDataMap.entrySet()) {
            Matcher matcher = PARAM_KEY_PATTERN.matcher(entry.getKey());
            if (!matcher.matches()) {
                invalidKeys.add(entry.getKey());
                continue;
            }
            Object value = entry.getValue();
            paramsByTask.computeIfAbsent(matcher.group(1), k -> new TreeMap<>())
                    .put(matcher.group(2), value != null ? String.valueOf(value).trim() : null);
        }
        if (!invalidKeys.isEmpty()) {
            throw new IllegalArgumentException("unexpected job param key(s): " + String.join(", ", invalidKeys)
                    + ", expected taskN_id, taskN_order and optional taskN_name");
        }
        return paramsByTask;
    }

    private UUID parseId(String taskKey, String rawId, List<String> errors) {
        if (!StringUtils.hasText(rawId)) {
            errors.add(taskKey + "_id is missing or empty");
            return null;
        }
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            errors.add(taskKey + "_id is not a valid UUID: '" + rawId + "'");
            return null;
        }
    }

    private Integer parseOrdNo(String taskKey, String rawOrdNo, List<String> errors) {
        if (!StringUtils.hasText(rawOrdNo)) {
            errors.add(taskKey + "_order is missing or empty");
            return null;
        }
        try {
            return Integer.valueOf(rawOrdNo);
        } catch (NumberFormatException e) {
            errors.add(taskKey + "_order is not a valid int: '" + rawOrdNo + "'");
            return null;
        }
    }

    private record DynamicSyncTask(UUID id, int ordNo, String name) {
    }
}
