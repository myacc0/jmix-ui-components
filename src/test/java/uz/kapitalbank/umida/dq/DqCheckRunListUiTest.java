package uz.kapitalbank.umida.dq;

import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.enums.dq.DqCheckRunStatus;
import uz.kapitalbank.umida.enums.dq.DqCheckRunTrigger;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.view.dq.DqCheckRunListView;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The run list splits the runs in two tabs: the journal of the ended runs — successful, failed or
 * cancelled — newest first, and the runs still in progress.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqCheckRunListUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    private final List<DqCheckRun> checkRuns = new ArrayList<>();

    @Test
    void theJournalListsTheEndedRunsAndTheRunningTabTheOthers() {
        OffsetDateTime startedAt = OffsetDateTime.now();
        for (DqCheckRunStatus status : DqCheckRunStatus.values()) {
            checkRuns.add(dataManager.save(newCheckRun(status, "main", startedAt)));
        }

        DqCheckRunListView listView = openList();
        DataGrid<DqCheckRun> journal = UiTestUtils.getComponent(listView, "dqCheckRunsDataGrid");
        DataGrid<DqCheckRun> running = UiTestUtils.getComponent(listView, "runningCheckRunsDataGrid");

        for (DqCheckRun checkRun : checkRuns) {
            boolean isRunning = checkRun.getStatus() == DqCheckRunStatus.RUNNING;
            assertEquals(!isRunning, contains(journal, checkRun),
                    checkRun.getStatus() + " belongs to the journal: " + !isRunning);
            assertEquals(isRunning, contains(running, checkRun),
                    checkRun.getStatus() + " belongs to the running tab: " + isRunning);
        }
        assertTrue(running.getGenericDataView().getItems()
                        .allMatch(checkRun -> checkRun.getStatus() == DqCheckRunStatus.RUNNING),
                "the running tab shows nothing but running runs");
        assertTrue(journal.getGenericDataView().getItems()
                        .noneMatch(checkRun -> checkRun.getStatus() == DqCheckRunStatus.RUNNING),
                "the journal shows no running run");

        for (DataGrid<DqCheckRun> grid : List.of(journal, running)) {
            assertNotNull(grid.getColumnByKey("triggeredBy"), "both tabs show who triggered the run");
        }
    }

    @Test
    void theJournalIsSortedByStartNewestFirstThenByDataSource() {
        // in the future, so that these runs open the first page whatever else is stored
        OffsetDateTime later = OffsetDateTime.now().plusYears(100);
        OffsetDateTime earlier = later.minusDays(1);
        DqCheckRun earlierRun = dataManager.save(newCheckRun(DqCheckRunStatus.SUCCESS, "main", earlier));
        DqCheckRun laterMain = dataManager.save(newCheckRun(DqCheckRunStatus.FAILED, "main", later));
        DqCheckRun laterDwh = dataManager.save(newCheckRun(DqCheckRunStatus.CANCELLED, "dwh", later));
        checkRuns.addAll(List.of(earlierRun, laterMain, laterDwh));

        DataGrid<DqCheckRun> journal = UiTestUtils.getComponent(openList(), "dqCheckRunsDataGrid");

        List<UUID> order = journal.getGenericDataView().getItems()
                .map(DqCheckRun::getId)
                .filter(id -> checkRuns.stream().anyMatch(run -> run.getId().equals(id)))
                .toList();
        assertEquals(List.of(laterDwh.getId(), laterMain.getId(), earlierRun.getId()), order);
    }

    @Test
    void switchingToTheRunningTabReloadsIt() {
        DqCheckRunListView listView = openList();
        DataGrid<DqCheckRun> running = UiTestUtils.getComponent(listView, "runningCheckRunsDataGrid");

        DqCheckRun startedMeanwhile = dataManager.save(
                newCheckRun(DqCheckRunStatus.RUNNING, "main", OffsetDateTime.now()));
        checkRuns.add(startedMeanwhile);
        assertFalse(contains(running, startedMeanwhile), "the tab was loaded before the run started");

        JmixTabSheet tabSheet = UiTestUtils.getComponent(listView, "checkRunsTabSheet");
        tabSheet.setSelectedIndex(1);

        assertTrue(contains(running, startedMeanwhile), "selecting the tab shows the runs started since");
    }

    @Test
    void anEndedRunCanBeOpenedFromTheJournal() {
        DqCheckRun checkRun = dataManager.save(newCheckRun(DqCheckRunStatus.SUCCESS, "main", OffsetDateTime.now()));
        checkRuns.add(checkRun);

        DqCheckRunListView listView = openList();
        JmixButton readButton = UiTestUtils.getComponent(listView, "readButton");
        assertFalse(readButton.isEnabled(), "nothing is selected yet, so there is nothing to open");

        DataGrid<DqCheckRun> journal = UiTestUtils.getComponent(listView, "dqCheckRunsDataGrid");
        journal.select(journal.getGenericDataView().getItems()
                .filter(item -> checkRun.getId().equals(item.getId()))
                .findFirst()
                .orElseGet(() -> fail("check run " + checkRun.getId() + " is not shown in the journal")));
        assertTrue(readButton.isEnabled());
    }

    private DqCheckRunListView openList() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqCheckRunListView.class).navigate();
        return UiTestUtils.getCurrentView();
    }

    private DqCheckRun newCheckRun(DqCheckRunStatus status, String dataSource, OffsetDateTime startedAt) {
        DqCheckRun checkRun = dataManager.create(DqCheckRun.class);
        checkRun.setDataSource(dataSource);
        checkRun.setTriggered(DqCheckRunTrigger.SYSTEM);
        checkRun.setStartedAt(startedAt);
        checkRun.setStatus(status);
        checkRun.setRulesTotal(1);
        return checkRun;
    }

    private boolean contains(DataGrid<DqCheckRun> grid, DqCheckRun expected) {
        return grid.getGenericDataView().getItems().anyMatch(item -> expected.getId().equals(item.getId()));
    }

    @AfterEach
    void tearDown() {
        checkRuns.forEach(dataManager::remove);
        checkRuns.clear();
    }
}
