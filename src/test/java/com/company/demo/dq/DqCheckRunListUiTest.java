package com.company.demo.dq;

import com.company.demo.DemoApplication;
import com.company.demo.entity.dq.DqCheckRun;
import com.company.demo.enums.dq.DqCheckRunStatus;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.company.demo.view.dqcheckrun.DqCheckRunListView;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A check run can be opened once it has stopped running. The read action is therefore offered for
 * every status but {@link DqCheckRunStatus#RUNNING}, which this walks through the real list view.
 */
@UiTest
@SpringBootTest(classes = {DemoApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqCheckRunListUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    private final List<DqCheckRun> checkRuns = new ArrayList<>();

    @BeforeEach
    void setUp() {
        for (DqCheckRunStatus status : DqCheckRunStatus.values()) {
            checkRuns.add(dataManager.save(newCheckRun(status)));
        }
    }

    @Test
    void readIsOfferedForEveryStatusButRunning() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqCheckRunListView.class).navigate();
        DqCheckRunListView listView = UiTestUtils.getCurrentView();

        JmixButton readButton = UiTestUtils.getComponent(listView, "readButton");
        assertFalse(readButton.isEnabled(), "nothing is selected yet, so there is nothing to open");

        DataGrid<DqCheckRun> grid = UiTestUtils.getComponent(listView, "dqCheckRunsDataGrid");
        for (DqCheckRun checkRun : checkRuns) {
            grid.select(gridItem(grid, checkRun));

            boolean running = checkRun.getStatus() == DqCheckRunStatus.RUNNING;
            assertFalse(running && readButton.isEnabled(),
                    "a running check run cannot be opened");
            assertTrue(running || readButton.isEnabled(),
                    checkRun.getStatus() + " has a settled result, so it can be opened");
        }
    }

    private DqCheckRun newCheckRun(DqCheckRunStatus status) {
        DqCheckRun checkRun = dataManager.create(DqCheckRun.class);
        checkRun.setDataSource("main");
        checkRun.setTriggeredUsername("admin");
        checkRun.setStartedAt(LocalDateTime.now());
        checkRun.setStatus(status);
        checkRun.setRulesTotal(1);
        return checkRun;
    }

    /** The grid's own instance of the given run — the one its selection model can match. */
    private DqCheckRun gridItem(DataGrid<DqCheckRun> grid, DqCheckRun expected) {
        return grid.getGenericDataView().getItems()
                .filter(item -> expected.getId().equals(item.getId()))
                .findFirst()
                .orElseGet(() -> fail("check run " + expected.getId() + " is not shown in the list"));
    }

    @AfterEach
    void tearDown() {
        checkRuns.forEach(dataManager::remove);
        checkRuns.clear();
    }
}
