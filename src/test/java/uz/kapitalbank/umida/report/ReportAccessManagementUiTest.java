package uz.kapitalbank.umida.report;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.InputDialog;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.headerfilter.DataGridHeaderFilter;
import io.jmix.flowui.component.propertyfilter.PropertyFilter;
import io.jmix.flowui.component.main.JmixListMenu;
import io.jmix.flowui.component.radiobuttongroup.JmixRadioButtonGroup;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.kit.component.main.ListMenu;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.reports.ReportsPersistence;
import io.jmix.reports.ReportsSerialization;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.dto.report.ReportOwnerMemberItem;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportAccessGrant;
import uz.kapitalbank.umida.entity.report.ReportAccessRequest;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.enums.report.ReportAccessGrantSource;
import uz.kapitalbank.umida.enums.report.ReportAccessRequestStatus;
import uz.kapitalbank.umida.security.ReportAuthorRole;
import uz.kapitalbank.umida.security.UiMinimalRole;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.ReportTestData;
import uz.kapitalbank.umida.view.report.access.ReportAccessGrantView;
import uz.kapitalbank.umida.view.report.access.ReportAccessManagementView;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The access management as the head of an owner, someone who manages nothing and an author use it: the
 * tabs, the filters of the columns, the number of the pending requests on the tab and in the menu, the decisions, the
 * grants to a subdivision and the revocations.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportAccessManagementUiTest {

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
    private ReportCustom otherReport;
    private User head;
    private User requester;
    private User outsider;
    private OrgStructureSubdivision team;
    private OrgStructureSubdivision teamUnit;
    private OrgStructureEmployee teamMember;
    private ReportAccessRequest request;

    @BeforeEach
    void setUp() {
        data = new ReportTestData(dataManager, metadata, reportsPersistence, reportsSerialization, jdbcTemplate);
        OrgStructureSubdivision owner = data.createSubdivision(true);
        ownReport = data.createReport(owner);
        otherReport = data.createReport(data.createSubdivision(true));
        head = data.createViewer(data.createEmployeeIn(owner, true));
        requester = data.createViewer(data.createEmployeeIn(data.createSubdivision(true), false));
        outsider = data.createViewer(data.createEmployeeIn(data.createSubdivision(true), false));
        team = data.createSubdivision(true);
        data.createEmployeeIn(team, true);
        teamUnit = data.createSubdivision(team, true);
        teamMember = data.createEmployeeIn(teamUnit, false);

        request = systemAuthenticator.withUser(requester.getUsername(),
                () -> accessService.requestAccess(ownReport, "for the audit"));
        systemAuthenticator.withUser(requester.getUsername(), () -> accessService.requestAccess(otherReport, null));
    }

    @AfterEach
    void tearDown() {
        systemAuthenticator.end();
        data.cleanup();
    }

    @Test
    void theHeadSeesTheRequestsOfTheOwnReportsAndTheirNumberOnTheTabAndInTheMenu() {
        ReportAccessManagementView view = open(head);

        assertEquals(Set.of(request.getId()), ids(requestsGrid(view)), "not the request for the other report");
        assertEquals("1", badgeOf(UiTestUtils.getComponent(view, "tabSheet.requestsTab")));
        ListMenu.MenuItem menuItem = menu().getMenuItem("reportAccessManagement");
        assertNotNull(menuItem, "the head keeps the menu item");
        Span menuBadge = (Span) menuItem.getSuffixComponent();
        assertEquals("1", menuBadge.getText());
        assertNotEquals("none", menuBadge.getStyle().get("display"), "the number is shown");
    }

    @Test
    void theOwnRequestOfTheHeadForAnotherReportIsNotAmongTheRequestsToDecide() {
        ReportAccessRequest own = systemAuthenticator.withUser(head.getUsername(),
                () -> accessService.requestAccess(otherReport, "for the head"));

        ReportAccessManagementView view = open(head);

        Set<UUID> listed = ids(requestsGrid(view));
        assertFalse(listed.contains(own.getId()), "the head's own request is decided by the owner of the other report");
        assertEquals(Set.of(request.getId()), listed);
        assertEquals("1", badgeOf(UiTestUtils.getComponent(view, "tabSheet.requestsTab")));
    }

    @Test
    void someoneWhoManagesNothingSeesNeitherTheMenuItemNorTheLists() {
        ReportAccessManagementView view = open(outsider);

        assertFalse(UiTestUtils.<JmixTabSheet>getComponent(view, "tabSheet").isVisible());
        assertTrue(UiTestUtils.<Span>getComponent(view, "noAccessText").isVisible());
        assertNull(menu().getMenuItem("reportAccessManagement"), "the menu item is removed");
    }

    @Test
    void theHeadApprovesARequestInTheDialogAndTheGrantShowsUp() {
        ReportAccessManagementView view = open(head);
        // as the user does: the content of a tab is attached once the tab is opened
        JmixTabSheet tabs = UiTestUtils.getComponent(view, "tabSheet");
        tabs.setSelectedTab(UiTestUtils.getComponent(view, "tabSheet.requestsTab"));
        DataGrid<ReportAccessRequest> requests = requestsGrid(view);
        requests.select(requests.getItems().getItems().iterator().next());
        assertTrue(UiTestUtils.<JmixButton>getComponent(view, "approveBtn").isEnabled(), "a pending request");

        UiTestUtils.<JmixButton>getComponent(view, "approveBtn").click();
        pressOk();

        assertEquals(ReportAccessRequestStatus.APPROVED,
                dataManager.load(ReportAccessRequest.class).id(request.getId()).one().getStatus());
        assertEquals(Set.of(ReportAccessRequestStatus.APPROVED), requestsGrid(view).getItems().getItems().stream()
                .map(ReportAccessRequest::getStatus).collect(Collectors.toSet()), "the request stays, approved");
        DataGrid<ReportAccessGrant> grants = UiTestUtils.getComponent(view, "grantsDataGrid");
        assertEquals(1, grants.getItems().getItems().size(), "the new grant is listed");
        assertEquals("", badgeOf(UiTestUtils.getComponent(view, "tabSheet.requestsTab")), "the number is gone");
    }

    @Test
    void theHeadGivesAccessToASubdivisionAndRevokesItAgain() {
        ReportAccessManagementView view = open(head);

        UiTestUtils.<JmixButton>getComponent(view, "grantBtn").click();
        ReportAccessGrantView dialog = opened(ReportAccessGrantView.class).orElseGet(() -> fail("the dialog opens"));
        UiTestUtils.<EntityComboBox<ReportCustom>>getComponent(dialog, "reportField").setValue(ownReport);
        UiTestUtils.<JmixRadioButtonGroup<String>>getComponent(dialog, "targetField").setValue("subdivision");
        UiTestUtils.<EntityComboBox<OrgStructureSubdivision>>getComponent(dialog, "subdivisionField")
                .setValue(dataManager.load(OrgStructureSubdivision.class).id(team.getId()).one());
        DataGrid<ReportOwnerMemberItem> employees = UiTestUtils.getComponent(dialog, "employeesDataGrid");
        assertEquals(2, employees.getSelectedItems().size(), "the head of the team and its member, all ticked");
        UiTestUtils.<JmixButton>getComponent(dialog, "saveBtn").click();

        DataGrid<ReportAccessGrant> grants = UiTestUtils.getComponent(view, "grantsDataGrid");
        assertEquals(2, grants.getItems().getItems().size());
        assertTrue(grants.getItems().getItems().stream()
                .allMatch(g -> g.getSource() == ReportAccessGrantSource.SUBDIVISION));

        // the filters of the columns: the subdivision the member held a position in, and the member's name
        filterColumn(grants, "position.subdivision.name", teamUnit.getName());
        assertEquals(1, grants.getItems().getItems().size(), "the member, in the subdivision below the team");
        assertEquals(teamMember.getId(), grants.getItems().getItems().iterator().next().getEmployee().getId());
        filterColumn(grants, "employee.fullName", teamMember.getFullName());
        assertEquals(1, grants.getItems().getItems().size());
        grants.select(grants.getItems().getItems());
        UiTestUtils.<JmixButton>getComponent(view, "revokeBtn").click();
        pressOk();

        assertEquals(0, grants.getItems().getItems().size(), "the revoked grant leaves the access tab");
        assertTrue(dataManager.load(ReportAccessGrant.class)
                .query("select g from umida_ReportAccessGrant g where g.employee.id = :id")
                .parameter("id", teamMember.getId())
                .one().getRevokedAt() != null, "kept in the journal as revoked");
    }

    @Test
    void anAuthorSeesEveryRequestAndNarrowsThemByTheCodeOfTheReport() {
        User author = data.createUser(null);
        data.assignRole(author, UiMinimalRole.CODE, RoleAssignmentRoleType.RESOURCE);
        data.assignRole(author, ReportAuthorRole.CODE, RoleAssignmentRoleType.RESOURCE);
        ReportAccessManagementView view = open(author);

        Set<UUID> all = ids(requestsGrid(view));
        assertTrue(all.size() >= 2 && all.contains(request.getId()));
        // the code is given by the editor of the report, which the test data passes by
        String code = "RT-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("update REPORT_REPORT set CODE = ? where ID = ?", code, ownReport.getId());
        filterColumn(requestsGrid(view), "report.code", code);
        assertEquals(Set.of(request.getId()), ids(requestsGrid(view)));
    }

    // ---------------------------------------------------------------------

    private ReportAccessManagementView open(User user) {
        systemAuthenticator.begin(user.getUsername());
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportAccessManagementView.class).navigate();
        return UiTestUtils.getCurrentView();
    }

    /** Sets the value in the filter in the header of the column and applies it, as the user does. */
    @SuppressWarnings("unchecked")
    private static void filterColumn(DataGrid<?> grid, String columnKey, Object value) {
        DataGridHeaderFilter filter = (DataGridHeaderFilter) grid.getColumnByKey(columnKey).getHeaderComponent();
        ((PropertyFilter<Object>) filter.getPropertyFilter()).setValue(value);
        filter.apply();
    }

    private static DataGrid<ReportAccessRequest> requestsGrid(ReportAccessManagementView view) {
        return UiTestUtils.getComponent(view, "requestsDataGrid");
    }

    private static Set<UUID> ids(DataGrid<ReportAccessRequest> grid) {
        return grid.getItems().getItems().stream().map(ReportAccessRequest::getId).collect(Collectors.toSet());
    }

    /** The text of the visible number on the tab, empty when there is none. */
    private static String badgeOf(Tab tab) {
        return tab.getChildren()
                .filter(c -> c instanceof Span span && !"none".equals(span.getStyle().get("display")))
                .map(c -> ((Span) c).getText())
                .findFirst()
                .orElse("");
    }

    private static JmixListMenu menu() {
        return descendants(UI.getCurrent())
                .filter(JmixListMenu.class::isInstance)
                .map(JmixListMenu.class::cast)
                .findFirst()
                .orElseGet(() -> fail("the main view has its menu"));
    }

    /** Presses OK in the input dialog just opened. */
    private static void pressOk() {
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        InputDialog dialog = opened(InputDialog.class).orElseGet(() -> fail("the input dialog opens"));
        descendants(dialog)
                .filter(JmixButton.class::isInstance)
                .map(JmixButton.class::cast)
                .filter(button -> button.getAction() instanceof DialogAction action
                        && action.getType() == DialogAction.Type.OK)
                .findFirst()
                .orElseGet(() -> fail("the dialog has its OK button"))
                .click();
    }

    private static <V extends Component> Optional<V> opened(Class<V> viewClass) {
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        return descendants(UI.getCurrent()).filter(viewClass::isInstance).map(viewClass::cast).findFirst();
    }

    private static Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(ReportAccessManagementUiTest::descendants));
    }
}
