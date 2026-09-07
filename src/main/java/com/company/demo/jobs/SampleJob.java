package com.company.demo.jobs;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SampleJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(SampleJob.class);

    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("Sample job executed");
    }
}
