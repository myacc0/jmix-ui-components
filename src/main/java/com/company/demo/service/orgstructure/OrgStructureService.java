package com.company.demo.service.orgstructure;

import com.company.demo.dto.orgstructure.D3OrgChartCsvItem;
import com.company.demo.dto.orgstructure.OrgChartNode;
import com.company.demo.entity.orgstructure.Department;
import com.company.demo.entity.orgstructure.Employee;
import com.company.demo.entity.orgstructure.JobTitle;
import com.company.demo.entity.orgstructure.Position;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.Sort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class OrgStructureService {

    protected static final String POSITION_NODE_PREFIX = "pos-";
    protected static final String DEPARTMENT_NODE_PREFIX = "dept-";

    private final DataManager dataManager;
    private final Messages messages;
    private final EmployeePhotoService employeePhotoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrgStructureService(DataManager dataManager,
                               Messages messages,
                               EmployeePhotoService employeePhotoService) {
        this.dataManager = dataManager;
        this.messages = messages;
        this.employeePhotoService = employeePhotoService;
    }

    public List<Position> getPositions(String searchText, int size) {
        return dataManager.load(Position.class)
                .query("select p from demo_Position p where p.employee.fullName like :name")
                .parameter("name", "(?i)%" + searchText + "%")
                .sort(Sort.by(Sort.Order.asc("employee.fullName")))
                .firstResult(0)
                .maxResults(size)
                .list();
    }

    /**
     * Builds the org chart for the given department and serializes it for
     * {@code D3OrgChart.setData(String)}.
     */
    public String getOrgChartNodesJson(UUID rootDepartmentId) {
        return toJson(getOrgChartNodes(rootDepartmentId));
    }

    /**
     * Serializes already built nodes, so that a caller that also needs the node list
     * itself does not have to query the structure twice.
     */
    public String toJson(List<OrgChartNode> nodes) {
        try {
            return objectMapper.writeValueAsString(nodes);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Org chart JSON serialization error", e);
        }
    }

    /**
     * Collects the whole department subtree below {@code rootDepartmentId} (the root
     * department itself included) together with the positions of every department, and
     * flattens it into d3-org-chart nodes.
     * <p>
     * Linking rules: the head position ({@code ishead = 1}) of a department is the
     * department's anchor — its remaining positions become its children, and the anchor
     * of a child department hangs under the anchor of its parent department. A department
     * without a head position gets a synthetic department node as its anchor, which keeps
     * the result single-rooted as d3-org-chart requires.
     */
    public List<OrgChartNode> getOrgChartNodes(UUID rootDepartmentId) {
        if (rootDepartmentId == null) {
            return List.of();
        }

        Map<UUID, Department> departmentsById = loadDepartmentsById();
        Department rootDepartment = departmentsById.get(rootDepartmentId);
        if (rootDepartment == null) {
            return List.of();
        }

        List<Department> subtree = collectSubtree(rootDepartment, departmentsById);
        Map<UUID, List<Position>> positionsByDepartment = loadPositionsByDepartment(subtree);

        List<OrgChartNode> nodes = new ArrayList<>();
        // anchor node id per department; filled top-down, so a parent anchor is always
        // present by the time its children are processed
        Map<UUID, String> anchorNodeIds = new HashMap<>();

        for (Department department : subtree) {
            List<Position> positions = positionsByDepartment.getOrDefault(department.getId(), List.of());
            Position headPosition = positions.stream()
                    .filter(this::isHead)
                    .findFirst()
                    .orElse(null);

            String parentNodeId = department.equals(rootDepartment)
                    ? null
                    : anchorNodeIds.get(referencedId(department.getParentDepartment()));

            String anchorNodeId;
            if (headPosition != null) {
                OrgChartNode headNode = createPositionNode(headPosition, department, parentNodeId);
                anchorNodeId = headNode.getId();
                nodes.add(headNode);
            } else {
                OrgChartNode departmentNode = createDepartmentNode(department, parentNodeId);
                anchorNodeId = departmentNode.getId();
                nodes.add(departmentNode);
            }
            anchorNodeIds.put(department.getId(), anchorNodeId);

            for (Position position : positions) {
                if (position.equals(headPosition)) {
                    continue;
                }
                nodes.add(createPositionNode(position, department, anchorNodeId));
            }
        }

        return nodes;
    }

    private Map<UUID, Department> loadDepartmentsById() {
        List<Department> departments = dataManager.load(Department.class)
                .query("select d from demo_Department d order by d.ordNo")
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("parentDepartment", FetchPlan.INSTANCE_NAME))
                .list();

        Map<UUID, Department> departmentsById = new LinkedHashMap<>();
        for (Department department : departments) {
            departmentsById.put(department.getId(), department);
        }
        return departmentsById;
    }

    /**
     * Breadth-first walk from the root department, so that every department is listed
     * after its parent and the sibling order of {@code ordNo} is preserved.
     */
    private List<Department> collectSubtree(Department rootDepartment, Map<UUID, Department> departmentsById) {
        Map<UUID, List<Department>> childrenByParent = new HashMap<>();
        for (Department department : departmentsById.values()) {
            UUID parentId = referencedId(department.getParentDepartment());
            if (parentId != null) {
                childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(department);
            }
        }

        List<Department> subtree = new ArrayList<>();
        Deque<Department> queue = new ArrayDeque<>();
        queue.add(rootDepartment);

        while (!queue.isEmpty()) {
            Department department = queue.removeFirst();
            // guards against a cycle in PARENT_DEPARTMENT_ID, which would otherwise loop forever
            if (subtree.contains(department)) {
                continue;
            }
            subtree.add(department);
            queue.addAll(childrenByParent.getOrDefault(department.getId(), List.of()));
        }

        return subtree;
    }

    private Map<UUID, List<Position>> loadPositionsByDepartment(List<Department> departments) {
        List<UUID> departmentIds = departments.stream()
                .map(Department::getId)
                .toList();

        List<Position> positions = dataManager.load(Position.class)
                .query("select p from demo_Position p where p.department.id in :departmentIds")
                .parameter("departmentIds", departmentIds)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("department", FetchPlan.INSTANCE_NAME)
                        .add("jobTitle", FetchPlan.INSTANCE_NAME)
                        // BASE, not INSTANCE_NAME: the employee card shows the e-mail too
                        .add("employee", FetchPlan.BASE))
                .list();

        // head first, then the most senior staff (higher lvl) first, then by employee name
        Comparator<Position> order = Comparator
                .comparing((Position p) -> isHead(p) ? 0 : 1)
                .thenComparing(Position::getLvl, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(this::employeeName, Comparator.nullsLast(Comparator.naturalOrder()));

        Map<UUID, List<Position>> positionsByDepartment = new HashMap<>();
        for (Position position : positions) {
            UUID departmentId = referencedId(position.getDepartment());
            if (departmentId != null) {
                positionsByDepartment.computeIfAbsent(departmentId, key -> new ArrayList<>()).add(position);
            }
        }
        positionsByDepartment.values().forEach(list -> list.sort(order));

        return positionsByDepartment;
    }

    private OrgChartNode createPositionNode(Position position, Department department, String parentNodeId) {
        OrgChartNode node = new OrgChartNode();
        node.setId(POSITION_NODE_PREFIX + position.getId());
        node.setParentId(parentNodeId);
        node.setOrgLevelName(department.getName());

        Employee employee = position.getEmployee();
        node.setName(employee != null && employee.getFullName() != null
                ? employee.getFullName()
                : messages.getMessage(OrgStructureService.class, "orgChart.vacantPosition"));
        node.setEmployeeId(employee != null ? String.valueOf(employee.getId()) : null);
        // null when the employee has no photo in the file storage — the chart then renders
        // the initials placeholder instead of an <img>
        node.setImage(employee != null ? employeePhotoService.getPhotoUrl(employee.getId()) : null);
        node.setEmail(employee != null ? employee.getEmail() : null);

        JobTitle jobTitle = position.getJobTitle();
        node.setPosition(jobTitle != null && jobTitle.getName() != null ? jobTitle.getName() : "");

        node.setDepartmentId(String.valueOf(department.getId()));
        node.setPositionId(String.valueOf(position.getId()));
        node.setStatus(position.getStatus() != null ? position.getStatus().getId() : null);
        node.setHead(isHead(position));
        node.setDepartmentNode(false);
        return node;
    }

    private OrgChartNode createDepartmentNode(Department department, String parentNodeId) {
        OrgChartNode node = new OrgChartNode();
        node.setId(DEPARTMENT_NODE_PREFIX + department.getId());
        node.setParentId(parentNodeId);
        node.setOrgLevelName(department.getName());
        node.setName("");
        node.setPosition("");
        node.setDepartmentId(String.valueOf(department.getId()));
        node.setHead(false);
        node.setDepartmentNode(true);
        return node;
    }

    private boolean isHead(Position position) {
        return Objects.equals(position.getIshead(), 1);
    }

    private String employeeName(Position position) {
        return position.getEmployee() != null ? position.getEmployee().getFullName() : null;
    }

    private UUID referencedId(Department department) {
        return department != null ? department.getId() : null;
    }

    public String getNodesJsonFromCsv() {
        try {
            return objectMapper.writeValueAsString(
                    parseCsv("data/d3-orgchart-demo-data.csv"));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Json load error", e);
        }
    }

    private List<D3OrgChartCsvItem> parseCsv(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);

            try (Reader reader = new InputStreamReader(resource.getInputStream())) {
                CsvToBean<D3OrgChartCsvItem> csvToBean = new CsvToBeanBuilder<D3OrgChartCsvItem>(reader)
                        .withType(D3OrgChartCsvItem.class)
                        .withIgnoreLeadingWhiteSpace(true)
                        .build();

                return csvToBean.parse();
            }

        } catch (Exception e) {
            throw new RuntimeException("Csv load error", e);
        }
    }

}
