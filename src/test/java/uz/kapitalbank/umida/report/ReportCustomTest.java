package uz.kapitalbank.umida.report;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.tabs.Tab;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.MetadataTools;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.ViewOpeningAction;
import io.jmix.flowui.action.entitypicker.EntityLookupAction;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.multiselectcombobox.JmixMultiSelectComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.kit.component.dropdownbutton.DropdownButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.DataContext;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.util.OperationResult;
import io.jmix.flowui.view.ViewControllerUtils;
import io.jmix.flowui.view.ViewRegistry;
import io.jmix.reports.ReportImportExport;
import io.jmix.reports.ReportRepository;
import io.jmix.reports.ReportsPersistence;
import io.jmix.reports.ReportsSerialization;
import io.jmix.reports.entity.BandDefinition;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportOutputType;
import io.jmix.reports.entity.ReportTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.service.report.ReportCustomService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.view.report.ReportCustomDetailView;
import uz.kapitalbank.umida.view.report.ReportCustomListView;
import uz.kapitalbank.umida.view.report.ReportGroupCustomListView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The report of the Reports add-on replaced by {@link ReportCustom}: the add-on keeps working with the
 * replacing entity on all of its save paths, the code is generated, the classification survives them,
 * and the list and the editor open with the changes made to the views of the add-on.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportCustomTest {

    @Autowired
    DataManager dataManager;
    @Autowired
    Metadata metadata;
    @Autowired
    MetadataTools metadataTools;
    @Autowired
    ReportsPersistence reportsPersistence;
    @Autowired
    ReportsSerialization reportsSerialization;
    @Autowired
    ReportRepository reportRepository;
    @Autowired
    ReportImportExport reportImportExport;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    ViewRegistry viewRegistry;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private final List<UUID> reportCleanup = new ArrayList<>();
    private final List<Integer> productCleanup = new ArrayList<>();

    private OrgStructureSubdivision subdivision;
    private DictDataDomain domainA;
    private DictDataDomain domainB;
    private DictDataProduct product;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        subdivision = dataManager.load(OrgStructureSubdivision.class).all().maxResults(1).optional().orElse(null);
        if (subdivision == null) {
            throw new IllegalStateException("The test needs a subdivision in the database");
        }
        domainA = saveDomain("A " + suffix);
        domainB = saveDomain("B " + suffix);
        product = saveProduct("P " + suffix);
    }

    @AfterEach
    void tearDown() {
        reportCleanup.forEach(id -> {
            jdbcTemplate.update("delete from UMIDA_REPORT_SECONDARY_DOMAIN_LINK where REPORT_ID = ?", id);
            jdbcTemplate.update("delete from UMIDA_REPORT_DATA_PRODUCT_LINK where REPORT_ID = ?", id);
            removeForReal(Report.class, id);
        });
        reportCleanup.clear();
        productCleanup.forEach(id -> removeForReal(DictDataProduct.class, id));
        productCleanup.clear();
        removeForReal(DictDataProduct.class, product.getId());
        removeForReal(DictDataDomain.class, domainB.getId());
        removeForReal(DictDataDomain.class, domainA.getId());
    }

    private <E> void removeForReal(Class<E> entityClass, Object id) {
        dataManager.load(entityClass)
                .id(id)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .optional()
                .ifPresent(current -> dataManager.save(new SaveContext()
                        .removing(current)
                        .setHint(PersistenceHints.SOFT_DELETION, false)));
    }

    private DictDataDomain saveDomain(String name) {
        DictDataDomain domain = dataManager.create(DictDataDomain.class);
        domain.setCode(name);
        domain.setShortName(name);
        domain.setBusinessOwner(subdivision);
        domain.setAssignDate(LocalDate.now());
        return dataManager.save(domain);
    }

    private DictDataProduct saveProduct(String name) {
        DictDataProduct dataProduct = dataManager.create(DictDataProduct.class);
        dataProduct.setCode(name);
        dataProduct.setShortName(name);
        dataProduct.setBusinessOwner(subdivision);
        dataProduct.setAssignDate(LocalDate.now());
        return dataManager.save(dataProduct);
    }

    /** The two entities have sequences of their own, so a product and a domain share an id sooner or later. */
    private DictDataProduct saveProductWithIdOf(DictDataDomain domain) {
        assertTrue(dataManager.load(DictDataProduct.class).id(domain.getId())
                .hint(PersistenceHints.SOFT_DELETION, false).optional().isEmpty(), "the id must be free");
        DictDataProduct dataProduct = dataManager.create(DictDataProduct.class);
        dataProduct.setId(domain.getId());
        dataProduct.setCode("same id " + domain.getId());
        dataProduct.setShortName("same id " + domain.getId());
        dataProduct.setBusinessOwner(subdivision);
        dataProduct.setAssignDate(LocalDate.now());
        DictDataProduct saved = dataManager.save(dataProduct);
        productCleanup.add(saved.getId());
        return saved;
    }

    /** A report the way the editor of the add-on hands it over: with a root band, a template and the serialized structure. */
    private ReportCustom newReport() {
        ReportCustom report = assertInstanceOf(ReportCustom.class, metadata.create(Report.class));
        report.setName("Report " + UUID.randomUUID());

        BandDefinition root = metadata.create(BandDefinition.class);
        root.setName("Root");
        root.setPosition(0);
        root.setOrientation(io.jmix.reports.entity.Orientation.HORIZONTAL);
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

    private ReportCustom saveClassified() {
        ReportCustom report = newReport();
        report.setPersonalData(true);
        report.setBankData(true);
        report.setDomain(domainA);
        report.setSecondaryDomains(new ArrayList<>(List.of(domainB)));
        report.setDataProducts(new ArrayList<>(List.of(product)));
        return save(report);
    }

    private ReportCustom reload(UUID id) {
        return dataManager.load(ReportCustom.class).id(id).fetchPlan("report.edit").one();
    }

    @Test
    void theAddOnCreatesAndLoadsTheReplacingEntity() {
        ReportCustom saved = saveClassified();

        assertInstanceOf(ReportCustom.class, dataManager.create(Report.class));
        assertEquals("umida_ReportCustom", jdbcTemplate.queryForObject(
                "select DTYPE from REPORT_REPORT where ID = ?", String.class, saved.getId()));

        Report loaded = dataManager.load(Report.class)
                .query("select r from report_Report r where r.id = :id")
                .parameter("id", saved.getId())
                .fetchPlan("report.edit")
                .one();
        assertFalse(loaded.getBands().isEmpty(), "the detach listener of the add-on restores the bands from the XML");
    }

    @Test
    void aReportGetsACodeFromTheSequence() {
        ReportCustom first = save(newReport());
        ReportCustom second = save(newReport());

        assertTrue(first.getCode().matches("R-\\d+"), first.getCode());
        long firstNumber = Long.parseLong(first.getCode().substring(ReportCustomService.CODE_PREFIX.length()));
        long secondNumber = Long.parseLong(second.getCode().substring(ReportCustomService.CODE_PREFIX.length()));
        assertTrue(firstNumber >= 1000);
        assertEquals(firstNumber + 1, secondNumber);
    }

    @Test
    void aReportKeepsTheCodeItHas() {
        ReportCustom report = newReport();
        report.setCode("legacy-" + UUID.randomUUID());
        String code = report.getCode();

        ReportCustom saved = save(report);

        assertEquals(code, saved.getCode());
    }

    @Test
    void theClassificationSurvivesTheSaveOfTheAddOn() {
        ReportCustom saved = saveClassified();

        assertEquals(domainA, saved.getDomain(), "the add-on reloads the report it saved with report.edit");
        ReportCustom loaded = reload(saved.getId());
        assertTrue(loaded.getPersonalData());
        assertTrue(loaded.getBankData());
        assertFalse(loaded.getConfidentialData());
        assertFalse(loaded.getCommercialData());
        assertEquals(domainA, loaded.getDomain());
        assertEquals(List.of(domainB), loaded.getSecondaryDomains());
        assertEquals(List.of(product), loaded.getDataProducts());
    }

    @Test
    void theListOfTheAddOnLoadsTheDomain() {
        ReportCustom saved = saveClassified();

        Collection<Report> reports = reportRepository.getAllReports();

        Report listed = reports.stream().filter(r -> r.getId().equals(saved.getId())).findFirst().orElseThrow();
        assertEquals(domainA.getShortName(),
                metadataTools.getInstanceName(assertInstanceOf(ReportCustom.class, listed).getDomain()));
    }

    @Test
    void aCopyGetsACodeOfItsOwnAndKeepsTheClassification() {
        ReportCustom saved = saveClassified();
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        ReportCustomListView listView = UiTestUtils.getCurrentView();
        DataGrid<Report> grid = UiTestUtils.getComponent(listView, "reportsDataGrid");
        Report row = grid.getItems().getItems().stream()
                .filter(r -> r.getId().equals(saved.getId())).findFirst().orElseThrow();
        grid.select(row);

        Set<UUID> before = Set.copyOf(dataManager.loadValues("select r.id from report_Report r")
                .properties("id").list().stream().map(v -> v.<UUID>getValue("id")).toList());
        grid.getAction("copy").actionPerform(grid);

        UUID copyId = dataManager.loadValues("select r.id from report_Report r")
                .properties("id").list().stream().map(v -> v.<UUID>getValue("id"))
                .filter(id -> !before.contains(id)).findFirst().orElseThrow();
        reportCleanup.add(copyId);
        ReportCustom copy = reload(copyId);
        assertNotEquals(saved.getCode(), copy.getCode());
        assertTrue(copy.getCode().startsWith(ReportCustomService.CODE_PREFIX));
        assertEquals(domainA, copy.getDomain());
        assertEquals(List.of(domainB), copy.getSecondaryDomains());
        assertTrue(copy.getPersonalData());
    }

    @Test
    void anExportedReportWithDomainsCanBeImported() {
        ReportCustom saved = saveClassified();
        byte[] zip = reportImportExport.exportReports(List.of(reload(saved.getId())));

        Collection<Report> imported = reportImportExport.importReports(zip);

        assertEquals(1, imported.size());
        assertEquals(saved.getId(), imported.iterator().next().getId());
    }

    @Test
    void anExportedReportIsImportedAfterItWasRemoved() {
        ReportCustom saved = saveClassified();
        // the editor stores the structure of the report the export takes the code from
        assertEquals(OperationResult.Status.SUCCESS, openEditor(saved).save().getStatus());
        byte[] zip = reportImportExport.exportReports(List.of(reload(saved.getId())));
        jdbcTemplate.update("delete from UMIDA_REPORT_SECONDARY_DOMAIN_LINK where REPORT_ID = ?", saved.getId());
        jdbcTemplate.update("delete from UMIDA_REPORT_DATA_PRODUCT_LINK where REPORT_ID = ?", saved.getId());
        removeForReal(Report.class, saved.getId());

        reportImportExport.importReports(zip);

        ReportCustom loaded = reload(saved.getId());
        assertEquals(saved.getCode(), loaded.getCode());
        assertTrue(loaded.getPersonalData());
        assertEquals(domainA, loaded.getDomain());
        assertEquals(List.of(domainB), loaded.getSecondaryDomains());
        assertEquals(List.of(product), loaded.getDataProducts());
    }

    @Test
    void theEditorSavesANewReportAndThenShowsItsCode() {
        ReportCustomDetailView view = openEditor(metadata.create(ReportCustom.class));
        ReportCustom report = (ReportCustom) view.getEditedEntity();
        report.setName("Report " + UUID.randomUUID());
        report.setDomain(domainA);
        report.setCommercialData(true);
        DataContext dataContext = ViewControllerUtils.getViewData(view).getDataContext();
        ReportTemplate template = dataContext.create(ReportTemplate.class);
        template.setReport(report);
        template.setCode(ReportTemplate.DEFAULT_TEMPLATE_CODE);
        template.setName("template.txt");
        template.setReportOutputType(ReportOutputType.CUSTOM);
        template.setContent("template".getBytes());
        report.setTemplates(new ArrayList<>(List.of(template)));
        report.setDefaultTemplate(template);

        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());
        reportCleanup.add(report.getId());

        ReportCustom stored = reload(report.getId());
        assertTrue(stored.getCode().startsWith(ReportCustomService.CODE_PREFIX), stored.getCode());
        assertEquals(domainA, stored.getDomain());
        assertTrue(stored.getCommercialData());
        assertFalse(stored.getBands().isEmpty());

        TypedTextField<String> codeField = UiTestUtils.getComponent(view, "codeField");
        assertTrue(codeField.isVisible(), "saved without closing, the report now has a code to show");
        assertEquals(stored.getCode(), codeField.getTypedValue());
    }

    @Test
    void theEditorSavesTheClassificationOfASavedReport() {
        ReportCustom saved = saveClassified();
        ReportCustomDetailView view = openEditor(saved);
        JmixMultiSelectComboBox<DictDataDomain> secondary = UiTestUtils.getComponent(view, "secondaryDomainsField");
        JmixMultiSelectComboBox<DictDataProduct> products = UiTestUtils.getComponent(view, "dataProductsField");

        secondary.setValue(Set.of());
        products.setValue(Set.of());
        ((ReportCustom) view.getEditedEntity()).setConfidentialData(true);
        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());

        ReportCustom stored = reload(saved.getId());
        assertEquals(saved.getCode(), stored.getCode());
        assertTrue(stored.getSecondaryDomains().isEmpty());
        assertTrue(stored.getDataProducts().isEmpty());
        assertTrue(stored.getConfidentialData());
        assertEquals(domainA, stored.getDomain());
    }

    @Test
    void theEditorAssignsADataProductWithTheIdOfTheDomain() {
        DictDataProduct sameId = saveProductWithIdOf(domainA);
        ReportCustom report = newReport();
        report.setDomain(domainA);
        ReportCustom saved = save(report);
        ReportCustomDetailView view = openEditor(saved);
        JmixMultiSelectComboBox<DictDataProduct> products = UiTestUtils.getComponent(view, "dataProductsField");
        CollectionContainer<DictDataProduct> options = ViewControllerUtils.getViewData(view).getContainer("dataProductsDc");

        products.setValue(Set.of(options.getItems().stream().filter(sameId::equals).findFirst().orElseThrow()));
        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());

        ReportCustom stored = reload(saved.getId());
        assertEquals(domainA, stored.getDomain());
        assertEquals(List.of(sameId), stored.getDataProducts());
    }

    @Test
    void aDomainAndADataProductWithTheSameIdSurviveAnExportAndAnImport() {
        DictDataProduct sameIdAsDomain = saveProductWithIdOf(domainA);
        DictDataProduct sameIdAsSecondaryDomain = saveProductWithIdOf(domainB);
        ReportCustom report = newReport();
        report.setDomain(domainA);
        report.setSecondaryDomains(new ArrayList<>(List.of(domainB)));
        report.setDataProducts(new ArrayList<>(List.of(sameIdAsDomain, sameIdAsSecondaryDomain)));
        ReportCustom saved = save(report);
        // the editor stores the structure of the report the export takes the classification from
        assertEquals(OperationResult.Status.SUCCESS, openEditor(saved).save().getStatus());
        byte[] zip = reportImportExport.exportReports(List.of(reload(saved.getId())));
        jdbcTemplate.update("delete from UMIDA_REPORT_SECONDARY_DOMAIN_LINK where REPORT_ID = ?", saved.getId());
        jdbcTemplate.update("delete from UMIDA_REPORT_DATA_PRODUCT_LINK where REPORT_ID = ?", saved.getId());
        removeForReal(Report.class, saved.getId());

        reportImportExport.importReports(zip);

        ReportCustom loaded = reload(saved.getId());
        assertEquals(domainA, loaded.getDomain());
        assertEquals(List.of(domainB), loaded.getSecondaryDomains());
        assertEquals(Set.of(sameIdAsDomain, sameIdAsSecondaryDomain), Set.copyOf(loaded.getDataProducts()));
    }

    @Test
    void theMarkersAreOptional() {
        ReportCustom saved = saveClassified();
        ReportCustomDetailView view = openEditor(saved);

        for (String id : List.of("personalDataField", "confidentialDataField", "commercialDataField", "bankDataField")) {
            JmixCheckbox marker = UiTestUtils.getComponent(view, id);
            assertFalse(marker.isRequired(), id);
            marker.setValue(false);
        }
        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());

        ReportCustom stored = reload(saved.getId());
        assertFalse(stored.getPersonalData());
        assertFalse(stored.getConfidentialData());
        assertFalse(stored.getCommercialData());
        assertFalse(stored.getBankData());
    }

    @Test
    void aReportWithoutMarkerValuesIsSaved() {
        ReportCustom report = newReport();
        report.setPersonalData(null);
        report.setConfidentialData(null);
        report.setCommercialData(null);
        report.setBankData(null);

        ReportCustom loaded = reload(save(report).getId());

        assertNull(loaded.getPersonalData());
        assertNull(loaded.getConfidentialData());
        assertNull(loaded.getCommercialData());
        assertNull(loaded.getBankData());

        ReportCustomDetailView view = openEditor(loaded);
        JmixCheckbox personalData = UiTestUtils.getComponent(view, "personalDataField");
        assertFalse(Boolean.TRUE.equals(personalData.getValue()));
        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());
    }

    @Test
    void theListHasAPlainCreateButtonAndTheDomainColumn() {
        ReportCustom saved = saveClassified();
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        ReportCustomListView view = UiTestUtils.getCurrentView();

        DropdownButton dropdown = UiTestUtils.getComponent(view, "dropdownCreateBtn");
        assertFalse(dropdown.isVisible(), "the dropdown with the wizard is gone");
        JmixButton create = UiTestUtils.getComponent(view, "createBtn");
        assertTrue(create.isVisible());
        assertEquals("umida_ReportCustom.detail", ((ViewOpeningAction) create.getAction()).getViewId());
        assertNotNull(create.getIcon());
        assertFalse(create.getText().isBlank());

        DataGrid<Report> grid = UiTestUtils.getComponent(view, "reportsDataGrid");
        assertEquals("umida_ReportCustom.detail", ((ViewOpeningAction) grid.getAction("edit")).getViewId());
        assertNotNull(grid.getColumnByKey("domain"));
        assertFalse(grid.getColumnByKey("description").isVisible());
        List<String> visibleColumns = grid.getColumns().stream()
                .filter(Component::isVisible).map(c -> c.getKey()).toList();
        assertEquals(List.of("name", "group", "domain", "code", "source", "updateTs", "system"), visibleColumns);

        grid.sort(List.of(new com.vaadin.flow.component.grid.GridSortOrder<>(
                grid.getColumnByKey("domain"), com.vaadin.flow.data.provider.SortDirection.ASCENDING)));
        assertTrue(grid.getItems().getItems().stream().anyMatch(r -> r.getId().equals(saved.getId())),
                "the list sorts by the domain");
    }

    @Test
    void theEditorOfANewReportHidesTheCodeAndHasTheRequestedTabs() {
        ReportCustomDetailView view = openEditor(metadata.create(ReportCustom.class));

        TypedTextField<String> codeField = UiTestUtils.getComponent(view, "codeField");
        assertFalse(codeField.isVisible());

        Component localeField = UiTestUtils.getComponent(view, "localeTextField");
        assertFalse(localeField.isVisible());

        JmixTabSheet tabs = UiTestUtils.getComponent(view, "mainTabSheet");
        List<String> tabIds = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabIds.add(tabs.getTabAt(i).getId().orElseThrow());
        }
        assertEquals(List.of("detailsTab", "bandsTab", "parametersTab",
                "valuesFormatsTab", "templatesTab", "businessOwnerTab"), tabIds);
        Tab businessOwnerTab = tabs.getTabAt(5);
        assertEquals("Бизнес владелец", businessOwnerTab.getLabel());
        assertEquals("Детали отчёта", tabs.getTabAt(0).getLabel(), "the messages of the add-on are found");

        EntityLookupAction<?> groupLookup = (EntityLookupAction<?>) ((io.jmix.flowui.component.valuepicker.EntityPicker<?>)
                UiTestUtils.getComponent(view, "groupPicker")).getAction("entityLookupAction");
        assertEquals(viewRegistry.getViewInfo("umida_ReportGroupCustom.list").getControllerClass(),
                ReportGroupCustomListView.class);
        assertEquals("umida_ReportGroupCustom.list", groupLookup.getViewId());
    }

    @Test
    void theEditorOfASavedReportShowsTheCodeReadOnly() {
        ReportCustom saved = saveClassified();

        ReportCustomDetailView view = openEditor(saved);

        TypedTextField<String> codeField = UiTestUtils.getComponent(view, "codeField");
        assertTrue(codeField.isVisible());
        assertTrue(codeField.isReadOnly());
        assertEquals(saved.getCode(), codeField.getTypedValue());

        JmixMultiSelectComboBox<DictDataDomain> secondary = UiTestUtils.getComponent(view, "secondaryDomainsField");
        assertEquals(Set.of(domainB), secondary.getValue());
        JmixMultiSelectComboBox<DictDataProduct> products = UiTestUtils.getComponent(view, "dataProductsField");
        assertEquals(Set.of(product), products.getValue());
    }

    @Test
    void theEditorFormIsOneColumnOfTheRequestedOrder() {
        ReportCustomDetailView view = openEditor(saveClassified());

        FormLayout form = UiTestUtils.getComponent(view, "reportForm");

        assertEquals("60%", form.getWidth());
        assertEquals(1, form.getResponsiveSteps().size());
        // the box of the add-on with the check boxes has no id, so it goes by its first check box
        List<String> visibleFields = form.getChildren()
                .filter(Component::isVisible)
                .map(field -> field.getId().or(() -> field.getChildren().findFirst().flatMap(Component::getId)).orElseThrow())
                .toList();
        assertEquals(List.of("codeField", "nameField", "groupPicker", "systemField", "domainField",
                "secondaryDomainsField", "dataProductsField", "dataMarkersBox", "descriptionField"), visibleFields);
    }

    @Test
    void thePrimaryDomainIsNotOfferedAsASecondaryOne() {
        ReportCustom saved = saveClassified();
        ReportCustomDetailView view = openEditor(saved);
        CollectionContainer<DictDataDomain> options = ViewControllerUtils.getViewData(view).getContainer("secondaryDomainOptionsDc");
        JmixMultiSelectComboBox<DictDataDomain> secondary = UiTestUtils.getComponent(view, "secondaryDomainsField");

        assertFalse(options.getItems().contains(domainA));
        assertTrue(options.getItems().contains(domainB));

        // domainB, the selected secondary domain, becomes the primary one
        ReportCustom report = (ReportCustom) view.getEditedEntity();
        report.setDomain(report.getSecondaryDomains().get(0));

        assertFalse(options.getItems().contains(domainB));
        assertTrue(options.getItems().contains(domainA));
        assertTrue(secondary.getValue().isEmpty(), "the domain leaves the secondary ones");
        assertTrue(report.getSecondaryDomains().isEmpty());
    }

    @Test
    void theEditorSavesTheSelectionsOfItsFields() {
        ReportCustom saved = saveClassified();
        ReportCustomDetailView view = openEditor(saved);

        JmixMultiSelectComboBox<DictDataDomain> secondary = UiTestUtils.getComponent(view, "secondaryDomainsField");
        CollectionContainer<DictDataDomain> options = ViewControllerUtils.getViewData(view).getContainer("secondaryDomainOptionsDc");
        DictDataDomain optionB = options.getItems().stream().filter(domainB::equals).findFirst().orElseThrow();
        secondary.setValue(Set.of());
        assertTrue(((ReportCustom) view.getEditedEntity()).getSecondaryDomains().isEmpty());
        secondary.setValue(Set.of(optionB));

        assertEquals(Set.of(domainB), ((ReportCustom) view.getEditedEntity()).getSecondaryDomains().stream()
                .collect(Collectors.toSet()));
    }

    private ReportCustomDetailView openEditor(ReportCustom report) {
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        if (report.getId() != null && dataManager.load(Report.class).id(report.getId()).optional().isPresent()) {
            viewNavigators.detailView(UiTestUtils.getCurrentView(), Report.class)
                    .withViewClass(ReportCustomDetailView.class)
                    .editEntity(report)
                    .navigate();
        } else {
            viewNavigators.detailView(UiTestUtils.getCurrentView(), Report.class)
                    .withViewClass(ReportCustomDetailView.class)
                    .newEntity()
                    .navigate();
        }
        return UiTestUtils.getCurrentView();
    }
}
