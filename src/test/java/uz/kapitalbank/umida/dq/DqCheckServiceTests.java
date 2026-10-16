package uz.kapitalbank.umida.dq;

import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.dto.dq.DqRuleFilter;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqCheck;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.service.dq.DqCheckService;
import uz.kapitalbank.umida.service.dq.DqRuleFilterService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bookkeeping of a check: its code, the counts of its rules, its deletion once its last rule is
 * deleted, and the rule filter of the dialog its rules are picked in.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqCheckServiceTests {

    @Autowired
    DataManager dataManager;

    @Autowired
    Metadata metadata;

    @Autowired
    DqCheckService checkService;

    @Autowired
    DqRuleFilterService ruleFilterService;

    private DqTestData testData;
    private DictDataDomain domain;
    private DqRule activeRule;
    private DqRule inactiveRule;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        domain = testData.createDomain();
        activeRule = testData.createRule(domain, "check active", DqRuleType.NOT_NULL, "short_name", "{}");
        inactiveRule = testData.createRule(domain, "check inactive", DqRuleType.NOT_NULL, "short_name", "{}",
                rule -> rule.setActive(false));
    }

    @Test
    void aNewCheckGetsTheNextCodeAndKeepsIt() {
        DqCheck first = createCheck(activeRule);
        DqCheck second = createCheck(activeRule);

        assertTrue(first.getCode().matches("DQC-\\d+"), first.getCode());
        assertEquals(codeNumber(first) + 1, codeNumber(second), "the codes follow one sequence");

        DqCheck loaded = dataManager.load(DqCheck.class).id(first.getId()).one();
        loaded.setName(loaded.getName() + " renamed");
        assertEquals(first.getCode(), dataManager.save(loaded).getCode(), "a saved check keeps its code");
    }

    @Test
    void countsTheRulesOfEachCheckAndTheActiveOnesAmongThem() {
        DqCheck mixed = createCheck(activeRule, inactiveRule);
        DqCheck idle = createCheck(inactiveRule);

        Map<UUID, DqCheckService.RuleCounts> counts = checkService.countRules(List.of(mixed, idle));

        assertEquals(new DqCheckService.RuleCounts(1, 2), counts.get(mixed.getId()));
        assertEquals(new DqCheckService.RuleCounts(0, 1), counts.get(idle.getId()));
    }

    @Test
    void aRunExecutesTheActiveRulesOfTheCheckOnly() {
        DqCheck check = createCheck(activeRule, inactiveRule);

        assertEquals(List.of(activeRule.getId()),
                checkService.loadActiveRules(check.getId()).stream().map(DqRule::getId).toList());
    }

    @Test
    void deletingTheLastRuleOfACheckDeletesTheCheck() {
        DqCheck onlyThisRule = createCheck(activeRule);
        DqCheck otherRulesToo = createCheck(activeRule, inactiveRule);

        dataManager.remove(dataManager.load(DqRule.class).id(activeRule.getId()).one());

        assertTrue(dataManager.load(DqCheck.class).id(onlyThisRule.getId()).optional().isEmpty(),
                "a check without rules has nothing to run");
        DqCheck deleted = dataManager.load(DqCheck.class)
                .id(onlyThisRule.getId())
                .hint(PersistenceHints.SOFT_DELETION, false)
                .one();
        assertNotNull(deleted.getDeletedDate(), "the check is soft-deleted, like the rule");

        assertTrue(dataManager.load(DqCheck.class).id(otherRulesToo.getId()).optional().isPresent(),
                "a check with rules left is kept");
        assertEquals(new DqCheckService.RuleCounts(0, 1),
                checkService.countRules(List.of(otherRulesToo)).get(otherRulesToo.getId()),
                "the deleted rule is no longer part of the check");
    }

    @Test
    void theRuleDialogOffersActiveRulesNotInTheCheckYet() {
        DqRule otherRule = testData.createRule(domain, "check other", DqRuleType.NOT_NULL, "short_name", "{}");

        DqRuleFilter filter = metadata.create(DqRuleFilter.class);
        filter.setDomain(domain);

        assertEquals(Set.of(activeRule.getId(), otherRule.getId()), offered(filter, List.of()),
                "inactive rules are never offered");
        assertEquals(Set.of(otherRule.getId()), offered(filter, List.of(activeRule.getId())),
                "the rules the check holds are left out");

        // a part of the code in another case: the match is "contains", case-insensitive
        filter.setRuleCode(otherRule.getCode().substring(3).toUpperCase());
        assertEquals(Set.of(otherRule.getId()), offered(filter, List.of()));
    }

    private Set<UUID> offered(DqRuleFilter filter, Collection<UUID> excludedIds) {
        return new HashSet<>(dataManager.load(DqRule.class)
                .query("select e from umida_DqRule e")
                .condition(ruleFilterService.createRuleCondition())
                .parameters(ruleFilterService.createRuleParameters(filter, excludedIds))
                .list()
                .stream()
                .map(DqRule::getId)
                .toList());
    }

    private DqCheck createCheck(DqRule... rules) {
        DqCheck check = dataManager.create(DqCheck.class);
        check.setName("dq test check " + UUID.randomUUID());
        check.setRules(new HashSet<>(Arrays.asList(rules)));
        // tracked after the rules, so that cleanup removes the check first
        return testData.track(dataManager.save(check));
    }

    private static int codeNumber(DqCheck check) {
        return Integer.parseInt(check.getCode().substring(DqCheckService.CODE_PREFIX.length()));
    }

    @AfterEach
    void tearDown() {
        testData.cleanup();
    }
}
