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

import java.util.*;
import java.util.regex.Pattern;

public class StarrocksToMainMultipleTaskDynamicSyncJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(StarrocksToMainMultipleTaskDynamicSyncJob.class);

    private static final Pattern pattern = Pattern.compile("^task\\d+_(?:id|order|name)$");

    @Autowired
    private TableDynamicSynchronizeManager tableDynamicSynchronizeManager;

    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        try {
            List<DynamicSyncTask> dynamicSyncTasks = getSortedDynamicSyncTasks(jobDataMap);
            if (dynamicSyncTasks.isEmpty()) {
                log.error("StarrocksToMainMultipleTaskDynamicSyncJob finished, tasks skipped, no correct params found...");
            }

            for (DynamicSyncTask task : dynamicSyncTasks) {
//                SyncResult result = tableDynamicSynchronizeManager.sync(UUID.fromString(task.id));
//                log.info("Task [{}] with id[{}] finished: {}", task.name, task.id, result);
                log.info("Task [{}] with id[{}] finished", task.name, task.id);
            }
            log.error("StarrocksToMainMultipleTaskDynamicSyncJob finished");
        } catch (Exception e) {
            log.error("StarrocksToMainMultipleTaskDynamicSyncJob failed", e);
            throw new JobExecutionException("StarrocksToMainMultipleTaskDynamicSyncJob failed", e);
        }
    }

    private List<DynamicSyncTask> getSortedDynamicSyncTasks(JobDataMap jobDataMap) {
        if (!isJobParamKeysValid(jobDataMap.keySet())) {
            return Collections.emptyList();
        }

        List<DynamicSyncTask> dynamicSyncTasks = new ArrayList<>();
        try {
            Map<String, List<String>> paramKeyGroup = new HashMap<>();
            for (Map.Entry<String, Object> entry : jobDataMap.entrySet()) {
                String[] paramsParts = entry.getKey().split("_");
                paramKeyGroup.computeIfAbsent(paramsParts[0], k -> new ArrayList<>()).add(entry.getKey());
            }

            paramKeyGroup.forEach((keyGroup, keys) -> {
                String id = keys.stream()
                        .filter(k -> k.contains("id"))
                        .findFirst()
                        .map(k -> String.valueOf(jobDataMap.get(k)))
                        .orElse(null);

                Integer ordNo = keys.stream()
                        .filter(k -> k.contains("order"))
                        .findFirst()
                        .map(k -> Integer.parseInt(String.valueOf(jobDataMap.get(k))))
                        .orElse(null);

                String name = keys.stream()
                        .filter(k -> k.contains("name"))
                        .findFirst()
                        .map(k -> String.valueOf(jobDataMap.get(k)))
                        .orElse(null);

                if (StringUtils.hasText(id) && ordNo != null) {
                    dynamicSyncTasks.add(new DynamicSyncTask(id, ordNo, name));
                }
            });
        } catch (Exception e) {
            log.error("Failed to parse job params", e);
            dynamicSyncTasks.clear();
        }
        return dynamicSyncTasks;
    }

    private boolean isJobParamKeysValid(Set<String> keys) {
        return keys.stream().allMatch(pattern.asPredicate());
    }

    private record DynamicSyncTask(String id, int ordNo, String name) {
    }
}
