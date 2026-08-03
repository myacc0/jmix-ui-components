package com.company.demo.service;

import com.company.demo.dto.DqRuleFilter;
import io.jmix.core.querycondition.JpqlCondition;
import io.jmix.core.querycondition.LogicalCondition;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates the {@link DqRuleFilter} model of the "run check" page into a query condition
 * over {@code DqRule}.
 * <p>
 * Every condition is created with {@code skipNullOrEmpty()}, so a condition whose parameter is
 * absent or null is dropped from the query instead of being evaluated against null. The global
 * {@code jmix.core.skip-null-or-empty-conditions-by-default} property is off, hence the explicit
 * per-condition flag.
 */
@Service
public class DqRuleFilterService {

    public static final String PARAM_DATA_SOURCE = "dataSource";
    public static final String PARAM_DOMAIN = "domain";
    public static final String PARAM_DATA_PRODUCT = "dataProduct";
    public static final String PARAM_DIMENSION = "dimension";
    public static final String PARAM_SEVERITY = "severity";
    public static final String PARAM_RULE_TYPE = "ruleType";
    public static final String PARAM_TABLE_NAME = "tableName";
    public static final String PARAM_COLUMN_NAME = "columnName";
    public static final String PARAM_OWNER = "owner";

    /**
     * Returns the condition to assign to the rules loader once, at view init. The actual filtering
     * is driven by the parameters produced by {@link #createRuleParameters(DqRuleFilter)}.
     */
    public LogicalCondition createRuleCondition() {
        return LogicalCondition.and(
                condition("{E}.dataSource = :" + PARAM_DATA_SOURCE),
                condition("{E}.domain = :" + PARAM_DOMAIN),
                condition("{E}.dataProduct = :" + PARAM_DATA_PRODUCT),
                condition("{E}.dimension = :" + PARAM_DIMENSION),
                condition("{E}.severity = :" + PARAM_SEVERITY),
                condition("{E}.ruleType = :" + PARAM_RULE_TYPE),
                condition("{E}.tableName = :" + PARAM_TABLE_NAME),
                condition("{E}.columnName = :" + PARAM_COLUMN_NAME),
                condition("{E}.owner = :" + PARAM_OWNER)
        );
    }

    /**
     * Maps the filter attributes to loader parameters. Null values are kept in the map: the query
     * builder treats a null-valued parameter as absent and drops the condition that uses it.
     */
    public Map<String, Object> createRuleParameters(DqRuleFilter filter) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(PARAM_DATA_SOURCE, filter.getDataSource());
        parameters.put(PARAM_DOMAIN, filter.getDomain());
        parameters.put(PARAM_DATA_PRODUCT, filter.getDataProduct());
        parameters.put(PARAM_DIMENSION, filter.getDimension());
        parameters.put(PARAM_SEVERITY, filter.getSeverity());
        parameters.put(PARAM_RULE_TYPE, filter.getRuleType());
        parameters.put(PARAM_TABLE_NAME, filter.getTableName());
        parameters.put(PARAM_COLUMN_NAME, filter.getColumnName());
        parameters.put(PARAM_OWNER, filter.getOwner());
        return parameters;
    }

    private JpqlCondition condition(String where) {
        return JpqlCondition.create(where, null).skipNullOrEmpty();
    }
}
