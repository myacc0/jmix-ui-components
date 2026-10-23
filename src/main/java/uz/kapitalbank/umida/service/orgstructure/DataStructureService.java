package uz.kapitalbank.umida.service.orgstructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.dto.orgstructure.DataAssetChartNode;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataDomainSteward;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.dict.DictDataProductSteward;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

import static uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql.TODAY_PARAM;
import static uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql.active;
import static uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql.currentAssignment;

/**
 * Builds the d3-org-chart of the data domain and the data product hierarchies shown in
 * {@code DataDomainStructureView} and {@code DataProductStructureView}.
 * <p>
 * Both hierarchies are built the same way: every domain / product becomes one node hanging
 * under the node of its parent. A node shows the business owner subdivision, the head of
 * that subdivision — the employee currently assigned to its active {@code ishead = 1} position — with
 * the photo of the head, and carries the details {@code DataAssetCardView} shows: the long name, the
 * description, the dates and the names of the stewards that are active today.
 */
@Service
public class DataStructureService {

    private final DataManager dataManager;
    private final EmployeePhotoService employeePhotoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataStructureService(DataManager dataManager, EmployeePhotoService employeePhotoService) {
        this.dataManager = dataManager;
        this.employeePhotoService = employeePhotoService;
    }

    /** Domains whose short or long name contains {@code searchText}, ignoring case. */
    public List<DictDataDomain> searchDomains(String searchText, int size) {
        return dataManager.load(DictDataDomain.class)
                .query("select e from umida_DictDataDomain e " +
                        "where lower(e.shortName) like :text or lower(e.longName) like :text")
                .parameter("text", containsPattern(searchText))
                .sort(Sort.by(Sort.Order.asc("shortName")))
                .firstResult(0)
                .maxResults(size)
                .list();
    }

    /** Data products whose short or long name contains {@code searchText}, ignoring case. */
    public List<DictDataProduct> searchProducts(String searchText, int size) {
        return dataManager.load(DictDataProduct.class)
                .query("select e from umida_DictDataProduct e " +
                        "where lower(e.shortName) like :text or lower(e.longName) like :text")
                .parameter("text", containsPattern(searchText))
                .sort(Sort.by(Sort.Order.asc("shortName")))
                .firstResult(0)
                .maxResults(size)
                .list();
    }

    /**
     * Flattens the domain subtree below {@code rootDomainId} (the root domain itself
     * included) into chart nodes, parents always listed before their children.
     */
    public List<DataAssetChartNode> getDomainChartNodes(Integer rootDomainId) {
        if (rootDomainId == null) {
            return List.of();
        }

        List<AssetRow> rows = dataManager.load(DictDataDomain.class)
                .query("select e from umida_DictDataDomain e order by e.shortName")
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("createdDate")
                        .add("parent", FetchPlan.INSTANCE_NAME)
                        .add("businessOwner", FetchPlan.INSTANCE_NAME))
                .list()
                .stream()
                .map(domain -> new AssetRow(domain.getId(),
                        domain.getParent() != null ? domain.getParent().getId() : null,
                        domain.getShortName(), domain.getLongName(), domain.getDescription(),
                        domain.getCreatedDate(), domain.getAssignDate(), domain.getBusinessOwner()))
                .toList();

        return buildNodes(rows, rootDomainId, this::loadActiveDomainStewardNames);
    }

    /**
     * Flattens the data product subtree below {@code rootProductId} (the root product
     * itself included) into chart nodes, parents always listed before their children.
     */
    public List<DataAssetChartNode> getProductChartNodes(Integer rootProductId) {
        if (rootProductId == null) {
            return List.of();
        }

        List<AssetRow> rows = dataManager.load(DictDataProduct.class)
                .query("select e from umida_DictDataProduct e order by e.shortName")
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("createdDate")
                        .add("parent", FetchPlan.INSTANCE_NAME)
                        .add("businessOwner", FetchPlan.INSTANCE_NAME))
                .list()
                .stream()
                .map(product -> new AssetRow(product.getId(),
                        product.getParent() != null ? product.getParent().getId() : null,
                        product.getShortName(), product.getLongName(), product.getDescription(),
                        product.getCreatedDate(), product.getAssignDate(), product.getBusinessOwner()))
                .toList();

        return buildNodes(rows, rootProductId, this::loadActiveProductStewardNames);
    }

    /** Serializes the nodes for {@code D3OrgChart.setData(String)}; card-only fields are left out. */
    public String toJson(List<DataAssetChartNode> nodes) {
        try {
            return objectMapper.writeValueAsString(nodes);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Data structure chart JSON serialization error", e);
        }
    }

    private List<DataAssetChartNode> buildNodes(List<AssetRow> rows, Integer rootId,
                                                Function<List<Integer>, Map<Integer, List<String>>> stewardNamesLoader) {
        Map<Integer, AssetRow> rowsById = new LinkedHashMap<>();
        rows.forEach(row -> rowsById.put(row.id(), row));

        AssetRow root = rowsById.get(rootId);
        if (root == null) {
            return List.of();
        }

        List<AssetRow> subtree = collectSubtree(root, rowsById.values());
        List<Integer> subtreeIds = subtree.stream().map(AssetRow::id).toList();

        Set<String> businessOwnerIds = new LinkedHashSet<>();
        subtree.stream()
                .map(AssetRow::businessOwner)
                .filter(Objects::nonNull)
                .forEach(owner -> businessOwnerIds.add(owner.getId()));
        Map<String, Head> headsBySubdivision = loadHeads(businessOwnerIds);
        Map<Integer, List<String>> stewardNames = stewardNamesLoader.apply(subtreeIds);

        List<DataAssetChartNode> nodes = new ArrayList<>();
        for (AssetRow row : subtree) {
            // the selected element is the root of the chart even when it has a parent
            String parentId = Objects.equals(row.id(), root.id()) ? null : String.valueOf(row.parentId());
            OrgStructureSubdivision owner = row.businessOwner();
            Head head = owner != null ? headsBySubdivision.get(owner.getId()) : null;
            nodes.add(createNode(row, parentId, head, stewardNames.getOrDefault(row.id(), List.of())));
        }
        return nodes;
    }

    /**
     * Breadth-first walk from the root, so that every element is listed after its parent
     * and the sibling order of the query is preserved.
     */
    private List<AssetRow> collectSubtree(AssetRow root, Iterable<AssetRow> rows) {
        Map<Integer, List<AssetRow>> childrenByParent = new HashMap<>();
        for (AssetRow row : rows) {
            if (row.parentId() != null) {
                childrenByParent.computeIfAbsent(row.parentId(), key -> new ArrayList<>()).add(row);
            }
        }

        List<AssetRow> subtree = new ArrayList<>();
        Set<Integer> visited = new LinkedHashSet<>();
        Deque<AssetRow> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            AssetRow row = queue.removeFirst();
            // guards against a cycle in PARENT_ID, which would otherwise loop forever
            if (!visited.add(row.id())) {
                continue;
            }
            subtree.add(row);
            queue.addAll(childrenByParent.getOrDefault(row.id(), List.of()));
        }
        return subtree;
    }

    /**
     * The head of every given subdivision: an active head position together with the employee currently
     * assigned to it. When a subdivision has several heads, a filled one wins over a vacant one, then the
     * most senior (higher lvl), then by employee name.
     */
    private Map<String, Head> loadHeads(Set<String> subdivisionIds) {
        if (subdivisionIds.isEmpty()) {
            return Map.of();
        }

        List<OrgStructurePosition> positions = dataManager.load(OrgStructurePosition.class)
                .query("select p from umida_OrgStructurePosition p" +
                        " where p.subdivision.id in :subdivisionIds and p.isheadofsubdivision = 1 and " + active("p"))
                .parameter("subdivisionIds", subdivisionIds)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("subdivision", FetchPlan.INSTANCE_NAME)
                        .add("jobTitle", FetchPlan.INSTANCE_NAME))
                .list();

        Map<String, List<OrgStructureEmployee>> employeesByPosition = new HashMap<>();
        if (!positions.isEmpty()) {
            dataManager.load(OrgStructureAssignments.class)
                    .query("select a from umida_OrgStructureAssignments a" +
                            " where a.position.id in :positionIds and a.employee is not null and " + currentAssignment("a"))
                    .parameter("positionIds", positions.stream().map(OrgStructurePosition::getId).toList())
                    .parameter(TODAY_PARAM, LocalDate.now())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                            .add("position", FetchPlan.INSTANCE_NAME)
                            // BASE, not INSTANCE_NAME: the card shows the e-mail too
                            .add("employee", FetchPlan.BASE))
                    .list()
                    .forEach(assignment -> employeesByPosition
                            .computeIfAbsent(assignment.getPosition().getId(), key -> new ArrayList<>())
                            .add(assignment.getEmployee()));
        }

        Comparator<Head> order = Comparator
                .comparing((Head h) -> h.employee() != null ? 0 : 1)
                .thenComparing(h -> h.position().getLvl(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(h -> h.employee() != null ? h.employee().getFullName() : null,
                        Comparator.nullsLast(Comparator.naturalOrder()));

        Map<String, Head> headsBySubdivision = new HashMap<>();
        for (OrgStructurePosition position : positions) {
            if (position.getSubdivision() == null) {
                continue;
            }
            List<OrgStructureEmployee> employees = employeesByPosition.getOrDefault(position.getId(), List.of());
            List<Head> candidates = employees.isEmpty()
                    ? List.of(new Head(position, null))
                    : employees.stream().map(employee -> new Head(position, employee)).toList();
            for (Head candidate : candidates) {
                headsBySubdivision.merge(position.getSubdivision().getId(), candidate,
                        (current, next) -> order.compare(next, current) < 0 ? next : current);
            }
        }
        return headsBySubdivision;
    }

    private Map<Integer, List<String>> loadActiveDomainStewardNames(List<Integer> domainIds) {
        List<DictDataDomainSteward> stewards = dataManager.load(DictDataDomainSteward.class)
                .query("select s from umida_DictDataDomainSteward s " +
                        "where s.domain.id in :ids and s.dateBegin <= :today " +
                        "and (s.dateEnd is null or s.dateEnd >= :today)")
                .parameter("ids", domainIds)
                .parameter("today", LocalDate.now())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("domain", FetchPlan.INSTANCE_NAME)
                        .add("employee", FetchPlan.INSTANCE_NAME))
                .list();

        Map<Integer, List<String>> namesByDomain = new HashMap<>();
        for (DictDataDomainSteward steward : stewards) {
            addStewardName(namesByDomain, steward.getDomain() != null ? steward.getDomain().getId() : null,
                    steward.getEmployee());
        }
        namesByDomain.values().forEach(names -> names.sort(Comparator.naturalOrder()));
        return namesByDomain;
    }

    private Map<Integer, List<String>> loadActiveProductStewardNames(List<Integer> productIds) {
        List<DictDataProductSteward> stewards = dataManager.load(DictDataProductSteward.class)
                .query("select s from umida_DictDataProductSteward s " +
                        "where s.dataProduct.id in :ids and s.dateBegin <= :today " +
                        "and (s.dateEnd is null or s.dateEnd >= :today)")
                .parameter("ids", productIds)
                .parameter("today", LocalDate.now())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("dataProduct", FetchPlan.INSTANCE_NAME)
                        .add("employee", FetchPlan.INSTANCE_NAME))
                .list();

        Map<Integer, List<String>> namesByProduct = new HashMap<>();
        for (DictDataProductSteward steward : stewards) {
            addStewardName(namesByProduct, steward.getDataProduct() != null ? steward.getDataProduct().getId() : null,
                    steward.getEmployee());
        }
        namesByProduct.values().forEach(names -> names.sort(Comparator.naturalOrder()));
        return namesByProduct;
    }

    private void addStewardName(Map<Integer, List<String>> namesByAsset, Integer assetId, OrgStructureEmployee employee) {
        if (assetId == null || employee == null || employee.getFullName() == null) {
            return;
        }
        List<String> names = namesByAsset.computeIfAbsent(assetId, key -> new ArrayList<>());
        // the same employee may hold two overlapping steward periods — list the name once
        if (!names.contains(employee.getFullName())) {
            names.add(employee.getFullName());
        }
    }

    private DataAssetChartNode createNode(AssetRow row, String parentId, @Nullable Head head, List<String> stewardNames) {
        DataAssetChartNode node = new DataAssetChartNode();
        node.setId(String.valueOf(row.id()));
        node.setParentId(parentId);
        node.setName(row.shortName());
        node.setBusinessOwnerName(row.businessOwner() != null ? row.businessOwner().getName() : "");

        OrgStructureEmployee headOrgStructureEmployee = head != null ? head.employee() : null;
        node.setHeadName(headOrgStructureEmployee != null && headOrgStructureEmployee.getFullName() != null ? headOrgStructureEmployee.getFullName() : "");
        // null when the head has no photo in the file storage — the chart then renders the
        // initials of the domain / product name instead of an <img>
        node.setImage(headOrgStructureEmployee != null ? employeePhotoService.getPhotoUrl(headOrgStructureEmployee.getId()) : null);
        node.setHeadEmail(headOrgStructureEmployee != null ? headOrgStructureEmployee.getEmail() : null);
        node.setHeadJobTitle(head != null && head.position().getJobTitle() != null ? head.position().getJobTitle().getName() : null);

        node.setLongName(row.longName());
        node.setDescription(row.description());
        node.setCreatedDate(row.createdDate());
        node.setAssignDate(row.assignDate());
        node.setStewardNames(new ArrayList<>(stewardNames));
        return node;
    }

    private String containsPattern(String searchText) {
        return "%" + Objects.toString(searchText, "").trim().toLowerCase() + "%";
    }

    /** The attributes a domain and a data product have in common, so that one builder serves both. */
    private record AssetRow(Integer id, Integer parentId, String shortName, String longName, String description,
                            OffsetDateTime createdDate, LocalDate assignDate, OrgStructureSubdivision businessOwner) {
    }

    /** A head position of a subdivision and the employee holding it, {@code null} when it is vacant. */
    private record Head(OrgStructurePosition position, @Nullable OrgStructureEmployee employee) {
    }
}
