package uz.kapitalbank.umida.dq;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.QueryParameters;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.entity.EntityValues;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.view.View;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqCheck;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqCheckRunStatus;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;
import uz.kapitalbank.umida.view.dq.DqCheckDetailView;
import uz.kapitalbank.umida.view.dq.DqCheckRunListView;
import uz.kapitalbank.umida.view.dq.DqRuleLookupView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Walks the check catalog the way a user does: a check is put together in its editor from the rules
 * picked in the selection dialog, and run from the catalog.
 * <p>
 * This is the render check the descriptors cannot get from a compiler: the fields, grids and buttons
 * are resolved by id and driven through their bindings, so a broken data container, property path or
 * component id fails here rather than in front of the user.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqCheckUiTest {

    private static final long RUN_TIMEOUT_MS = 60_000;
    private static final String DIMMED = "inactive-row";

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    private DqTestData testData;
    private DictDataDomain domain;
    private DqRule activeRule;
    private DqRule inactiveRule;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        domain = testData.createDomain();
        activeRule = testData.createRule(domain, "check ui active", DqRuleType.NOT_NULL, "short_name", "{}");
        inactiveRule = testData.createRule(domain, "check ui inactive", DqRuleType.NOT_NULL, "short_name", "{}",
                rule -> rule.setActive(false));
    }

    @Test
    void aCheckIsMadeOfTheRulesPickedInTheSelectionDialog() {
        DqCheckDetailView detailView = createCheckInEditor();
        TypedTextField<String> codeField = UiTestUtils.getComponent(detailView, "codeField");
        assertFalse(codeField.isVisible(), "a new check gets its code on save, so there is none to show");
        String name = "dq test check ui " + UUID.randomUUID();
        TypedTextField<String> nameField = UiTestUtils.getComponent(detailView, "nameField");
        nameField.setValue(name);

        JmixButton addRulesButton = UiTestUtils.getComponent(detailView, "addRulesButton");
        addRulesButton.click();

        DqRuleLookupView lookupView = openedDialogView(DqRuleLookupView.class);
        EntityPicker<DictDataDomain> domainField = UiTestUtils.getComponent(lookupView, "domainField");
        domainField.setValue(domain);
        DataGrid<DqRule> offered = UiTestUtils.getComponent(lookupView, "dqRulesDataGrid");
        assertEquals(Set.of(activeRule.getId()), ids(offered), "only active rules are offered");

        offered.select(itemOf(offered, activeRule));
        JmixButton selectButton = UiTestUtils.getComponent(lookupView, "selectButton");
        selectButton.click();

        DataGrid<DqRule> rulesGrid = UiTestUtils.getComponent(detailView, "rulesDataGrid");
        assertEquals(Set.of(activeRule.getId()), ids(rulesGrid), "the picked rule joins the check");

        JmixButton saveButton = UiTestUtils.getComponent(detailView, "saveAndCloseButton");
        saveButton.click();

        DqCheck saved = trackSaved(name)
                .orElseGet(() -> fail("the check is saved"));
        assertTrue(saved.getCode().matches("DQC-\\d+"), "a new check gets a code: " + saved.getCode());
        assertEquals(Set.of(activeRule.getId()), ruleIds(saved));
    }

    @Test
    void aCheckWithoutRulesIsNotSaved() {
        DqCheckDetailView detailView = createCheckInEditor();
        String name = "dq test check ui empty " + UUID.randomUUID();
        TypedTextField<String> nameField = UiTestUtils.getComponent(detailView, "nameField");
        nameField.setValue(name);

        JmixButton saveButton = UiTestUtils.getComponent(detailView, "saveAndCloseButton");
        saveButton.click();

        assertTrue(trackSaved(name).isEmpty(), "a check without rules has nothing to run");
        assertInstanceOf(DqCheckDetailView.class, UiTestUtils.getCurrentView(), "the editor stays open");
    }

    @Test
    void anInactiveRuleOfTheCheckIsShownDimmedAndCanBeRemoved() {
        DqCheck check = createCheck(activeRule, inactiveRule);

        DqCheckDetailView detailView = editCheckInEditor(check);
        TypedTextField<String> codeField = UiTestUtils.getComponent(detailView, "codeField");
        assertTrue(codeField.isVisible(), "a saved check shows its code");
        assertTrue(codeField.isReadOnly(), "the code is assigned, not entered");
        assertEquals(check.getCode(), codeField.getValue());

        DataGrid<DqRule> rulesGrid = UiTestUtils.getComponent(detailView, "rulesDataGrid");
        assertEquals(Set.of(activeRule.getId(), inactiveRule.getId()), ids(rulesGrid));
        assertNull(rulesGrid.getPartNameGenerator().apply(itemOf(rulesGrid, activeRule)));
        assertEquals(DIMMED, rulesGrid.getPartNameGenerator().apply(itemOf(rulesGrid, inactiveRule)),
                "a rule that is no longer active stays in the check, dimmed");

        rulesGrid.select(itemOf(rulesGrid, inactiveRule));
        JmixButton removeRulesButton = UiTestUtils.getComponent(detailView, "removeRulesButton");
        removeRulesButton.click();
        assertEquals(Set.of(activeRule.getId()), ids(rulesGrid));

        JmixButton saveButton = UiTestUtils.getComponent(detailView, "saveAndCloseButton");
        saveButton.click();

        assertEquals(Set.of(activeRule.getId()), ruleIds(check), "the rule is removed from the check only");
        assertTrue(dataManager.load(DqRule.class).id(inactiveRule.getId()).optional().isPresent(),
                "the rule itself is kept");
    }

    @Test
    void aCheckWithoutActiveRulesIsDimmedAndCannotBeRun() {
        DqCheck runnable = createCheck(activeRule, inactiveRule);
        DqCheck idle = createCheck(inactiveRule);

        DqCheckRunListView listView = openList();
        DataGrid<DqCheck> checksGrid = UiTestUtils.getComponent(listView, "dqChecksDataGrid");
        JmixButton runCheckButton = UiTestUtils.getComponent(listView, "runCheckButton");

        assertEquals(DIMMED, checksGrid.getPartNameGenerator().apply(itemOf(checksGrid, idle)));
        assertNull(checksGrid.getPartNameGenerator().apply(itemOf(checksGrid, runnable)));

        checksGrid.select(itemOf(checksGrid, idle));
        assertFalse(runCheckButton.isEnabled(), "a check without active rules has nothing to run");

        checksGrid.select(itemOf(checksGrid, runnable));
        assertTrue(runCheckButton.isEnabled());
    }

    @Test
    void runningACheckStartsARunOfItsActiveRulesAndShowsTheRunningTab() {
        DqCheck check = createCheck(activeRule, inactiveRule);

        DqCheckRunListView listView = openList();
        DataGrid<DqCheck> checksGrid = UiTestUtils.getComponent(listView, "dqChecksDataGrid");
        checksGrid.select(itemOf(checksGrid, check));
        JmixButton runCheckButton = UiTestUtils.getComponent(listView, "runCheckButton");
        runCheckButton.click();

        JmixTabSheet tabSheet = UiTestUtils.getComponent(listView, "checkRunsTabSheet");
        assertEquals("runningTab", tabSheet.getSelectedTab().getId().orElse(null),
                "a started run is shown among the running ones");

        DqCheckRun run = awaitFinished(check);
        assertEquals(DqCheckRunStatus.SUCCESS, run.getStatus());
        assertEquals(1, run.getRulesTotal(), "the inactive rule is left out");
        assertEquals(1, run.getRulesPassed());
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /**
     * The run list opened on the catalog tab, the way coming back from the check editor opens it: by
     * the tab named in the URL. The content of the other tabs is not attached.
     */
    private DqCheckRunListView openList() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqCheckRunListView.class)
                .withQueryParameters(QueryParameters.of("tab", "catalogTab"))
                .navigate();
        DqCheckRunListView listView = UiTestUtils.getCurrentView();
        // the tab sheet attaches the content of the selected tab right before the response
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        assertCatalogShown("the tab named in the URL is selected");
        return listView;
    }

    /*
     * The catalog opens the editor with backward navigation, which asks the browser for the current
     * URL first: a headless test gets no answer, so the editor is navigated to directly.
     */

    private DqCheckDetailView createCheckInEditor() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqCheck.class)
                .newEntity()
                .withViewClass(DqCheckDetailView.class)
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    private DqCheckDetailView editCheckInEditor(DqCheck check) {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqCheck.class)
                .editEntity(check)
                .withViewClass(DqCheckDetailView.class)
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    private DqCheck createCheck(DqRule... rules) {
        DqCheck check = dataManager.create(DqCheck.class);
        check.setName("dq test check ui " + UUID.randomUUID());
        check.setRules(new HashSet<>(Arrays.asList(rules)));
        // tracked after the rules, so that cleanup removes the check first
        return testData.track(dataManager.save(check));
    }

    /** The check saved under the name, registered for cleanup. */
    private Optional<DqCheck> trackSaved(String name) {
        return dataManager.load(DqCheck.class)
                .query("select c from umida_DqCheck c where c.name = :name")
                .parameter("name", name)
                .optional()
                .map(testData::track);
    }

    private Set<UUID> ruleIds(DqCheck check) {
        return dataManager.load(DqCheck.class)
                .id(check.getId())
                .fetchPlan(fp -> fp.add("rules", FetchPlan.INSTANCE_NAME))
                .one()
                .getRules().stream()
                .map(DqRule::getId)
                .collect(Collectors.toSet());
    }

    private static <E> Set<UUID> ids(DataGrid<E> grid) {
        return grid.getGenericDataView().getItems()
                .map(item -> (UUID) EntityValues.getId(item))
                .collect(Collectors.toSet());
    }

    private static <E> E itemOf(DataGrid<E> grid, Object entity) {
        Object id = EntityValues.getId(entity);
        return grid.getGenericDataView().getItems()
                .filter(item -> id.equals(EntityValues.getId(item)))
                .findFirst()
                .orElseGet(() -> fail(entity + " is not shown in the grid"));
    }

    /** Waits for the run of the check to end and returns it as stored. */
    private DqCheckRun awaitFinished(DqCheck check) {
        long deadline = System.currentTimeMillis() + RUN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            List<DqCheckRun> runs = runsOf(check);
            if (runs.size() == 1 && runs.get(0).getStatus() != DqCheckRunStatus.RUNNING) {
                return runs.get(0);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for the check run");
            }
        }
        return fail("the run of check " + check.getCode() + " did not finish within " + RUN_TIMEOUT_MS + " ms");
    }

    private List<DqCheckRun> runsOf(DqCheck check) {
        return dataManager.load(DqCheckRun.class)
                .query("select r from umida_DqCheckRun r where r.check = :check")
                .parameter("check", check)
                .fetchPlan(FetchPlan.BASE)
                .list();
    }

    private static void assertCatalogShown(String message) {
        DqCheckRunListView listView = assertInstanceOf(DqCheckRunListView.class, UiTestUtils.getCurrentView(), message);
        JmixTabSheet tabSheet = UiTestUtils.getComponent(listView, "checkRunsTabSheet");
        assertEquals("catalogTab", tabSheet.getSelectedTab().getId().orElse(null), message);
    }

    /** The view of the dialog window open over the current view. */
    private static <V extends View<?>> V openedDialogView(Class<V> viewClass) {
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        return UI.getCurrent().getChildren()
                .flatMap(DqCheckUiTest::selfAndDescendants)
                .filter(viewClass::isInstance)
                .map(viewClass::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError(viewClass.getSimpleName() + " is not open"));
    }

    private static Stream<Component> selfAndDescendants(Component component) {
        return Stream.concat(Stream.of(component),
                component.getChildren().flatMap(DqCheckUiTest::selfAndDescendants));
    }

    @AfterEach
    void tearDown() {
        // issues and results first, then the runs: each step drops references the next one would
        // otherwise leave dangling; the checks the runs belong to go with the test data
        for (DqRule rule : List.of(activeRule, inactiveRule)) {
            dataManager.load(DqIssue.class)
                    .query("select i from umida_DqIssue i where i.rule = :rule")
                    .parameter("rule", rule)
                    .fetchPlan(FetchPlan.BASE)
                    .list()
                    .forEach(dataManager::remove);
            List<DqCheckRun> runs = new ArrayList<>(dataManager.load(DqCheckRun.class)
                    .query("select distinct r.checkRun from umida_DqCheckRunResult r where r.rule = :rule")
                    .parameter("rule", rule)
                    .fetchPlan(FetchPlan.BASE)
                    .list());
            for (DqCheckRun run : runs) {
                dataManager.load(DqCheckRunResult.class)
                        .query("select r from umida_DqCheckRunResult r where r.checkRun = :checkRun")
                        .parameter("checkRun", run)
                        .fetchPlan(FetchPlan.BASE)
                        .list()
                        .forEach(dataManager::remove);
                dataManager.remove(run);
            }
        }
        testData.cleanup();
    }
}
