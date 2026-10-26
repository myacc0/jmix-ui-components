package uz.kapitalbank.umida.service.dq;

import io.jmix.core.Id;
import io.jmix.core.entity.KeyValueEntity;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.data.PersistenceHints;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.dq.DqCheck;
import uz.kapitalbank.umida.entity.dq.DqRule;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Check bookkeeping: the code of a new check, the rules a check runs and the checks a rule deletion empties. */
@Service
public class DqCheckService {

    public static final String CODE_PREFIX = "DQC-";

    /** Created by Liquibase; the start value here only matters if the sequence is missing. */
    private static final Sequence CODE_SEQUENCE = Sequence.withName("UMIDA_DQ_CHECK_CODE_SEQ")
            .setStartValue(100)
            .setIncrement(1);

    /** Unconstrained: the bookkeeping reads what the check holds, whatever the current user may see of it. */
    private final UnconstrainedDataManager dataManager;
    private final Sequences sequences;

    public DqCheckService(UnconstrainedDataManager dataManager, Sequences sequences) {
        this.dataManager = dataManager;
        this.sequences = sequences;
    }

    /** The next free check code: {@code DQC-100}, {@code DQC-101}, … */
    public String generateCode() {
        return CODE_PREFIX + sequences.createNextValue(CODE_SEQUENCE);
    }

    /** Fills in what a check is not asked for: a new check gets its code. */
    public void prepareForSave(DqCheck check, boolean isNew) {
        if (isNew && check.getCode() == null) {
            check.setCode(generateCode());
        }
    }

    /**
     * The rules a run of the check executes: its active rules, by name. Deleted rules are not part
     * of the check any more.
     */
    public List<DqRule> loadActiveRules(UUID checkId) {
        DqCheck check = dataManager.load(DqCheck.class)
                .id(checkId)
                .fetchPlan(fp -> fp.add("rules", rule -> rule.add("name").add("active")))
                .one();
        if (check.getRules() == null) {
            return List.of();
        }
        return check.getRules().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getActive()))
                .sorted(Comparator.comparing(DqRule::getName))
                .toList();
    }

    /**
     * How many rules each of the checks holds, and how many of them are active, by check id. A check
     * without rules has no entry. All the checks are counted in one query.
     */
    public Map<UUID, RuleCounts> countRules(Collection<DqCheck> checks) {
        if (checks.isEmpty()) {
            return Map.of();
        }
        List<UUID> checkIds = checks.stream().map(DqCheck::getId).toList();
        List<KeyValueEntity> rows = dataManager.loadValues(
                        "select c.id, r.active, count(r) from umida_DqCheck c join c.rules r"
                                + " where c.id in :checkIds and r.deletedDate is null"
                                + " group by c.id, r.active")
                .properties("checkId", "active", "count")
                .parameter("checkIds", checkIds)
                .list();

        Map<UUID, RuleCounts> counts = new HashMap<>();
        for (KeyValueEntity row : rows) {
            UUID checkId = row.getValue("checkId");
            Number count = row.getValue("count");
            boolean active = Boolean.TRUE.equals(row.getValue("active"));
            counts.merge(checkId,
                    new RuleCounts(active ? count.intValue() : 0, count.intValue()),
                    RuleCounts::plus);
        }
        return counts;
    }

    /**
     * Deletes the checks the deleted rule was the last rule of: a check without rules has nothing to
     * run. Runs in the transaction that deletes the rule.
     * <p>
     * Soft deletion is off for the lookup of the checks, as the rule being deleted may already be marked
     * deleted in this transaction and would otherwise not be found in the checks it belongs to. With soft
     * deletion off Jmix prints {@code deletedDate is null} as {@code 0=0}, so the checks deleted before
     * are left out here rather than in the query: removing one again would persist it anew.
     */
    public void removeChecksLeftWithoutRules(Id<DqRule> deletedRuleId) {
        List<UUID> checkIds = dataManager.loadValues(
                        "select c.id, c.deletedDate from umida_DqCheck c join c.rules r where r.id = :ruleId")
                .properties("checkId", "deletedDate")
                .parameter("ruleId", deletedRuleId.getValue())
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list()
                .stream()
                .filter(row -> row.getValue("deletedDate") == null)
                .map(row -> row.<UUID>getValue("checkId"))
                .toList();

        for (UUID checkId : checkIds) {
            // soft deletion on: the deleted rules no longer count
            Long otherRules = dataManager.loadValue(
                            "select count(r) from umida_DqCheck c join c.rules r"
                                    + " where c.id = :checkId and r.id <> :ruleId", Long.class)
                    .parameter("checkId", checkId)
                    .parameter("ruleId", deletedRuleId.getValue())
                    .one();
            if (otherRules == 0) {
                dataManager.remove(Id.of(checkId, DqCheck.class));
            }
        }
    }

    /**
     * The rules of a check: all of them, and the active ones, which are the ones a run executes.
     */
    public record RuleCounts(int active, int total) {

        public static final RuleCounts NONE = new RuleCounts(0, 0);

        RuleCounts plus(RuleCounts other) {
            return new RuleCounts(active + other.active, total + other.total);
        }
    }
}
