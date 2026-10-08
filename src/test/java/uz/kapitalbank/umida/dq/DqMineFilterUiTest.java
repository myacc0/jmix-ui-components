package uz.kapitalbank.umida.dq;

import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.data.grid.TreeDataGridItems;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.dq.DqRuleGroup;
import uz.kapitalbank.umida.enums.dq.DqDimension;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSeverity;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.view.dq.DqRuleGroupListView;
import uz.kapitalbank.umida.view.dq.DqRuleListView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The "mine" checkbox of the rule and rule group lists keeps only what the current user created. */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqMineFilterUiTest {

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;

    private final List<Object> cleanup = new ArrayList<>();

    @Test
    void theRuleListShowsOnlyTheRulesOfTheCurrentUser() {
        DqRule mine = createRule(null);
        DqRule others = createRule("someone-else");

        viewNavigators.view(UiTestUtils.getCurrentView(), DqRuleListView.class).navigate();
        DqRuleListView view = UiTestUtils.getCurrentView();
        DataGrid<DqRule> grid = UiTestUtils.getComponent(view, "dqRulesDataGrid");
        JmixCheckbox mineField = UiTestUtils.getComponent(view, "mineField");

        assertThat(mineField.getValue()).isFalse();
        assertThat(ids(grid.getGenericDataView().getItems().toList())).contains(mine.getId(), others.getId());

        mineField.setValue(true);
        assertThat(ids(grid.getGenericDataView().getItems().toList()))
                .contains(mine.getId())
                .doesNotContain(others.getId());

        mineField.setValue(false);
        assertThat(ids(grid.getGenericDataView().getItems().toList())).contains(mine.getId(), others.getId());
    }

    @Test
    void theRuleGroupListShowsOnlyTheGroupsOfTheCurrentUser() {
        DqRuleGroup mine = createGroup(null);
        DqRuleGroup others = createGroup("someone-else");

        viewNavigators.view(UiTestUtils.getCurrentView(), DqRuleGroupListView.class).navigate();
        DqRuleGroupListView view = UiTestUtils.getCurrentView();
        TreeDataGrid<DqRuleGroup> grid = UiTestUtils.getComponent(view, "dqRuleGroupsDataGrid");
        JmixCheckbox mineField = UiTestUtils.getComponent(view, "mineField");

        assertThat(groupIds(grid)).contains(mine.getId(), others.getId());

        mineField.setValue(true);
        assertThat(groupIds(grid)).contains(mine.getId()).doesNotContain(others.getId());

        mineField.setValue(false);
        assertThat(groupIds(grid)).contains(mine.getId(), others.getId());
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private List<UUID> ids(List<DqRule> rules) {
        return rules.stream().map(DqRule::getId).toList();
    }

    private List<UUID> groupIds(TreeDataGrid<DqRuleGroup> grid) {
        TreeDataGridItems<DqRuleGroup> items = grid.getItems();
        return items == null ? List.of() : items.getItems().stream().map(DqRuleGroup::getId).toList();
    }

    /**
     * A rule created by the current user, handed over to {@code author} when given: the audit sets
     * {@code createdBy} on the first save only, so a second save can put another author in place.
     */
    private DqRule createRule(String author) {
        DqRule rule = dataManager.create(DqRule.class);
        rule.setName("dq mine filter ui test " + UUID.randomUUID());
        rule.setDataSource("main");
        rule.setTableName("umida_dq_rule");
        rule.setColumnName("name");
        rule.setDimension(DqDimension.COMPLETENESS);
        rule.setRuleType(DqRuleType.NOT_NULL);
        rule.setRuleConfig("{}");
        rule.setSeverity(DqSeverity.LOW);
        DqRule saved = dataManager.save(rule);
        if (author != null) {
            saved.setCreatedBy(author);
            saved = dataManager.save(saved);
        }
        cleanup.add(saved);
        return saved;
    }

    private DqRuleGroup createGroup(String author) {
        DqRuleGroup group = dataManager.create(DqRuleGroup.class);
        group.setName("dq mine filter ui test " + UUID.randomUUID());
        DqRuleGroup saved = dataManager.save(group);
        if (author != null) {
            saved.setCreatedBy(author);
            saved = dataManager.save(saved);
        }
        cleanup.add(saved);
        return saved;
    }

    @AfterEach
    void tearDown() {
        cleanup.forEach(entity -> dataManager.remove(Id.of(entity)));
        cleanup.clear();
    }
}
