package uz.kapitalbank.umida.report;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.internal.nodefeature.VirtualChildrenList;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.InputDialog;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.headerfilter.DataGridHeaderFilter;
import io.jmix.flowui.component.propertyfilter.PropertyFilter;
import io.jmix.flowui.component.main.JmixListMenu;
import io.jmix.flowui.component.radiobuttongroup.JmixRadioButtonGroup;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.kit.component.main.ListMenu;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.view.ViewControllerUtils;
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
import uz.kapitalbank.umida.view.report.ReportCustomListView;
import uz.kapitalbank.umida.view.report.access.ReportAccessManagementView;
import uz.kapitalbank.umida.view.report.access.ReportOwnerMembersFragment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The access management as the head of an owner, a delegate, someone who manages nothing and an author use it:
 * the tabs, the owner list kept on the tab Owners, the filters of the columns, the number of the pending requests on the tab and in the menu, the decisions, the
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
    private OrgStructureSubdivision owner;
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
        owner = data.createSubdivision(true);
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
        systemAuthenticator.withUser(requester.getUsername(), () -> accessService.requestAccess(otherReport, "for the work"));
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
    @SuppressWarnings("unchecked")
    void theHeadGivesAccessToASubdivisionAndRevokesItAgain() {
        ReportAccessManagementView view = open(head);
        // as the user does: the content of a tab is attached once the tab is opened
        UiTestUtils.<JmixTabSheet>getComponent(view, "tabSheet")
                .setSelectedTab(UiTestUtils.getComponent(view, "tabSheet.grantsTab"));

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
        Grid<ReportAccessGrant> revoked = opened(InputDialog.class)
                .flatMap(d -> descendants(d).filter(c -> c.getId().filter("revokeGrantsGrid"::equals).isPresent())
                        .findFirst())
                .map(c -> (Grid<ReportAccessGrant>) c)
                .orElseGet(() -> fail("the dialog lists the grants revoked"));
        assertEquals(5, revoked.getColumns().size(), "code, name of the report, employee, position, subdivision");
        assertEquals(List.of(teamMember.getId()), revoked.getListDataView().getItems()
                .map(g -> g.getEmployee().getId()).toList());
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
    // owners
    // ---------------------------------------------------------------------

    @Test
    void theHeadKeepsTheOwnerListOfTheFirstReportByTheCodeAndTheAccessTabLeavesItOut() {
        ReportCustom secondOwn = data.createReport(owner);
        jdbcTemplate.update("update REPORT_REPORT set CODE = ? where ID = ?", "ZZ-" + UUID.randomUUID(), ownReport.getId());
        jdbcTemplate.update("update REPORT_REPORT set CODE = ? where ID = ?", "AA-" + UUID.randomUUID(), secondOwn.getId());
        OrgStructureEmployee member = data.createEmployeeIn(owner, false);

        ReportAccessManagementView view = open(head);

        assertEquals("ownersTab", UiTestUtils.<JmixTabSheet>getComponent(view, "tabSheet").getSelectedTab()
                .getId().orElse(null), "the tab Owners comes first");
        EntityComboBox<ReportCustom> reportField = UiTestUtils.getComponent(view, "ownersReportField");
        assertEquals(secondOwn.getId(), reportField.getValue().getId(), "the first of the own reports by the code");
        assertEquals(Set.of(secondOwn.getId(), ownReport.getId()), ownerReports(view), "not the report of the others");
        JmixButton save = UiTestUtils.getComponent(view, "saveOwnersBtn");
        assertFalse(save.isEnabled(), "nothing to save yet");

        Map<String, ReportOwnerMemberItem> rows = ownerRows(view);
        assertFalse(checkbox(view, "run", rows.get(head.getEmployee().getId())).isEnabled(), "the head keeps the rights");
        tick(checkbox(view, "run", rows.get(member.getId())), true);
        assertTrue(save.isEnabled());
        save.click();

        List<ReportAccessGrant> members = accessService.ownerMembers(secondOwn);
        assertEquals(List.of(member.getId()), members.stream().map(g -> g.getEmployee().getId()).toList());
        assertEquals(head.getUsername(), members.getFirst().getCreatedBy());
        assertFalse(save.isEnabled(), "saved");
        assertTrue(ownerRows(view).get(member.getId()).getRun(), "the list as saved");
        DataGrid<ReportAccessGrant> grants = UiTestUtils.getComponent(view, "grantsDataGrid");
        assertTrue(grants.getItems().getItems().stream().noneMatch(g -> g.getSource() == ReportAccessGrantSource.OWNER),
                "the owner list is not on the tab Access");
        DataGrid<ReportAccessGrant> journal = UiTestUtils.getComponent(view, "journalDataGrid");
        assertTrue(journal.getItems().getItems().stream().anyMatch(g -> g.getId().equals(members.getFirst().getId())),
                "the journal shows the change");
    }

    @Test
    void aDelegateKeepsTheOwnRowAndCancelPutsTheTicksBack() {
        OrgStructureEmployee member = data.createEmployeeIn(owner, false);
        OrgStructureEmployee delegateEmployee = data.createEmployeeIn(owner, false);
        accessService.saveOwnerMembers(ownReport, List.of(new ReportAccessService.OwnerMember(delegateEmployee, true)));
        User delegate = data.createViewer(delegateEmployee);

        ReportAccessManagementView view = open(delegate);

        Map<String, ReportOwnerMemberItem> rows = ownerRows(view);
        assertFalse(checkbox(view, "manage", rows.get(delegateEmployee.getId())).isEnabled(), "the own row is locked");
        tick(checkbox(view, "manage", rows.get(member.getId())), true);
        assertTrue(rows.get(member.getId()).getRun(), "managing implies running");
        JmixButton cancel = UiTestUtils.getComponent(view, "cancelOwnersBtn");
        assertTrue(cancel.isEnabled());
        cancel.click();

        assertFalse(ownerRows(view).get(member.getId()).getRun(), "back as saved");
        assertFalse(cancel.isEnabled());
        assertEquals(List.of(delegateEmployee.getId()), accessService.ownerMembers(ownReport).stream()
                .map(g -> g.getEmployee().getId()).toList(), "nothing saved");
    }

    @Test
    void anotherReportWithTheListChangedAsksFirst() {
        ReportCustom secondOwn = data.createReport(owner);
        OrgStructureEmployee member = data.createEmployeeIn(owner, false);
        ReportAccessManagementView view = open(head);
        EntityComboBox<ReportCustom> reportField = UiTestUtils.getComponent(view, "ownersReportField");
        ReportCustom shown = reportField.getValue();
        ReportCustom other = ownerReportsDc(view).getItems().stream()
                .filter(r -> !r.getId().equals(shown.getId())).findFirst().orElseGet(() -> fail("two own reports"));
        tick(checkbox(view, "run", ownerRows(view).get(member.getId())), true);

        reportField.setValue(other);
        assertEquals(shown.getId(), reportField.getValue().getId(), "the report stays until the user decides");
        press(DialogAction.Type.NO);
        assertEquals(shown.getId(), reportField.getValue().getId());
        assertTrue(ownerRows(view).get(member.getId()).getRun(), "the tick stays");

        reportField.setValue(other);
        press(DialogAction.Type.YES);
        assertEquals(other.getId(), reportField.getValue().getId());
        assertFalse(ownerRows(view).get(member.getId()).getRun(), "the list of the other report");
        assertTrue(Set.of(ownReport.getId(), secondOwn.getId()).contains(other.getId()));
        assertEquals(List.of(), accessService.ownerMembers(shown), "the dropped tick is not saved");
    }

    @Test
    void leavingTheViewWithTheListChangedAsksFirst() {
        OrgStructureEmployee member = data.createEmployeeIn(owner, false);
        ReportAccessManagementView view = open(head);
        tick(checkbox(view, "run", ownerRows(view).get(member.getId())), true);

        viewNavigators.view(view, ReportCustomListView.class).navigate();
        press(DialogAction.Type.NO);
        assertSame(view, UiTestUtils.getCurrentView(), "the user stays");

        viewNavigators.view(view, ReportCustomListView.class).navigate();
        press(DialogAction.Type.YES);
        assertInstanceOf(ReportCustomListView.class, UiTestUtils.getCurrentView());
        assertEquals(List.of(), accessService.ownerMembers(ownReport));
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

    @SuppressWarnings("unchecked")
    private static CollectionContainer<ReportCustom> ownerReportsDc(ReportAccessManagementView view) {
        return ViewControllerUtils.getViewData(view).getContainer("ownerReportsDc");
    }

    private static Set<UUID> ownerReports(ReportAccessManagementView view) {
        return ownerReportsDc(view).getItems().stream().map(ReportCustom::getId).collect(Collectors.toSet());
    }

    /** The rows of the owner list shown, by the employee. */
    private static Map<String, ReportOwnerMemberItem> ownerRows(ReportAccessManagementView view) {
        DataGrid<ReportOwnerMemberItem> grid = inOwnersFragment(view, "ownerMembersDataGrid");
        return grid.getItems().getItems().stream().collect(Collectors.toMap(i -> i.getEmployee().getId(), i -> i));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T inOwnersFragment(ReportAccessManagementView view, String id) {
        ReportOwnerMembersFragment fragment = UiTestUtils.getComponent(view, "ownersFragment");
        return (T) UiComponentUtils.getComponent(fragment, id);
    }

    /** The check box of the right in the row, as the grid renders it. */
    @SuppressWarnings("unchecked")
    private static JmixCheckbox checkbox(ReportAccessManagementView view, String column, ReportOwnerMemberItem item) {
        DataGrid<ReportOwnerMemberItem> grid = inOwnersFragment(view, "ownerMembersDataGrid");
        return ((ComponentRenderer<JmixCheckbox, ReportOwnerMemberItem>) grid.getColumnByKey(column).getRenderer())
                .createComponent(item);
    }

    /** A tick as the user makes it: the value changes in the browser. */
    private static void tick(JmixCheckbox checkbox, boolean value) {
        boolean old = Boolean.TRUE.equals(checkbox.getValue());
        checkbox.setValue(value);
        ComponentUtil.fireEvent(checkbox, new AbstractField.ComponentValueChangeEvent<>(checkbox, checkbox, old, true));
    }

    /** Presses the button of the option dialog just opened, see ReportListAccessUiTest. */
    private static void press(DialogAction.Type type) {
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        elementsBelow(UI.getCurrent().getElement())
                .flatMap(element -> element.getComponent().stream())
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getId().filter(type.getId()::equals).isPresent())
                .findFirst()
                .orElseGet(() -> fail("the dialog has its " + type + " button"))
                .click();
    }

    /** The element and those below it, the virtual children included: the footer of a dialog is one. */
    private static Stream<Element> elementsBelow(Element element) {
        Stream<Element> virtual = Optional.of(element.getNode())
                .filter(node -> node.hasFeature(VirtualChildrenList.class))
                .flatMap(node -> node.getFeatureIfInitialized(VirtualChildrenList.class))
                .map(list -> StreamSupport.stream(Spliterators.spliteratorUnknownSize(list.iterator(), 0), false)
                        .map(Element::get))
                .orElseGet(Stream::empty);
        return Stream.concat(Stream.of(element), Stream.concat(element.getChildren(), virtual)
                .flatMap(ReportAccessManagementUiTest::elementsBelow));
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
