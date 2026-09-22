package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.entity.tablesync.TableCol;
import com.company.demo.entity.tablesync.TableColConfig;
import com.company.demo.entity.tablesync.TableSynchronizer;
import com.company.demo.service.dq.DqDataSourceProvider;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The two-stage StarRocks -> main sync of a table with a self-referenced column.
 * <p>
 * No StarRocks runs under the tests, so the source is a plain PostgreSQL table of the main store with
 * no foreign key, served as the "StarRocks" template. The target does enforce {@code parent_id -> id},
 * which is what the two-stage upsert exists for.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class StarrocksToMainSelfReferencedSyncTests {

    @Autowired
    DqDataSourceProvider dataSourceProvider;

    @Autowired
    DataManager dataManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JdbcTemplate jdbc;
    private StarrocksToMainDynamicSynchronizer synchronizer;
    private String sourceTable;
    private String targetTable;

    @BeforeEach
    void setUp() {
        StarrocksSyncService syncService = new StarrocksSyncService(dataSourceProvider) {
            @Override
            public JdbcTemplate getStarrocksStoreJdbcTemplate() {
                return getMainStoreJdbcTemplate();
            }
        };
        jdbc = syncService.getMainStoreJdbcTemplate();
        synchronizer = new StarrocksToMainDynamicSynchronizer(syncService);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        sourceTable = "test_sync_src_" + suffix;
        targetTable = "test_sync_tgt_" + suffix;
        jdbc.execute("CREATE TABLE " + sourceTable + " (id BIGINT PRIMARY KEY, name VARCHAR(50), parent_id BIGINT)");
        jdbc.execute("CREATE TABLE " + targetTable + " (id BIGINT PRIMARY KEY, name VARCHAR(50), "
                + "parent_id BIGINT REFERENCES " + targetTable + " (id))");
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TABLE IF EXISTS " + targetTable);
        jdbc.execute("DROP TABLE IF EXISTS " + sourceTable);
    }

    @Test
    void childWrittenBeforeItsParentKeepsTheReference() {
        // a fresh heap table returns the rows in insertion order: the child comes first
        insertSource(1, "child", 2L);
        insertSource(2, "parent", null);

        // one row per batch, so the child is upserted while its parent is not there yet
        SyncResult result = synchronizer.sync(synchronizer(1));

        assertEquals(2, result.written());
        assertEquals(parents(Map.of(1L, 2L), 2L), targetParents());
    }

    @Test
    void referenceToARowThatIsNotWrittenIsCleared() {
        insertSource(1, "root", null);
        insertSource(2, "child", 1L);
        insertSource(3, "orphan", 99L);

        SyncResult result = synchronizer.sync(synchronizer(100));

        assertEquals(3, result.written());
        assertEquals(parents(Map.of(2L, 1L), 1L, 3L), targetParents());
    }

    @Test
    void rerunFollowsTheSourceReferences() {
        insertSource(1, "root", null);
        insertSource(2, "child", 1L);
        insertSource(3, "other root", null);
        TableSynchronizer t = synchronizer(2);
        synchronizer.sync(t);

        // unchanged source: the references survive a second run
        synchronizer.sync(t);
        assertEquals(parents(Map.of(2L, 1L), 1L, 3L), targetParents());

        // moved and detached at the source
        jdbc.update("UPDATE " + sourceTable + " SET parent_id = 3 WHERE id = 2");
        jdbc.update("UPDATE " + sourceTable + " SET parent_id = 2 WHERE id = 1");
        synchronizer.sync(t);
        assertEquals(parents(Map.of(1L, 2L, 2L, 3L), 3L), targetParents());
    }

    @Test
    void parentRemovedAtTheSourceIsDeletedAndItsChildDetached() {
        insertSource(1, "root", null);
        insertSource(2, "child", 1L);
        TableSynchronizer t = synchronizer(100);
        synchronizer.sync(t);

        // the source keeps a child whose parent is gone
        jdbc.update("DELETE FROM " + sourceTable + " WHERE id = 1");
        synchronizer.sync(t);

        assertEquals(parents(Map.of(), 2L), targetParents());
    }

    private TableSynchronizer synchronizer(int batchSize) {
        TableColConfig cfg = new TableColConfig();
        cfg.setColumns(List.of(
                new TableCol("id", "java.lang.Long", Types.BIGINT, null, null, true, false, false),
                new TableCol("name", "java.lang.String", Types.VARCHAR, null, null, false, false, true),
                new TableCol("parent_id", "java.lang.Long", Types.BIGINT, null, null, false, true, true)));

        TableSynchronizer t = dataManager.create(TableSynchronizer.class);
        t.setSourceTableName(sourceTable);
        t.setTargetTableName(targetTable);
        t.setBatchSize(batchSize);
        try {
            t.setTableColConfig(objectMapper.writeValueAsString(cfg));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return t;
    }

    private void insertSource(long id, String name, Long parentId) {
        jdbc.update("INSERT INTO " + sourceTable + " (id, name, parent_id) VALUES (?, ?, ?)", id, name, parentId);
    }

    /** id -> parent_id of every target row. */
    private Map<Long, Long> targetParents() {
        Map<Long, Long> parents = new HashMap<>();
        jdbc.query("SELECT id, parent_id FROM " + targetTable,
                (RowCallbackHandler) rs -> parents.put(rs.getLong("id"), rs.getObject("parent_id", Long.class)));
        return parents;
    }

    /** The expected id -> parent_id map: {@code linked} rows plus {@code roots} without a parent. */
    private static Map<Long, Long> parents(Map<Long, Long> linked, Long... roots) {
        Map<Long, Long> parents = new HashMap<>(linked);
        for (Long root : roots) {
            parents.put(root, null);
        }
        return parents;
    }
}
