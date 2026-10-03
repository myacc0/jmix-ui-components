package uz.kapitalbank.umida.service.orgstructure;

import uz.kapitalbank.umida.dto.orgstructure.OrgChartNode;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OrgStructureService {

    protected static final String POSITION_NODE_PREFIX = "pos-";
    protected static final String SUBDIVISION_NODE_PREFIX = "dept-";

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

    public List<OrgStructurePosition> getPositions(String searchText, int size) {
        return dataManager.load(OrgStructurePosition.class)
                .query("select p from umida_OrgStructurePosition p where p.employee.fullName like :name")
                .parameter("name", "(?i)%" + searchText + "%")
                .sort(Sort.by(Sort.Order.asc("employee.fullName")))
                .firstResult(0)
                .maxResults(size)
                .list();
    }

    /**
     * Builds the org chart for the given subdivision and serializes it for
     * {@code D3OrgChart.setData(String)}.
     */
    public String getOrgChartNodesJson(String rootSubdivisionId) {
        return toJson(getOrgChartNodes(rootSubdivisionId));
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
     * Collects the whole subdivision subtree below {@code rootSubdivisionId} (the root
     * subdivision itself included) together with the positions of every subdivision, and
     * flattens it into d3-org-chart nodes.
     * <p>
     * Linking rules: the head position ({@code ishead = 1}) of a subdivision is the
     * subdivision's anchor — its remaining positions become its children, and the anchor
     * of a child subdivision hangs under the anchor of its parent subdivision. A subdivision
     * without a head position gets a synthetic subdivision node as its anchor, which keeps
     * the result single-rooted as d3-org-chart requires.
     */
    public List<OrgChartNode> getOrgChartNodes(String rootSubdivisionId) {
        if (rootSubdivisionId == null) {
            return List.of();
        }

        Map<String, OrgStructureSubdivision> subdivisionsById = loadSubdivisionsById();
        OrgStructureSubdivision rootSubdivision = subdivisionsById.get(rootSubdivisionId);
        if (rootSubdivision == null) {
            return List.of();
        }

        List<OrgStructureSubdivision> subtree = collectSubtree(rootSubdivision, subdivisionsById);
        Map<String, List<OrgStructurePosition>> positionsBySubdivision = loadPositionsBySubdivision(subtree);

        List<OrgChartNode> nodes = new ArrayList<>();
        // anchor node id per subdivision; filled top-down, so a parent anchor is always
        // present by the time its children are processed
        Map<String, String> anchorNodeIds = new HashMap<>();

        for (OrgStructureSubdivision subdivision : subtree) {
            List<OrgStructurePosition> positions = positionsBySubdivision.getOrDefault(subdivision.getId(), List.of());
            OrgStructurePosition headPosition = positions.stream()
                    .filter(this::isHead)
                    .findFirst()
                    .orElse(null);

            String parentNodeId = subdivision.equals(rootSubdivision)
                    ? null
                    : anchorNodeIds.get(referencedId(subdivision.getParent()));

            String anchorNodeId;
            if (headPosition != null) {
                OrgChartNode headNode = createPositionNode(headPosition, subdivision, parentNodeId);
                anchorNodeId = headNode.getId();
                nodes.add(headNode);
            } else {
                OrgChartNode subdivisionNode = createSubdivisionNode(subdivision, parentNodeId);
                anchorNodeId = subdivisionNode.getId();
                nodes.add(subdivisionNode);
            }
            anchorNodeIds.put(subdivision.getId(), anchorNodeId);

            for (OrgStructurePosition position : positions) {
                if (position.equals(headPosition)) {
                    continue;
                }
                nodes.add(createPositionNode(position, subdivision, anchorNodeId));
            }
        }

        return nodes;
    }

    private Map<String, OrgStructureSubdivision> loadSubdivisionsById() {
        List<OrgStructureSubdivision> subdivisions = dataManager.load(OrgStructureSubdivision.class)
                .query("select d from umida_OrgStructureSubdivision d")
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("parent", FetchPlan.INSTANCE_NAME))
                .list();

        Map<String, OrgStructureSubdivision> subdivisionsById = new LinkedHashMap<>();
        for (OrgStructureSubdivision subdivision : subdivisions) {
            subdivisionsById.put(subdivision.getId(), subdivision);
        }
        return subdivisionsById;
    }

    /**
     * Breadth-first walk from the root subdivision, so that every subdivision is listed
     * after its parent and the sibling order of {@code ordNo} is preserved.
     */
    private List<OrgStructureSubdivision> collectSubtree(OrgStructureSubdivision rootSubdivision, Map<String, OrgStructureSubdivision> subdivisionsById) {
        Map<String, List<OrgStructureSubdivision>> childrenByParent = new HashMap<>();
        for (OrgStructureSubdivision subdivision : subdivisionsById.values()) {
            String parentId = referencedId(subdivision.getParent());
            if (parentId != null) {
                childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(subdivision);
            }
        }

        List<OrgStructureSubdivision> subtree = new ArrayList<>();
        Deque<OrgStructureSubdivision> queue = new ArrayDeque<>();
        queue.add(rootSubdivision);

        while (!queue.isEmpty()) {
            OrgStructureSubdivision subdivision = queue.removeFirst();
            // guards against a cycle in PARENT_ID, which would otherwise loop forever
            if (subtree.contains(subdivision)) {
                continue;
            }
            subtree.add(subdivision);
            queue.addAll(childrenByParent.getOrDefault(subdivision.getId(), List.of()));
        }

        return subtree;
    }

    private Map<String, List<OrgStructurePosition>> loadPositionsBySubdivision(List<OrgStructureSubdivision> subdivisions) {
        List<String> subdivisionIds = subdivisions.stream()
                .map(OrgStructureSubdivision::getId)
                .toList();

        List<OrgStructurePosition> positions = dataManager.load(OrgStructurePosition.class)
                .query("select p from umida_OrgStructurePosition p where p.subdivision.id in :subdivisionIds")
                .parameter("subdivisionIds", subdivisionIds)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("subdivision", FetchPlan.INSTANCE_NAME)
                        .add("jobTitle", FetchPlan.INSTANCE_NAME)
                        // BASE, not INSTANCE_NAME: the employee card shows the e-mail too
                        .add("employee", FetchPlan.BASE))
                .list();

        // head first, then the most senior staff (higher lvl) first, then by employee name
        Comparator<OrgStructurePosition> order = Comparator
                .comparing((OrgStructurePosition p) -> isHead(p) ? 0 : 1)
                .thenComparing(OrgStructurePosition::getLvl, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(this::employeeName, Comparator.nullsLast(Comparator.naturalOrder()));

        Map<String, List<OrgStructurePosition>> positionsBySubdivision = new HashMap<>();
        for (OrgStructurePosition position : positions) {
            String subdivisionId = referencedId(position.getSubdivision());
            if (subdivisionId != null) {
                positionsBySubdivision.computeIfAbsent(subdivisionId, key -> new ArrayList<>()).add(position);
            }
        }
        positionsBySubdivision.values().forEach(list -> list.sort(order));

        return positionsBySubdivision;
    }

    private OrgChartNode createPositionNode(OrgStructurePosition position, OrgStructureSubdivision subdivision, String parentNodeId) {
        OrgChartNode node = new OrgChartNode();
        node.setId(POSITION_NODE_PREFIX + position.getId());
        node.setParentId(parentNodeId);
        node.setOrgLevelName(subdivision.getName());

        OrgStructureEmployee employee = position.getEmployee();
        node.setName(employee != null && employee.getFullName() != null
                ? employee.getFullName()
                : messages.getMessage(OrgStructureService.class, "orgChart.vacantPosition"));
        node.setEmployeeId(employee != null ? String.valueOf(employee.getId()) : null);
        // null when the employee has no photo in the file storage — the chart then renders
        // the initials placeholder instead of an <img>
        node.setImage(employee != null ? employeePhotoService.getPhotoUrl(employee.getId()) : null);
        node.setEmail(employee != null ? employee.getEmail() : null);

        OrgStructureJobTitle jobTitle = position.getJobTitle();
        node.setPosition(jobTitle != null && jobTitle.getName() != null ? jobTitle.getName() : "");

        node.setSubdivisionId(String.valueOf(subdivision.getId()));
        node.setPositionId(String.valueOf(position.getId()));
        node.setStatus(position.getStatus() != null ? position.getStatus().getId() : null);
        node.setHead(isHead(position));
        node.setSubdivisionNode(false);
        return node;
    }

    private OrgChartNode createSubdivisionNode(OrgStructureSubdivision subdivision, String parentNodeId) {
        OrgChartNode node = new OrgChartNode();
        node.setId(SUBDIVISION_NODE_PREFIX + subdivision.getId());
        node.setParentId(parentNodeId);
        node.setOrgLevelName(subdivision.getName());
        node.setName("");
        node.setPosition("");
        node.setSubdivisionId(String.valueOf(subdivision.getId()));
        node.setHead(false);
        node.setSubdivisionNode(true);
        return node;
    }

    private boolean isHead(OrgStructurePosition position) {
        return Objects.equals(position.getIsheadofsubdivision(), 1);
    }

    private String employeeName(OrgStructurePosition position) {
        return position.getEmployee() != null ? position.getEmployee().getFullName() : null;
    }

    private String referencedId(OrgStructureSubdivision subdivision) {
        return subdivision != null ? subdivision.getId() : null;
    }

}
