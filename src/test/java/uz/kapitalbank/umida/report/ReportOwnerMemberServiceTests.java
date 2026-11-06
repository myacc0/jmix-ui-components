package uz.kapitalbank.umida.report;

import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.reports.ReportsPersistence;
import io.jmix.reports.ReportsSerialization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.kapitalbank.umida.dto.report.ReportOwnerMemberItem;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportAccessGrant;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.service.report.ReportOwnerMemberService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.ReportTestData;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rows of the business owner list as {@link ReportOwnerMemberService} builds them — the owner and the
 * subdivisions below it, the head on top — and the list they save.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportOwnerMemberServiceTests {

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
    ReportOwnerMemberService ownerMemberService;
    @Autowired
    ReportAccessService accessService;

    private ReportTestData data;
    private OrgStructureSubdivision owner;
    private OrgStructureEmployee head;
    private OrgStructureEmployee ofOwner;
    private OrgStructureEmployee ofChild;
    private OrgStructureEmployee ofGrandchild;
    private ReportCustom report;

    @BeforeEach
    void setUp() {
        data = new ReportTestData(dataManager, metadata, reportsPersistence, reportsSerialization, jdbcTemplate);
        owner = data.createSubdivision(true);
        OrgStructureSubdivision child = data.createSubdivision(owner, true);
        OrgStructureSubdivision grandchild = data.createSubdivision(child, true);
        OrgStructureSubdivision closedChild = data.createSubdivision(owner, false);

        head = data.createEmployeeIn(owner, true);
        ofOwner = data.createEmployeeIn(owner, false);
        ofChild = data.createEmployeeIn(child, true); // the head of the child is an ordinary member
        ofGrandchild = data.createEmployeeIn(grandchild, false);
        data.createEmployeeIn(closedChild, false);
        data.createEmployeeIn(owner, false, LocalDate.now().minusDays(1));
        data.createEmployeeIn(data.createSubdivision(true), false);
        report = data.createReport(owner);
    }

    @AfterEach
    void tearDown() {
        data.cleanup();
    }

    @Test
    void aPickedOwnerListsItsSubtreeWithTheHeadFirstAndEveryoneTickedToRun() {
        List<ReportOwnerMemberItem> items = ownerMemberService.items(report, owner, true);

        assertEquals(List.of(head.getId()), items.subList(0, 1).stream().map(i -> i.getEmployee().getId()).toList());
        assertEquals(Map.of(head.getId(), true, ofOwner.getId(), false, ofChild.getId(), false, ofGrandchild.getId(), false),
                items.stream().collect(Collectors.toMap(i -> i.getEmployee().getId(), ReportOwnerMemberItem::getHead)),
                "the closed child, the dismissed employee and other subdivisions are left out");
        assertTrue(items.stream().allMatch(ReportOwnerMemberItem::getRun), "everyone runs by default");
        assertEquals(List.of(head.getId()), items.stream().filter(ReportOwnerMemberItem::getManage)
                .map(i -> i.getEmployee().getId()).toList(), "only the head manages by default");
    }

    @Test
    void theSavedListComesBackAsTicked() {
        List<ReportOwnerMemberItem> items = ownerMemberService.items(report, owner, true);
        item(items, ofChild).setManage(true);
        item(items, ofGrandchild).setRun(false);

        ownerMemberService.save(report, items);

        Map<String, ReportAccessGrant> grants = accessService.ownerMembers(report).stream()
                .collect(Collectors.toMap(g -> g.getEmployee().getId(), g -> g));
        assertEquals(java.util.Set.of(ofOwner.getId(), ofChild.getId()), grants.keySet(), "no grant for the head");
        assertTrue(grants.get(ofChild.getId()).getCanManage());

        List<ReportOwnerMemberItem> reloaded = ownerMemberService.items(report, owner, false);
        assertTrue(item(reloaded, ofOwner).getRun());
        assertFalse(item(reloaded, ofOwner).getManage());
        assertTrue(item(reloaded, ofChild).getManage());
        assertFalse(item(reloaded, ofGrandchild).getRun());
        assertTrue(item(reloaded, head).getRun() && item(reloaded, head).getManage());
    }

    @Test
    void aMemberWhoLeftTheOwnerKeepsARowUntilUnticked() {
        OrgStructureEmployee leaver = data.createEmployeeIn(data.createSubdivision(true), false);
        accessService.saveOwnerMembers(report, List.of(new ReportAccessService.OwnerMember(leaver, true)));

        List<ReportOwnerMemberItem> items = ownerMemberService.items(report, owner, false);
        ReportOwnerMemberItem row = item(items, leaver);
        assertTrue(row.getOutside());
        assertTrue(row.getRun() && row.getManage());
        assertEquals(leaver.getId(), items.getLast().getEmployee().getId(), "the leavers come last");

        row.setRun(false);
        row.setManage(false);
        ownerMemberService.save(report, items);
        assertTrue(accessService.ownerMembers(report).stream()
                .noneMatch(g -> g.getEmployee().getId().equals(leaver.getId())));
    }

    @Test
    void theChangedRowsAloneAreSavedAndANewMemberIsAnEmployeeOfTheOwner() {
        accessService.saveOwnerMembers(report, List.of(new ReportAccessService.OwnerMember(ofOwner, false)));
        List<ReportOwnerMemberItem> items = ownerMemberService.items(report, owner, false);
        // meanwhile someone else adds a member: the rows changed here leave it as it is
        accessService.saveOwnerMembers(report, List.of(new ReportAccessService.OwnerMember(ofOwner, false),
                new ReportAccessService.OwnerMember(ofGrandchild, false)));
        ReportOwnerMemberItem child = item(items, ofChild);
        child.setRun(true);
        child.setManage(true);

        ownerMemberService.saveChanges(report, List.of(child, item(items, head)));

        Map<String, ReportAccessGrant> grants = accessService.ownerMembers(report).stream()
                .collect(Collectors.toMap(g -> g.getEmployee().getId(), g -> g));
        assertEquals(java.util.Set.of(ofOwner.getId(), ofChild.getId(), ofGrandchild.getId()), grants.keySet(),
                "the member added meanwhile stays, the head gets no grant");
        assertTrue(grants.get(ofChild.getId()).getCanManage());

        OrgStructureSubdivision elsewhere = data.createSubdivision(true);
        data.createEmployeeIn(elsewhere, false);
        ReportOwnerMemberItem foreign = ownerMemberService.employeesOf(elsewhere).getFirst();
        foreign.setRun(true);
        assertThrows(io.jmix.core.security.AccessDeniedException.class,
                () -> ownerMemberService.saveChanges(report, List.of(foreign)), "not an employee of the owner");
    }

    @Test
    void anEmployeeHoldingSeveralPositionsHasOneRowNamedAfterTheHeadPosition() {
        data.assign(head, data.createSubdivision(owner, true), false);

        List<ReportOwnerMemberItem> items = ownerMemberService.items(report, owner, true);

        assertEquals(1, items.stream().filter(i -> i.getEmployee().getId().equals(head.getId())).count());
        assertTrue(item(items, head).getHead());
        assertEquals(owner.getName(), item(items, head).getSubdivisionName());
    }

    @Test
    void noOwnerGivesNoRowsAndSavingThemEmptiesTheList() {
        ownerMemberService.save(report, ownerMemberService.items(report, owner, true));
        assertFalse(accessService.ownerMembers(report).isEmpty());

        jdbcTemplate.update("update REPORT_REPORT set BUSINESS_OWNER_ID = null where ID = ?", report.getId());
        List<ReportOwnerMemberItem> items = ownerMemberService.items(report, null, false);
        ownerMemberService.save(report, items);

        assertEquals(List.of(), items);
        assertEquals(List.of(), accessService.ownerMembers(report));
    }

    private static ReportOwnerMemberItem item(List<ReportOwnerMemberItem> items, OrgStructureEmployee employee) {
        return items.stream()
                .filter(i -> i.getEmployee().getId().equals(employee.getId()))
                .findFirst()
                .orElseGet(() -> fail("no row of " + employee.getFullName()));
    }
}
