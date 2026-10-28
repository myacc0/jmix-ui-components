package uz.kapitalbank.umida.report;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Metadata;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.flowui.view.ViewRegistry;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.ViewOpeningAction;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.util.OperationResult;
import io.jmix.flowui.view.ViewControllerUtils;
import io.jmix.reports.ReportGroupRepository;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.report.ReportGroupCustom;
import uz.kapitalbank.umida.service.report.ReportGroupCustomService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.view.report.ReportGroupCustomDetailView;
import uz.kapitalbank.umida.view.report.ReportGroupCustomListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The report group of the Reports add-on replaced by {@link ReportGroupCustom}: what the add-on
 * creates and loads is the replacing entity, the hierarchy cannot get a cycle, and the list and the
 * editor open and show the icon and the color of a group.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportGroupCustomTest {

    @Autowired
    DataManager dataManager;
    @Autowired
    ReportGroupRepository reportGroupRepository;
    @Autowired
    ReportGroupCustomService reportGroupCustomService;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    ViewRegistry viewRegistry;
    @Autowired
    Metadata metadata;

    private final List<ReportGroupCustom> cleanup = new ArrayList<>();
    private final List<Report> reportCleanup = new ArrayList<>();

    private String suffix;
    private ReportGroupCustom root;
    private ReportGroupCustom child;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        root = saveGroup("Root " + suffix, null, "reports", "#FD9575");
        child = saveGroup("Child " + suffix, root, null, null);
    }

    @AfterEach
    void tearDown() {
        // reports and children first: a soft-deleted row would keep its foreign key, so remove them for real
        reportCleanup.forEach(report -> removeForReal(Report.class, report.getId()));
        reportCleanup.clear();
        List<ReportGroupCustom> reversed = new ArrayList<>(cleanup);
        Collections.reverse(reversed);
        reversed.forEach(group -> removeForReal(ReportGroupCustom.class, group.getId()));
        cleanup.clear();
    }

    /** Reloaded, the soft-deleted rows included: a test may have saved a newer version or removed the row softly. */
    private <E> void removeForReal(Class<E> entityClass, Object id) {
        dataManager.load(entityClass)
                .id(id)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .optional()
                .ifPresent(current -> dataManager.save(new SaveContext()
                        .removing(current)
                        .setHint(PersistenceHints.SOFT_DELETION, false)));
    }

    @Test
    void theAddOnCreatesAndLoadsTheReplacingEntity() {
        assertInstanceOf(ReportGroupCustom.class, dataManager.create(ReportGroup.class));

        // the add-on queries report_ReportGroup, which is rewritten to the replacing entity
        ReportGroup loaded = dataManager.load(ReportGroup.class)
                .query("select g from report_ReportGroup g where g.id = :id")
                .parameter("id", child.getId())
                .one();
        ReportGroupCustom group = assertInstanceOf(ReportGroupCustom.class, loaded);

        ReportGroupCustom parent = dataManager.load(ReportGroupCustom.class)
                .id(group.getParent().getId())
                .one();
        assertEquals(root.getId(), parent.getId());
        assertEquals("reports", parent.getIcon());
        assertEquals("#FD9575", parent.getColor());

        assertTrue(reportGroupRepository.loadAll().stream().anyMatch(g -> g.getId().equals(root.getId())),
                "the add-on's repository still lists the groups");
    }

    @Test
    void theAddOnViewsStayTheDefaultOnes() {
        // the add-on and its pickers look the views up by the replaced entity
        for (Class<?> entityClass : List.of(ReportGroup.class, ReportGroupCustom.class)) {
            MetaClass metaClass = metadata.getClass(entityClass);
            assertEquals("report_ReportGroup.detail", viewRegistry.getDetailViewInfo(metaClass).getId());
            assertEquals("report_ReportGroup.list", viewRegistry.getListViewInfo(metaClass).getId());
            assertEquals("report_ReportGroup.list", viewRegistry.getLookupViewInfo(metaClass).getId());
        }
    }

    @Test
    void theListOpensItsOwnEditor() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportGroupCustomListView.class).navigate();
        ReportGroupCustomListView view = UiTestUtils.getCurrentView();
        DataGrid<ReportGroupCustom> grid = UiTestUtils.getComponent(view, "reportGroupsDataGrid");

        for (String actionId : List.of("createAction", "editAction")) {
            Action action = grid.getAction(actionId);
            assertEquals("umida_ReportGroupCustom.detail", ((ViewOpeningAction) action).getViewId(), actionId);
        }
        assertTrue(viewRegistry.hasView("umida_ReportGroupCustom.detail"));
    }

    @Test
    void aGroupCannotBePlacedBelowItself() {
        assertEquals(Set.of(root.getId(), child.getId()), reportGroupCustomService.getSelfAndDescendantIds(root));
        assertTrue(reportGroupCustomService.isCyclicParent(root, root));
        assertTrue(reportGroupCustomService.isCyclicParent(root, child));
        assertFalse(reportGroupCustomService.isCyclicParent(child, root));
        assertFalse(reportGroupCustomService.isCyclicParent(root, null));
    }

    @Test
    void aRemovedGroupUnlinksItsSubgroupsAndReports() {
        Report report = dataManager.create(Report.class);
        report.setName("Report " + suffix);
        report.setGroup(root);
        report = dataManager.save(report);
        reportCleanup.add(report);

        // removed softly, as the list removes it
        dataManager.remove(dataManager.load(ReportGroupCustom.class).id(root.getId()).one());

        ReportGroupCustom unlinkedChild = dataManager.load(ReportGroupCustom.class)
                .id(child.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("parent"))
                .one();
        assertNull(unlinkedChild.getParent());
        Report unlinkedReport = dataManager.load(Report.class)
                .id(report.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("group"))
                .one();
        assertNull(unlinkedReport.getGroup());
    }

    @Test
    void theIconsAreTheIconsetFiles() {
        List<String> icons = reportGroupCustomService.getIcons();
        assertTrue(icons.contains(ReportGroupCustomService.DEFAULT_ICON));
        assertEquals(icons.size(), Set.copyOf(icons).size(), "no icon twice");
        // the list is kept by hand: each name must be a file the iconset is registered from
        for (String icon : icons) {
            assertNotNull(getClass().getResource("/META-INF/resources/icons/" + icon + ".svg"), icon);
        }

        assertEquals(ReportGroupCustomService.DEFAULT_ICON, reportGroupCustomService.getIconOrDefault(null));
        assertEquals(ReportGroupCustomService.DEFAULT_COLOR, reportGroupCustomService.getColorOrDefault(" "));
        assertEquals(ReportGroupCustomService.COLORS.size(), Set.copyOf(ReportGroupCustomService.COLORS).size());
        assertEquals(ReportGroupCustomService.DEFAULT_COLOR, ReportGroupCustomService.COLORS.getFirst());
    }

    @Test
    void theListShowsTheGroups() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportGroupCustomListView.class).navigate();
        ReportGroupCustomListView view = UiTestUtils.getCurrentView();

        DataGrid<ReportGroupCustom> grid = UiTestUtils.getComponent(view, "reportGroupsDataGrid");
        List<UUID> ids = grid.getGenericDataView().getItems().map(ReportGroupCustom::getId).toList();
        assertTrue(ids.containsAll(List.of(root.getId(), child.getId())));
        assertNotNull(grid.getColumnByKey("badge"));
        assertNotNull(grid.getColumnByKey("title"));
        assertNotNull(grid.getColumnByKey("parent"));
    }

    @Test
    void theParentLookupExcludesTheGroupAndItsSubgroups() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportGroupCustomListView.class).navigate();
        ReportGroupCustomListView view = UiTestUtils.getCurrentView();
        view.setExcludedGroupIds(reportGroupCustomService.getSelfAndDescendantIds(root));
        ViewControllerUtils.getViewData(view).loadAll();

        DataGrid<ReportGroupCustom> grid = UiTestUtils.getComponent(view, "reportGroupsDataGrid");
        List<UUID> ids = grid.getGenericDataView().getItems().map(ReportGroupCustom::getId).toList();
        assertFalse(ids.contains(root.getId()));
        assertFalse(ids.contains(child.getId()));
    }

    @Test
    void theEditorPicksTheIconAndTheColor() {
        ReportGroupCustomDetailView view = edit(child);
        ReportGroupCustom group = view.getEditedEntity();

        Span previewTitle = UiTestUtils.getComponent(view, "previewTitle");
        assertEquals("Child " + suffix, previewTitle.getText());

        Div iconPalette = UiTestUtils.getComponent(view, "iconPalette");
        assertEquals(reportGroupCustomService.getIcons().size(), iconPalette.getComponentCount());
        Div colorPalette = UiTestUtils.getComponent(view, "colorPalette");
        assertEquals(ReportGroupCustomService.COLORS.size(), colorPalette.getComponentCount());
        // no color stored: the default one is shown picked
        assertTrue(tile(colorPalette, ReportGroupCustomService.DEFAULT_COLOR).getClassNames().contains("selected"));

        click(tile(iconPalette, "star"));
        assertEquals("star", group.getIcon());
        assertTrue(tile(iconPalette, "star").getClassNames().contains("selected"));
        click(tile(iconPalette, "star"));
        assertNull(group.getIcon(), "an icon picked again is cleared");

        click(tile(colorPalette, "#8FC7F7"));
        assertEquals("#8FC7F7", group.getColor());
        assertTrue(tile(colorPalette, "#8FC7F7").getClassNames().contains("selected"));
        assertFalse(tile(colorPalette, ReportGroupCustomService.DEFAULT_COLOR).getClassNames().contains("selected"));

        click(tile(iconPalette, "star"));
        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());
        ReportGroupCustom saved = dataManager.load(ReportGroupCustom.class).id(child.getId()).one();
        assertEquals("star", saved.getIcon());
        assertEquals("#8FC7F7", saved.getColor());
    }

    @Test
    void theEditorRejectsACyclicParent() {
        ReportGroupCustomDetailView view = edit(root);
        view.getEditedEntity().setParent(child);
        assertEquals(OperationResult.Status.FAIL, view.save().getStatus());
    }

    @Test
    void theEditorCreatesAGroup() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), ReportGroupCustom.class)
                .newEntity()
                .withViewClass(ReportGroupCustomDetailView.class)
                .navigate();
        ReportGroupCustomDetailView view = UiTestUtils.getCurrentView();
        view.getEditedEntity().setTitle("New " + suffix);
        view.getEditedEntity().setParent(root);

        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());
        ReportGroupCustom saved = dataManager.load(ReportGroupCustom.class).id(view.getEditedEntity().getId()).one();
        cleanup.add(saved);
        assertEquals("New " + suffix, saved.getTitle());
        assertNull(saved.getCode(), "the code is not generated");
        assertNull(saved.getIcon());
        assertNull(saved.getColor());
    }

    private ReportGroupCustomDetailView edit(ReportGroupCustom group) {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), ReportGroupCustom.class)
                .editEntity(group)
                .withViewClass(ReportGroupCustomDetailView.class)
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    private static Div tile(Div palette, String title) {
        return palette.getChildren()
                .map(Div.class::cast)
                .filter(tile -> title.equals(tile.getElement().getAttribute("title")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no tile " + title));
    }

    private static void click(Div tile) {
        ComponentUtil.fireEvent(tile, new com.vaadin.flow.component.ClickEvent<>(tile));
    }

    private ReportGroupCustom saveGroup(String title, ReportGroupCustom parent, String icon, String color) {
        ReportGroupCustom group = dataManager.create(ReportGroupCustom.class);
        group.setTitle(title);
        group.setParent(parent);
        group.setIcon(icon);
        group.setColor(color);
        ReportGroupCustom saved = dataManager.save(group);
        cleanup.add(saved);
        return saved;
    }
}
