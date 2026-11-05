package uz.kapitalbank.umida.report;

import com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.entitypicker.EntityLookupAction;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.util.OperationResult;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.view.LookupView;
import io.jmix.flowui.view.ViewControllerUtils;
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
import uz.kapitalbank.umida.dto.report.ReportOwnerMemberItem;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportAccessGrant;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.security.ReportAuthorRole;
import uz.kapitalbank.umida.security.UiMinimalRole;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.service.report.ReportAccessService.OwnerMember;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.ReportTestData;
import uz.kapitalbank.umida.view.report.ReportCustomDetailView;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tab Business owner of the editor of a report, as an author uses it: the list as saved, the ticks, the
 * pick of another owner, the search, and the save of the list with the report.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportOwnerTabUiTest {

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
    private OrgStructureEmployee head;
    private OrgStructureEmployee member;
    private OrgStructureEmployee other;
    private ReportCustom report;
    private OrgStructureSubdivision newOwner;
    private OrgStructureEmployee newHead;
    private OrgStructureEmployee newMember;
    private OrgStructureSubdivision inactive;

    @BeforeEach
    void setUp() {
        data = new ReportTestData(dataManager, metadata, reportsPersistence, reportsSerialization, jdbcTemplate);
        owner = data.createSubdivision(true);
        head = data.createEmployeeIn(owner, true);
        member = data.createEmployeeIn(owner, false);
        other = data.createEmployeeIn(data.createSubdivision(owner, true), false);
        report = data.createReport(owner);
        accessService.saveOwnerMembers(report, List.of(new OwnerMember(member, false)));
        newOwner = data.createSubdivision(true);
        newHead = data.createEmployeeIn(newOwner, true);
        newMember = data.createEmployeeIn(newOwner, false);
        inactive = data.createSubdivision(false);

        User author = data.createUser(null);
        data.assignRole(author, UiMinimalRole.CODE, RoleAssignmentRoleType.RESOURCE);
        data.assignRole(author, ReportAuthorRole.CODE, RoleAssignmentRoleType.RESOURCE);
        systemAuthenticator.begin(author.getUsername());
    }

    @AfterEach
    void tearDown() {
        systemAuthenticator.end();
        data.cleanup();
    }

    @Test
    void theTabShowsTheSavedListAndSavesTheTicksWithTheReport() {
        ReportCustomDetailView view = openEditor();
        DataGrid<ReportOwnerMemberItem> grid = UiTestUtils.getComponent(view, "ownerMembersDataGrid");
        Map<String, ReportOwnerMemberItem> rows = rows(view);

        assertEquals(Set.of(head.getId(), member.getId(), other.getId()), rows.keySet());
        assertTrue(rows.get(member.getId()).getRun());
        assertFalse(rows.get(other.getId()).getRun(), "not on the saved list");
        assertFalse(view.hasUnsavedChanges());

        JmixCheckbox headRun = checkbox(grid, "run", rows.get(head.getId()));
        assertTrue(headRun.getValue());
        assertFalse(headRun.isEnabled(), "the head keeps the rights");

        tick(checkbox(grid, "manage", rows.get(other.getId())), true);
        assertTrue(rows.get(other.getId()).getRun(), "managing implies running");
        assertTrue(view.hasUnsavedChanges(), "a tick is a change");

        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());

        Map<String, ReportAccessGrant> grants = grants();
        assertEquals(Set.of(member.getId(), other.getId()), grants.keySet());
        assertTrue(grants.get(other.getId()).getCanManage());
        assertFalse(view.hasUnsavedChanges(), "the list is saved");
    }

    @Test
    void anotherOwnerTicksAllItsEmployeesAndRevokesTheOldListOnSave() {
        ReportCustomDetailView view = openEditor();

        // as the lookup of the picker hands it over: loaded, with its parent for the caption
        ((ReportCustom) view.getEditedEntity()).setBusinessOwner(dataManager.load(OrgStructureSubdivision.class)
                .id(newOwner.getId())
                .fetchPlan(fp -> fp.addFetchPlan("_instance_name").add("parent", "_instance_name"))
                .one());

        Map<String, ReportOwnerMemberItem> rows = rows(view);
        assertEquals(Set.of(newHead.getId(), newMember.getId()), rows.keySet());
        assertTrue(rows.get(newMember.getId()).getRun(), "everyone of the new owner runs by default");
        assertTrue(view.hasUnsavedChanges());

        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());

        assertEquals(Set.of(newMember.getId()), grants().keySet(), "the old member is revoked");
        assertEquals(newOwner.getId(), dataManager.load(ReportCustom.class).id(report.getId())
                .fetchPlan(fp -> fp.add("businessOwner")).one().getBusinessOwner().getId());
    }

    @Test
    void theSearchNarrowsTheRowsButTheSaveKeepsTheHiddenOnes() {
        ReportCustomDetailView view = openEditor();
        TypedTextField<String> search = UiTestUtils.getComponent(view, "ownerMembersSearchField");

        search.setValue(other.getFullName());

        assertEquals(Set.of(other.getId()), rows(view).keySet());
        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());
        assertEquals(Set.of(member.getId()), grants().keySet(), "the hidden member stays on the list");
    }

    @Test
    void tickAllFollowsTheRowsAndTicksOrUnticksAllButTheHead() {
        ReportCustomDetailView view = openEditor();
        JmixCheckbox tickAll = UiTestUtils.getComponent(view, "checkAllRunField");
        Map<String, ReportOwnerMemberItem> rows = rows(view);

        assertFalse(tickAll.getValue());
        assertTrue(tickAll.isIndeterminate(), "the member runs, the other one does not");

        tick(tickAll, true);
        assertTrue(rows.get(member.getId()).getRun());
        assertTrue(rows.get(other.getId()).getRun());
        assertFalse(tickAll.isIndeterminate());
        assertTrue(view.hasUnsavedChanges());

        rows.get(member.getId()).setManage(true);
        tick(tickAll, false);
        assertFalse(rows.get(member.getId()).getRun());
        assertFalse(rows.get(member.getId()).getManage(), "no managing without running");
        assertFalse(rows.get(other.getId()).getRun());
        assertTrue(rows.get(head.getId()).getRun(), "the head keeps the rights");

        DataGrid<ReportOwnerMemberItem> grid = UiTestUtils.getComponent(view, "ownerMembersDataGrid");
        tick(checkbox(grid, "run", rows.get(other.getId())), true);
        assertTrue(tickAll.isIndeterminate(), "a single tick shows on the check box");

        assertEquals(OperationResult.Status.SUCCESS, view.save().getStatus());
        assertEquals(Set.of(other.getId()), grants().keySet());
    }

    @Test
    void anInactiveSubdivisionCannotBePickedAsTheOwner() {
        ReportCustomDetailView view = openEditor();
        EntityPicker<OrgStructureSubdivision> ownerField = UiTestUtils.getComponent(view, "businessOwnerField");
        EntityLookupAction<OrgStructureSubdivision> lookup =
                (EntityLookupAction<OrgStructureSubdivision>) ownerField.getAction("entityLookupAction");

        assertEquals("umida_OrgStructureSubdivision.list", lookup.getViewId());
        assertTrue(selectable(view, lookup, newOwner));
        assertFalse(selectable(view, lookup, inactive));
    }

    @SuppressWarnings("unchecked")
    private static boolean selectable(ReportCustomDetailView view, EntityLookupAction<OrgStructureSubdivision> lookup,
                                      OrgStructureSubdivision subdivision) {
        try {
            var field = EntityLookupAction.class.getDeclaredField("selectValidator");
            field.setAccessible(true);
            var validator = (java.util.function.Predicate<LookupView.ValidationContext<OrgStructureSubdivision>>) field.get(lookup);
            return validator.test(new LookupView.ValidationContext<>(view, List.of(subdivision)));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReportCustomDetailView openEditor() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Report.class)
                .editEntity(report)
                .withViewClass(ReportCustomDetailView.class)
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    private static Map<String, ReportOwnerMemberItem> rows(ReportCustomDetailView view) {
        CollectionContainer<ReportOwnerMemberItem> container =
                ViewControllerUtils.getViewData(view).getContainer("ownerMembersDc");
        return container.getItems().stream().collect(Collectors.toMap(i -> i.getEmployee().getId(), i -> i));
    }

    @SuppressWarnings("unchecked")
    private static JmixCheckbox checkbox(DataGrid<ReportOwnerMemberItem> grid, String column, ReportOwnerMemberItem item) {
        ComponentRenderer<JmixCheckbox, ReportOwnerMemberItem> renderer =
                (ComponentRenderer<JmixCheckbox, ReportOwnerMemberItem>) grid.getColumnByKey(column).getRenderer();
        return renderer.createComponent(item);
    }

    /** A tick as the user makes it: the value changes in the browser. */
    private static void tick(JmixCheckbox checkbox, boolean value) {
        boolean old = Boolean.TRUE.equals(checkbox.getValue());
        checkbox.setValue(value);
        ComponentUtil.fireEvent(checkbox, new ComponentValueChangeEvent<>(checkbox, checkbox, old, true));
    }

    private Map<String, ReportAccessGrant> grants() {
        return accessService.ownerMembers(report).stream()
                .collect(Collectors.toMap(g -> g.getEmployee().getId(), g -> g));
    }
}
