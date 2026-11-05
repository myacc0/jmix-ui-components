package uz.kapitalbank.umida.report;

import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.reports.ReportsPersistence;
import io.jmix.reports.ReportsSerialization;
import io.jmix.reports.entity.BandDefinition;
import io.jmix.reports.entity.Orientation;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportOutputType;
import io.jmix.reports.entity.ReportTemplate;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.dto.report.ReportTreeItem;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.enums.report.ReportGrouping;
import uz.kapitalbank.umida.security.ReportAuthorRole;
import uz.kapitalbank.umida.security.ReportViewerRole;
import uz.kapitalbank.umida.security.ReportViewerRowsRole;
import uz.kapitalbank.umida.security.UiMinimalRole;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;
import uz.kapitalbank.umida.view.report.ReportCustomDetailView;
import uz.kapitalbank.umida.view.report.ReportCustomListView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The list of the reports opens for a user with {@link ReportViewerRole} and {@link ReportViewerRowsRole}
 * only — the paged list, the grouping and the tiles — and shows the reports with their columns.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportViewerListUiTest {

    @Autowired
    DataManager dataManager;
    @Autowired
    Metadata metadata;
    @Autowired
    ReportsPersistence reportsPersistence;
    @Autowired
    ReportsSerialization reportsSerialization;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    SystemAuthenticator systemAuthenticator;

    private DqTestData testData;
    private ReportCustom report;
    private DictDataDomain domain;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        domain = testData.createDomain();
        report = createReport();

        User viewer = dataManager.create(User.class);
        viewer.setUsername("report-viewer-" + UUID.randomUUID().toString().substring(0, 8));
        viewer.setFirstName("Report");
        viewer.setLastName("Viewer");
        viewer = testData.track(dataManager.save(viewer));
        assignRole(viewer, UiMinimalRole.CODE, RoleAssignmentRoleType.RESOURCE);
        assignRole(viewer, ReportViewerRole.CODE, RoleAssignmentRoleType.RESOURCE);
        assignRole(viewer, ReportViewerRowsRole.CODE, RoleAssignmentRoleType.ROW_LEVEL);

        systemAuthenticator.begin(viewer.getUsername());
    }

    @AfterEach
    void tearDown() {
        systemAuthenticator.end();
        testData.cleanup();
    }

    @Test
    void aViewerSeesTheReportsInTheListTheGroupingAndTheTiles() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        ReportCustomListView view = UiTestUtils.getCurrentView();

        DataGrid<Report> grid = UiTestUtils.getComponent(view, "reportsDataGrid");
        assertNotNull(grid.getColumnByKey("name"));
        assertNotNull(grid.getColumnByKey("domain"), "the viewer sees the domain column");
        ReportCustom shown = (ReportCustom) grid.getItems().getItems().stream()
                .filter(r -> r.getId().equals(report.getId()))
                .findFirst()
                .orElseGet(() -> fail("the report is listed"));
        assertEquals(domain.getShortName(), shown.getDomain().getShortName(), "the domain column is filled");

        JmixSelect<ReportGrouping> groupingField = UiTestUtils.getComponent(view, "groupingField");
        groupingField.setValue(ReportGrouping.DOMAIN);
        TreeDataGrid<ReportTreeItem> tree = UiTestUtils.getComponent(view, "reportsTreeDataGrid");
        assertTrue(tree.getItems().getItems().stream()
                        .anyMatch(item -> item.getReport() != null && item.getReport().getId().equals(report.getId())),
                "the report is in the grouped list");

        groupingField.setValue(ReportGrouping.ALL);
        UiTestUtils.<JmixButton>getComponent(view, "tilesModeBtn").click();
        assertTrue(UiTestUtils.<com.vaadin.flow.component.Component>getComponent(view, "tilesBox").isVisible(), "the tiles open");
    }

    @Test
    void anAuthorOpensTheListAndTheEditorOfAReport() {
        systemAuthenticator.end();
        User author = dataManager.create(User.class);
        author.setUsername("report-author-" + UUID.randomUUID().toString().substring(0, 8));
        author.setFirstName("Report");
        author.setLastName("Author");
        author = testData.track(dataManager.save(author));
        assignRole(author, UiMinimalRole.CODE, RoleAssignmentRoleType.RESOURCE);
        assignRole(author, ReportAuthorRole.CODE, RoleAssignmentRoleType.RESOURCE);
        systemAuthenticator.begin(author.getUsername());

        viewNavigators.view(UiTestUtils.getCurrentView(), ReportCustomListView.class).navigate();
        ReportCustomListView list = UiTestUtils.getCurrentView();
        DataGrid<Report> grid = UiTestUtils.getComponent(list, "reportsDataGrid");
        assertTrue(grid.getItems().getItems().stream().anyMatch(r -> r.getId().equals(report.getId())));
        assertTrue(UiTestUtils.<JmixButton>getComponent(list, "createBtn").isEnabled(), "an author creates reports");

        viewNavigators.detailView(list, Report.class)
                .editEntity(report)
                .withViewClass(ReportCustomDetailView.class)
                .navigate();
        ReportCustomDetailView detail = UiTestUtils.getCurrentView();
        assertEquals(report.getId(), detail.getEditedEntity().getId(), "the editor opens the report");
    }

    private void assignRole(User user, String roleCode, String roleType) {
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(user.getUsername());
        assignment.setRoleCode(roleCode);
        assignment.setRoleType(roleType);
        testData.track(dataManager.save(assignment));
    }

    private ReportCustom createReport() {
        ReportCustom report = assertInstanceOf(ReportCustom.class, metadata.create(Report.class));
        report.setName("Report viewer " + UUID.randomUUID());
        report.setDomain(domain);

        BandDefinition root = metadata.create(BandDefinition.class);
        root.setName("Root");
        root.setPosition(0);
        root.setOrientation(Orientation.HORIZONTAL);
        root.setReport(report);
        report.getBands().add(root);

        ReportTemplate template = metadata.create(ReportTemplate.class);
        template.setReport(report);
        template.setCode(ReportTemplate.DEFAULT_TEMPLATE_CODE);
        template.setName("template.html");
        template.setReportOutputType(ReportOutputType.HTML);
        template.setContent("ok".getBytes(StandardCharsets.UTF_8));
        report.setTemplates(new ArrayList<>(List.of(template)));
        report.setDefaultTemplate(template);
        report.setXml(reportsSerialization.convertToString(report));
        return testData.track(assertInstanceOf(ReportCustom.class, reportsPersistence.save(report)));
    }
}
