package uz.kapitalbank.umida.test_support;

import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.dq.DqDimension;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSeverity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The data a data quality test stands on: a domain, which needs a subdivision to own it, and rules
 * in it. Everything created here, and everything handed to {@link #track}, is removed for real by
 * {@link #cleanup()} in reverse order — domains and rules are soft-deletable, and a soft-deleted row
 * would keep its foreign keys.
 */
public class DqTestData {

    /** The table behind {@link DictDataDomain}: a table of the main store that the tests can check. */
    public static final String DOMAIN_TABLE = "umida_dict_data_domain";

    private final DataManager dataManager;
    private final List<Object> created = new ArrayList<>();

    public DqTestData(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** A domain of its own, without a parent, owned by a subdivision of its own. */
    public DictDataDomain createDomain() {
        String suffix = suffix();

        OrgStructureSubdivision owner = dataManager.create(OrgStructureSubdivision.class);
        owner.setId(UUID.randomUUID().toString());
        owner.setName("DQ test owner " + suffix);
        track(dataManager.save(owner));

        DictDataDomain domain = dataManager.create(DictDataDomain.class);
        domain.setCode("dq-test-" + suffix);
        domain.setShortName("DQ test domain " + suffix);
        domain.setBusinessOwner(owner);
        domain.setAssignDate(LocalDate.now());
        return track(dataManager.save(domain));
    }

    /** An active rule of the main store over {@value #DOMAIN_TABLE}. */
    public DqRule createRule(DictDataDomain domain, String name, DqRuleType ruleType, String columnName,
                             String ruleConfig) {
        return createRule(domain, name, ruleType, columnName, ruleConfig, rule -> {
        });
    }

    /** Same as above, with the rule adjusted by {@code customizer} before it is saved. */
    public DqRule createRule(DictDataDomain domain, String name, DqRuleType ruleType, String columnName,
                             String ruleConfig, Consumer<DqRule> customizer) {
        String suffix = suffix();

        DqRule rule = dataManager.create(DqRule.class);
        rule.setCode("dq-" + suffix);
        rule.setName("dq test " + name + " " + suffix);
        rule.setDataSource("main");
        rule.setTableName(DOMAIN_TABLE);
        rule.setColumnName(columnName);
        rule.setDimension(DqDimension.COMPLETENESS);
        rule.setRuleType(ruleType);
        rule.setRuleConfig(ruleConfig);
        rule.setSeverity(DqSeverity.HIGH);
        rule.setActive(true);
        rule.setDomain(domain);
        rule.setKeyFieldsChangedAt(OffsetDateTime.now());
        customizer.accept(rule);
        return track(dataManager.save(rule));
    }

    /** Registers a saved entity for removal; anything that references it must be tracked after it. */
    public <T> T track(T saved) {
        created.add(saved);
        return saved;
    }

    public void cleanup() {
        List<Object> reversed = new ArrayList<>(created);
        Collections.reverse(reversed);
        for (Object entity : reversed) {
            // reloaded: a test may have saved the entity again since, which leaves the tracked
            // instance with a stale version, or removed it already
            dataManager.load(Id.of(entity))
                    .hint(PersistenceHints.SOFT_DELETION, false)
                    .optional()
                    .ifPresent(current -> dataManager.save(new SaveContext()
                            .removing(current)
                            .setHint(PersistenceHints.SOFT_DELETION, false)));
        }
        created.clear();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
