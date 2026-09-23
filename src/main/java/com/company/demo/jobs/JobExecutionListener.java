package com.company.demo.jobs;

import com.company.demo.service.quartz.QuartzJobExecutionService;
import jakarta.annotation.PostConstruct;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.listeners.JobListenerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JobExecutionListener extends JobListenerSupport {
    private static final Logger log = LoggerFactory.getLogger(JobExecutionListener.class);

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private QuartzJobExecutionService quartzJobExecutionService;

    @Override
    public String getName() {
        return "JobExecutionListener";
    }

    @PostConstruct
    private void registerListener() {
        try {
            scheduler.getListenerManager().addJobListener(this);
        } catch (SchedulerException e) {
            log.error("Cannot register job listener", e);
        }
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        log.info("jobWasExecuted: name={}, runTimeMs={}, failed={}",
                context.getJobDetail().getKey().getName(), context.getJobRunTime(), jobException != null);

        quartzJobExecutionService.registerExecution(context, jobException);
    }

}
