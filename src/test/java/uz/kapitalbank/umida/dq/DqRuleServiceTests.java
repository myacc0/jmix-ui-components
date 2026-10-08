package uz.kapitalbank.umida.dq;

import io.jmix.core.DataManager;
import io.jmix.core.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqDimension;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSeverity;
import uz.kapitalbank.umida.service.dq.DqRuleService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bookkeeping of a rule that its editor does not ask for: the generated code, the key fields
 * timestamp and the default resolution period.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
class DqRuleServiceTests {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private DqRuleService ruleService;

    private final List<Object> cleanup = new ArrayList<>();

    @Test
    void aNewRuleGetsTheNextCodeOfTheSequence() {
        DqRule first = save(newRule());
        DqRule second = save(newRule());

        assertThat(first.getCode()).matches("DQR-\\d+");
        long firstNumber = codeNumber(first);
        assertThat(firstNumber).isGreaterThanOrEqualTo(100);
        assertThat(codeNumber(second)).isEqualTo(firstNumber + 1);
    }

    @Test
    void theCodeIsNotRegeneratedOnUpdate() {
        DqRule rule = save(newRule());
        String code = rule.getCode();

        rule.setName(rule.getName() + " renamed");
        rule = save(rule);

        assertThat(reload(rule).getCode()).isEqualTo(code);
    }

    @Test
    void theDueDaysDefaultToTheSeverityDayCost() {
        DqRule rule = newRule();
        rule.setSeverity(DqSeverity.HIGH);
        rule = save(rule);
        assertThat(reload(rule).getDueDays()).isEqualTo(DqSeverity.HIGH.getDayCost());

        DqRule custom = newRule();
        custom.setSeverity(DqSeverity.HIGH);
        custom.setDueDays(42);
        custom = save(custom);
        assertThat(reload(custom).getDueDays()).isEqualTo(42);
    }

    @Test
    void keyFieldsChangedAtIsSetOnCreationAndRenewedOnlyByAKeyField() {
        OffsetDateTime beforeCreation = OffsetDateTime.now().minusSeconds(1);
        DqRule rule = save(newRule());
        OffsetDateTime created = reload(rule).getKeyFieldsChangedAt();
        assertThat(created).isAfter(beforeCreation);

        // not a key field
        rule.setName(rule.getName() + " renamed");
        rule.setSeverity(DqSeverity.CRITICAL);
        rule = save(rule);
        assertThat(reload(rule).getKeyFieldsChangedAt()).isEqualTo(created);

        // the same config, reformatted as the editor does
        rule.setRuleConfig("{\n  \"threshold\" : 100\n}");
        rule = save(rule);
        assertThat(reload(rule).getKeyFieldsChangedAt()).isEqualTo(created);

        rule.setRuleConfig("{\"threshold\": 90.0}");
        rule = save(rule);
        OffsetDateTime configChanged = reload(rule).getKeyFieldsChangedAt();
        assertThat(configChanged).isAfter(created);

        rule.setTableName("umida_dq_rule_group");
        rule = save(rule);
        assertThat(reload(rule).getKeyFieldsChangedAt()).isAfter(configChanged);
    }

    @Test
    void eachKeyFieldRenewsKeyFieldsChangedAt() {
        List<Consumer<DqRule>> changes = List.of(
                rule -> rule.setDataSource("dwh"),
                rule -> rule.setDbSchema("other"),
                rule -> rule.setTableName("other_table"),
                rule -> rule.setColumnName("other_column"),
                rule -> rule.setRuleType(DqRuleType.UNIQUENESS),
                rule -> rule.setRuleConfig("{\"threshold\": 50.0}"));

        for (Consumer<DqRule> change : changes) {
            DqRule rule = save(newRule());
            OffsetDateTime created = reload(rule).getKeyFieldsChangedAt();

            change.accept(rule);
            rule = save(rule);

            assertThat(reload(rule).getKeyFieldsChangedAt()).isAfter(created);
        }
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private DqRule newRule() {
        DqRule rule = dataManager.create(DqRule.class);
        rule.setName("dq rule service test " + UUID.randomUUID());
        rule.setDataSource("main");
        rule.setDbSchema("public");
        rule.setTableName("umida_dq_rule");
        rule.setColumnName("name");
        rule.setDimension(DqDimension.COMPLETENESS);
        rule.setRuleType(DqRuleType.NOT_NULL);
        rule.setRuleConfig("{\"threshold\": 100.0}");
        rule.setSeverity(DqSeverity.LOW);
        return rule;
    }

    /** Saves and keeps the saved instance for cleanup, replacing an earlier instance of the rule. */
    private DqRule save(DqRule rule) {
        DqRule saved = dataManager.save(rule);
        cleanup.removeIf(entity -> entity instanceof DqRule stored && stored.getId().equals(saved.getId()));
        cleanup.add(0, saved);
        return saved;
    }

    private DqRule reload(DqRule rule) {
        return dataManager.load(DqRule.class).id(rule.getId()).one();
    }

    private long codeNumber(DqRule rule) {
        return Long.parseLong(rule.getCode().substring(DqRuleService.CODE_PREFIX.length()));
    }

    @AfterEach
    void tearDown() {
        // by id: the fixture instances are stale once a test saved them again
        cleanup.forEach(entity -> dataManager.remove(Id.of(entity)));
        cleanup.clear();
    }
}
