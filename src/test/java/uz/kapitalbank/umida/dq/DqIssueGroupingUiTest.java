package uz.kapitalbank.umida.dq;

import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.pagination.SimplePagination;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.view.ViewControllerUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.dto.dq.DqIssueTreeItem;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.dq.DqRuleGroup;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.dq.DqIssueGrouping;
import uz.kapitalbank.umida.enums.dq.DqIssueStatus;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;
import uz.kapitalbank.umida.view.dq.DqIssueListView;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The issue list shows a paged list by default and, once a grouping is chosen, a tree of the issues
 * under their rule group, domain or assignee, with the number of issues of each group, the issues
 * without a value last, and the filters of the paged list applied.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqIssueGroupingUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    @Autowired
    Messages messages;

    private DqTestData testData;
    private DqRuleGroup group;
    private OrgStructureSubdivision assignee;
    private DqIssue groupedIssue;
    private DqIssue otherGroupedIssue;
    private DqIssue ungroupedIssue;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        DictDataDomain domain = testData.createDomain();

        OrgStructureSubdivision newAssignee = dataManager.create(OrgStructureSubdivision.class);
        newAssignee.setId(UUID.randomUUID().toString());
        newAssignee.setName("DQ grouping " + suffix());
        assignee = testData.track(dataManager.save(newAssignee));

        DqRuleGroup newGroup = dataManager.create(DqRuleGroup.class);
        newGroup.setName("DQ grouping " + suffix());
        group = testData.track(dataManager.save(newGroup));

        DqRule groupedRule = testData.createRule(domain, "grouping", DqRuleType.NOT_NULL, "short_name", "{}", r -> {
            r.setGroup(group);
            r.setAssignee(assignee);
        });
        DqRule ungroupedRule = testData.createRule(domain, "grouping none", DqRuleType.NOT_NULL, "short_name", "{}");

        groupedIssue = createIssue(groupedRule);
        otherGroupedIssue = createIssue(groupedRule);
        ungroupedIssue = createIssue(ungroupedRule);
    }

    @Test
    void theListIsPagedByDefaultAndOffersEveryGrouping() {
        DqIssueListView view = openList();
        JmixSelect<DqIssueGrouping> groupingField = UiTestUtils.getComponent(view, "groupingField");

        assertEquals(DqIssueGrouping.ALL, groupingField.getValue());
        assertEquals(List.of(DqIssueGrouping.values()), groupingField.getListDataView().getItems().toList(),
                "admin may see every attribute the issues are grouped by");
        assertTrue(listGrid(view).isVisible());
        assertTrue(this.<SimplePagination>component(view, "pagination").isVisible());
        assertFalse(tree(view).isVisible());
    }

    @Test
    void groupingByRuleGroupShowsTheIssuesUnderTheirGroupWithTheCountAndNoPages() {
        DqIssueListView view = openList();
        group(view, DqIssueGrouping.GROUP);

        TreeDataGrid<DqIssueTreeItem> tree = tree(view);
        assertTrue(tree.isVisible());
        assertFalse(listGrid(view).isVisible());
        assertFalse(this.<SimplePagination>component(view, "pagination").isVisible(), "the tree has no pages");

        DqIssueTreeItem first = treeItem(tree, groupedIssue.getId());
        DqIssueTreeItem second = treeItem(tree, otherGroupedIssue.getId());
        assertSame(first.getParent(), second.getParent(), "the issues of a rule share its group");
        assertEquals(group.getName() + " (2)", first.getParent().getCaption());
        assertEquals(2, first.getParent().getCount());
        assertEquals(groupedIssue.getCode(), first.getCaption());

        DqIssueTreeItem noGroup = treeItem(tree, ungroupedIssue.getId()).getParent();
        assertTrue(noGroup.getCaption().startsWith(
                messages.getMessage(DqIssueGrouping.class, "DqIssueGrouping.GROUP.none")));
        List<DqIssueTreeItem> groups = tree.getItems().getItems().stream().filter(DqIssueTreeItem::isGroup).toList();
        assertSame(noGroup, groups.get(groups.size() - 1), "the issues without a group come last");

        assertFalse(tree.getColumnByKey("ruleGroup").isVisible(), "the group row shows the group");
        assertTrue(tree.getColumnByKey("ruleDomain").isVisible());
    }

    @Test
    void theButtonsActOnTheIssueRowsOfTheTree() {
        DqIssueListView view = openList();
        group(view, DqIssueGrouping.ASSIGNEE);
        TreeDataGrid<DqIssueTreeItem> tree = tree(view);
        JmixButton readButton = component(view, "readButton");
        JmixButton violationsButton = component(view, "violationsButton");
        JmixButton closeIssueButton = component(view, "closeIssueButton");

        assertSame(tree.getAction("readAction"), readButton.getAction());
        assertEquals("", readButton.getText(), "the button keeps showing the icon only");

        DqIssueTreeItem issueRow = treeItem(tree, groupedIssue.getId());
        assertEquals(assignee.getName() + " (2)", issueRow.getParent().getCaption());

        tree.select(issueRow.getParent());
        assertFalse(readButton.isEnabled(), "a group row is no issue");
        assertFalse(violationsButton.isEnabled());

        tree.select(issueRow);
        assertTrue(readButton.isEnabled());
        assertFalse(violationsButton.isEnabled(), "the issue has no failed check with violating rows");
        assertFalse(closeIssueButton.isVisible(), "admin is not an employee of the assignee subdivision");

        group(view, DqIssueGrouping.ALL);
        assertTrue(listGrid(view).isVisible());
        assertSame(listGrid(view).getAction("readAction"), readButton.getAction());
    }

    @Test
    void theTreeHoldsWhatTheFiltersOfThePagedListSelect() {
        DqIssueListView view = openList();
        CollectionLoader<DqIssue> loader = ViewControllerUtils.getViewData(view).getLoader("dqIssuesDl");
        // stands for a column header filter, which narrows the loader the same way
        loader.setCondition(PropertyCondition.equal("code", groupedIssue.getCode()));

        group(view, DqIssueGrouping.DOMAIN);

        List<DqIssueTreeItem> items = List.copyOf(tree(view).getItems().getItems());
        assertEquals(2, items.size(), "one domain with one issue: " + items);
        assertEquals(groupedIssue.getId(), items.get(1).getIssue().getId());
        assertEquals(1, items.get(0).getCount());
    }

    @AfterEach
    void tearDown() {
        testData.cleanup();
    }

    private DqIssue createIssue(DqRule rule) {
        DqIssue issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setStatus(DqIssueStatus.OPEN);
        return testData.track(dataManager.save(issue));
    }

    private DqIssueListView openList() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqIssueListView.class).navigate();
        return UiTestUtils.getCurrentView();
    }

    private void group(DqIssueListView view, DqIssueGrouping grouping) {
        this.<JmixSelect<DqIssueGrouping>>component(view, "groupingField").setValue(grouping);
    }

    private DataGrid<DqIssue> listGrid(DqIssueListView view) {
        return component(view, "dqIssuesDataGrid");
    }

    private TreeDataGrid<DqIssueTreeItem> tree(DqIssueListView view) {
        return component(view, "dqIssuesTreeDataGrid");
    }

    private <T> T component(DqIssueListView view, String id) {
        return UiTestUtils.getComponent(view, id);
    }

    private static DqIssueTreeItem treeItem(TreeDataGrid<DqIssueTreeItem> tree, UUID issueId) {
        DqIssueTreeItem item = tree.getItems() != null ? tree.getItems().getItem(issueId) : null;
        if (item == null) {
            fail("issue " + issueId + " is not shown in the tree");
        }
        return item;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
