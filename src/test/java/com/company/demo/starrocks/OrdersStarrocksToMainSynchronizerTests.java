package com.company.demo.starrocks;

import com.company.demo.service.starrockssync.OrdersStarrocksToMainSynchronizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class OrdersStarrocksToMainSynchronizerTests {

    @Autowired
    OrdersStarrocksToMainSynchronizer starrocksToMainSynchronizer;

    @Test
    void test_generateSql() {
        String sql = starrocksToMainSynchronizer.prepareSelectSQLFromSource();
        System.out.println(sql);
    }

}
