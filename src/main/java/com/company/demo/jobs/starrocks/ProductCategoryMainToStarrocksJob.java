package com.company.demo.jobs.starrocks;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.service.starrockssync.ProductCategoriesMainToStarrocksSynchronizer;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Copies the MAIN store {@code DEMO_PRODUCT_CATEGORY} table into the StarRocks
 * {@code product_categories} table, inserting rows the mirror does not have yet and updating the
 * ones it does.
 * <p>
 * Register it in <em>Administration &rarr; Quartz jobs</em> with a trigger of your choice.
 */
public class ProductCategoryMainToStarrocksJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ProductCategoryMainToStarrocksJob.class);

    @Autowired
    private ProductCategoriesMainToStarrocksSynchronizer productCategoriesMainToStarrocksSynchronizer;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            SyncResult result = productCategoriesMainToStarrocksSynchronizer.sync();
            log.info("ProductCategoryMainToStarrocksJob finished: {}", result);
        } catch (Exception e) {
            log.error("ProductCategoryMainToStarrocksJob failed", e);
            throw new JobExecutionException("Failed to sync product categories from main to starrocks", e);
        }
    }
}
