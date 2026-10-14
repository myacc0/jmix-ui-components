package uz.kapitalbank.umida.dq;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.vaadin.flow.component.UI;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.kit.component.button.JmixButton;
import uz.kapitalbank.umida.component.slider.Slider;
import uz.kapitalbank.umida.view.dq.DqSqlEditorView;
import java.util.stream.Stream;
import com.vaadin.flow.component.Component;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.view.View;
import uz.kapitalbank.umida.view.dq.DqRuleGeneralInfoFragment;
import com.vaadin.flow.component.formlayout.FormLayout;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.Messages;
import io.jmix.flowui.ViewNavigators;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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
import static org.assertj.core.api.Assertions.tuple;

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
    void theCustomSqlFormAsksForTheDataSourceAndTwoQueries() {
        DqRuleCustomSqlDetailView view = openNewCustomSqlRule();

        assertThat(UiComponentUtils.findComponent(view, "dataSourceField")).isPresent();
        assertThat(UiComponentUtils.findComponent(view, "sqlField")).isPresent();
        assertThat(UiComponentUtils.findComponent(view, "violationsSqlField")).isPresent();
        for (String absent : List.of("dbSchemaField", "tableNameField", "columnNameField", "ruleTypeField",
                "samplesQueryColumnsField")) {
            assertThat(UiComponentUtils.findComponent(view, absent)).as(absent).isEmpty();
        }
    }

    @Test
    void theCustomSqlFormWritesTheQueryToTheConfig() {
        DqRuleCustomSqlDetailView view = openNewCustomSqlRule();
        CodeEditor sqlField = UiTestUtils.getComponent(view, "sqlField");

        CodeEditor violationsSqlField = UiTestUtils.getComponent(view, "violationsSqlField");

        sqlField.setValue("select 10, 4");
        violationsSqlField.setValue("select id from umida_dq_rule");

        DqRuleConfig config = JsonUtils.parseConfig(view.getEditedEntity().getRuleConfig(), DqRuleConfig.class,
                new ObjectMapper());
        assertThat(config.getSql()).isEqualTo("select 10, 4");
        assertThat(config.getViolationsSql()).isEqualTo("select id from umida_dq_rule");
        assertThat(config.getThreshold()).isEqualTo(100.0);
        assertThat(config.getSamplesQueryColumns()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sql", "violationsSql"})
    void theFullScreenEditorAppliesItsTextToTheForm(String query) {
        DqRuleCustomSqlDetailView view = openNewCustomSqlRule();
        CodeEditor field = UiTestUtils.getComponent(view, query + "Field");
        field.setValue("select 1, 2");

        JmixButton fullScreenButton = UiTestUtils.getComponent(view, query + "FullScreenButton");
        fullScreenButton.click();
        DqSqlEditorView editorView = openedDialogView(DqSqlEditorView.class);
        assertThat(editorView.getPageTitle()).isEqualTo(messages.getMessage(DqSqlEditorView.class, "ruleConfig." + query));
        CodeEditor editor = UiTestUtils.getComponent(editorView, "editor");
        assertThat(editor.getValue()).isEqualTo("select 1, 2");

        editor.setValue("select 3, 4");
        JmixButton applyButton = UiTestUtils.getComponent(editorView, "applyButton");
        applyButton.click();

        assertThat(field.getValue()).isEqualTo("select 3, 4");
    }

    @Test
    void aStoredCustomSqlRuleShowsItsQuery() {
        DqRule rule = createRule(DqRuleType.CUSTOM_SQL,
                "{\"sql\": \"select 10, 4\", \"violationsSql\": \"select id from umida_dq_rule\", \"threshold\": 90}");

        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqRule.class)
                .editEntity(rule)
                .withViewClass(DqRuleCustomSqlDetailView.class)
                .navigate();
        DqRuleCustomSqlDetailView view = UiTestUtils.getCurrentView();

        CodeEditor sqlField = UiTestUtils.getComponent(view, "sqlField");
        CodeEditor violationsSqlField = UiTestUtils.getComponent(view, "violationsSqlField");
        Slider thresholdField = UiTestUtils.getComponent(view, "thresholdField");
        assertThat(sqlField.getValue()).isEqualTo("select 10, 4");
        assertThat(violationsSqlField.getValue()).isEqualTo("select id from umida_dq_rule");
        assertThat(thresholdField.getValue()).isEqualTo(90);
    }

    @Test
    void theCrossSourceFormStartsWithOnePairThatCannotBeRemoved() {
        DqRuleCrossSourceDetailView view = openNewCrossSourceRule();

        for (String present : List.of("dataSourceField", "crossDataSourceField", "primarySqlField",
                "secondarySqlField", "addColumnPairButton")) {
            assertThat(UiComponentUtils.findComponent(view, present)).as(present).isPresent();
        }
        for (String absent : List.of("thresholdField", "samplesQueryColumnsField", "tableNameField")) {
            assertThat(UiComponentUtils.findComponent(view, absent)).as(absent).isEmpty();
        }

        List<HorizontalLayout> rows = columnPairRows(view);
        assertThat(rows).hasSize(1);
        assertThat(aliasField(rows.get(0), 0).getValue()).isEmpty();
        assertThat(aliasField(rows.get(0), 1).getValue()).isEmpty();
        assertThat(removeButton(rows.get(0)).isEnabled()).as("the last pair stays").isFalse();
    }

    @Test
    void theCrossSourceFormWritesItsPairsToTheConfig() {
        DqRuleCrossSourceDetailView view = openNewCrossSourceRule();
        JmixSelect<String> crossDataSourceField = UiTestUtils.getComponent(view, "crossDataSourceField");
        CodeEditor primarySqlField = UiTestUtils.getComponent(view, "primarySqlField");
        CodeEditor secondarySqlField = UiTestUtils.getComponent(view, "secondarySqlField");
        JmixButton addColumnPairButton = UiTestUtils.getComponent(view, "addColumnPairButton");

        crossDataSourceField.setValue("main");
        primarySqlField.setValue("select count(*) as cnt from a");
        secondarySqlField.setValue("select count(*) as total from b");
        aliasField(columnPairRows(view).get(0), 0).setValue("cnt");
        aliasField(columnPairRows(view).get(0), 1).setValue("total");
        addColumnPairButton.click();
        HorizontalLayout second = columnPairRows(view).get(1);
        aliasField(second, 0).setValue("sum1");
        aliasField(second, 1).setValue("sum2");

        DqRuleConfig config = crossSourceConfig(view);
        assertThat(config.getCrossDataSource()).isEqualTo("main");
        assertThat(config.getCrossPrimarySql()).isEqualTo("select count(*) as cnt from a");
        assertThat(config.getCrossDataSourceSql()).isEqualTo("select count(*) as total from b");
        assertThat(config.getCrossColumnPairs())
                .extracting(DqRuleConfig.CrossColumnPair::getPrimaryAlias, DqRuleConfig.CrossColumnPair::getSecondaryAlias)
                .containsExactly(tuple("cnt", "total"), tuple("sum1", "sum2"));
        assertThat(config.getThreshold()).as("the rule has no threshold to set").isNull();
        assertThat(config.getSamplesQueryColumns()).isNull();
        assertThat(columnPairRows(view)).allSatisfy(row -> assertThat(removeButton(row).isEnabled()).isTrue());

        removeButton(columnPairRows(view).get(0)).click();

        assertThat(columnPairRows(view)).hasSize(1);
        assertThat(removeButton(columnPairRows(view).get(0)).isEnabled()).isFalse();
        assertThat(crossSourceConfig(view).getCrossColumnPairs())
                .extracting(DqRuleConfig.CrossColumnPair::getPrimaryAlias)
                .containsExactly("sum1");
    }

    @Test
    void aStoredCrossSourceRuleShowsItsPairs() {
        DqRule rule = createRule(DqRuleType.CROSS_SOURCE_AGGREGATED,
                "{\"crossPrimarySql\": \"select 1 as a, 2 as b\", \"crossDataSource\": \"main\","
                        + " \"crossDataSourceSql\": \"select 1 as x, 2 as y\", \"crossColumnPairs\": ["
                        + "{\"primaryAlias\": \"a\", \"secondaryAlias\": \"x\"},"
                        + " {\"primaryAlias\": \"b\", \"secondaryAlias\": \"y\"}]}");

        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqRule.class)
                .editEntity(rule)
                .withViewClass(DqRuleCrossSourceDetailView.class)
                .navigate();
        DqRuleCrossSourceDetailView view = UiTestUtils.getCurrentView();

        JmixSelect<String> crossDataSourceField = UiTestUtils.getComponent(view, "crossDataSourceField");
        CodeEditor primarySqlField = UiTestUtils.getComponent(view, "primarySqlField");
        assertThat(crossDataSourceField.getValue()).isEqualTo("main");
        assertThat(primarySqlField.getValue()).isEqualTo("select 1 as a, 2 as b");
        List<HorizontalLayout> rows = columnPairRows(view);
        assertThat(rows).hasSize(2);
        assertThat(aliasField(rows.get(1), 0).getValue()).isEqualTo("b");
        assertThat(aliasField(rows.get(1), 1).getValue()).isEqualTo("y");
        assertThat(view.hasUnsavedChanges()).as("showing the rule changes nothing").isFalse();
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

    private DqRuleCustomSqlDetailView openNewCustomSqlRule() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqRule.class)
                .newEntity()
                .withViewClass(DqRuleCustomSqlDetailView.class)
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    private DqRuleCrossSourceDetailView openNewCrossSourceRule() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqRule.class)
                .newEntity()
                .withViewClass(DqRuleCrossSourceDetailView.class)
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    /** The rows of alias pairs, which the cross-source form builds itself. */
    private static List<HorizontalLayout> columnPairRows(View<?> view) {
        VerticalLayout box = UiTestUtils.getComponent(view, "columnPairRowsBox");
        return box.getChildren().map(HorizontalLayout.class::cast).toList();
    }

    /** The alias field of the first (0) or the second (1) source in a row. */
    @SuppressWarnings("unchecked")
    private static TypedTextField<String> aliasField(HorizontalLayout row, int source) {
        return row.getChildren()
                .filter(TypedTextField.class::isInstance)
                .map(c -> (TypedTextField<String>) c)
                .toList()
                .get(source);
    }

    private static JmixButton removeButton(HorizontalLayout row) {
        return row.getChildren()
                .filter(JmixButton.class::isInstance)
                .map(JmixButton.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static DqRuleConfig crossSourceConfig(DqRuleCrossSourceDetailView view) {
        return JsonUtils.parseConfig(view.getEditedEntity().getRuleConfig(), DqRuleConfig.class, new ObjectMapper());
    }

    /** The view of the dialog window open over the current view. */
    private static <V extends View<?>> V openedDialogView(Class<V> viewClass) {
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        return UI.getCurrent().getChildren()
                .flatMap(DqRuleDetailUiTest::selfAndDescendants)
                .filter(viewClass::isInstance)
                .map(viewClass::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError(viewClass.getSimpleName() + " is not open"));
    }

    private static Stream<Component> selfAndDescendants(Component component) {
        return Stream.concat(Stream.of(component),
                component.getChildren().flatMap(DqRuleDetailUiTest::selfAndDescendants));
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
