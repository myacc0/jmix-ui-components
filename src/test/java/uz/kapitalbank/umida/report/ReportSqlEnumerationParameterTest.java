package uz.kapitalbank.umida.report;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import io.jmix.core.Metadata;
import io.jmix.core.SaveContext;
import io.jmix.core.Stores;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.OpenedDialogWindows;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.util.OperationResult;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.sys.ViewControllersConfiguration;
import io.jmix.flowui.sys.ViewControllersConfigurationSorter;
import io.jmix.flowui.view.View;
import io.jmix.flowui.view.ViewControllerUtils;
import io.jmix.flowui.view.ViewRegistry;
import io.jmix.flowui.model.ViewData;
import io.jmix.reports.ReportsPersistence;
import io.jmix.reports.ReportsSerialization;
import io.jmix.reports.entity.BandDefinition;
import io.jmix.reports.entity.DataSet;
import io.jmix.reports.entity.DataSetType;
import io.jmix.reports.entity.Orientation;
import io.jmix.reports.entity.ParameterType;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportInputParameter;
import io.jmix.reports.entity.ReportOutputType;
import io.jmix.reports.entity.ReportTemplate;
import io.jmix.reports.runner.ReportRunner;
import io.jmix.reportsflowui.view.run.ParameterComponentGenerationStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.entity.report.ReportInputParameterCustom;
import uz.kapitalbank.umida.enums.report.ReportCustomParameterType;
import uz.kapitalbank.umida.service.report.ReportSqlEnumerationService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.view.report.ReportCustomDetailView;
import uz.kapitalbank.umida.view.report.ReportCustomListView;
import uz.kapitalbank.umida.view.report.parameter.ReportCustomParameterComponentGenerationStrategy;
import uz.kapitalbank.umida.view.report.parameter.ReportParameterCustomDetailView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@link ReportCustomParameterType#SQL_ENUMERATION} report parameter: its values come from its query, it
 * survives the serialization of the add-on, the editor of the parameter sets it up, the run dialog shows a
 * list of the values, and the value picked goes into the bands as the value of a text parameter does.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportSqlEnumerationParameterTest {

    private static final String QUERY = """
            select v, c from (values ('b', 'Bee'), ('a', 'Ay'), ('b', 'again'), (null, 'none'), ('c', null)) t(v, c)""";

    @Autowired
    DataManager dataManager;
    @Autowired
    Metadata metadata;
    @Autowired
    Messages messages;
    @Autowired
    ReportsPersistence reportsPersistence;
    @Autowired
    ReportsSerialization reportsSerialization;
    @Autowired
    ReportRunner reportRunner;
    @Autowired
    ReportSqlEnumerationService sqlEnumerationService;
    @Autowired
    ParameterComponentGenerationStrategy parameterComponentGenerationStrategy;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    DialogWindows dialogWindows;
    @Autowired
    ViewRegistry viewRegistry;
    @Autowired
    ObjectProvider<OpenedDialogWindows> openedDialogWindows;

    @Autowired
    ViewControllersConfigurationSorter viewControllersConfigurationSorter;

    private final List<UUID> reportCleanup = new ArrayList<>();

    /**
     * The registry sorts the view configurations by the order of the modules, so that a view of the
     * application replaces the view of an add-on with the same id. The UI test extension sets the
     * configurations without that sorting, which leaves the editor of the add-on in place; they are sorted
     * here as the application does it, see {@link ReportParameterEditorRegistrationTest}.
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void sortViewConfigurations() {
        List<ViewControllersConfiguration> configurations = (List<ViewControllersConfiguration>)
                ReflectionTestUtils.getField(viewRegistry, "configurations");
        ReflectionTestUtils.setField(viewRegistry, "configurations",
                viewControllersConfigurationSorter.sort(new ArrayList<>(configurations)));
        viewRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        reportCleanup.forEach(id -> {
            jdbcTemplate.update("delete from REPORT_EXECUTION where REPORT_ID = ?", id);
            dataManager.load(Report.class)
                    .id(id)
                    .hint(PersistenceHints.SOFT_DELETION, false)
                    .optional()
                    .ifPresent(report -> dataManager.save(new SaveContext()
                            .removing(report)
                            .setHint(PersistenceHints.SOFT_DELETION, false)));
        });
        reportCleanup.clear();
    }

    // the service

    @Test
    void theQueryGivesTheValuesWithTheCaptionsOfTheSecondColumn() {
        Map<String, String> options = sqlEnumerationService.loadOptions(Stores.MAIN, QUERY);

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("b", "Bee");
        expected.put("a", "Ay");
        expected.put("c", "c");
        assertEquals(expected, options, "in the order of the query, no empty value, the first caption of a value");
        assertEquals(List.copyOf(expected.keySet()), List.copyOf(options.keySet()));
    }

    @Test
    void aQueryOfOneColumnShowsTheValuesThemselves() {
        Map<String, String> options = sqlEnumerationService.loadOptions(Stores.MAIN,
                "select n from generate_series(1, 3) n order by n desc;");

        assertEquals(Map.of("3", "3", "2", "2", "1", "1"), options);
        assertEquals(List.of("3", "2", "1"), List.copyOf(options.keySet()));
    }

    @Test
    void theListIsCut() {
        Map<String, String> options = sqlEnumerationService.loadOptions(Stores.MAIN,
                "select n from generate_series(1, 5000) n");

        assertEquals(ReportSqlEnumerationService.MAX_OPTIONS, options.size());
    }

    @Test
    void onlyASingleReadQueryInAKnownDataStoreIsRun() {
        assertRejected(null, "select 1");
        assertRejected("nowhere", "select 1");
        assertRejected(Stores.MAIN, "  ");
        assertRejected(Stores.MAIN, "delete from REPORT_REPORT");
        assertRejected(Stores.MAIN, "select 1; select 2");
        assertRejected(Stores.MAIN, "with d as (delete from REPORT_REPORT returning id) select id from d");
        assertRejected(Stores.MAIN, "select no_such_column from REPORT_REPORT");

        assertNull(sqlEnumerationService.validate(Stores.MAIN, "select 1"));
        assertNotNull(sqlEnumerationService.validate(Stores.MAIN, "select no_such_column from REPORT_REPORT"));
        assertNotNull(sqlEnumerationService.validate(null, "select 1"));
    }

    private void assertRejected(String dataStore, String sql) {
        ReportSqlEnumerationService.SqlEnumerationException e = assertThrows(
                ReportSqlEnumerationService.SqlEnumerationException.class,
                () -> sqlEnumerationService.loadOptions(dataStore, sql), sql);
        assertFalse(e.getMessage().isBlank());
        assertFalse(e.getMessage().startsWith("sqlEnumeration."), "a message from the bundle: " + e.getMessage());
    }

    // the entity

    @Test
    void theAddOnCreatesTheReplacingParameterAndKeepsItsSettingsInTheStructure() {
        assertInstanceOf(ReportInputParameterCustom.class, metadata.create(ReportInputParameter.class));

        ReportCustom saved = save(reportWithParameter(QUERY));
        Report loaded = dataManager.load(Report.class).id(saved.getId()).fetchPlan("report.edit").one();

        ReportInputParameterCustom parameter = assertInstanceOf(ReportInputParameterCustom.class,
                loaded.getInputParameters().get(0));
        assertEquals(ReportCustomParameterType.SQL_ENUMERATION, parameter.getCustomType());
        assertEquals(ParameterType.TEXT, parameter.getType(), "the add-on sees a text parameter");
        assertEquals(QUERY, parameter.getSqlQuery());
        assertEquals(Stores.MAIN, parameter.getSqlDataStore());
        assertEquals("b", parameter.getDefaultValue());

        assertEquals(messages.getMessage(ReportCustomParameterType.SQL_ENUMERATION),
                sqlEnumerationService.getTypeCaption(parameter),
                "the list of the parameters shows the custom type, not the text one");
        assertEquals(messages.getMessage(ParameterType.TEXT), sqlEnumerationService.getTypeCaption(textParameter()));
    }

    @Test
    void aStandardParameterHasNoCustomType() {
        ReportCustom report = newReport();
        ReportInputParameter parameter = textParameter();
        parameter.setReport(report);
        report.setInputParameters(new ArrayList<>(List.of(parameter)));
        ReportCustom saved = save(report);

        Report loaded = dataManager.load(Report.class).id(saved.getId()).fetchPlan("report.edit").one();
        ReportInputParameterCustom stored = (ReportInputParameterCustom) loaded.getInputParameters().get(0);
        assertNull(stored.getCustomType());
        assertFalse(sqlEnumerationService.isSqlEnumeration(stored));
    }

    // the field in the run dialog

    @Test
    void theRunDialogShowsTheValuesOfTheQueryInAComboBox() {
        assertInstanceOf(ReportCustomParameterComponentGenerationStrategy.class, parameterComponentGenerationStrategy);
        JmixComboBox<String> comboBox = assertComboBox(
                parameterComponentGenerationStrategy.createField(sqlParameter(QUERY)));
        assertEquals(List.of("b", "a", "c"), comboBox.getListDataView().getItems().toList());
        assertEquals("Bee", comboBox.getItemLabelGenerator().apply("b"));
        assertEquals("c", comboBox.getItemLabelGenerator().apply("c"));
        assertEquals("param_choice", comboBox.getId().orElseThrow());
        assertEquals("Choice", comboBox.getLabel());
        assertFalse(comboBox.isInvalid());

        ReportInputParameterCustom required = sqlParameter(QUERY);
        required.setRequired(true);
        comboBox = assertComboBox(parameterComponentGenerationStrategy.createField(required));
        assertTrue(comboBox.isRequired());
        assertFalse(comboBox.getRequiredMessage().contains("error.paramIsRequiredButEmpty"),
                "the required message comes from our bundle: " + comboBox.getRequiredMessage());
    }

    @Test
    void aFailingQueryLeavesTheListEmptyWithTheReason() {
        AbstractField<?, ?> field = parameterComponentGenerationStrategy.createField(
                sqlParameter("select no_such_column from REPORT_REPORT"));

        JmixComboBox<String> comboBox = assertComboBox(field);
        assertEquals(0, comboBox.getListDataView().getItemCount());
        assertTrue(comboBox.isInvalid());
        assertTrue(comboBox.getErrorMessage().contains("no_such_column"), comboBox.getErrorMessage());
    }

    @Test
    void aTextParameterKeepsItsTextField() {
        AbstractField<?, ?> field = parameterComponentGenerationStrategy.createField(textParameter());
        assertInstanceOf(TypedTextField.class, field);
    }

    @SuppressWarnings("unchecked")
    private JmixComboBox<String> assertComboBox(AbstractField<?, ?> field) {
        return assertInstanceOf(JmixComboBox.class, field);
    }

    // the run

    @Test
    void thePickedValueGoesIntoTheBandsAsATextParameterDoes() {
        ReportCustom report = reportWithParameter(QUERY);
        BandDefinition root = report.getRootBandDefinition();

        BandDefinition data = metadata.create(BandDefinition.class);
        data.setName("Data");
        data.setPosition(0);
        data.setOrientation(Orientation.HORIZONTAL);
        data.setReport(report);
        data.setParentBandDefinition(root);
        DataSet dataSet = metadata.create(DataSet.class);
        dataSet.setName("Data");
        dataSet.setType(DataSetType.SQL);
        dataSet.setText("select 'picked ' || ${choice} as v");
        dataSet.setBandDefinition(data);
        data.setDataSets(new ArrayList<>(List.of(dataSet)));
        root.getChildrenBandDefinitions().add(data);
        report.getBands().add(data);

        ReportTemplate template = report.getDefaultTemplate();
        template.setName("template.html");
        template.setReportOutputType(ReportOutputType.HTML);
        template.setContent("<p>${Root.bands.Data[0].fields.v}</p>".getBytes(StandardCharsets.UTF_8));
        ReportCustom saved = save(report);

        byte[] content = reportRunner.byReportEntity(saved)
                .addParam("choice", "a")
                .run()
                .getContent();

        assertTrue(new String(content, StandardCharsets.UTF_8).contains("picked a"),
                new String(content, StandardCharsets.UTF_8));
    }

    // the editor of the parameter

    @Test
    void ourEditorReplacesTheOneOfTheAddOn() {
        assertEquals(ReportParameterCustomDetailView.class,
                viewRegistry.getViewInfo("report_ReportInputParameter.detail").getControllerClass());
        assertEquals(ReportParameterCustomDetailView.class,
                viewRegistry.getDetailViewInfo(ReportInputParameter.class).getControllerClass());
    }

    @Test
    void theEditorOffersTheTypeAndSetsUpTheQueryAndTheDefaultValue() {
        ReportCustomDetailView reportEditor = openNewReportEditor();
        ReportParameterCustomDetailView editor = openNewParameter(reportEditor);

        JmixComboBox<Object> typeField = UiTestUtils.getComponent(editor, "typeField");
        List<Object> types = typeField.getListDataView().getItems().toList();
        assertEquals(ParameterType.values().length + 1, types.size());
        assertEquals(ReportCustomParameterType.SQL_ENUMERATION,
                types.get(types.indexOf(ParameterType.ENUMERATION) + 1));
        assertEquals(ParameterType.TEXT, typeField.getValue());

        JmixSelect<String> dataStoreField = UiTestUtils.getComponent(editor, "sqlDataStoreField");
        CodeEditor queryField = UiTestUtils.getComponent(editor, "sqlQueryField");
        JmixButton checkButton = UiTestUtils.getComponent(editor, "sqlCheckBtn");
        VerticalLayout transformationBox = UiTestUtils.getComponent(editor, "predefinedTransformationBox");
        assertFalse(dataStoreField.isVisible());
        assertFalse(queryField.isVisible());
        assertFalse(checkButton.isVisible());
        assertTrue(transformationBox.isVisible());
        assertFalse(UiTestUtils.<Component>getComponent(editor, "parameterTypeField").isVisible());

        typeField.setValue(ReportCustomParameterType.SQL_ENUMERATION);

        ReportInputParameterCustom parameter = (ReportInputParameterCustom) editor.getEditedEntity();
        assertEquals(ReportCustomParameterType.SQL_ENUMERATION, parameter.getCustomType());
        assertEquals(ParameterType.TEXT, parameter.getType());
        assertEquals(Stores.MAIN, parameter.getSqlDataStore());
        assertTrue(dataStoreField.isVisible());
        assertTrue(queryField.isVisible());
        assertTrue(checkButton.isVisible());
        assertFalse(transformationBox.isVisible());
        assertTrue(dataStoreField.getListDataView().getItems().toList().contains(Stores.MAIN));
        assertTrue(defaultValueBox(editor).getComponentCount() == 0, "no query yet, no values to pick from");

        queryField.setValue(QUERY);
        checkButton.click();

        JmixComboBox<String> defaultValueField = assertComboBox(
                (AbstractField<?, ?>) defaultValueBox(editor).getComponentAt(0));
        assertEquals(List.of("b", "a", "c"), defaultValueField.getListDataView().getItems().toList());
        defaultValueField.setValue("a");
        assertEquals("a", parameter.getDefaultValue());

        parameter.setName("Choice");
        parameter.setAlias("choice");
        assertEquals(OperationResult.Status.SUCCESS, editor.closeWithSave().getStatus());

        CollectionContainer<ReportInputParameter> parametersDc =
                ViewControllerUtils.getViewData(reportEditor).getContainer("parametersDc");
        ReportInputParameterCustom added = (ReportInputParameterCustom) parametersDc.getItems().get(0);
        assertEquals(ReportCustomParameterType.SQL_ENUMERATION, added.getCustomType());
        assertEquals(QUERY, added.getSqlQuery());
        assertEquals("a", added.getDefaultValue());
    }

    @Test
    void theEditorDropsTheQueryOfAParameterTurnedIntoAnotherType() {
        ReportCustomDetailView reportEditor = openNewReportEditor();
        ReportParameterCustomDetailView editor = openNewParameter(reportEditor);
        JmixComboBox<Object> typeField = UiTestUtils.getComponent(editor, "typeField");
        ReportInputParameterCustom parameter = (ReportInputParameterCustom) editor.getEditedEntity();

        typeField.setValue(ReportCustomParameterType.SQL_ENUMERATION);
        parameter.setSqlQuery(QUERY);
        typeField.setValue(ParameterType.DATE);

        assertNull(parameter.getCustomType());
        assertEquals(ParameterType.DATE, parameter.getType());
        assertFalse(UiTestUtils.<Component>getComponent(editor, "sqlQueryField").isVisible());

        parameter.setName("Date");
        parameter.setAlias("date");
        assertEquals(OperationResult.Status.SUCCESS, editor.closeWithSave().getStatus());
        assertNull(parameter.getSqlQuery());
        assertNull(parameter.getSqlDataStore());
    }

    @Test
    void theEditorDoesNotSaveAQueryThatChangesData() {
        ReportCustomDetailView reportEditor = openNewReportEditor();
        ReportParameterCustomDetailView editor = openNewParameter(reportEditor);
        JmixComboBox<Object> typeField = UiTestUtils.getComponent(editor, "typeField");
        ReportInputParameterCustom parameter = (ReportInputParameterCustom) editor.getEditedEntity();

        typeField.setValue(ReportCustomParameterType.SQL_ENUMERATION);
        parameter.setName("Choice");
        parameter.setAlias("choice");
        UiTestUtils.<CodeEditor>getComponent(editor, "sqlQueryField").setValue("delete from REPORT_REPORT");

        assertNotEquals(OperationResult.Status.SUCCESS, editor.closeWithSave().getStatus());
    }

    private ReportCustomDetailView openNewReportEditor() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Report.class)
                .withViewClass(ReportCustomDetailView.class)
                .newEntity()
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    /**
     * Opens the editor the way the create action of the parameter grid does: the editor resolved for the
     * entity, over the parameter container of the report. The grid itself is not attached to the view in a
     * UI test, its tab is never rendered.
     */
    private ReportParameterCustomDetailView openNewParameter(ReportCustomDetailView reportEditor) {
        ViewData viewData = ViewControllerUtils.getViewData(reportEditor);
        CollectionContainer<ReportInputParameter> parametersDc = viewData.getContainer("parametersDc");
        dialogWindows.detail(reportEditor, ReportInputParameter.class)
                .withContainer(parametersDc)
                .withParentDataContext(viewData.getDataContext())
                .withInitializer(parameter -> {
                    parameter.setReport(reportEditor.getEditedEntity());
                    parameter.setPosition(parametersDc.getItems().size());
                })
                .newEntity()
                .open();
        View<?> dialog = openedDialogWindows.getObject().getCurrentDialog().orElseThrow();
        return assertInstanceOf(ReportParameterCustomDetailView.class, dialog);
    }

    private HorizontalLayout defaultValueBox(ReportParameterCustomDetailView editor) {
        return UiTestUtils.getComponent(editor, "defaultValueBox");
    }

    // the data

    private ReportInputParameterCustom sqlParameter(String sql) {
        ReportInputParameterCustom parameter = (ReportInputParameterCustom) metadata.create(ReportInputParameter.class);
        parameter.setName("Choice");
        parameter.setAlias("choice");
        parameter.setPosition(0);
        parameter.setType(ParameterType.TEXT);
        parameter.setCustomType(ReportCustomParameterType.SQL_ENUMERATION);
        parameter.setSqlDataStore(Stores.MAIN);
        parameter.setSqlQuery(sql);
        return parameter;
    }

    private ReportInputParameter textParameter() {
        ReportInputParameter parameter = metadata.create(ReportInputParameter.class);
        parameter.setName("Text");
        parameter.setAlias("text");
        parameter.setPosition(0);
        parameter.setType(ParameterType.TEXT);
        return parameter;
    }

    private ReportCustom reportWithParameter(String sql) {
        ReportCustom report = newReport();
        ReportInputParameterCustom parameter = sqlParameter(sql);
        parameter.setDefaultValue("b");
        parameter.setReport(report);
        report.setInputParameters(new ArrayList<>(List.of(parameter)));
        return report;
    }

    /** A report the way the editor of the add-on hands it over: with a root band, a template and the serialized structure. */
    private ReportCustom newReport() {
        ReportCustom report = assertInstanceOf(ReportCustom.class, metadata.create(Report.class));
        report.setName("Report " + UUID.randomUUID());

        BandDefinition root = metadata.create(BandDefinition.class);
        root.setName("Root");
        root.setPosition(0);
        root.setOrientation(Orientation.HORIZONTAL);
        root.setReport(report);
        report.getBands().add(root);

        ReportTemplate template = metadata.create(ReportTemplate.class);
        template.setReport(report);
        template.setCode(ReportTemplate.DEFAULT_TEMPLATE_CODE);
        template.setName("template.txt");
        template.setReportOutputType(ReportOutputType.CUSTOM);
        template.setContent("template".getBytes());
        report.setTemplates(new ArrayList<>(List.of(template)));
        report.setDefaultTemplate(template);
        return report;
    }

    private ReportCustom save(ReportCustom report) {
        report.setXml(reportsSerialization.convertToString(report));
        ReportCustom saved = assertInstanceOf(ReportCustom.class, reportsPersistence.save(report));
        reportCleanup.add(saved.getId());
        return saved;
    }
}
