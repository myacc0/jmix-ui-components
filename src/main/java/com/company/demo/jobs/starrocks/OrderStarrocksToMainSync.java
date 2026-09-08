package com.company.demo.jobs.starrocks;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.service.starrockssync.OrdersTableSynchronizer;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Copies the StarRocks {@code orders} table into the MAIN store {@code orders} table, inserting
 * rows the main store does not have yet and updating the ones it does.
 * <p>
 * Register it in <em>Administration &rarr; Quartz jobs</em> with a trigger of your choice. Orders
 * whose product is missing from the main store are skipped rather than failing the run — see
 * {@link OrdersTableSynchronizer#syncStarrocksToMain()} — so run {@link ProductMainToStarrocksSync}'s
 * source data into shape first if the mirror is ahead of the main store.
 */
public class OrderStarrocksToMainSync implements Job {

    private static final Logger log = LoggerFactory.getLogger(OrderStarrocksToMainSync.class);

    @Autowired
    private OrdersTableSynchronizer ordersTableSynchronizer;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            SyncResult result = ordersTableSynchronizer.syncStarrocksToMain();
            log.info("OrderStarrocksToMainSync finished: {}", result);
        } catch (Exception e) {
            log.error("OrderStarrocksToMainSync failed", e);
            throw new JobExecutionException("Failed to sync orders from starrocks to main", e);
        }
    }
}
