package uz.kapitalbank.umida.datastructure;

import uz.kapitalbank.umida.dto.orgstructure.DataAssetChartNode;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataDomainSteward;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.dict.DictDataProductSteward;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import uz.kapitalbank.umida.enums.orgstructure.OrgStructurePositionStatus;
import uz.kapitalbank.umida.service.orgstructure.DataStructureService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how {@link DataStructureService} turns the data domain and data product
 * hierarchies into d3-org-chart nodes.
 * <p>
 * The whole fixture is created by the test and removed afterwards, and the assertions only
 * ever look at the nodes of that fixture, so rows a developer left in the database cannot
 * change the outcome.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
class DataStructureServiceTests {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private DataStructureService dataStructureService;

    private final List<Object> cleanup = new ArrayList<>();

    @Test
    void buildsDomainSubtreeWithBusinessOwnerAndItsHead() {
        String suffix = UUID.randomUUID().toString();

        OrgStructureSubdivision ownerA = createSubdivision("Owner A " + suffix);
        OrgStructureSubdivision ownerB = createSubdivision("Owner B " + suffix);
        OrgStructureEmployee headA = createEmployee("Head A " + suffix, "head.a@test.com");
        createPosition(ownerA, createJobTitle("Director " + suffix), headA, "10", 1);
        // a non-head position of the same subdivision must not be taken for the head
        createPosition(ownerA, createJobTitle("Specialist " + suffix), createEmployee("Staff A " + suffix, null), "12", 0);

        DictDataDomain root = createDomain("Root " + suffix, null, ownerA);
        DictDataDomain child = createDomain("Child " + suffix, root, ownerB);
        DictDataDomain grandChild = createDomain("Grand child " + suffix, child, ownerA);
        DictDataDomain outside = createDomain("Outside " + suffix, null, ownerA);

        List<DataAssetChartNode> nodes = dataStructureService.getDomainChartNodes(root.getId());
        Map<String, DataAssetChartNode> byId = byId(nodes);

        assertEquals(3, nodes.size());
        assertFalse(byId.containsKey(String.valueOf(outside.getId())));
        // parents come before their children, as d3-org-chart requires
        assertEquals(String.valueOf(root.getId()), nodes.get(0).getId());

        DataAssetChartNode rootNode = byId.get(String.valueOf(root.getId()));
        assertNull(rootNode.getParentId());
        assertEquals("Root " + suffix, rootNode.getName());
        assertEquals("Owner A " + suffix, rootNode.getBusinessOwnerName());
        assertEquals("Head A " + suffix, rootNode.getHeadName());
        assertEquals("Director " + suffix, rootNode.getHeadJobTitle());
        assertEquals("head.a@test.com", rootNode.getHeadEmail());
        assertEquals("Long Root " + suffix, rootNode.getLongName());
        assertEquals("Description of Root " + suffix, rootNode.getDescription());
        assertEquals(LocalDate.of(2026, 1, 15), rootNode.getAssignDate());
        assertNotNull(rootNode.getCreatedDate());
        // the head has no photo in the storage, so the chart falls back to the initials
        assertNull(rootNode.getImage());

        DataAssetChartNode childNode = byId.get(String.valueOf(child.getId()));
        assertEquals(String.valueOf(root.getId()), childNode.getParentId());
        assertEquals("Owner B " + suffix, childNode.getBusinessOwnerName());
        // Owner B has no head position
        assertEquals("", childNode.getHeadName());
        assertNull(childNode.getHeadJobTitle());

        DataAssetChartNode grandChildNode = byId.get(String.valueOf(grandChild.getId()));
        assertEquals(String.valueOf(child.getId()), grandChildNode.getParentId());
        assertEquals("Head A " + suffix, grandChildNode.getHeadName());
    }

    @Test
    void selectedDomainBecomesTheRootOfTheChart() {
        String suffix = UUID.randomUUID().toString();
        OrgStructureSubdivision owner = createSubdivision("Owner " + suffix);

        DictDataDomain root = createDomain("Root " + suffix, null, owner);
        DictDataDomain child = createDomain("Child " + suffix, root, owner);
        DictDataDomain grandChild = createDomain("Grand child " + suffix, child, owner);

        List<DataAssetChartNode> nodes = dataStructureService.getDomainChartNodes(child.getId());
        Map<String, DataAssetChartNode> byId = byId(nodes);

        assertEquals(2, nodes.size());
        assertFalse(byId.containsKey(String.valueOf(root.getId())));
        assertNull(byId.get(String.valueOf(child.getId())).getParentId());
        assertEquals(String.valueOf(child.getId()), byId.get(String.valueOf(grandChild.getId())).getParentId());
    }

    @Test
    void prefersFilledAndSeniorHeadPosition() {
        String suffix = UUID.randomUUID().toString();
        OrgStructureSubdivision owner = createSubdivision("Owner " + suffix);
        OrgStructureJobTitle title = createJobTitle("Head " + suffix);
        createPosition(owner, title, null, "20", 1);
        createPosition(owner, title, createEmployee("Junior head " + suffix, null), "5", 1);
        createPosition(owner, title, createEmployee("Senior head " + suffix, null), "9", 1);

        DictDataDomain domain = createDomain("Domain " + suffix, null, owner);

        DataAssetChartNode node = dataStructureService.getDomainChartNodes(domain.getId()).get(0);

        assertEquals("Senior head " + suffix, node.getHeadName());
    }

    @Test
    void listsOnlyStewardsActiveToday() {
        String suffix = UUID.randomUUID().toString();
        OrgStructureSubdivision owner = createSubdivision("Owner " + suffix);
        DictDataDomain domain = createDomain("Domain " + suffix, null, owner);
        LocalDate today = LocalDate.now();

        createDomainSteward(domain, createEmployee("Open-ended " + suffix, null), today.minusDays(10), null);
        createDomainSteward(domain, createEmployee("Ends today " + suffix, null), today.minusDays(10), today);
        createDomainSteward(domain, createEmployee("Expired " + suffix, null), today.minusDays(10), today.minusDays(1));
        createDomainSteward(domain, createEmployee("Future " + suffix, null), today.plusDays(1), null);

        DataAssetChartNode node = dataStructureService.getDomainChartNodes(domain.getId()).get(0);

        assertEquals(List.of("Ends today " + suffix, "Open-ended " + suffix), node.getStewardNames());
    }

    @Test
    void buildsProductSubtreeWithActiveStewards() {
        String suffix = UUID.randomUUID().toString();
        OrgStructureSubdivision owner = createSubdivision("Owner " + suffix);
        OrgStructureEmployee head = createEmployee("Head " + suffix, null);
        createPosition(owner, createJobTitle("Director " + suffix), head, "10", 1);

        DictDataProduct root = createProduct("Root " + suffix, null, owner);
        DictDataProduct child = createProduct("Child " + suffix, root, owner);
        LocalDate today = LocalDate.now();
        createProductSteward(child, createEmployee("Steward " + suffix, null), today.minusDays(1), null);
        createProductSteward(child, createEmployee("Expired " + suffix, null), today.minusDays(5), today.minusDays(1));

        List<DataAssetChartNode> nodes = dataStructureService.getProductChartNodes(root.getId());
        Map<String, DataAssetChartNode> byId = byId(nodes);

        assertEquals(2, nodes.size());
        DataAssetChartNode rootNode = byId.get(String.valueOf(root.getId()));
        assertNull(rootNode.getParentId());
        assertEquals("Head " + suffix, rootNode.getHeadName());
        assertTrue(rootNode.getStewardNames().isEmpty());

        DataAssetChartNode childNode = byId.get(String.valueOf(child.getId()));
        assertEquals(String.valueOf(root.getId()), childNode.getParentId());
        assertEquals(List.of("Steward " + suffix), childNode.getStewardNames());
    }

    @Test
    void serializesOnlyTheChartFieldsToJson() throws Exception {
        String suffix = UUID.randomUUID().toString();
        OrgStructureSubdivision owner = createSubdivision("Owner " + suffix);
        createPosition(owner, createJobTitle("Director " + suffix), createEmployee("Head " + suffix, null), "10", 1);
        DictDataDomain domain = createDomain("Domain " + suffix, null, owner);

        String json = dataStructureService.toJson(dataStructureService.getDomainChartNodes(domain.getId()));
        JsonNode node = new ObjectMapper().readTree(json).get(0);

        assertEquals(String.valueOf(domain.getId()), node.get("id").asText());
        assertTrue(node.get("parentId").isNull());
        assertEquals("Domain " + suffix, node.get("name").asText());
        assertEquals("Owner " + suffix, node.get("businessOwnerName").asText());
        assertEquals("Head " + suffix, node.get("headName").asText());
        // card-only details — the dates would not even serialize without the java.time module
        assertFalse(node.has("description"));
        assertFalse(node.has("createdDate"));
        assertFalse(node.has("assignDate"));
        assertFalse(node.has("stewardNames"));
    }

    @Test
    void searchesDomainsAndProductsByNameIgnoringCase() {
        String suffix = UUID.randomUUID().toString();
        OrgStructureSubdivision owner = createSubdivision("Owner " + suffix);
        DictDataDomain domain = createDomain("Findable " + suffix, null, owner);
        DictDataProduct product = createProduct("Findable " + suffix, null, owner);

        String term = "FINDABLE " + suffix.substring(0, 8).toUpperCase();

        assertEquals(List.of(domain), dataStructureService.searchDomains(term, 20));
        assertEquals(List.of(product), dataStructureService.searchProducts(term, 20));
        // long name matches too
        assertEquals(List.of(domain), dataStructureService.searchDomains("long findable " + suffix, 20));
    }

    @Test
    void returnsEmptyListForUnknownOrNullRoot() {
        assertTrue(dataStructureService.getDomainChartNodes(null).isEmpty());
        assertTrue(dataStructureService.getDomainChartNodes(Integer.MIN_VALUE).isEmpty());
        assertTrue(dataStructureService.getProductChartNodes(null).isEmpty());
        assertTrue(dataStructureService.getProductChartNodes(Integer.MIN_VALUE).isEmpty());
    }

    private Map<String, DataAssetChartNode> byId(List<DataAssetChartNode> nodes) {
        return nodes.stream().collect(Collectors.toMap(DataAssetChartNode::getId, Function.identity()));
    }

    private OrgStructureSubdivision createSubdivision(String name) {
        OrgStructureSubdivision subdivision = dataManager.create(OrgStructureSubdivision.class);
        subdivision.setName(name);
        return track(dataManager.save(subdivision));
    }

    private OrgStructureJobTitle createJobTitle(String name) {
        OrgStructureJobTitle jobTitle = dataManager.create(OrgStructureJobTitle.class);
        jobTitle.setName(name);
        return track(dataManager.save(jobTitle));
    }

    private OrgStructureEmployee createEmployee(String fullName, String email) {
        OrgStructureEmployee orgStructureEmployee = dataManager.create(OrgStructureEmployee.class);
        orgStructureEmployee.setFullName(fullName);
        orgStructureEmployee.setEmail(email);
        return track(dataManager.save(orgStructureEmployee));
    }

    private void createPosition(OrgStructureSubdivision subdivision, OrgStructureJobTitle jobTitle, OrgStructureEmployee orgStructureEmployee, String lvl, Integer ishead) {
        OrgStructurePosition position = dataManager.create(OrgStructurePosition.class);
        position.setSubdivision(subdivision);
        position.setJobTitle(jobTitle);
        position.setEmployee(orgStructureEmployee);
        position.setLvl(lvl);
        position.setIsheadofsubdivision(ishead);
        position.setStatus(orgStructureEmployee != null ? OrgStructurePositionStatus.FILLED : OrgStructurePositionStatus.VACANT);
        track(dataManager.save(position));
    }

    private DictDataDomain createDomain(String shortName, DictDataDomain parent, OrgStructureSubdivision owner) {
        DictDataDomain domain = dataManager.create(DictDataDomain.class);
        domain.setCode("code-" + UUID.randomUUID());
        domain.setShortName(shortName);
        domain.setLongName("Long " + shortName);
        domain.setDescription("Description of " + shortName);
        domain.setParent(parent);
        domain.setBusinessOwner(owner);
        domain.setAssignDate(LocalDate.of(2026, 1, 15));
        return track(dataManager.save(domain));
    }

    private DictDataProduct createProduct(String shortName, DictDataProduct parent, OrgStructureSubdivision owner) {
        DictDataProduct product = dataManager.create(DictDataProduct.class);
        product.setCode("code-" + UUID.randomUUID());
        product.setShortName(shortName);
        product.setLongName("Long " + shortName);
        product.setParent(parent);
        product.setBusinessOwner(owner);
        product.setAssignDate(LocalDate.of(2026, 2, 1));
        return track(dataManager.save(product));
    }

    private void createDomainSteward(DictDataDomain domain, OrgStructureEmployee orgStructureEmployee, LocalDate begin, LocalDate end) {
        DictDataDomainSteward steward = dataManager.create(DictDataDomainSteward.class);
        steward.setDomain(domain);
        steward.setEmployee(orgStructureEmployee);
        steward.setDateBegin(begin);
        steward.setDateEnd(end);
        track(dataManager.save(steward));
    }

    private void createProductSteward(DictDataProduct product, OrgStructureEmployee orgStructureEmployee, LocalDate begin, LocalDate end) {
        DictDataProductSteward steward = dataManager.create(DictDataProductSteward.class);
        steward.setProduct(product);
        steward.setEmployee(orgStructureEmployee);
        steward.setDateBegin(begin);
        steward.setDateEnd(end);
        track(dataManager.save(steward));
    }

    private <T> T track(T saved) {
        cleanup.add(saved);
        return saved;
    }

    @AfterEach
    void tearDown() {
        // reverse creation order: stewards and positions before what they reference, child
        // domains before their parents. Domains and stewards are soft-deletable — a soft-deleted
        // row would keep its foreign key to the subdivision, so they are removed for real.
        List<Object> reversed = new ArrayList<>(cleanup);
        Collections.reverse(reversed);
        for (Object entity : reversed) {
            dataManager.save(new SaveContext()
                    .removing(entity)
                    .setHint(PersistenceHints.SOFT_DELETION, false));
        }
        cleanup.clear();
    }
}
