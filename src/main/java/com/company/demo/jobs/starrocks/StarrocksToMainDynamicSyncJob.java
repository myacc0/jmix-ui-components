package com.company.demo.jobs.starrocks;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.service.starrockssync.TableDynamicSynchronizer;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

public class StarrocksToMainDynamicSyncJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(StarrocksToMainDynamicSyncJob.class);

    @Autowired
    private TableDynamicSynchronizer tableDynamicSynchronizer;

    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        try {
            SyncResult result = tableDynamicSynchronizer.sync(UUID.fromString(String.valueOf(jobDataMap.get("id"))));
            log.info("StarrocksToMainDynamicSyncJob finished: {}", result);
        } catch (Exception e) {
            String tableName = String.valueOf(jobDataMap.get("tableName"));
            log.error("StarrocksToMainDynamicSyncJob failed", e);
            throw new JobExecutionException("Failed to sync " + tableName + " from starrocks to main", e);
        }
    }
}
