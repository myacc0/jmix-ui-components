package uz.kapitalbank.umida.dto.dq;

import org.springframework.lang.Nullable;

/**
 * The statements needed to evaluate one {@code DqRule} against its data source.
 *
 * @param metrics    one row with the {@code total_count} and {@code failed_count} columns, feeding
 *                   {@code DqCheckRunResult.totalRecords} / {@code failedRecords} / {@code passRate}
 * @param violations every violating row, uncapped, feeding {@code DqCheckRunResult.violationsQuery};
 *                   {@code null} when the rule type cannot point at individual rows
 */
public record DqRuleQueries(DqSqlQuery metrics, @Nullable DqSqlQuery violations) {

    public static DqRuleQueries metricsOnly(DqSqlQuery metrics) {
        return new DqRuleQueries(metrics, null);
    }
}
