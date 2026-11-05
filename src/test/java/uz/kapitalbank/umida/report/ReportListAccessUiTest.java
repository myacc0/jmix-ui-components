package uz.kapitalbank.umida.report;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.app.inputdialog.InputDialog;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.reports.ReportsPersistence;
import io.jmix.reports.ReportsSerialization;
import io.jmix.reports.entity.Report;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.dto.report.ReportTreeItem;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.enums.report.ReportAccessGrantSource;
import uz.kapitalbank.umida.enums.report.ReportGrouping;
import uz.kapitalbank.umida.security.ReportAuthorRole;
import uz.kapitalbank.umida.security.UiMinimalRole;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.service.report.ReportAccessService.OwnerMember;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.ReportTestData;
import uz.kapitalbank.umida.view.report.ReportCustomDetailView;
import uz.kapitalbank.umida.view.report.ReportCustomListView;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the list of the reports offers by the rights of the user: the authors' buttons, running, the history
 * and the request of access, in the paged list and in the grouped one.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportListAccessUiTest {

    @Autowired
    DataManager dataManager;
    @Autowired
    Metadata metadata;
    @Autowired
    ReportsPersistence reportsPersistence;
    @Autowired
    ReportsSerialization reportsSerialization;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    SystemAuthenticator systemAuthenticator;
    @Autowired
    ReportAccessService accessService;

    private ReportTestData data;
    private ReportCustom ownReport;
    private ReportCustom grantedReport;
    private ReportCustom closedReport;
    private User member;
    private OrgStructureEmployee memberEmployee;
    private User author;

    @BeforeEach
    void setUp() {
        data = new ReportTestData(dataManager, metadata, reportsPersistence, reportsSerialization, jdbcTemplate);
        OrgStructureSubdivision owner = data.createSubdivision(true);
        ownReport = data.createReport(owner);
        grantedReport = data.createReport(data.createSubdivision(true));
        closedReport = data.createReport(data.createSubdivision(true));

        OrgStructureEmployee employee = data.createEmployeeIn(owner, false);
        member = data.createViewer(employee);
        memberEmployee = employee;
        accessService.saveOwnerMembers(ownReport, List.of(new OwnerMember(employee, false)));
        accessService.grant(grantedReport, List.of(employee), ReportAccessGrantSource.DIRECT, null);

        author = data.createUser(null);
        data.assignRole(author, UiMinimalRole.CODE, RoleAssignmentRoleType.RESOURCE);
        data.assignRole(author, ReportAuthorRole.CODE, RoleAssignmentRoleType.RESOURCE);
    }

    @AfterEach
    void tearDown() {
        systemAuthenticator.end();
        data.cleanup();
    }

    @Test
    void aViewerSeesNoButtonOfTheAuthorsAndActsByTheRightsOfEachReport() {
        ReportCustomListView view = openListAs(member);

        Stream.of("createBtn", "editBtn", "removeBtn", "copyBtn", "importBtn", "exportBtn")
                .forEach(id -> assertFalse(button(view, id).isVisible(), id + " is the authors'"));
        assertFalse(UiTestUtils.<JmixCheckbox>getComponent(view, "mineField").isVisible());
        assertTrue(button(view, "requestAccessBtn").isVisible());

        DataGrid<Report> grid = UiTestUtils.getComponent(view, "reportsDataGrid");
        select(grid, ownReport);
        assertButtons(view, true, true, false, "a member runs and sees the own runs");
        select(grid, grantedReport);
        assertButtons(view, true, false, false, "a grant gives no history");
        select(grid, closedReport);
        assertButtons(view, false, false, true, "no access: request it");

        button(view, "requestAccessBtn").click();
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        InputDialog dialog = openedInputDialog().orElseThrow(() -> new AssertionError("the request asks for the reason"));
        assertEquals(closedReport.getName(), dialog.getValue("reportName"), "the dialog shows the report");
        assertEquals(memberEmployee.getFullName(), dialog.getValue("employee"), "and the requester");
    }

    @Test
    void theGroupedListFollowsTheSameRights() {
        ReportCustomListView view = openListAs(member);
        UiTestUtils.<JmixSelect<ReportGrouping>>getComponent(view, "groupingField").setValue(ReportGrouping.GROUP);
        TreeDataGrid<ReportTreeItem> tree = UiTestUtils.getComponent(view, "reportsTreeDataGrid");

        tree.select(treeItem(tree, closedReport));
        assertButtons(view, false, false, true, "the tree: no access");
        tree.select(treeItem(tree, ownReport));
        assertButtons(view, true, true, false, "the tree: a member");
        assertFalse(button(view, "editBtn").isVisible(), "still no editing in the tree");
    }

    @Test
    void anAuthorHasEveryButtonButTheRequest() {
        ReportCustomListView view = openListAs(author);

        Stream.of("createBtn", "editBtn", "removeBtn", "copyBtn")
                .forEach(id -> assertTrue(button(view, id).isVisible(), id + " is shown to an author"));
        assertTrue(UiTestUtils.<JmixCheckbox>getComponent(view, "mineField").isVisible());
        assertFalse(button(view, "requestAccessBtn").isVisible());

        select(UiTestUtils.getComponent(view, "reportsDataGrid"), closedReport);
        assertTrue(button(view, "runReportBtn").isEnabled());
        assertTrue(button(view, "executionsBtn").isEnabled());
        assertTrue(button(view, "editBtn").isEnabled());
    }

    @Test
    void aDoubleClickOnAReportInThePagedListOpensTheEditorForAnAuthor() {
        ReportCustomListView view = openListAs(author);

        doubleClick(UiTestUtils.getComponent(view, "reportsDataGrid"), closedReport);

        ReportCustomDetailView editor = assertInstanceOf(ReportCustomDetailView.class, UiTestUtils.getCurrentView());
        assertEquals(closedReport.getId(), editor.getEditedEntity().getId());
    }

    @Test
    void aDoubleClickOnAReportTheViewerMayNotRunDoesNothing() {
        ReportCustomListView view = openListAs(member);

        doubleClick(UiTestUtils.getComponent(view, "reportsDataGrid"), closedReport);

        assertSame(view, UiTestUtils.getCurrentView(), "no editor for a viewer");
    }

    private ReportCustomListView openListAs(User user) {
        systemAuthenticator.begin(user.getUsername());
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        return UiTestUtils.getCurrentView();
    }

    private static JmixButton button(ReportCustomListView view, String id) {
        return UiTestUtils.getComponent(view, id);
    }

    private static void assertButtons(ReportCustomListView view, boolean run, boolean history, boolean request,
                                      String message) {
        assertEquals(run, button(view, "runReportBtn").isEnabled(), message + ": run");
        assertEquals(history, button(view, "executionsBtn").isEnabled(), message + ": history");
        assertEquals(request, button(view, "requestAccessBtn").isEnabled(), message + ": request");
    }

    private static void select(DataGrid<Report> grid, ReportCustom report) {
        Report item = grid.getItems().getItems().stream()
                .filter(r -> r.getId().equals(report.getId()))
                .findFirst()
                .orElseGet(() -> fail("the report is on the first page of the list"));
        grid.select(item);
    }

    /** A double click as the browser sends it: on the row of the report, by the key the grid gave it. */
    private static void doubleClick(DataGrid<Report> grid, ReportCustom report) {
        Report item = grid.getItems().getItems().stream()
                .filter(r -> r.getId().equals(report.getId()))
                .findFirst()
                .orElseGet(() -> fail("the report is on the first page of the list"));
        String key = grid.getDataCommunicator().getKeyMapper().key(item);
        ComponentUtil.fireEvent(grid, new ItemDoubleClickEvent<>(grid, true, key, null,
                0, 0, 0, 0, 2, 0, false, false, false, false));
    }

    private static ReportTreeItem treeItem(TreeDataGrid<ReportTreeItem> tree, ReportCustom report) {
        return tree.getItems().getItems().stream()
                .filter(item -> item.getReport() != null && item.getReport().getId().equals(report.getId()))
                .findFirst()
                .orElseGet(() -> fail("the report is in the tree"));
    }

    private static java.util.Optional<InputDialog> openedInputDialog() {
        return UI.getCurrent().getChildren()
                .flatMap(ReportListAccessUiTest::selfAndDescendants)
                .filter(InputDialog.class::isInstance)
                .map(InputDialog.class::cast)
                .findFirst();
    }

    private static Stream<Component> selfAndDescendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(ReportListAccessUiTest::selfAndDescendants));
    }
}
