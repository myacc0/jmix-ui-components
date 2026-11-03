package uz.kapitalbank.umida.report;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.router.QueryParameters;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import io.jmix.core.Metadata;
import io.jmix.core.MetadataTools;
import io.jmix.core.SaveContext;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.OpenedDialogWindows;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.ViewOpeningAction;
import io.jmix.flowui.action.entitypicker.EntityLookupAction;
import io.jmix.flowui.action.multivaluepicker.MultiValueSelectAction;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.multiselectcombobox.JmixMultiSelectComboBox;
import io.jmix.flowui.component.propertyfilter.PropertyFilter;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.DataGridColumn;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.grid.headerfilter.DataGridHeaderFilter;
import io.jmix.flowui.component.pagination.SimplePagination;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.HasActions;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
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
import io.jmix.reportsflowui.view.history.ReportExecutionListView;
import io.jmix.reportsflowui.view.importdialog.ReportImportDialogView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.component.reportgroup.ReportTile;
import uz.kapitalbank.umida.dto.report.ReportTreeItem;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.entity.report.ReportGroupCustom;
import uz.kapitalbank.umida.enums.report.ReportGrouping;
import uz.kapitalbank.umida.service.report.ReportCustomService;
import uz.kapitalbank.umida.service.report.ReportGroupCustomService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.view.report.ReportCustomDetailView;
import uz.kapitalbank.umida.view.report.ReportCustomListView;
import uz.kapitalbank.umida.view.report.ReportGroupCustomListView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The report of the Reports add-on replaced by {@link ReportCustom}: the add-on keeps working with the
 * replacing entity on all of its save paths, the code is generated, the classification survives them,
 * the list of its own does what the list of the add-on does, groups the reports and shows only the ones of
 * the current user on demand, and the editor opens with the changes made to the one of the add-on.
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
    Messages messages;
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
    @Autowired
    ObjectProvider<OpenedDialogWindows> openedDialogWindows;

    private final List<UUID> reportCleanup = new ArrayList<>();
    private final List<Integer> productCleanup = new ArrayList<>();
    private final List<UUID> groupCleanup = new ArrayList<>();

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
        // the subgroups first: they were saved after their parents
        groupCleanup.reversed().forEach(id -> removeForReal(ReportGroupCustom.class, id));
        groupCleanup.clear();
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

    private ReportGroupCustom saveGroup() {
        return saveGroup(null);
    }

    private ReportGroupCustom saveGroup(@org.springframework.lang.Nullable ReportGroupCustom parent) {
        ReportGroupCustom group = dataManager.create(ReportGroupCustom.class);
        group.setTitle("Group " + UUID.randomUUID());
        group.setParent(parent);
        ReportGroupCustom saved = dataManager.save(group);
        groupCleanup.add(saved.getId());
        return saved;
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

        assertTrue(UiComponentUtils.findComponent(view, "filterDetails").isEmpty(), "no filter panel of the add-on");
        assertTrue(UiComponentUtils.findComponent(view, "dropdownCreateBtn").isEmpty(), "no dropdown with the wizard");
        JmixButton create = UiTestUtils.getComponent(view, "createBtn");
        assertTrue(create.isVisible());
        assertEquals("umida_ReportCustom.detail", ((ViewOpeningAction) create.getAction()).getViewId());
        assertNotNull(create.getIcon());

        DataGrid<Report> grid = UiTestUtils.getComponent(view, "reportsDataGrid");
        assertEquals("umida_ReportCustom.detail", ((ViewOpeningAction) grid.getAction("edit")).getViewId());
        // the order is not checked: the settings facet applies the one saved for the user in the database
        Set<String> columns = grid.getColumns().stream().map(c -> c.getKey()).collect(Collectors.toSet());
        assertEquals(Set.of("name", "group", "domain", "code", "updateTs", "system"), columns);

        for (String column : columns) {
            grid.sort(List.of(new com.vaadin.flow.component.grid.GridSortOrder<>(
                    grid.getColumnByKey(column), com.vaadin.flow.data.provider.SortDirection.ASCENDING)));
            assertTrue(grid.getItems().getItems().stream().anyMatch(r -> r.getId().equals(saved.getId())),
                    "the list sorts by " + column);
        }
    }

    @Test
    void theListAppliesTheConditionsOfItsHeaderFilters() {
        ReportCustom classified = saveClassified();
        save(newReport());
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        ReportCustomListView view = UiTestUtils.getCurrentView();
        DataGrid<Report> grid = UiTestUtils.getComponent(view, "reportsDataGrid");
        grid.getColumns().forEach(column -> assertTrue(((DataGridColumn<Report>) column).isFilterable(), column.getKey()));

        CollectionLoader<Report> loader = ViewControllerUtils.getViewData(view).getLoader("reportsDl");
        loader.setCondition(PropertyCondition.equal("domain", domainA));
        loader.load();

        assertEquals(List.of(classified.getId()), grid.getItems().getItems().stream().map(Report::getId).toList());
    }

    @Test
    void theFilterOfTheGroupColumnPicksFromOurListOfGroupsAfterAnyChangeOfTheOperation() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        ReportCustomListView view = UiTestUtils.getCurrentView();
        DataGrid<Report> grid = UiTestUtils.getComponent(view, "reportsDataGrid");
        PropertyFilter<?> filter = headerFilter(grid, "group");

        assertEquals("umida_ReportGroupCustom.list", lookupAction(filter).getViewId());

        filter.setOperation(PropertyFilter.Operation.IN_LIST);
        MultiValueSelectAction<?> select = (MultiValueSelectAction<?>)
                ((HasActions) filter.getValueComponent()).getAction(MultiValueSelectAction.ID);
        assertEquals("umida_ReportGroupCustom.list", select.getLookupViewId());

        filter.setOperation(PropertyFilter.Operation.IS_SET);
        filter.setOperation(PropertyFilter.Operation.EQUAL);
        assertEquals("umida_ReportGroupCustom.list", lookupAction(filter).getViewId());

        assertNull(lookupAction(headerFilter(grid, "domain")).getViewId(), "the other columns keep the default lookup");
        assertEquals("report_ReportGroup.list", viewRegistry.getLookupViewInfo(
                metadata.getClass(io.jmix.reports.entity.ReportGroup.class)).getId(), "the entity keeps its lookup");
    }

    private PropertyFilter<?> headerFilter(DataGrid<Report> grid, String column) {
        return ((DataGridHeaderFilter) grid.getColumnByKey(column).getHeaderComponent()).getPropertyFilter();
    }

    private EntityLookupAction<?> lookupAction(PropertyFilter<?> filter) {
        return (EntityLookupAction<?>) ((HasActions) filter.getValueComponent()).getAction(EntityLookupAction.ID);
    }

    @Test
    void theActionsOfTheListFollowTheSelectionAndOpenTheDialogsOfTheAddOn() {
        ReportCustom saved = saveClassified();
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        ReportCustomListView view = UiTestUtils.getCurrentView();
        DataGrid<Report> grid = UiTestUtils.getComponent(view, "reportsDataGrid");

        List<String> itemActions = List.of("edit", "remove", "copy", "export", "runReport", "executions");
        itemActions.forEach(id -> assertFalse(grid.getAction(id).isEnabled(), id));
        assertTrue(grid.getAction("create").isEnabled());
        assertTrue(grid.getAction("importAction").isEnabled());

        grid.select(grid.getItems().getItems().stream()
                .filter(r -> r.getId().equals(saved.getId())).findFirst().orElseThrow());
        itemActions.forEach(id -> assertTrue(grid.getAction(id).isEnabled(), id));

        grid.getAction("executions").actionPerform(grid);
        io.jmix.flowui.view.View<?> history = openedDialogWindows.getObject().getCurrentDialog().orElseThrow();
        assertInstanceOf(ReportExecutionListView.class, history);
        history.closeWithDefaultAction();

        grid.getAction("importAction").actionPerform(grid);
        assertInstanceOf(ReportImportDialogView.class, openedDialogWindows.getObject().getCurrentDialog().orElseThrow());
    }

    @Test
    void theButtonsOfTheListShowAnIconWithTheCaptionInTheTitle() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        assertIconOnlyButtons(UiTestUtils.getCurrentView());
    }

    @Test
    void theButtonsOfImportAndExportAreHiddenInTheListAndInTheTree() {
        ReportCustomListView view = openList();
        assertImportAndExportHidden(view);

        group(view, ReportGrouping.GROUP);
        assertImportAndExportHidden(view);
    }

    private void assertImportAndExportHidden(ReportCustomListView view) {
        assertFalse(UiTestUtils.<JmixButton>getComponent(view, "importBtn").isVisible(), "importBtn");
        assertFalse(UiTestUtils.<JmixButton>getComponent(view, "exportBtn").isVisible(), "exportBtn");
        List.of("createBtn", "editBtn", "removeBtn", "copyBtn", "runReportBtn", "executionsBtn")
                .forEach(id -> assertTrue(UiTestUtils.<JmixButton>getComponent(view, id).isVisible(), id));
    }

    private void assertIconOnlyButtons(ReportCustomListView view) {
        String group = "io.jmix.reportsflowui.view.report";
        assertIconOnly(view, "createBtn", messages.getMessage("actions.Create"));
        assertIconOnly(view, "editBtn", messages.getMessage("actions.Edit"));
        assertIconOnly(view, "removeBtn", messages.getMessage("actions.Remove"));
        assertIconOnly(view, "copyBtn", messages.getMessage(group, "action.copy.text"));
        assertIconOnly(view, "importBtn", messages.getMessage(group, "action.import.description"));
        assertIconOnly(view, "exportBtn", messages.getMessage(group, "action.export.description"));
        assertIconOnly(view, "runReportBtn", messages.getMessage(group, "action.runReport.text"));
        assertIconOnly(view, "executionsBtn", messages.getMessage(group, "action.executions.text"));
    }

    private void assertIconOnly(ReportCustomListView view, String buttonId, String title) {
        JmixButton button = UiTestUtils.getComponent(view, buttonId);
        assertNotNull(button.getIcon(), buttonId);
        assertTrue(button.getText().isEmpty(), buttonId);
        assertEquals(title, button.getTitle(), buttonId);
    }

    @Test
    void theListIsPagedByDefaultAndOffersEveryGrouping() {
        ReportCustomListView view = openList();
        JmixSelect<ReportGrouping> groupingField = UiTestUtils.getComponent(view, "groupingField");

        assertEquals(ReportGrouping.ALL, groupingField.getValue());
        assertEquals(List.of(ReportGrouping.values()), groupingField.getListDataView().getItems().toList(),
                "admin may see every attribute the reports are grouped by");
        assertTrue(listGrid(view).isVisible());
        assertTrue(paginationShown(view));
        assertFalse(tree(view).isVisible());
    }

    @Test
    void groupingByGroupShowsTheReportsUnderTheirGroupWithTheCountAndNoPages() {
        ReportGroupCustom group = saveGroup();
        ReportCustom first = newReport();
        first.setGroup(group);
        first = save(first);
        ReportCustom second = newReport();
        second.setGroup(group);
        second = save(second);
        ReportCustom ungrouped = save(newReport());

        ReportCustomListView view = openList();
        group(view, ReportGrouping.GROUP);

        TreeDataGrid<ReportTreeItem> tree = tree(view);
        assertTrue(tree.isVisible());
        assertFalse(listGrid(view).isVisible());
        assertFalse(paginationShown(view), "the tree has no pages");

        ReportTreeItem firstRow = treeItem(tree, first.getId());
        assertSame(firstRow.getParent(), treeItem(tree, second.getId()).getParent());
        assertEquals(group.getTitle() + " (2)", firstRow.getParent().getCaption());
        assertEquals(2, firstRow.getParent().getCount());
        assertEquals(first.getName(), firstRow.getCaption());

        ReportTreeItem noGroup = treeItem(tree, ungrouped.getId()).getParent();
        assertTrue(noGroup.getCaption().startsWith(
                messages.getMessage(ReportGrouping.class, "ReportGrouping.GROUP.none")), noGroup.getCaption());
        List<ReportTreeItem> groups = tree.getItems().getItems().stream().filter(ReportTreeItem::isGroup).toList();
        assertSame(noGroup, groups.get(groups.size() - 1), "the reports without a group come last");

        assertFalse(tree.getColumnByKey("group").isVisible(), "the group row shows the group");
        assertTrue(tree.getColumnByKey("domain").isVisible());
    }

    @Test
    void groupingByDomainShowsTheReportsUnderTheirDomain() {
        ReportCustom classified = saveClassified();

        ReportCustomListView view = openList();
        group(view, ReportGrouping.DOMAIN);

        TreeDataGrid<ReportTreeItem> tree = tree(view);
        assertEquals(domainA.getShortName() + " (1)", treeItem(tree, classified.getId()).getParent().getCaption());
        assertFalse(tree.getColumnByKey("domain").isVisible(), "the group row shows the domain");
        assertTrue(tree.getColumnByKey("group").isVisible());
    }

    @Test
    void theGroupingInTheUrlOpensTheTree() {
        ReportCustom classified = saveClassified();

        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class)
                .withQueryParameters(QueryParameters.of("grouping", "domain"))
                .navigate();
        ReportCustomListView view = UiTestUtils.getCurrentView();

        assertEquals(ReportGrouping.DOMAIN, UiTestUtils.<JmixSelect<ReportGrouping>>getComponent(view, "groupingField").getValue());
        assertTrue(tree(view).isVisible());
        treeItem(tree(view), classified.getId());
    }

    @Test
    void theButtonsActOnTheReportRowsOfTheTreeAndKeepTheirLook() {
        ReportCustom classified = saveClassified();
        ReportCustomListView view = openList();
        group(view, ReportGrouping.DOMAIN);
        TreeDataGrid<ReportTreeItem> tree = tree(view);
        Map<String, String> buttons = Map.of("editBtn", "edit", "removeBtn", "remove", "copyBtn", "copy",
                "exportBtn", "export", "runReportBtn", "runReport", "executionsBtn", "executions");

        buttons.forEach((button, action) ->
                assertSame(tree.getAction(action), UiTestUtils.<JmixButton>getComponent(view, button).getAction(), button));
        assertSame(listGrid(view).getAction("create"), UiTestUtils.<JmixButton>getComponent(view, "createBtn").getAction());
        assertSame(listGrid(view).getAction("importAction"), UiTestUtils.<JmixButton>getComponent(view, "importBtn").getAction());
        assertIconOnlyButtons(view);

        ReportTreeItem row = treeItem(tree, classified.getId());
        buttons.keySet().forEach(button ->
                assertFalse(UiTestUtils.<JmixButton>getComponent(view, button).isEnabled(), button));

        tree.select(row.getParent());
        buttons.keySet().forEach(button ->
                assertFalse(UiTestUtils.<JmixButton>getComponent(view, button).isEnabled(), "a group row is no report: " + button));

        tree.select(row);
        buttons.keySet().forEach(button ->
                assertTrue(UiTestUtils.<JmixButton>getComponent(view, button).isEnabled(), button));

        group(view, ReportGrouping.ALL);
        assertTrue(listGrid(view).isVisible());
        buttons.forEach((button, action) ->
                assertSame(listGrid(view).getAction(action), UiTestUtils.<JmixButton>getComponent(view, button).getAction(), button));
        assertIconOnlyButtons(view);
    }

    @Test
    void theEditActionOfTheTreeOpensTheEditor() {
        ReportCustom classified = saveClassified();
        ReportCustomListView view = openList();
        group(view, ReportGrouping.DOMAIN);
        TreeDataGrid<ReportTreeItem> tree = tree(view);
        tree.select(treeItem(tree, classified.getId()));

        tree.getAction("edit").actionPerform(tree);

        ReportCustomDetailView editor = assertInstanceOf(ReportCustomDetailView.class, UiTestUtils.getCurrentView());
        assertEquals(classified.getId(), editor.getEditedEntity().getId());
    }

    @Test
    void theCopyActionOfTheTreeReloadsItAndKeepsTheGroupExpanded() {
        ReportCustom classified = saveClassified();
        ReportCustomListView view = openList();
        group(view, ReportGrouping.DOMAIN);
        TreeDataGrid<ReportTreeItem> tree = tree(view);
        ReportTreeItem row = treeItem(tree, classified.getId());
        tree.expand(row.getParent());
        tree.select(row);
        Set<UUID> before = reportIds();

        tree.getAction("copy").actionPerform(tree);

        UUID copyId = reportIds().stream().filter(id -> !before.contains(id)).findFirst().orElseThrow();
        reportCleanup.add(copyId);
        ReportTreeItem copyRow = treeItem(tree, copyId);
        assertEquals(domainA.getShortName() + " (2)", copyRow.getParent().getCaption());
        assertTrue(tree.isExpanded(copyRow.getParent()));
    }

    @Test
    void theRemoveActionOfTheTreeAsksAndRemovesTheReport() {
        ReportCustom classified = saveClassified();
        jdbcTemplate.update("delete from UMIDA_REPORT_SECONDARY_DOMAIN_LINK where REPORT_ID = ?", classified.getId());
        jdbcTemplate.update("delete from UMIDA_REPORT_DATA_PRODUCT_LINK where REPORT_ID = ?", classified.getId());
        ReportCustomListView view = openList();
        group(view, ReportGrouping.DOMAIN);
        TreeDataGrid<ReportTreeItem> tree = tree(view);
        tree.select(treeItem(tree, classified.getId()));

        tree.getAction("remove").actionPerform(tree);
        assertTrue(treeReportIds(tree).contains(classified.getId()), "nothing happens before the confirmation");
        confirmationButton(messages.getMessage("actions.Yes")).click();

        assertTrue(dataManager.load(Report.class).id(classified.getId()).optional().isEmpty());
        assertFalse(treeReportIds(tree).contains(classified.getId()));
        assertFalse(UiTestUtils.<JmixButton>getComponent(view, "removeBtn").isEnabled(), "the row is gone, so is the selection");
    }

    /** The dialog opens when the response is written, which a test without a browser has to trigger. */
    private Button confirmationButton(String text) {
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        return UI.getCurrent().getChildren()
                .filter(Dialog.class::isInstance)
                .map(Dialog.class::cast)
                .flatMap(dialog -> dialog.getFooter().getElement().getChildren())
                .flatMap(element -> element.getComponent().stream())
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theExecutionsActionOfTheTreeOpensTheHistory() {
        ReportCustom classified = saveClassified();
        ReportCustomListView view = openList();
        group(view, ReportGrouping.DOMAIN);
        TreeDataGrid<ReportTreeItem> tree = tree(view);
        tree.select(treeItem(tree, classified.getId()));

        tree.getAction("executions").actionPerform(tree);

        assertInstanceOf(ReportExecutionListView.class, openedDialogWindows.getObject().getCurrentDialog().orElseThrow());
    }

    @Test
    void theTreeHoldsWhatTheFiltersOfThePagedListSelect() {
        ReportCustom classified = saveClassified();
        save(newReport());
        ReportCustomListView view = openList();
        CollectionLoader<Report> loader = ViewControllerUtils.getViewData(view).getLoader("reportsDl");
        // stands for a column header filter, which narrows the loader the same way
        loader.setCondition(PropertyCondition.equal("domain", domainA));

        group(view, ReportGrouping.GROUP);

        List<ReportTreeItem> items = List.copyOf(tree(view).getItems().getItems());
        assertEquals(2, items.size(), "one group with one report: " + items);
        assertEquals(classified.getId(), items.get(1).getReport().getId());
    }

    @Test
    void theMineCheckboxKeepsTheReportsOfTheCurrentUserInTheListAndInTheTree() {
        ReportCustom mine = save(newReport());
        ReportCustom others = saveOfSomeoneElse();
        ReportCustomListView view = openList();
        DataGrid<Report> grid = listGrid(view);
        JmixCheckbox mineField = UiTestUtils.getComponent(view, "mineField");

        assertFalse(mineField.getValue());
        assertTrue(reportIds(grid).containsAll(List.of(mine.getId(), others.getId())));

        mineField.setValue(true);
        assertTrue(reportIds(grid).contains(mine.getId()));
        assertFalse(reportIds(grid).contains(others.getId()));

        group(view, ReportGrouping.DOMAIN);
        treeItem(tree(view), mine.getId());
        assertFalse(treeReportIds(tree(view)).contains(others.getId()), "the tree holds what the paged list selects");

        mineField.setValue(false);
        treeItem(tree(view), others.getId());

        mineField.setValue(true);
        assertFalse(treeReportIds(tree(view)).contains(others.getId()), "the checkbox reloads the tree it is shown over");

        group(view, ReportGrouping.ALL);
        assertFalse(reportIds(grid).contains(others.getId()));

        mineField.setValue(false);
        assertTrue(reportIds(grid).containsAll(List.of(mine.getId(), others.getId())));
    }

    @Test
    void theMineCheckboxWorksTogetherWithTheHeaderFilters() {
        ReportCustom others = saveOfSomeoneElse();
        save(newReport());
        ReportCustomListView view = openList();
        DataGrid<Report> grid = listGrid(view);
        JmixCheckbox mineField = UiTestUtils.getComponent(view, "mineField");
        DataGridHeaderFilter codeFilter = (DataGridHeaderFilter) grid.getColumnByKey("code").getHeaderComponent();
        @SuppressWarnings("unchecked")
        PropertyFilter<String> codeProperty = (PropertyFilter<String>) codeFilter.getPropertyFilter();

        codeProperty.setValue(others.getCode());
        codeFilter.apply();
        assertEquals(Set.of(others.getId()), reportIds(grid));

        mineField.setValue(true);
        assertTrue(reportIds(grid).isEmpty(), "both filters apply");

        codeProperty.setValue(null);
        codeFilter.apply();
        assertFalse(reportIds(grid).isEmpty());
        assertFalse(reportIds(grid).contains(others.getId()), "the mine filter outlives the header filter");
    }

    @Test
    void theTilesShowTheGroupsAsFoldersFirstAndTheReportsAsFilesAfterThem() {
        ReportGroupCustom parent = saveGroup();
        ReportGroupCustom child = saveGroup(parent);
        ReportCustom inParent = newReport();
        inParent.setGroup(parent);
        inParent = save(inParent);
        ReportCustom inChild = newReport();
        inChild.setGroup(child);
        inChild = save(inChild);

        ReportCustomListView view = openList();
        showTiles(view);

        assertFalse(listGrid(view).isVisible());
        assertFalse(tree(view).isVisible());
        assertFalse(paginationShown(view));
        assertFalse(UiTestUtils.<JmixSelect<?>>getComponent(view, "groupingField").isVisible());
        assertTrue(UiTestUtils.<JmixButton>getComponent(view, "tilesModeBtn").hasClassName("active"));
        assertFalse(UiTestUtils.<JmixButton>getComponent(view, "listModeBtn").hasClassName("active"));
        assertFalse(UiTestUtils.<JmixButton>getComponent(view, "folderBackBtn").isEnabled(), "the top level");
        assertFalse(UiTestUtils.<JmixButton>getComponent(view, "folderUpBtn").isEnabled(), "the top level");
        assertTrue(tileCaptions(view).contains(parent.getTitle()));
        assertFalse(tileCaptions(view).contains(child.getTitle()), "a subgroup lies in its parent");

        doubleClick(tile(view, parent.getTitle()));
        assertEquals(List.of(child.getTitle(), inParent.getName()), tileCaptions(view), "the folders first");
        assertEquals(messages.getMessage(ReportCustomListView.class, "folderPath.top") + " / " + parent.getTitle(),
                UiTestUtils.<Span>getComponent(view, "folderPath").getText());
        assertTrue(UiTestUtils.<JmixButton>getComponent(view, "folderBackBtn").isEnabled());

        doubleClick(tile(view, child.getTitle()));
        assertEquals(List.of(inChild.getName()), tileCaptions(view));

        UiTestUtils.<JmixButton>getComponent(view, "folderBackBtn").click();
        assertEquals(List.of(child.getTitle(), inParent.getName()), tileCaptions(view), "back is one level up");
        assertTrue(tile(view, child.getTitle()).isSelected(), "the folder left is selected");

        doubleClick(tile(view, child.getTitle()));
        UiTestUtils.<JmixButton>getComponent(view, "folderUpBtn").click();
        assertTrue(tileCaptions(view).contains(parent.getTitle()), "up is the top level");
        assertTrue(tile(view, parent.getTitle()).isSelected(), "the folder the one left lies in is selected");
    }

    @Test
    void theTilesLookOfTheirGroupOrOfAReport() {
        ReportGroupCustom group = saveGroup();
        group.setIcon("finance");
        group.setColor("#F6C86B");
        group = dataManager.save(group);
        ReportCustom report = newReport();
        report.setGroup(group);
        report = save(report);
        ReportGroupCustom plain = saveGroup();

        ReportCustomListView view = openList();
        showTiles(view);
        assertEquals("#F6C86B", tile(view, group.getTitle()).getStyle().get("--report-tile-color"));
        assertEquals("#CDD0D5", tile(view, plain.getTitle()).getStyle().get("--report-tile-color"), "the default color");

        assertFalse(tile(view, group.getTitle()).hasClassName("report-file"), "a group is no file");

        doubleClick(tile(view, group.getTitle()));
        assertEquals(ReportGroupCustomService.FOLDER_COLOR, tile(view, report.getName()).getStyle().get("--report-tile-color"),
                "a report has the color of a file");
        assertTrue(tile(view, report.getName()).hasClassName("report-file"));
        assertEquals("umida-icons:reports", tile(view, report.getName()).getChildren()
                .flatMap(Component::getChildren)
                .filter(com.vaadin.flow.component.icon.Icon.class::isInstance)
                .findFirst().orElseThrow()
                .getElement().getAttribute("icon"));
    }

    @Test
    void theButtonsActOnTheReportOfTheSelectedTile() {
        ReportGroupCustom group = saveGroup();
        ReportGroupCustom child = saveGroup(group);
        ReportCustom report = newReport();
        report.setGroup(group);
        report = save(report);

        ReportCustomListView view = openList();
        showTiles(view);
        doubleClick(tile(view, group.getTitle()));
        List<String> buttons = List.of("editBtn", "removeBtn", "copyBtn", "runReportBtn", "executionsBtn");

        buttons.forEach(button -> assertFalse(UiTestUtils.<JmixButton>getComponent(view, button).isEnabled(), button));
        assertTrue(UiTestUtils.<JmixButton>getComponent(view, "createBtn").isEnabled());
        assertImportAndExportHidden(view);
        assertIconOnlyButtons(view);

        click(tile(view, child.getTitle()));
        assertTrue(tile(view, child.getTitle()).isSelected());
        buttons.forEach(button -> assertFalse(UiTestUtils.<JmixButton>getComponent(view, button).isEnabled(),
                "a group is no report: " + button));

        click(tile(view, report.getName()));
        assertTrue(tile(view, report.getName()).isSelected());
        assertFalse(tile(view, child.getTitle()).isSelected(), "one tile is selected");
        buttons.forEach(button -> assertTrue(UiTestUtils.<JmixButton>getComponent(view, button).isEnabled(), button));

        UiTestUtils.<JmixButton>getComponent(view, "executionsBtn").click();
        io.jmix.flowui.view.View<?> history = openedDialogWindows.getObject().getCurrentDialog().orElseThrow();
        assertInstanceOf(ReportExecutionListView.class, history);
        history.closeWithDefaultAction();

        Set<UUID> before = reportIds();
        UiTestUtils.<JmixButton>getComponent(view, "copyBtn").click();
        UUID copyId = reportIds().stream().filter(id -> !before.contains(id)).findFirst().orElseThrow();
        reportCleanup.add(copyId);
        assertEquals(3, tileCaptions(view).size(), "the copy lies in the folder of its report");
        assertTrue(tile(view, report.getName()).isSelected(), "the selection outlives the reload");

        UiTestUtils.<JmixButton>getComponent(view, "listModeBtn").click();
        assertTrue(listGrid(view).isVisible());
        assertFalse(UiTestUtils.<Div>getComponent(view, "tilesBox").isVisible());
        assertSame(listGrid(view).getAction("edit"), UiTestUtils.<JmixButton>getComponent(view, "editBtn").getAction());
        assertIconOnlyButtons(view);
    }

    @Test
    void aDoubleClickOnTheTileOfAReportOpensTheEditor() {
        ReportCustom report = save(newReport());
        ReportCustomListView view = openList();
        showTiles(view);

        doubleClick(tile(view, report.getName()));

        ReportCustomDetailView editor = assertInstanceOf(ReportCustomDetailView.class, UiTestUtils.getCurrentView());
        assertEquals(report.getId(), editor.getEditedEntity().getId());
    }

    @Test
    void theTilesAndTheFolderInTheUrlOpenTheFolder() {
        ReportGroupCustom group = saveGroup();
        ReportCustom report = newReport();
        report.setGroup(group);
        report = save(report);

        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class)
                .withQueryParameters(new QueryParameters(Map.of(
                        "mode", List.of("tiles"), "folder", List.of(group.getId().toString()))))
                .navigate();
        ReportCustomListView view = UiTestUtils.getCurrentView();

        assertFalse(listGrid(view).isVisible());
        assertEquals(List.of(report.getName()), tileCaptions(view));
    }

    @Test
    void theMineCheckboxKeepsTheTilesOfTheCurrentUser() {
        ReportGroupCustom mineGroup = saveGroup();
        ReportGroupCustom mineChild = saveGroup(mineGroup);
        ReportCustom mine = newReport();
        mine.setGroup(mineChild);
        save(mine);
        ReportGroupCustom othersGroup = saveGroup();
        ReportCustom others = newReport();
        others.setGroup(othersGroup);
        others = save(others);
        jdbcTemplate.update("update REPORT_REPORT set CREATED_BY = ? where ID = ?", "someone-else", others.getId());
        ReportCustom othersOnTop = saveOfSomeoneElse();
        ReportGroupCustom empty = saveGroup();

        ReportCustomListView view = openList();
        showTiles(view);
        assertTrue(tileCaptions(view).containsAll(List.of(
                mineGroup.getTitle(), othersGroup.getTitle(), empty.getTitle(), othersOnTop.getName())));

        UiTestUtils.<JmixCheckbox>getComponent(view, "mineField").setValue(true);
        assertTrue(tileCaptions(view).contains(mineGroup.getTitle()), "a report of the user lies below it");
        assertFalse(tileCaptions(view).contains(othersGroup.getTitle()));
        assertFalse(tileCaptions(view).contains(empty.getTitle()));
        assertFalse(tileCaptions(view).contains(othersOnTop.getName()));
    }

    /**
     * The pagination is hidden by its style and stays visible to Vaadin, see ReportCustomListView#setPaginationShown:
     * the JavaScript it runs while hidden is sent at once, so none is left to be answered once it is shown.
     */
    private boolean paginationShown(ReportCustomListView view) {
        SimplePagination pagination = UiTestUtils.getComponent(view, "pagination");
        assertTrue(pagination.isVisible(), "hidden by its style only");
        return !"none".equals(pagination.getStyle().get("display"));
    }

    private void showTiles(ReportCustomListView view) {
        UiTestUtils.<JmixButton>getComponent(view, "tilesModeBtn").click();
    }

    private List<ReportTile> tiles(ReportCustomListView view) {
        return UiTestUtils.<Div>getComponent(view, "tilesBox").getChildren()
                .map(ReportTile.class::cast)
                .toList();
    }

    private List<String> tileCaptions(ReportCustomListView view) {
        return tiles(view).stream().map(tile -> tile.getTitle().orElseThrow()).toList();
    }

    /** Throws if no tile shows the caption. */
    private ReportTile tile(ReportCustomListView view, String caption) {
        return tiles(view).stream()
                .filter(tile -> caption.equals(tile.getTitle().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no tile " + caption));
    }

    private static void click(ReportTile tile) {
        ComponentUtil.fireEvent(tile, new ClickEvent<>(tile, true, 0, 0, 0, 0, 1, 0, false, false, false, false));
    }

    /** What the browser sends: a click, then a click with the count of two. */
    private static void doubleClick(ReportTile tile) {
        click(tile);
        ComponentUtil.fireEvent(tile, new ClickEvent<>(tile, true, 0, 0, 0, 0, 2, 0, false, false, false, false));
    }

    @Test
    void theMineCheckboxGoesBackToTheFirstPage() {
        save(newReport());
        ReportCustomListView view = openList();
        CollectionLoader<Report> loader = ViewControllerUtils.getViewData(view).getLoader("reportsDl");
        loader.setFirstResult(1000);

        UiTestUtils.<JmixCheckbox>getComponent(view, "mineField").setValue(true);

        assertEquals(0, loader.getFirstResult());
        assertFalse(reportIds(listGrid(view)).isEmpty());
    }

    private ReportCustom saveOfSomeoneElse() {
        ReportCustom report = save(newReport());
        jdbcTemplate.update("update REPORT_REPORT set CREATED_BY = ? where ID = ?", "someone-else", report.getId());
        return report;
    }

    private ReportCustomListView openList() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        return UiTestUtils.getCurrentView();
    }

    private void group(ReportCustomListView view, ReportGrouping grouping) {
        UiTestUtils.<JmixSelect<ReportGrouping>>getComponent(view, "groupingField").setValue(grouping);
    }

    private DataGrid<Report> listGrid(ReportCustomListView view) {
        return UiTestUtils.getComponent(view, "reportsDataGrid");
    }

    private TreeDataGrid<ReportTreeItem> tree(ReportCustomListView view) {
        return UiTestUtils.getComponent(view, "reportsTreeDataGrid");
    }

    /** Throws if the report is not shown in the tree. */
    private ReportTreeItem treeItem(TreeDataGrid<ReportTreeItem> tree, UUID reportId) {
        return tree.getItems().getItem(reportId);
    }

    private Set<UUID> treeReportIds(TreeDataGrid<ReportTreeItem> tree) {
        return tree.getItems().getItems().stream().map(ReportTreeItem::getId).collect(Collectors.toSet());
    }

    private Set<UUID> reportIds(DataGrid<Report> grid) {
        return grid.getItems().getItems().stream().map(Report::getId).collect(Collectors.toSet());
    }

    private Set<UUID> reportIds() {
        return dataManager.loadValues("select r.id from report_Report r")
                .properties("id").list().stream().map(v -> v.<UUID>getValue("id")).collect(Collectors.toSet());
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
