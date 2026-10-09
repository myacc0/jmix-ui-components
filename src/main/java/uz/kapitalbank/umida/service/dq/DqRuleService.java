package uz.kapitalbank.umida.service.dq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.utils.JsonUtils;

import java.time.OffsetDateTime;
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
     * The attributes that locate the data a rule checks. Changing any of them closes the open issues
     * of the rule as {@code RULE_DATA_SOURCE_CHANGED}, see {@link DqIssueService}.
     */
    public static final String[] DATA_SOURCE_FIELDS = {"dataSource", "dbSchema", "tableName", "columnName"};

    /** Unconstrained: the bookkeeping reads what the rule holds, whatever the current user may see of it. */
    private final UnconstrainedDataManager dataManager;
    private final Sequences sequences;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DqRuleService(UnconstrainedDataManager dataManager, Sequences sequences) {
        this.dataManager = dataManager;
        this.sequences = sequences;
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

    /** Whether a stored rule is about to be moved to another data source, schema, table or column. */
    public boolean isDataSourceChanged(DqRule rule) {
        return loadStored(rule, DATA_SOURCE_FIELDS)
                .map(original -> isDataSourceChanged(original, rule))
                .orElse(false);
    }

    /**
     * Whether the key fields of a stored rule differ from what the database holds. The rule config
     * is compared as JSON: the editor reformats it, which alone is no change.
     */
    public boolean isKeyFieldsChanged(DqRule rule) {
        return loadStored(rule, KEY_FIELDS)
                .map(original -> isDataSourceChanged(original, rule)
                        || original.getRuleType() != rule.getRuleType()
                        || !JsonUtils.sameJson(original.getRuleConfig(), rule.getRuleConfig(), objectMapper))
                .orElse(false);
    }

    private boolean isDataSourceChanged(DqRule original, DqRule rule) {
        return !Objects.equals(original.getDataSource(), rule.getDataSource())
                || !Objects.equals(original.getDbSchema(), rule.getDbSchema())
                || !Objects.equals(original.getTableName(), rule.getTableName())
                || !Objects.equals(original.getColumnName(), rule.getColumnName());
    }

    /** The rule as the database holds it, outside of the transaction saving it; empty for a new one. */
    private Optional<DqRule> loadStored(DqRule rule, String[] properties) {
        if (rule.getId() == null) {
            return Optional.empty();
        }
        return dataManager.load(DqRule.class)
                .id(rule.getId())
                .fetchPlanProperties(properties)
                .joinTransaction(false)
                .optional();
    }
}
