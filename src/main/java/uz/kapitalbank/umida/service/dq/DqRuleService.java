package uz.kapitalbank.umida.service.dq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.FetchPlan;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.utils.JsonUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Rule bookkeeping that the editor delegates rather than asking the user to fill in. */
@Service
public class DqRuleService {

    public static final String CODE_PREFIX = "DQR-";

    /** Created by Liquibase; the start value here only matters if the sequence is missing. */
    private static final Sequence CODE_SEQUENCE = Sequence.withName("UMIDA_DQ_RULE_CODE_SEQ")
            .setStartValue(100)
            .setIncrement(1);

    /**
     * The attributes that define what a rule checks. Changing any of them makes the earlier check
     * results of the rule incomparable with the later ones, which {@code keyFieldsChangedAt} records.
     */
    private static final String[] KEY_FIELDS = {
            "dataSource", "dbSchema", "tableName", "columnName", "ruleType", "ruleConfig"
    };

    /**
     * Unconstrained: the bookkeeping reads what the rule and the org structure hold, whatever the
     * current user may see of them.
     */
    private final UnconstrainedDataManager dataManager;
    private final Sequences sequences;
    private final CurrentAuthentication currentAuthentication;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DqRuleService(UnconstrainedDataManager dataManager,
                         Sequences sequences,
                         CurrentAuthentication currentAuthentication) {
        this.dataManager = dataManager;
        this.sequences = sequences;
        this.currentAuthentication = currentAuthentication;
    }

    /** The next free rule code: {@code DQR-100}, {@code DQR-101}, … */
    public String generateCode() {
        return CODE_PREFIX + sequences.createNextValue(CODE_SEQUENCE);
    }

    /**
     * Fills in what a rule is not asked for: a new rule gets its code and the key fields timestamp,
     * a stored one gets the timestamp renewed when one of its key fields changes. A missing due
     * period defaults to the one of the rule severity.
     */
    public void prepareForSave(DqRule rule, boolean isNew) {
        if (isNew) {
            if (rule.getCode() == null) {
                rule.setCode(generateCode());
            }
            if (rule.getKeyFieldsChangedAt() == null) {
                rule.setKeyFieldsChangedAt(OffsetDateTime.now());
            }
        } else if (isKeyFieldsChanged(rule)) {
            rule.setKeyFieldsChangedAt(OffsetDateTime.now());
        }

        if (rule.getDueDays() == null && rule.getSeverity() != null) {
            rule.setDueDays(rule.getSeverity().getDayCost());
        }
    }

    /**
     * Whether the key fields of a stored rule differ from what the database holds. The rule config
     * is compared as JSON: the editor reformats it, which alone is no change.
     */
    public boolean isKeyFieldsChanged(DqRule rule) {
        Optional<DqRule> stored = dataManager.load(DqRule.class)
                .id(rule.getId())
                .fetchPlanProperties(KEY_FIELDS)
                .joinTransaction(false)
                .optional();
        if (stored.isEmpty()) {
            return false;
        }

        DqRule original = stored.get();
        return !Objects.equals(original.getDataSource(), rule.getDataSource())
                || !Objects.equals(original.getDbSchema(), rule.getDbSchema())
                || !Objects.equals(original.getTableName(), rule.getTableName())
                || !Objects.equals(original.getColumnName(), rule.getColumnName())
                || original.getRuleType() != rule.getRuleType()
                || !JsonUtils.sameJson(original.getRuleConfig(), rule.getRuleConfig(), objectMapper);
    }

    /**
     * The subdivisions the employee of the current user holds a position in, ordered by name.
     * Empty for a user without an employee, such as {@code admin}.
     */
    public List<OrgStructureSubdivision> getCurrentUserSubdivisions() {
        String username = currentAuthentication.getUser().getUsername();
        return dataManager.load(OrgStructurePosition.class)
                .query("select p from umida_OrgStructurePosition p, umida_User u " +
                        "where u.username = :username and p.employee = u.employee " +
                        "and p.subdivision is not null " +
                        "and (p.dismissalDate is null or p.dismissalDate > :today)")
                .parameter("username", username)
                .parameter("today", LocalDate.now())
                .fetchPlan(fp -> fp.add("subdivision", FetchPlan.INSTANCE_NAME))
                .list()
                .stream()
                .map(OrgStructurePosition::getSubdivision)
                .distinct()
                .sorted(Comparator.comparing(OrgStructureSubdivision::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }
}
