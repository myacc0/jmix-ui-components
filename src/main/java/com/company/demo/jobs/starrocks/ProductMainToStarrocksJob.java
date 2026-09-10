package com.company.demo.jobs.starrocks;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.service.starrockssync.ProductsMainToStarrocksSynchronizer;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Copies the MAIN store {@code products} table into the StarRocks {@code products} table,
 * inserting rows the mirror does not have yet and updating the ones it does.
 * <p>
 * Register it in <em>Administration &rarr; Quartz jobs</em> with a trigger of your choice. Run it
 * after {@link ProductCategoryMainToStarrocksJob} if you want a product's category row to already
 * be in the mirror; StarRocks enforces no foreign key, so the order only affects readers.
 */
public class ProductMainToStarrocksJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ProductMainToStarrocksJob.class);

    @Autowired
    private ProductsMainToStarrocksSynchronizer productsMainToStarrocksSynchronizer;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            SyncResult result = productsMainToStarrocksSynchronizer.sync();
            log.info("ProductMainToStarrocksJob finished: {}", result);
        } catch (Exception e) {
            log.error("ProductMainToStarrocksJob failed", e);
            throw new JobExecutionException("Failed to sync products from main to starrocks", e);
        }
    }
}
