package uz.kapitalbank.umida.orgstructure;

import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.accesscontext.CrudEntityContext;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.accesscontext.UiShowViewContext;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.dto.orgstructure.DataAssetChartNode;
import uz.kapitalbank.umida.dto.orgstructure.OrgChartNode;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataDomainSteward;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.dict.DictDataProductSteward;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.security.OrgStructureViewerRole;
import uz.kapitalbank.umida.service.orgstructure.DataStructureService;
import uz.kapitalbank.umida.service.orgstructure.OrgStructureService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A user with {@link OrgStructureViewerRole} sees the org chart and the structures of the data domains
 * and data products, and changes nothing.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class OrgStructureViewerAccessTests {

    private static final List<Class<?>> SECTION_ENTITIES = List.of(OrgStructureSubdivision.class,
            OrgStructurePosition.class, OrgStructureJobTitle.class, OrgStructureAssignments.class,
            OrgStructureEmployee.class, DictDataDomain.class, DictDataDomainSteward.class,
            DictDataProduct.class, DictDataProductSteward.class);

    @Autowired
    DataManager dataManager;

    @Autowired
    AccessManager accessManager;

    @Autowired
    Metadata metadata;

    @Autowired
    SystemAuthenticator systemAuthenticator;

    @Autowired
    OrgStructureService orgStructureService;

    @Autowired
    DataStructureService dataStructureService;

    private DqTestData testData;
    private OrgStructureSubdivision subdivision;
    private OrgStructureEmployee head;
    private DictDataDomain domain;
    private DictDataProduct product;
    private User viewer;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        subdivision = createSubdivision();
        head = createHeadOf(subdivision);
        domain = createDomainOwnedBy(subdivision);
        product = createProductOwnedBy(subdivision);
        viewer = createViewer();
    }

    @Test
    void aViewerSeesTheOrgChart() {
        List<OrgChartNode> nodes = systemAuthenticator.withUser(viewer.getUsername(),
                () -> orgStructureService.getOrgChartNodes(subdivision.getId()));

        OrgChartNode headNode = nodes.stream()
                .filter(node -> head.getId().equals(node.getEmployeeId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the head is on the chart"));
        assertTrue(headNode.isHead());
        assertEquals(head.getFullName(), headNode.getName());
        assertEquals(head.getEmail(), headNode.getEmail());
        assertEquals(subdivision.getName(), headNode.getOrgLevelName());

        List<OrgStructureAssignments> found = systemAuthenticator.withUser(viewer.getUsername(),
                () -> orgStructureService.searchCurrentAssignments(head.getFullName(), 10));
        assertEquals(1, found.size(), "the search finds the employee");
    }

    @Test
    void aViewerSeesTheDomainAndProductStructures() {
        List<DataAssetChartNode> domainNodes = systemAuthenticator.withUser(viewer.getUsername(),
                () -> dataStructureService.getDomainChartNodes(domain.getId()));
        List<DataAssetChartNode> productNodes = systemAuthenticator.withUser(viewer.getUsername(),
                () -> dataStructureService.getProductChartNodes(product.getId()));

        for (DataAssetChartNode node : List.of(single(domainNodes), single(productNodes))) {
            assertEquals(subdivision.getName(), node.getBusinessOwnerName());
            assertEquals(head.getFullName(), node.getHeadName());
            assertEquals(head.getEmail(), node.getHeadEmail());
            assertEquals(List.of(head.getFullName()), node.getStewardNames());
        }
        assertEquals(domain.getShortName(), single(domainNodes).getName());
        assertEquals(product.getShortName(), single(productNodes).getName());
    }

    @Test
    void aViewerOnlyReadsTheEntitiesOfTheSection() {
        systemAuthenticator.runWithUser(viewer.getUsername(), () -> {
            for (Class<?> entityClass : SECTION_ENTITIES) {
                CrudEntityContext context = new CrudEntityContext(metadata.getClass(entityClass));
                accessManager.applyRegisteredConstraints(context);
                String name = entityClass.getSimpleName();

                assertTrue(context.isReadPermitted(), name + " is readable");
                assertFalse(context.isCreatePermitted(), name + " is not creatable");
                assertFalse(context.isUpdatePermitted(), name + " is not updatable");
                assertFalse(context.isDeletePermitted(), name + " is not deletable");
            }
        });
    }

    @Test
    void aViewerCannotSaveASubdivision() {
        OrgStructureSubdivision loaded = dataManager.load(OrgStructureSubdivision.class).id(subdivision.getId()).one();
        loaded.setName("changed by viewer");

        assertThrows(AccessDeniedException.class,
                () -> systemAuthenticator.runWithUser(viewer.getUsername(), () -> dataManager.save(loaded)));
        assertEquals(subdivision.getName(),
                dataManager.load(OrgStructureSubdivision.class).id(subdivision.getId()).one().getName());
    }

    @Test
    void aViewerOpensTheScreensOfTheSection() {
        systemAuthenticator.runWithUser(viewer.getUsername(), () -> {
            for (String viewId : List.of("umida_OrgStructureView", "umida_EmployeeCardView",
                    "umida_DataDomainStructure", "umida_DataProductStructure", "umida_DataAssetCardView")) {
                assertTrue(canOpen(viewId), viewId + " opens");
            }
            assertFalse(canOpen("umida_OrgStructureSubdivision.list"), "the catalogs are not part of the section");
        });
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private boolean canOpen(String viewId) {
        UiShowViewContext context = new UiShowViewContext(viewId);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    private static DataAssetChartNode single(List<DataAssetChartNode> nodes) {
        assertEquals(1, nodes.size(), "a single element without children");
        return nodes.get(0);
    }

    private OrgStructureSubdivision createSubdivision() {
        OrgStructureSubdivision created = dataManager.create(OrgStructureSubdivision.class);
        created.setId(UUID.randomUUID().toString());
        created.setName("Org viewer subdivision " + suffix());
        return testData.track(dataManager.save(created));
    }

    /** An employee currently assigned to the head position of the subdivision. */
    private OrgStructureEmployee createHeadOf(OrgStructureSubdivision owner) {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("Org viewer head " + suffix());
        employee.setEmail("org-viewer-" + suffix() + "@example.com");
        employee = testData.track(dataManager.save(employee));

        OrgStructurePosition position = dataManager.create(OrgStructurePosition.class);
        position.setId(UUID.randomUUID().toString());
        position.setSubdivision(owner);
        position.setIsheadofsubdivision(1);
        position = testData.track(dataManager.save(position));

        OrgStructureAssignments assignment = dataManager.create(OrgStructureAssignments.class);
        assignment.setId(UUID.randomUUID().toString());
        assignment.setPosition(position);
        assignment.setEmployee(employee);
        assignment.setStartDate(LocalDate.now().minusYears(1));
        testData.track(dataManager.save(assignment));
        return employee;
    }

    private DictDataDomain createDomainOwnedBy(OrgStructureSubdivision owner) {
        DictDataDomain created = dataManager.create(DictDataDomain.class);
        created.setCode("org-viewer-" + suffix());
        created.setShortName("Org viewer domain " + suffix());
        created.setBusinessOwner(owner);
        created.setAssignDate(LocalDate.now());
        created = testData.track(dataManager.save(created));

        DictDataDomainSteward steward = dataManager.create(DictDataDomainSteward.class);
        steward.setDomain(created);
        steward.setEmployee(head);
        steward.setDateBegin(LocalDate.now().minusDays(1));
        testData.track(dataManager.save(steward));
        return created;
    }

    private DictDataProduct createProductOwnedBy(OrgStructureSubdivision owner) {
        DictDataProduct created = dataManager.create(DictDataProduct.class);
        created.setCode("org-viewer-" + suffix());
        created.setShortName("Org viewer product " + suffix());
        created.setBusinessOwner(owner);
        created.setAssignDate(LocalDate.now());
        created = testData.track(dataManager.save(created));

        DictDataProductSteward steward = dataManager.create(DictDataProductSteward.class);
        steward.setDataProduct(created);
        steward.setEmployee(head);
        steward.setDateBegin(LocalDate.now().minusDays(1));
        testData.track(dataManager.save(steward));
        return created;
    }

    private User createViewer() {
        User user = dataManager.create(User.class);
        user.setUsername("org-viewer-test-" + UUID.randomUUID());
        user.setFirstName("Org");
        user.setLastName("Viewer");
        user = testData.track(dataManager.save(user));

        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(user.getUsername());
        assignment.setRoleCode(OrgStructureViewerRole.CODE);
        assignment.setRoleType(RoleAssignmentRoleType.RESOURCE);
        testData.track(dataManager.save(assignment));
        return user;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        testData.cleanup();
    }
}
