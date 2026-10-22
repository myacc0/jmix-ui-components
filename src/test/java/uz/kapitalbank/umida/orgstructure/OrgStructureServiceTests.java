package uz.kapitalbank.umida.orgstructure;

import uz.kapitalbank.umida.dto.orgstructure.OrgChartNode;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
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
import io.jmix.core.Messages;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    @Autowired
    private Messages messages;

    @Value("${jmix.localfs.storage-dir}")
    private String storageDir;

    private final List<Object> cleanup = new ArrayList<>();
    /** The assignment {@link #createPosition} put on each position, by position id. */
    private final Map<String, OrgStructureAssignments> assignmentsByPosition = new HashMap<>();
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

        OrgChartNode rootHeadNode = nodes.get(nodeId(rootHead));
        OrgChartNode rootStaffNode = nodes.get(nodeId(rootStaff));
        OrgChartNode childHeadNode = nodes.get(nodeId(childHead));
        OrgChartNode childStaffNode = nodes.get(nodeId(childStaff));
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
        assertEquals(nodeId(rootHead), syntheticNode.getParentId());

        // the synthetic node anchors both its own staff and the child subdivision below it
        assertEquals(syntheticNode.getId(), nodes.get(nodeId(headlessStaff)).getParentId());
        assertEquals(syntheticNode.getId(), nodes.get(nodeId(grandChildHead)).getParentId());

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
        assertNotNull(nodes.get(nodeId(childHead)));
        assertNull(nodes.get(nodeId(siblingHead)));
        assertNull(nodes.get(nodeId(childHead)).getParentId());
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
        assertEquals(nodeId(head), node.get("id").asText());
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

        assertEquals("/employee-photos/" + withPhoto.getId(), nodes.get(nodeId(head)).getImage());
        // no photo file: orgchart.js falls back to the initials placeholder on a null image
        assertNull(nodes.get(nodeId(staff)).getImage());
    }

    @Test
    void leavesOutClosedSubdivisionsAndPositions() {
        String suffix = UUID.randomUUID().toString();

        OrgStructureSubdivision root = createSubdivision("Root " + suffix, null, 1);
        OrgStructureSubdivision closedChild = createSubdivision("Closed Child " + suffix, root, 2);
        close(closedChild);

        OrgStructureJobTitle title = createJobTitle("Manager " + suffix);
        OrgStructurePosition rootHead = createPosition(root, title, createEmployee("Root Head " + suffix), "10", 1);
        OrgStructurePosition closedPosition = createPosition(root, title, createEmployee("Closed Staff " + suffix), "6", 0);
        close(closedPosition);
        OrgStructurePosition closedChildHead = createPosition(closedChild, title, createEmployee("Closed Child Head " + suffix), "8", 1);

        Map<String, OrgChartNode> nodes = nodesById(root.getId());

        assertEquals(1, nodes.size());
        assertNotNull(nodes.get(nodeId(rootHead)));
        assertNull(nodes.get(nodeId(closedPosition)));
        assertNull(nodes.get("dept-" + closedChild.getId()));
        assertNull(nodes.get(nodeId(closedChildHead)));
        assertTrue(orgStructureService.getOrgChartNodes(closedChild.getId()).isEmpty());
    }

    @Test
    void positionWithoutCurrentAssignmentIsVacant() {
        String suffix = UUID.randomUUID().toString();
        LocalDate today = LocalDate.now();

        OrgStructureSubdivision root = createSubdivision("Root " + suffix, null, 1);
        OrgStructureJobTitle title = createJobTitle("Specialist " + suffix);
        OrgStructurePosition head = createPosition(root, title, createEmployee("Root Head " + suffix), "10", 1);

        OrgStructurePosition ended = createPosition(root, title, null, "6", 0);
        createAssignment(ended, createEmployee("Dismissed " + suffix), today.minusYears(1), today.minusDays(1));
        OrgStructurePosition future = createPosition(root, title, null, "6", 0);
        createAssignment(future, createEmployee("Future " + suffix), today.plusDays(1), null);
        OrgStructurePosition lastDay = createPosition(root, title, null, "6", 0);
        OrgStructureAssignments lastDayAssignment =
                createAssignment(lastDay, createEmployee("Last Day " + suffix), today.minusYears(1), today);

        Map<String, OrgChartNode> nodes = nodesById(root.getId());

        assertEquals(4, nodes.size());
        String vacant = messages.getMessage(OrgStructureService.class, "orgChart.vacantPosition");
        // an assignment that has ended, or has not started yet, leaves the position vacant
        assertEquals(vacant, nodes.get("pos-" + ended.getId()).getName());
        assertNull(nodes.get("pos-" + ended.getId()).getEmployeeId());
        assertEquals(vacant, nodes.get("pos-" + future.getId()).getName());
        assertNull(nodes.get("pos-" + future.getId()).getEmployeeId());
        // the end date is the last day on the position
        OrgChartNode lastDayNode = nodes.get("asg-" + lastDayAssignment.getId());
        assertNotNull(lastDayNode);
        assertEquals("Last Day " + suffix, lastDayNode.getName());
        assertEquals(nodeId(head), lastDayNode.getParentId());
    }

    @Test
    void givesANodeToEveryCurrentAssignmentOfAPosition() {
        String suffix = UUID.randomUUID().toString();

        OrgStructureSubdivision root = createSubdivision("Root " + suffix, null, 1);
        OrgStructureJobTitle title = createJobTitle("Specialist " + suffix);
        OrgStructurePosition head = createPosition(root, title, createEmployee("Root Head " + suffix), "10", 1);
        OrgStructurePosition shared = createPosition(root, title, createEmployee("First " + suffix), "6", 0);
        OrgStructureAssignments second = createAssignment(shared, createEmployee("Second " + suffix),
                LocalDate.now().minusMonths(1), null);

        Map<String, OrgChartNode> nodes = nodesById(root.getId());

        assertEquals(3, nodes.size());
        OrgChartNode firstNode = nodes.get(nodeId(shared));
        OrgChartNode secondNode = nodes.get("asg-" + second.getId());
        assertEquals("First " + suffix, firstNode.getName());
        assertEquals("Second " + suffix, secondNode.getName());
        assertEquals(shared.getId(), secondNode.getPositionId());
        assertEquals(nodeId(head), firstNode.getParentId());
        assertEquals(nodeId(head), secondNode.getParentId());
    }

    @Test
    void searchFindsOnlyCurrentAssignments() {
        String suffix = UUID.randomUUID().toString();
        LocalDate today = LocalDate.now();

        OrgStructureSubdivision root = createSubdivision("Root " + suffix, null, 1);
        OrgStructureJobTitle title = createJobTitle("Specialist " + suffix);
        OrgStructurePosition current = createPosition(root, title, createEmployee("Searched Current " + suffix), "6", 0);
        OrgStructurePosition ended = createPosition(root, title, null, "6", 0);
        createAssignment(ended, createEmployee("Searched Ended " + suffix), today.minusYears(1), today.minusDays(1));

        List<OrgStructureAssignments> found = orgStructureService.searchCurrentAssignments("searched current " + suffix, 20);
        assertEquals(1, found.size());
        assertEquals(assignmentsByPosition.get(current.getId()), found.get(0));
        assertEquals("Searched Current " + suffix, found.get(0).getEmployee().getFullName());
        assertEquals(root, found.get(0).getPosition().getSubdivision());

        assertTrue(orgStructureService.searchCurrentAssignments("searched ended " + suffix, 20).isEmpty());
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
        position.setLvl(lvl);
        position.setIsheadofsubdivision(ishead);
        position.setStatus(orgStructureEmployee != null ? OrgStructurePositionStatus.FILLED : OrgStructurePositionStatus.VACANT);
        OrgStructurePosition saved = dataManager.save(position);
        cleanup.add(saved);
        if (orgStructureEmployee != null) {
            assignmentsByPosition.put(saved.getId(),
                    createAssignment(saved, orgStructureEmployee, LocalDate.now().minusYears(1), null));
        }
        return saved;
    }

    private OrgStructureAssignments createAssignment(OrgStructurePosition position, OrgStructureEmployee employee,
                                                     LocalDate startDate, LocalDate endDate) {
        OrgStructureAssignments assignment = dataManager.create(OrgStructureAssignments.class);
        assignment.setId(UUID.randomUUID().toString());
        assignment.setPosition(position);
        assignment.setEmployee(employee);
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        OrgStructureAssignments saved = dataManager.save(assignment);
        cleanup.add(saved);
        return saved;
    }

    /** The chart node of a position: of its assignment when {@link #createPosition} filled it, vacant otherwise. */
    private String nodeId(OrgStructurePosition position) {
        OrgStructureAssignments assignment = assignmentsByPosition.get(position.getId());
        return assignment != null ? "asg-" + assignment.getId() : "pos-" + position.getId();
    }

    private void close(OrgStructureSubdivision subdivision) {
        subdivision.setIsActive(0);
        subdivision.setEndDate(LocalDate.now().minusDays(1));
        cleanup.set(cleanup.indexOf(subdivision), dataManager.save(subdivision));
    }

    private void close(OrgStructurePosition position) {
        position.setIsActive(0);
        position.setEndDate(LocalDate.now().minusDays(1));
        cleanup.set(cleanup.indexOf(position), dataManager.save(position));
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
        // reverse creation order: assignments before their positions and employees, positions
        // before the subdivisions/employees they reference,
        // and child subdivisions before their parents
        List<Object> reversed = new ArrayList<>(cleanup);
        Collections.reverse(reversed);
        reversed.forEach(dataManager::remove);
        cleanup.clear();
        assignmentsByPosition.clear();

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
