package uz.kapitalbank.umida.dq;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.valuepicker.EntityPicker;
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
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqCheckRunStatus;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;
import uz.kapitalbank.umida.view.dq.DqCheckRunListView;
import uz.kapitalbank.umida.view.dq.DqCheckRunNewView;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Walks the "run check" page the way a user does — open it from the run list, pick a data source and
 * a domain, press Run — and asserts what the press produces: a check run in the database and a
 * return to the run list.
 * <p>
 * This is the render check the descriptor cannot get from a compiler: the fields, the grid and the
 * button are resolved by id and driven through their bindings, so a broken data container, property
 * path or component id fails here rather than in front of the user.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqCheckRunNewUiTest {

    private static final long RUN_TIMEOUT_MS = 60_000;

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    private final List<DqCheckRun> checkRuns = new ArrayList<>();

    private DqTestData testData;
    private DictDataDomain domain;
    private DqRule rule;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        domain = testData.createDomain();
        rule = testData.createRule(domain, "new run ui", DqRuleType.NOT_NULL, "short_name", "{}");
    }

    @Test
    void runButtonStartsTheCheckAndReturnsToTheRunList() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqCheckRunListView.class).navigate();
        DqCheckRunListView listView = UiTestUtils.getCurrentView();

        JmixButton runCheckButton = UiTestUtils.getComponent(listView, "runCheckButton");
        runCheckButton.click();

        DqCheckRunNewView newView = UiTestUtils.getCurrentView();
        JmixButton runButton = UiTestUtils.getComponent(newView, "runButton");
        assertFalse(runButton.isEnabled(), "nothing is selected yet, so there is nothing to run");

        // choosing the data source loads the rules; the domain narrows them down to the test rule
        JmixSelect<String> dataSourceField = UiTestUtils.getComponent(newView, "dataSourceField");
        dataSourceField.setValue("main");

        EntityPicker<DictDataDomain> domainField = UiTestUtils.getComponent(newView, "domainField");
        domainField.setValue(domain);

        DataGrid<DqRule> rulesDataGrid = UiTestUtils.getComponent(newView, "dqRulesDataGrid");
        assertEquals(1, rulesDataGrid.getGenericDataView().getItems().count());
        assertTrue(runButton.isEnabled(), "a matching rule makes the run possible");

        runButton.click();

        assertInstanceOf(DqCheckRunListView.class, UiTestUtils.getCurrentView(),
                "starting a run returns to the run list");

        DqCheckRun run = awaitFinished(loadStartedRun());
        assertEquals(DqCheckRunStatus.SUCCESS, run.getStatus());
        assertEquals(1, run.getRulesTotal());
        assertEquals(1, run.getRulesPassed());
    }

    /** The run the button started: the only one covering exactly the rule created by this test. */
    private DqCheckRun loadStartedRun() {
        List<DqCheckRun> runs = dataManager.load(DqCheckRun.class)
                .query("select distinct r.checkRun from umida_DqCheckRunResult r where r.rule = :rule")
                .parameter("rule", rule)
                .fetchPlan(FetchPlan.BASE)
                .list();
        if (runs.size() != 1) {
            // the result is stored before the run is closed, so an empty list means the worker
            // thread has not reached the first rule yet
            return awaitStartedRun();
        }
        return runs.get(0);
    }

    private DqCheckRun awaitStartedRun() {
        long deadline = System.currentTimeMillis() + RUN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            List<DqCheckRun> runs = dataManager.load(DqCheckRun.class)
                    .query("select distinct r.checkRun from umida_DqCheckRunResult r where r.rule = :rule")
                    .parameter("rule", rule)
                    .fetchPlan(FetchPlan.BASE)
                    .list();
            if (runs.size() == 1) {
                return runs.get(0);
            }
            sleep();
        }
        return fail("the run started by the button stored no result within " + RUN_TIMEOUT_MS + " ms");
    }

    private DqCheckRun awaitFinished(DqCheckRun startedRun) {
        checkRuns.add(startedRun);
        long deadline = System.currentTimeMillis() + RUN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            DqCheckRun run = dataManager.load(DqCheckRun.class).id(startedRun.getId()).one();
            if (run.getStatus() != DqCheckRunStatus.RUNNING) {
                return run;
            }
            sleep();
        }
        return fail("check run " + startedRun.getId() + " did not finish within " + RUN_TIMEOUT_MS + " ms");
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the check run");
        }
    }

    @AfterEach
    void tearDown() {
        dataManager.load(DqIssue.class)
                .query("select i from umida_DqIssue i where i.rule = :rule")
                .parameter("rule", rule)
                .fetchPlan(FetchPlan.BASE)
                .list()
                .forEach(dataManager::remove);

        for (DqCheckRun run : checkRuns) {
            dataManager.load(DqCheckRunResult.class)
                    .query("select r from umida_DqCheckRunResult r where r.checkRun = :checkRun")
                    .parameter("checkRun", run)
                    .fetchPlan(FetchPlan.BASE)
                    .list()
                    .forEach(dataManager::remove);
        }
        checkRuns.forEach(dataManager::remove);
        checkRuns.clear();

        testData.cleanup();
    }
}
