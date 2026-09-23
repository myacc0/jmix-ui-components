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

import java.util.UUID;

public class DynamicTableSyncJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(DynamicTableSyncJob.class);

    @Autowired
    private TableDynamicSynchronizeManager tableDynamicSynchronizeManager;

    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        try {
            SyncResult result = tableDynamicSynchronizeManager.sync(UUID.fromString(String.valueOf(jobDataMap.get("id"))));
            log.info("DynamicTableSyncJob finished: {}", result);
        } catch (Exception e) {
            String taskName = String.valueOf(jobDataMap.get("name"));
            log.error("DynamicTableSyncJob failed", e);
            throw new JobExecutionException("Failed to run sync task " + taskName, e);
        }
    }
}
