package uz.kapitalbank.umida.orgstructure;

import uz.kapitalbank.umida.dto.orgstructure.OrgChartNode;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import uz.kapitalbank.umida.enums.orgstructure.OrgStructurePositionStatus;
import uz.kapitalbank.umida.service.orgstructure.OrgStructureService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import uz.kapitalbank.umida.service.orgstructure.EmployeePhotoService;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how {@link OrgStructureService#getOrgChartNodes(String)} flattens a subdivision
 * subtree into d3-org-chart nodes.
 * <p>
 * The whole fixture is created by the test and removed afterwards, and the assertions only
 * ever look at the nodes of that fixture, so rows a developer left in the database cannot
 * change the outcome.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
class OrgStructureServiceTests {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private OrgStructureService orgStructureService;
    @Autowired
    private EmployeePhotoService employeePhotoService;

    @Value("${jmix.localfs.storage-dir}")
    private String storageDir;

    private final List<Object> cleanup = new ArrayList<>();
    private final List<Path> photoFiles = new ArrayList<>();

    @Test
    void buildsPositionHierarchyAcrossSubdivisions() {
        String suffix = UUID.randomUUID().toString();

        OrgStructureSubdivision root = createSubdivision("Root " + suffix, null, 1);
        OrgStructureSubdivision child = createSubdivision("Child " + suffix, root, 2);

        OrgStructureJobTitle directorTitle = createJobTitle("Director " + suffix);
        OrgStructureJobTitle managerTitle = createJobTitle("Manager " + suffix);
        OrgStructureJobTitle specialistTitle = createJobTitle("Specialist " + suffix);

        OrgStructurePosition rootHead = createPosition(root, directorTitle, createEmployee("Root Head " + suffix), "10", 1);
        OrgStructurePosition rootStaff = createPosition(root, specialistTitle, createEmployee("Root Staff " + suffix), "6", 0);
        OrgStructurePosition childHead = createPosition(child, managerTitle, createEmployee("Child Head " + suffix), "8", 1);
        OrgStructurePosition childStaff = createPosition(child, specialistTitle, createEmployee("Child Staff " + suffix), "6", 0);

        Map<String, OrgChartNode> nodes = nodesById(root.getId());

        assertEquals(4, nodes.size());

        OrgChartNode rootHeadNode = nodes.get("pos-" + rootHead.getId());
        OrgChartNode rootStaffNode = nodes.get("pos-" + rootStaff.getId());
        OrgChartNode childHeadNode = nodes.get("pos-" + childHead.getId());
        OrgChartNode childStaffNode = nodes.get("pos-" + childStaff.getId());
        assertNotNull(rootHeadNode);
        assertNotNull(rootStaffNode);
        assertNotNull(childHeadNode);
        assertNotNull(childStaffNode);

        // the head of the selected subdivision is the single root of the chart
        assertNull(rootHeadNode.getParentId());
        assertTrue(rootHeadNode.isHead());

        // staff hang under the head of their own subdivision
        assertEquals(rootHeadNode.getId(), rootStaffNode.getParentId());
        assertEquals(childHeadNode.getId(), childStaffNode.getParentId());

        // the head of a child subdivision hangs under the head of the parent subdivision
        assertEquals(rootHeadNode.getId(), childHeadNode.getParentId());

        // node payload: subdivision name as title, employee as name, job title as position
        assertEquals("Root " + suffix, rootHeadNode.getOrgLevelName());
        assertEquals("Root Head " + suffix, rootHeadNode.getName());
        assertEquals("Director " + suffix, rootHeadNode.getPosition());
        assertEquals(OrgStructurePositionStatus.FILLED.getId(), rootHeadNode.getStatus());
        assertEquals("Child " + suffix, childStaffNode.getOrgLevelName());
    }

    @Test
    void createsSyntheticNodeForSubdivisionWithoutHeadPosition() {
        String suffix = UUID.randomUUID().toString();

        OrgStructureSubdivision root = createSubdivision("Root " + suffix, null, 1);
        OrgStructureSubdivision headless = createSubdivision("Headless " + suffix, root, 2);
        OrgStructureSubdivision grandChild = createSubdivision("GrandChild " + suffix, headless, 3);

        OrgStructureJobTitle directorTitle = createJobTitle("Director " + suffix);
        OrgStructureJobTitle specialistTitle = createJobTitle("Specialist " + suffix);

        OrgStructurePosition rootHead = createPosition(root, directorTitle, createEmployee("Root Head " + suffix), "10", 1);
        // headless subdivision has staff but no ishead = 1 position
        OrgStructurePosition headlessStaff = createPosition(headless, specialistTitle, createEmployee("Headless Staff " + suffix), "6", 0);
        OrgStructurePosition grandChildHead = createPosition(grandChild, directorTitle, createEmployee("GrandChild Head " + suffix), "8", 1);

        Map<String, OrgChartNode> nodes = nodesById(root.getId());

        OrgChartNode syntheticNode = nodes.get("dept-" + headless.getId());
        assertNotNull(syntheticNode, "subdivision without a head position must get a synthetic node");
        assertTrue(syntheticNode.isSubdivisionNode());
        assertEquals("Headless " + suffix, syntheticNode.getOrgLevelName());
        assertEquals("", syntheticNode.getName());
        assertEquals("", syntheticNode.getPosition());
        assertEquals("pos-" + rootHead.getId(), syntheticNode.getParentId());

        // the synthetic node anchors both its own staff and the child subdivision below it
        assertEquals(syntheticNode.getId(), nodes.get("pos-" + headlessStaff.getId()).getParentId());
        assertEquals(syntheticNode.getId(), nodes.get("pos-" + grandChildHead.getId()).getParentId());

        assertEquals(1, countRoots(nodes.values()), "chart must stay single-rooted");
    }

    @Test
    void selectedSubdivisionWithoutPositionsBecomesTheRoot() {
        String suffix = UUID.randomUUID().toString();

        OrgStructureSubdivision root = createSubdivision("Empty Root " + suffix, null, 1);
        OrgStructureSubdivision child = createSubdivision("Child " + suffix, root, 2);
        createPosition(child, createJobTitle("Manager " + suffix), createEmployee("Child Head " + suffix), "8", 1);

        Map<String, OrgChartNode> nodes = nodesById(root.getId());

        OrgChartNode rootNode = nodes.get("dept-" + root.getId());
        assertNotNull(rootNode);
        assertNull(rootNode.getParentId());
        assertTrue(rootNode.isSubdivisionNode());
        assertEquals(1, countRoots(nodes.values()));
    }

    @Test
    void excludesSubdivisionsOutsideTheSelectedSubtree() {
        String suffix = UUID.randomUUID().toString();

        OrgStructureSubdivision root = createSubdivision("Root " + suffix, null, 1);
        OrgStructureSubdivision child = createSubdivision("Child " + suffix, root, 2);
        OrgStructureSubdivision sibling = createSubdivision("Sibling " + suffix, root, 3);

        OrgStructureJobTitle title = createJobTitle("Manager " + suffix);
        createPosition(root, title, createEmployee("Root Head " + suffix), "10", 1);
        OrgStructurePosition childHead = createPosition(child, title, createEmployee("Child Head " + suffix), "8", 1);
        OrgStructurePosition siblingHead = createPosition(sibling, title, createEmployee("Sibling Head " + suffix), "8", 1);

        // selecting the child subdivision yields only that branch
        Map<String, OrgChartNode> nodes = nodesById(child.getId());

        assertEquals(1, nodes.size());
        assertNotNull(nodes.get("pos-" + childHead.getId()));
        assertNull(nodes.get("pos-" + siblingHead.getId()));
        assertNull(nodes.get("pos-" + childHead.getId()).getParentId());
    }

    @Test
    void serializesNodesToJsonForTheChartComponent() throws Exception {
        String suffix = UUID.randomUUID().toString();

        OrgStructureSubdivision root = createSubdivision("Root " + suffix, null, 1);
        OrgStructurePosition head = createPosition(root, createJobTitle("Director " + suffix),
                createEmployee("Root Head " + suffix), "10", 1);

        String json = orgStructureService.getOrgChartNodesJson(root.getId());

        JsonNode parsed = new ObjectMapper().readTree(json);
        assertTrue(parsed.isArray());
        assertEquals(1, parsed.size());

        JsonNode node = parsed.get(0);
        // field names consumed by orgchart.js
        assertEquals("pos-" + head.getId(), node.get("id").asText());
        assertTrue(node.get("parentId").isNull());
        assertEquals("Root " + suffix, node.get("orgLevelName").asText());
        assertEquals("Root Head " + suffix, node.get("name").asText());
        assertEquals("Director " + suffix, node.get("position").asText());
    }

    @Test
    void putsThePhotoUrlOfTheEmployeeOnTheNode() throws IOException {
        String suffix = UUID.randomUUID().toString();

        OrgStructureSubdivision root = createSubdivision("Root " + suffix, null, 1);
        OrgStructureJobTitle title = createJobTitle("Director " + suffix);

        OrgStructureEmployee withPhoto = createEmployee("With Photo " + suffix);
        OrgStructureEmployee withoutPhoto = createEmployee("Without Photo " + suffix);
        OrgStructurePosition head = createPosition(root, title, withPhoto, "10", 1);
        OrgStructurePosition staff = createPosition(root, title, withoutPhoto, "6", 0);

        writePhoto(withPhoto.getId() + ".jpg");

        Map<String, OrgChartNode> nodes = nodesById(root.getId());

        assertEquals("/employee-photos/" + withPhoto.getId(), nodes.get("pos-" + head.getId()).getImage());
        // no photo file: orgchart.js falls back to the initials placeholder on a null image
        assertNull(nodes.get("pos-" + staff.getId()).getImage());
    }

    @Test
    void returnsEmptyListForUnknownOrNullSubdivision() {
        assertTrue(orgStructureService.getOrgChartNodes(null).isEmpty());
        assertTrue(orgStructureService.getOrgChartNodes(UUID.randomUUID().toString()).isEmpty());
    }

    private Map<String, OrgChartNode> nodesById(String subdivisionId) {
        return orgStructureService.getOrgChartNodes(subdivisionId).stream()
                .collect(Collectors.toMap(OrgChartNode::getId, Function.identity()));
    }

    private long countRoots(java.util.Collection<OrgChartNode> nodes) {
        return nodes.stream().filter(node -> node.getParentId() == null).count();
    }

    private OrgStructureSubdivision createSubdivision(String name, OrgStructureSubdivision parent, int ordNo) {
        OrgStructureSubdivision subdivision = dataManager.create(OrgStructureSubdivision.class);
        subdivision.setId(UUID.randomUUID().toString());
        subdivision.setName(name);
        subdivision.setParent(parent);
        OrgStructureSubdivision saved = dataManager.save(subdivision);
        cleanup.add(saved);
        return saved;
    }

    private OrgStructureJobTitle createJobTitle(String name) {
        OrgStructureJobTitle jobTitle = dataManager.create(OrgStructureJobTitle.class);
        jobTitle.setId(UUID.randomUUID().toString());
        jobTitle.setName(name);
        OrgStructureJobTitle saved = dataManager.save(jobTitle);
        cleanup.add(saved);
        return saved;
    }

    private OrgStructureEmployee createEmployee(String fullName) {
        OrgStructureEmployee orgStructureEmployee = dataManager.create(OrgStructureEmployee.class);
        orgStructureEmployee.setId(UUID.randomUUID().toString());
        orgStructureEmployee.setFullName(fullName);
        OrgStructureEmployee saved = dataManager.save(orgStructureEmployee);
        cleanup.add(saved);
        return saved;
    }

    private OrgStructurePosition createPosition(OrgStructureSubdivision subdivision, OrgStructureJobTitle jobTitle, OrgStructureEmployee orgStructureEmployee,
                                                String lvl, Integer ishead) {
        OrgStructurePosition position = dataManager.create(OrgStructurePosition.class);
        position.setId(UUID.randomUUID().toString());
        position.setSubdivision(subdivision);
        position.setJobTitle(jobTitle);
        position.setEmployee(orgStructureEmployee);
        position.setLvl(lvl);
        position.setIsheadofsubdivision(ishead);
        position.setStatus(OrgStructurePositionStatus.FILLED);
        OrgStructurePosition saved = dataManager.save(position);
        cleanup.add(saved);
        return saved;
    }

    /**
     * Writes a photo file into a {@code yyyy/MM/dd} directory of the storage root — the only
     * layout {@code LocalFileStorage} can address — and makes the photo index see it at once.
     * The date is one no upload can produce, so the fixture never shares a directory with a
     * real photo.
     */
    private void writePhoto(String fileName) throws IOException {
        Path directory = Paths.get(storageDir.split(",")[0].trim(), "1970", "01", "01");
        Files.createDirectories(directory);

        Path file = directory.resolve(fileName);
        Files.write(file, "not-a-real-jpeg".getBytes());
        photoFiles.add(file);

        employeePhotoService.invalidateIndex();
    }

    /** A directory the fixture shares with another test only goes away once that test is done. */
    private void deleteIfEmpty(Path directory) throws IOException {
        try {
            Files.deleteIfExists(directory);
        } catch (DirectoryNotEmptyException e) {
            // another fixture file is still there
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        // reverse creation order: positions before the subdivisions/employees they reference,
        // and child subdivisions before their parents
        List<Object> reversed = new ArrayList<>(cleanup);
        Collections.reverse(reversed);
        reversed.forEach(dataManager::remove);
        cleanup.clear();

        for (Path file : photoFiles) {
            Files.deleteIfExists(file);
            deleteIfEmpty(file.getParent());
            deleteIfEmpty(file.getParent().getParent());
            deleteIfEmpty(file.getParent().getParent().getParent());
        }
        photoFiles.clear();
        // the index must not keep pointing at the files the test has just removed
        employeePhotoService.invalidateIndex();
    }
}
