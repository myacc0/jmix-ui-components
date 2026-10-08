package uz.kapitalbank.umida.dq;

import com.vaadin.flow.component.formlayout.FormLayout;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.Messages;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.dq.DqDimension;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSeverity;
import uz.kapitalbank.umida.security.FullAccessRole;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.view.dq.DqRuleDetailView;
import uz.kapitalbank.umida.view.dq.DqRuleListView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule editor: the code shows only once generated and never edits, the severity offers its
 * resolution period and proposes it, the owner is one of the author's subdivisions.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqRuleDetailUiTest {

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    Messages messages;
    @Autowired
    SystemAuthenticator systemAuthenticator;

    private final List<Object> cleanup = new ArrayList<>();

    @Test
    void aNewRuleHasNoCodeToShow() {
        DqRuleDetailView view = openNewRule();

        FormLayout.FormItem codeFormItem = UiTestUtils.getComponent(view, "codeFormItem");
        assertThat(codeFormItem.isVisible()).isFalse();
    }

    @Test
    void severityOptionsShowTheirResolutionPeriod() {
        DqRuleDetailView view = openNewRule();
        JmixSelect<DqSeverity> severityField = UiTestUtils.getComponent(view, "severityField");

        for (DqSeverity severity : DqSeverity.values()) {
            assertThat(severityField.getItemLabelGenerator().apply(severity))
                    .startsWith(messages.getMessage(severity) + " (")
                    .contains(String.valueOf(severity.getDayCost()));
        }
    }

    @Test
    void pickingASeverityProposesItsResolutionPeriod() {
        DqRuleDetailView view = openNewRule();
        JmixSelect<DqSeverity> severityField = UiTestUtils.getComponent(view, "severityField");
        JmixIntegerField dueDaysField = UiTestUtils.getComponent(view, "dueDaysField");

        severityField.setValue(DqSeverity.MEDIUM);
        assertThat(dueDaysField.getValue()).isEqualTo(DqSeverity.MEDIUM.getDayCost());

        // the user corrects it, and picking another severity proposes again
        dueDaysField.setValue(30);
        assertThat(view.getEditedEntity().getDueDays()).isEqualTo(30);
        severityField.setValue(DqSeverity.CRITICAL);
        assertThat(dueDaysField.getValue()).isEqualTo(DqSeverity.CRITICAL.getDayCost());
    }

    @Test
    void theResolutionPeriodIsBoundedAndRequired() {
        DqRuleDetailView view = openNewRule();
        JmixIntegerField dueDaysField = UiTestUtils.getComponent(view, "dueDaysField");

        assertThat(dueDaysField.isRequired()).isTrue();
        assertThat(dueDaysField.getMin()).isEqualTo(1);
        assertThat(dueDaysField.getMax()).isEqualTo(365);

        dueDaysField.setValue(366);
        assertThat(dueDaysField.isInvalid()).isTrue();
        dueDaysField.setValue(0);
        assertThat(dueDaysField.isInvalid()).isTrue();
        dueDaysField.setValue(365);
        assertThat(dueDaysField.isInvalid()).isFalse();
    }

    @Test
    void theAssigneeIsARequiredSubdivisionPicker() {
        DqRuleDetailView view = openNewRule();
        EntityPicker<OrgStructureSubdivision> assigneeField = UiTestUtils.getComponent(view, "assigneeField");

        assertThat(assigneeField.isRequired()).isTrue();
        assertThat(assigneeField.getActions()).hasSize(2);
    }

    @Test
    void aUserWithoutAnEmployeeHasNoOwnerToPick() {
        DqRuleDetailView view = openNewRule();
        JmixSelect<OrgStructureSubdivision> ownerField = UiTestUtils.getComponent(view, "ownerField");

        assertThat(ownerField.getGenericDataView().getItems()).isEmpty();
        assertThat(ownerField.getValue()).isNull();
        assertThat(ownerField.isReadOnly()).isFalse();
    }

    @Test
    void aNewRuleBelongsToTheFirstSubdivisionOfItsAuthor() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        OrgStructureSubdivision beta = createSubdivision("Beta " + suffix);
        OrgStructureSubdivision alpha = createSubdivision("Alpha " + suffix);
        OrgStructureEmployee employee = createEmployee();
        createPosition(beta, employee);
        createPosition(alpha, employee);
        User user = createUser(employee);

        DqRuleDetailView view = systemAuthenticator.withUser(user.getUsername(), this::openNewRule);
        JmixSelect<OrgStructureSubdivision> ownerField = UiTestUtils.getComponent(view, "ownerField");

        assertThat(ownerField.getGenericDataView().getItems()).containsExactly(alpha, beta);
        assertThat(ownerField.getValue()).isEqualTo(alpha);
        assertThat(view.getEditedEntity().getOwner()).isEqualTo(alpha);

        ownerField.setValue(beta);
        assertThat(view.getEditedEntity().getOwner()).isEqualTo(beta);
    }

    @Test
    void aStoredRuleShowsItsCodeAndOwnerReadOnly() {
        OrgStructureSubdivision owner = createSubdivision("Owner " + UUID.randomUUID());
        DqRule rule = createRule(owner);

        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqRule.class)
                .editEntity(rule)
                .withViewClass(DqRuleDetailView.class)
                .navigate();
        DqRuleDetailView view = UiTestUtils.getCurrentView();

        FormLayout.FormItem codeFormItem = UiTestUtils.getComponent(view, "codeFormItem");
        TypedTextField<String> codeField = UiTestUtils.getComponent(view, "codeField");
        assertThat(codeFormItem.isVisible()).isTrue();
        assertThat(codeField.isReadOnly()).isTrue();
        assertThat(codeField.isRequired()).isFalse();
        assertThat(codeField.getValue()).isEqualTo(rule.getCode()).startsWith("DQR-");

        JmixSelect<OrgStructureSubdivision> ownerField = UiTestUtils.getComponent(view, "ownerField");
        assertThat(ownerField.isReadOnly()).isTrue();
        assertThat(ownerField.getValue()).isEqualTo(owner);

        // a stored period is kept, not replaced by the one of the severity
        JmixIntegerField dueDaysField = UiTestUtils.getComponent(view, "dueDaysField");
        assertThat(dueDaysField.getValue()).isEqualTo(7);

        // opening the rule changes nothing in it
        assertThat(view.getEditedEntity().getOwner()).isEqualTo(owner);
        assertThat(view.hasUnsavedChanges()).isFalse();
    }

    @Test
    void theRuleListShowsTheCode() {
        DqRule rule = createRule(null);

        viewNavigators.view(UiTestUtils.getCurrentView(), DqRuleListView.class).navigate();
        DqRuleListView view = UiTestUtils.getCurrentView();
        DataGrid<DqRule> grid = UiTestUtils.getComponent(view, "dqRulesDataGrid");

        assertThat(grid.getColumnByKey("code")).isNotNull();
        assertThat(grid.getColumns().get(0).getKey()).isEqualTo("code");
        assertThat(grid.getGenericDataView().getItems()
                .filter(item -> item.getId().equals(rule.getId()))
                .map(DqRule::getCode))
                .containsExactly(rule.getCode());
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private DqRuleDetailView openNewRule() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqRule.class)
                .newEntity()
                .withViewClass(DqRuleDetailView.class)
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    private DqRule createRule(OrgStructureSubdivision owner) {
        DqRule rule = dataManager.create(DqRule.class);
        rule.setName("dq rule detail ui test " + UUID.randomUUID());
        rule.setDataSource("main");
        rule.setTableName("umida_dq_rule");
        rule.setColumnName("name");
        rule.setDimension(DqDimension.COMPLETENESS);
        rule.setRuleType(DqRuleType.NOT_NULL);
        rule.setRuleConfig("{}");
        rule.setSeverity(DqSeverity.LOW);
        rule.setDueDays(7);
        rule.setOwner(owner);
        DqRule saved = dataManager.save(rule);
        cleanup.add(0, saved);
        return saved;
    }

    private OrgStructureSubdivision createSubdivision(String name) {
        OrgStructureSubdivision subdivision = dataManager.create(OrgStructureSubdivision.class);
        subdivision.setId(UUID.randomUUID().toString());
        subdivision.setName(name);
        OrgStructureSubdivision saved = dataManager.save(subdivision);
        cleanup.add(saved);
        return saved;
    }

    private OrgStructureEmployee createEmployee() {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("Dq Rule Detail Test " + UUID.randomUUID());
        OrgStructureEmployee saved = dataManager.save(employee);
        cleanup.add(saved);
        return saved;
    }

    private void createPosition(OrgStructureSubdivision subdivision, OrgStructureEmployee employee) {
        OrgStructurePosition position = dataManager.create(OrgStructurePosition.class);
        position.setId(UUID.randomUUID().toString());
        position.setSubdivision(subdivision);
        position.setEmployee(employee);
        cleanup.add(0, dataManager.save(position));
    }

    /** A user allowed to open the editor, linked to the given employee. */
    private User createUser(OrgStructureEmployee employee) {
        User user = dataManager.create(User.class);
        user.setUsername("dq-rule-detail-test-" + UUID.randomUUID());
        user.setEmployee(employee);
        User saved = dataManager.save(user);
        cleanup.add(0, saved);

        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(saved.getUsername());
        assignment.setRoleCode(FullAccessRole.CODE);
        assignment.setRoleType(RoleAssignmentRoleType.RESOURCE);
        cleanup.add(0, dataManager.save(assignment));
        return saved;
    }

    @AfterEach
    void tearDown() {
        cleanup.forEach(entity -> dataManager.remove(Id.of(entity)));
        cleanup.clear();
    }
}
