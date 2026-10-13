package uz.kapitalbank.umida.dq;

import com.vaadin.flow.component.Component;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.view.View;
import uz.kapitalbank.umida.view.dq.DqRuleGeneralInfoFragment;
import com.vaadin.flow.component.formlayout.FormLayout;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.Messages;
import io.jmix.flowui.ViewNavigators;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.dto.dq.DqRuleConfig;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.dq.DqDimension;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSeverity;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.utils.JsonUtils;
import uz.kapitalbank.umida.view.dq.DqRuleCrossSourceDetailView;
import uz.kapitalbank.umida.view.dq.DqRuleCustomSqlDetailView;
import uz.kapitalbank.umida.view.dq.DqRuleDetailView;
import uz.kapitalbank.umida.view.dq.DqRuleListView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule editor: the code shows only once generated and never edits, the severity offers its
 * resolution period and proposes it.
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

    private final List<Object> cleanup = new ArrayList<>();

    @Test
    void aNewRuleHasNoCodeToShow() {
        DqRuleDetailView view = openNewRule();

        FormLayout.FormItem codeFormItem = generalInfoComponent(view, "codeFormItem");
        assertThat(codeFormItem.isVisible()).isFalse();
    }

    @Test
    void severityOptionsShowTheirResolutionPeriod() {
        DqRuleDetailView view = openNewRule();
        JmixSelect<DqSeverity> severityField = generalInfoComponent(view, "severityField");

        for (DqSeverity severity : DqSeverity.values()) {
            assertThat(severityField.getItemLabelGenerator().apply(severity))
                    .startsWith(messages.getMessage(severity) + " (")
                    .contains(String.valueOf(severity.getDayCost()));
        }
    }

    @Test
    void pickingASeverityProposesItsResolutionPeriod() {
        DqRuleDetailView view = openNewRule();
        JmixSelect<DqSeverity> severityField = generalInfoComponent(view, "severityField");
        JmixIntegerField dueDaysField = generalInfoComponent(view, "dueDaysField");

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
        JmixIntegerField dueDaysField = generalInfoComponent(view, "dueDaysField");

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
        EntityPicker<OrgStructureSubdivision> assigneeField = generalInfoComponent(view, "assigneeField");

        assertThat(assigneeField.isRequired()).isTrue();
        assertThat(assigneeField.getActions()).hasSize(2);
    }

    @Test
    void aStoredRuleShowsItsCode() {
        DqRule rule = createRule();

        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqRule.class)
                .editEntity(rule)
                .withViewClass(DqRuleDetailView.class)
                .navigate();
        DqRuleDetailView view = UiTestUtils.getCurrentView();

        FormLayout.FormItem codeFormItem = generalInfoComponent(view, "codeFormItem");
        TypedTextField<String> codeField = generalInfoComponent(view, "codeField");
        assertThat(codeFormItem.isVisible()).isTrue();
        assertThat(codeField.isReadOnly()).isTrue();
        assertThat(codeField.isRequired()).isFalse();
        assertThat(codeField.getValue()).isEqualTo(rule.getCode()).startsWith("DQR-");

        // a stored period is kept, not replaced by the one of the severity
        JmixIntegerField dueDaysField = generalInfoComponent(view, "dueDaysField");
        assertThat(dueDaysField.getValue()).isEqualTo(7);

        // opening the rule changes nothing in it
        assertThat(view.hasUnsavedChanges()).isFalse();
    }

    @Test
    void theRuleListShowsTheCode() {
        DqRule rule = createRule();

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

    @Test
    void theStandardFormDoesNotOfferRuleTypesWithFormsOfTheirOwn() {
        DqRuleDetailView view = openNewRule();
        JmixSelect<DqRuleType> ruleTypeField = UiTestUtils.getComponent(view, "ruleTypeField");

        assertThat(ruleTypeField.getListDataView().getItems())
                .contains(DqRuleType.NOT_NULL, DqRuleType.REFERENTIAL)
                .doesNotContain(DqRuleType.CUSTOM_SQL, DqRuleType.CROSS_SOURCE_AGGREGATED);
    }

    @Test
    void theCreateMenuOpensTheStandardForm() {
        assertThat(createFromList("createAction")).isInstanceOf(DqRuleDetailView.class);
    }

    @Test
    void theCreateMenuOpensTheCustomSqlForm() {
        View<?> view = createFromList("createCustomSqlAction");

        assertThat(view).isInstanceOf(DqRuleCustomSqlDetailView.class);
        assertThat(((DqRuleCustomSqlDetailView) view).getEditedEntity().getRuleType())
                .isEqualTo(DqRuleType.CUSTOM_SQL);
        EntityPicker<OrgStructureSubdivision> assigneeField = generalInfoComponent(view, "assigneeField");
        assertThat(assigneeField.isRequired()).isTrue();
    }

    @Test
    void theCreateMenuOpensTheCrossSourceForm() {
        View<?> view = createFromList("createCrossSourceAction");

        assertThat(view).isInstanceOf(DqRuleCrossSourceDetailView.class);
        assertThat(((DqRuleCrossSourceDetailView) view).getEditedEntity().getRuleType())
                .isEqualTo(DqRuleType.CROSS_SOURCE_AGGREGATED);
        EntityPicker<OrgStructureSubdivision> assigneeField = generalInfoComponent(view, "assigneeField");
        assertThat(assigneeField.isRequired()).isTrue();
    }

    @Test
    void aRuleOpensFromTheListInTheFormOfItsType() {
        assertThat(editFromList(createRule())).isInstanceOf(DqRuleDetailView.class);
        assertThat(editFromList(createRule(DqRuleType.CUSTOM_SQL, "{}")))
                .isInstanceOf(DqRuleCustomSqlDetailView.class);
        assertThat(editFromList(createRule(DqRuleType.CROSS_SOURCE_AGGREGATED, "{}")))
                .isInstanceOf(DqRuleCrossSourceDetailView.class);
    }

    @Test
    void theReferenceFieldsShowOnlyForAReferentialRule() {
        DqRuleDetailView view = openNewRule();
        VerticalLayout referentialBox = UiTestUtils.getComponent(view, "referentialBox");
        JmixSelect<DqRuleType> ruleTypeField = UiTestUtils.getComponent(view, "ruleTypeField");

        assertThat(referentialBox.isVisible()).isFalse();
        ruleTypeField.setValue(DqRuleType.REFERENTIAL);
        assertThat(referentialBox.isVisible()).isTrue();
        ruleTypeField.setValue(DqRuleType.NOT_NULL);
        assertThat(referentialBox.isVisible()).isFalse();
    }

    @Test
    void pickingTheReferenceWritesItToTheConfig() {
        DqRuleDetailView view = openNewRule();
        JmixSelect<String> dataSourceField = UiTestUtils.getComponent(view, "dataSourceField");
        JmixSelect<DqRuleType> ruleTypeField = UiTestUtils.getComponent(view, "ruleTypeField");
        JmixComboBox<String> refSchemaField = UiTestUtils.getComponent(view, "refSchemaField");
        JmixComboBox<String> refTableField = UiTestUtils.getComponent(view, "refTableField");
        JmixComboBox<String> refColumnField = UiTestUtils.getComponent(view, "refColumnField");
        JmixCheckbox allowNullField = UiTestUtils.getComponent(view, "allowNullField");

        dataSourceField.setValue("main");
        ruleTypeField.setValue(DqRuleType.REFERENTIAL);
        // the reference location is picked from the rule's data source, step by step
        assertThat(refSchemaField.getGenericDataView().getItems()).contains("public");
        refSchemaField.setValue("public");
        assertThat(refTableField.getGenericDataView().getItems()).contains("umida_dq_rule");
        refTableField.setValue("umida_dq_rule");
        assertThat(refColumnField.getGenericDataView().getItems()).contains("code");
        refColumnField.setValue("code");
        allowNullField.setValue(false);

        DqRuleConfig config = JsonUtils.parseConfig(view.getEditedEntity().getRuleConfig(), DqRuleConfig.class,
                new ObjectMapper());
        assertThat(config).isNotNull();
        assertThat(config.getRefSchema()).isEqualTo("public");
        assertThat(config.getRefTable()).isEqualTo("umida_dq_rule");
        assertThat(config.getRefColumn()).isEqualTo("code");
        assertThat(config.getAllowNull()).isFalse();
    }

    @Test
    void aStoredReferentialRuleShowsItsReference() {
        DqRule rule = createRule(DqRuleType.REFERENTIAL, "{\"refSchema\": \"public\","
                + " \"refTable\": \"umida_dq_rule\", \"refColumn\": \"code\", \"allowNull\": false}");

        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqRule.class)
                .editEntity(rule)
                .withViewClass(DqRuleDetailView.class)
                .navigate();
        DqRuleDetailView view = UiTestUtils.getCurrentView();

        VerticalLayout referentialBox = UiTestUtils.getComponent(view, "referentialBox");
        JmixComboBox<String> refSchemaField = UiTestUtils.getComponent(view, "refSchemaField");
        JmixComboBox<String> refTableField = UiTestUtils.getComponent(view, "refTableField");
        JmixComboBox<String> refColumnField = UiTestUtils.getComponent(view, "refColumnField");
        JmixCheckbox allowNullField = UiTestUtils.getComponent(view, "allowNullField");
        assertThat(referentialBox.isVisible()).isTrue();
        assertThat(refSchemaField.getValue()).isEqualTo("public");
        assertThat(refTableField.getValue()).isEqualTo("umida_dq_rule");
        assertThat(refColumnField.getValue()).isEqualTo("code");
        assertThat(refColumnField.getGenericDataView().getItems()).contains("code", "name");
        assertThat(allowNullField.getValue()).isFalse();

        // opening the rule changes nothing in it
        assertThat(view.hasUnsavedChanges()).isFalse();
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

    private View<?> createFromList(String actionId) {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqRuleListView.class).navigate();
        DataGrid<DqRule> grid = UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "dqRulesDataGrid");
        grid.getAction(actionId).actionPerform(grid);
        return UiTestUtils.getCurrentView();
    }

    private View<?> editFromList(DqRule rule) {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqRuleListView.class).navigate();
        DataGrid<DqRule> grid = UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "dqRulesDataGrid");
        grid.select(rule);
        grid.getAction("editAction").actionPerform(grid);
        return UiTestUtils.getCurrentView();
    }

    private DqRule createRule() {
        return createRule(DqRuleType.NOT_NULL, "{}");
    }

    private DqRule createRule(DqRuleType ruleType, String ruleConfig) {
        DqRule rule = dataManager.create(DqRule.class);
        rule.setName("dq rule detail ui test " + UUID.randomUUID());
        rule.setDataSource("main");
        rule.setTableName("umida_dq_rule");
        rule.setColumnName("name");
        rule.setDimension(DqDimension.COMPLETENESS);
        rule.setRuleType(ruleType);
        rule.setRuleConfig(ruleConfig);
        rule.setSeverity(DqSeverity.LOW);
        rule.setDueDays(7);
        DqRule saved = dataManager.save(rule);
        cleanup.add(0, saved);
        return saved;
    }

    @AfterEach
    void tearDown() {
        cleanup.forEach(entity -> dataManager.remove(Id.of(entity)));
        cleanup.clear();
    }

    /** An input of the "general information" block, which lives in a fragment of the rule form. */
    @SuppressWarnings("unchecked")
    private static <T extends Component> T generalInfoComponent(View<?> view, String id) {
        DqRuleGeneralInfoFragment fragment = UiTestUtils.getComponent(view, "generalInfoFragment");
        return (T) UiComponentUtils.getComponent(fragment, id);
    }
}
