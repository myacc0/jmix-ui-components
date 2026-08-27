package com.company.demo.orgstructure;

import com.company.demo.dto.orgstructure.OrgChartNode;
import com.company.demo.entity.orgstructure.Department;
import com.company.demo.entity.orgstructure.Employee;
import com.company.demo.entity.orgstructure.JobTitle;
import com.company.demo.entity.orgstructure.Position;
import com.company.demo.enums.orgstructure.PositionStatus;
import com.company.demo.service.orgstructure.OrgStructureService;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
 * Verifies how {@link OrgStructureService#getOrgChartNodes(UUID)} flattens a department
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

    private final List<Object> cleanup = new ArrayList<>();

    @Test
    void buildsPositionHierarchyAcrossDepartments() {
        String suffix = UUID.randomUUID().toString();

        Department root = createDepartment("Root " + suffix, null, 1);
        Department child = createDepartment("Child " + suffix, root, 2);

        JobTitle directorTitle = createJobTitle("Director " + suffix);
        JobTitle managerTitle = createJobTitle("Manager " + suffix);
        JobTitle specialistTitle = createJobTitle("Specialist " + suffix);

        Position rootHead = createPosition(root, directorTitle, createEmployee("Root Head " + suffix), 10, 1);
        Position rootStaff = createPosition(root, specialistTitle, createEmployee("Root Staff " + suffix), 6, 0);
        Position childHead = createPosition(child, managerTitle, createEmployee("Child Head " + suffix), 8, 1);
        Position childStaff = createPosition(child, specialistTitle, createEmployee("Child Staff " + suffix), 6, 0);

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

        // the head of the selected department is the single root of the chart
        assertNull(rootHeadNode.getParentId());
        assertTrue(rootHeadNode.isHead());

        // staff hang under the head of their own department
        assertEquals(rootHeadNode.getId(), rootStaffNode.getParentId());
        assertEquals(childHeadNode.getId(), childStaffNode.getParentId());

        // the head of a child department hangs under the head of the parent department
        assertEquals(rootHeadNode.getId(), childHeadNode.getParentId());

        // node payload: department name as title, employee as name, job title as position
        assertEquals("Root " + suffix, rootHeadNode.getOrgLevelName());
        assertEquals("Root Head " + suffix, rootHeadNode.getName());
        assertEquals("Director " + suffix, rootHeadNode.getPosition());
        assertEquals(PositionStatus.FILLED.getId(), rootHeadNode.getStatus());
        assertEquals("Child " + suffix, childStaffNode.getOrgLevelName());
    }

    @Test
    void createsSyntheticNodeForDepartmentWithoutHeadPosition() {
        String suffix = UUID.randomUUID().toString();

        Department root = createDepartment("Root " + suffix, null, 1);
        Department headless = createDepartment("Headless " + suffix, root, 2);
        Department grandChild = createDepartment("GrandChild " + suffix, headless, 3);

        JobTitle directorTitle = createJobTitle("Director " + suffix);
        JobTitle specialistTitle = createJobTitle("Specialist " + suffix);

        Position rootHead = createPosition(root, directorTitle, createEmployee("Root Head " + suffix), 10, 1);
        // headless department has staff but no ishead = 1 position
        Position headlessStaff = createPosition(headless, specialistTitle, createEmployee("Headless Staff " + suffix), 6, 0);
        Position grandChildHead = createPosition(grandChild, directorTitle, createEmployee("GrandChild Head " + suffix), 8, 1);

        Map<String, OrgChartNode> nodes = nodesById(root.getId());

        OrgChartNode syntheticNode = nodes.get("dept-" + headless.getId());
        assertNotNull(syntheticNode, "department without a head position must get a synthetic node");
        assertTrue(syntheticNode.isDepartmentNode());
        assertEquals("Headless " + suffix, syntheticNode.getOrgLevelName());
        assertEquals("", syntheticNode.getName());
        assertEquals("", syntheticNode.getPosition());
        assertEquals("pos-" + rootHead.getId(), syntheticNode.getParentId());

        // the synthetic node anchors both its own staff and the child department below it
        assertEquals(syntheticNode.getId(), nodes.get("pos-" + headlessStaff.getId()).getParentId());
        assertEquals(syntheticNode.getId(), nodes.get("pos-" + grandChildHead.getId()).getParentId());

        assertEquals(1, countRoots(nodes.values()), "chart must stay single-rooted");
    }

    @Test
    void selectedDepartmentWithoutPositionsBecomesTheRoot() {
        String suffix = UUID.randomUUID().toString();

        Department root = createDepartment("Empty Root " + suffix, null, 1);
        Department child = createDepartment("Child " + suffix, root, 2);
        createPosition(child, createJobTitle("Manager " + suffix), createEmployee("Child Head " + suffix), 8, 1);

        Map<String, OrgChartNode> nodes = nodesById(root.getId());

        OrgChartNode rootNode = nodes.get("dept-" + root.getId());
        assertNotNull(rootNode);
        assertNull(rootNode.getParentId());
        assertTrue(rootNode.isDepartmentNode());
        assertEquals(1, countRoots(nodes.values()));
    }

    @Test
    void excludesDepartmentsOutsideTheSelectedSubtree() {
        String suffix = UUID.randomUUID().toString();

        Department root = createDepartment("Root " + suffix, null, 1);
        Department child = createDepartment("Child " + suffix, root, 2);
        Department sibling = createDepartment("Sibling " + suffix, root, 3);

        JobTitle title = createJobTitle("Manager " + suffix);
        createPosition(root, title, createEmployee("Root Head " + suffix), 10, 1);
        Position childHead = createPosition(child, title, createEmployee("Child Head " + suffix), 8, 1);
        Position siblingHead = createPosition(sibling, title, createEmployee("Sibling Head " + suffix), 8, 1);

        // selecting the child department yields only that branch
        Map<String, OrgChartNode> nodes = nodesById(child.getId());

        assertEquals(1, nodes.size());
        assertNotNull(nodes.get("pos-" + childHead.getId()));
        assertNull(nodes.get("pos-" + siblingHead.getId()));
        assertNull(nodes.get("pos-" + childHead.getId()).getParentId());
    }

    @Test
    void serializesNodesToJsonForTheChartComponent() throws Exception {
        String suffix = UUID.randomUUID().toString();

        Department root = createDepartment("Root " + suffix, null, 1);
        Position head = createPosition(root, createJobTitle("Director " + suffix),
                createEmployee("Root Head " + suffix), 10, 1);

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
    void returnsEmptyListForUnknownOrNullDepartment() {
        assertTrue(orgStructureService.getOrgChartNodes(null).isEmpty());
        assertTrue(orgStructureService.getOrgChartNodes(UUID.randomUUID()).isEmpty());
    }

    private Map<String, OrgChartNode> nodesById(UUID departmentId) {
        return orgStructureService.getOrgChartNodes(departmentId).stream()
                .collect(Collectors.toMap(OrgChartNode::getId, Function.identity()));
    }

    private long countRoots(java.util.Collection<OrgChartNode> nodes) {
        return nodes.stream().filter(node -> node.getParentId() == null).count();
    }

    private Department createDepartment(String name, Department parent, int ordNo) {
        Department department = dataManager.create(Department.class);
        department.setName(name);
        department.setParentDepartment(parent);
        department.setOrdNo(ordNo);
        Department saved = dataManager.save(department);
        cleanup.add(saved);
        return saved;
    }

    private JobTitle createJobTitle(String name) {
        JobTitle jobTitle = dataManager.create(JobTitle.class);
        jobTitle.setName(name);
        JobTitle saved = dataManager.save(jobTitle);
        cleanup.add(saved);
        return saved;
    }

    private Employee createEmployee(String fullName) {
        Employee employee = dataManager.create(Employee.class);
        employee.setFullName(fullName);
        Employee saved = dataManager.save(employee);
        cleanup.add(saved);
        return saved;
    }

    private Position createPosition(Department department, JobTitle jobTitle, Employee employee,
                                    Integer lvl, Integer ishead) {
        Position position = dataManager.create(Position.class);
        position.setDepartment(department);
        position.setJobTitle(jobTitle);
        position.setEmployee(employee);
        position.setLvl(lvl);
        position.setIshead(ishead);
        position.setStatus(PositionStatus.FILLED);
        Position saved = dataManager.save(position);
        cleanup.add(saved);
        return saved;
    }

    @AfterEach
    void tearDown() {
        // reverse creation order: positions before the departments/employees they reference,
        // and child departments before their parents
        List<Object> reversed = new ArrayList<>(cleanup);
        Collections.reverse(reversed);
        reversed.forEach(dataManager::remove);
        cleanup.clear();
    }
}
