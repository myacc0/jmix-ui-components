package uz.kapitalbank.umida.dq;

import uz.kapitalbank.umida.dto.dq.DqRuleValidationError;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.service.dq.DqRuleValidator;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static uz.kapitalbank.umida.dto.dq.DqRuleValidationError.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqRuleValidatorTests {

    @Autowired
    DqRuleValidator validator;

    @Autowired
    DataManager dataManager;

    private DqRule rule(DqRuleType type, String config) {
        DqRule rule = dataManager.create(DqRule.class);
        rule.setName("test rule");
        rule.setDataSource("main");
        rule.setTableName("employee");
        rule.setColumnName("first_name");
        rule.setRuleType(type);
        rule.setRuleConfig(config);
        return rule;
    }

    private List<String> fields(DqRule rule) {
        return validator.validate(rule).stream()
                .map(DqRuleValidationError::field)
                .toList();
    }

    // ----- generic config attributes -----

    @Test
    void ruleTypeIsRequired() {
        assertEquals(List.of(FIELD_RULE_TYPE), fields(rule(null, "{}")));
    }

    @Test
    void blankConfigIsRejected() {
        assertEquals(List.of(FIELD_RULE_CONFIG), fields(rule(DqRuleType.NOT_NULL, "  ")));
    }

    @Test
    void malformedConfigJsonIsRejected() {
        assertEquals(List.of(FIELD_RULE_CONFIG), fields(rule(DqRuleType.NOT_NULL, "{not json")));
    }

    @Test
    void thresholdOutsideZeroToHundredIsRejected() {
        assertEquals(List.of(FIELD_THRESHOLD), fields(rule(DqRuleType.NOT_NULL, "{\"threshold\": 101}")));
        assertEquals(List.of(FIELD_THRESHOLD), fields(rule(DqRuleType.NOT_NULL, "{\"threshold\": -1}")));
        assertTrue(fields(rule(DqRuleType.NOT_NULL, "{\"threshold\": 100}")).isEmpty());
    }

    // ----- NOT_NULL / NOT_BLANK / UNIQUENESS -----

    @ParameterizedTest
    @EnumSource(value = DqRuleType.class, names = {"NOT_NULL", "NOT_BLANK", "UNIQUENESS"})
    void columnBoundTypesRequireTableAndColumn(DqRuleType type) {
        assertTrue(fields(rule(type, "{}")).isEmpty());

        DqRule rule = rule(type, "{}");
        rule.setTableName(null);
        rule.setColumnName("");
        assertEquals(List.of(FIELD_TABLE_NAME, FIELD_COLUMN_NAME), fields(rule));
    }

    // ----- REGEXP -----

    @Test
    void regexpIsRequiredAndMustCompile() {
        assertEquals(List.of(FIELD_REGEXP), fields(rule(DqRuleType.REGEXP, "{}")));
        assertEquals(List.of(FIELD_REGEXP), fields(rule(DqRuleType.REGEXP, "{\"regexp\": \"[a-\"}")));
        assertTrue(fields(rule(DqRuleType.REGEXP, "{\"regexp\": \"^[A-Z][a-z]+$\"}")).isEmpty());
    }

    // ----- RANGE_NUMBER -----

    @Test
    void numberRangeNeedsAtLeastOneBound() {
        assertEquals(List.of(FIELD_MIN), fields(rule(DqRuleType.RANGE_NUMBER, "{}")));
        assertTrue(fields(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 1}")).isEmpty());
        assertTrue(fields(rule(DqRuleType.RANGE_NUMBER, "{\"max\": 1}")).isEmpty());
    }

    @Test
    void numberRangeBoundsMustBeNumeric() {
        assertEquals(List.of(FIELD_MIN, FIELD_MAX),
                fields(rule(DqRuleType.RANGE_NUMBER, "{\"min\": \"abc\", \"max\": \"xyz\"}")));
    }

    @Test
    void reversedNumberRangeIsRejected() {
        assertEquals(List.of(FIELD_MIN), fields(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 10, \"max\": 5}")));
        assertTrue(fields(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 5, \"max\": 10}")).isEmpty());
    }

    @Test
    void equalNumberBoundsMustBothBeIncluded() {
        assertEquals(List.of(FIELD_MIN),
                fields(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 5, \"max\": 5, \"minIncluded\": true}")));
        assertTrue(fields(rule(DqRuleType.RANGE_NUMBER,
                "{\"min\": 5, \"max\": 5, \"minIncluded\": true, \"maxIncluded\": true}")).isEmpty());
    }

    // ----- RANGE_DATE -----

    @Test
    void dateRangeNeedsAtLeastOneBound() {
        assertEquals(List.of(FIELD_MIN), fields(rule(DqRuleType.RANGE_DATE, "{}")));
        assertTrue(fields(rule(DqRuleType.RANGE_DATE, "{\"min\": \"2026-01-01\"}")).isEmpty());
    }

    @Test
    void dateRangeBoundsMustBeIsoDates() {
        assertEquals(List.of(FIELD_MAX),
                fields(rule(DqRuleType.RANGE_DATE, "{\"min\": \"2026-01-01\", \"max\": \"31.12.2026\"}")));
    }

    @Test
    void reversedDateRangeIsRejected() {
        assertEquals(List.of(FIELD_MIN),
                fields(rule(DqRuleType.RANGE_DATE, "{\"min\": \"2026-12-31\", \"max\": \"2026-01-01\"}")));
        assertTrue(fields(rule(DqRuleType.RANGE_DATE,
                "{\"min\": \"2026-01-01\", \"max\": \"2026-12-31\"}")).isEmpty());
    }

    // ----- REFERENTIAL -----

    /** A rule over a real table of the main data source, so that the column types can be read. */
    private DqRule referentialRule(String column, String refColumn) {
        DqRule rule = rule(DqRuleType.REFERENTIAL, "{\"refSchema\": \"public\", \"refTable\": \"umida_dq_rule\","
                + " \"refColumn\": \"" + refColumn + "\"}");
        rule.setDbSchema("public");
        rule.setTableName("umida_dq_rule");
        rule.setColumnName(column);
        return rule;
    }

    @Test
    void referentialRequiresTheReferenceLocation() {
        assertEquals(List.of(FIELD_REF_SCHEMA, FIELD_REF_TABLE, FIELD_REF_COLUMN),
                fields(rule(DqRuleType.REFERENTIAL, "{}")));

        DqRule rule = rule(DqRuleType.REFERENTIAL, "{}");
        rule.setTableName(null);
        rule.setColumnName(null);
        assertEquals(List.of(FIELD_TABLE_NAME, FIELD_COLUMN_NAME, FIELD_REF_SCHEMA, FIELD_REF_TABLE, FIELD_REF_COLUMN),
                fields(rule));
    }

    @Test
    void referenceColumnOfAComparableTypeIsAccepted() {
        // varchar against varchar
        assertTrue(fields(referentialRule("code", "name")).isEmpty());
        // uuid against uuid: compared by the database's type name
        assertTrue(fields(referentialRule("group_id", "id")).isEmpty());
    }

    @Test
    void referenceColumnOfAnotherTypeIsRejected() {
        // varchar against integer: PostgreSQL has no = for the pair
        assertEquals(List.of(FIELD_REF_COLUMN), fields(referentialRule("code", "due_days")));
        // uuid against varchar
        assertEquals(List.of(FIELD_REF_COLUMN), fields(referentialRule("group_id", "code")));
    }

    @Test
    void columnTypesThatCannotBeReadAreLeftToTheRun() {
        // no such column, and no such table: the run reports them
        assertTrue(fields(referentialRule("code", "no_such_column")).isEmpty());
        assertTrue(fields(rule(DqRuleType.REFERENTIAL,
                "{\"refSchema\": \"public\", \"refTable\": \"no_such_table\", \"refColumn\": \"code\"}")).isEmpty());
    }

    // ----- CUSTOM_SQL -----

    private static final String VIOLATIONS_SQL = "select id, short_name from umida_dict_data_domain where parent_id is null";

    private DqRule customSql(String sql) {
        return customSql(sql, VIOLATIONS_SQL);
    }

    private DqRule customSql(String sql, String violationsSql) {
        DqRule rule = rule(DqRuleType.CUSTOM_SQL,
                "{\"sql\": " + quoted(sql) + ", \"violationsSql\": " + quoted(violationsSql) + "}");
        rule.setTableName(null);
        rule.setColumnName(null);
        return rule;
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private List<String> messages(DqRule rule) {
        return validator.validate(rule).stream().map(DqRuleValidationError::message).toList();
    }

    @Test
    void customSqlRequiresBothQueriesButNoTable() {
        DqRule rule = rule(DqRuleType.CUSTOM_SQL, "{}");
        rule.setTableName(null);
        rule.setColumnName(null);
        assertEquals(List.of(FIELD_SQL, FIELD_VIOLATIONS_SQL), fields(rule));
    }

    @Test
    void customSqlAcceptsReadQueries() {
        assertTrue(fields(customSql(
                "select count(*), count(*) filter (where parent_id is null) from umida_dict_data_domain;")).isEmpty());
        assertTrue(fields(customSql(
                "-- the domains without a parent\n"
                        + "with d as (select * from umida_dict_data_domain where short_name <> 'a;b')\n"
                        + "select count(*), sum(case when parent_id is null then 1 else 0 end) from d",
                "with d as (select * from umida_dict_data_domain) select * from d order by code -- newest last")).isEmpty());
    }

    @Test
    void customSqlMustBeASingleReadQuery() {
        assertEquals(List.of(FIELD_SQL), fields(customSql("delete from umida_dict_data_domain")));
        assertEquals(List.of(FIELD_SQL), fields(customSql("select 1, 2; delete from umida_dict_data_domain")));
        assertEquals(List.of(FIELD_SQL), fields(customSql(
                "with d as (delete from umida_dict_data_domain returning id) select count(*), 0 from d")));
    }

    @Test
    void customSqlMustReturnTwoNumericColumns() {
        assertEquals(List.of(FIELD_SQL), fields(customSql("select count(*) from umida_dict_data_domain")));
        assertTrue(messages(customSql("select count(*) from umida_dict_data_domain")).get(0).contains("1"));

        assertEquals(List.of(FIELD_SQL), fields(customSql("select count(*), max(short_name) from umida_dict_data_domain")));
    }

    @Test
    void customSqlTheDataSourceRejectsIsReported() {
        assertEquals(List.of(FIELD_SQL), fields(customSql("select count(*), 0 from no_such_table")));
        assertTrue(messages(customSql("select count(*), 0 from no_such_table")).get(0).contains("no_such_table"));
    }

    @Test
    void theViolationsQueryMustBeAReadQueryTheDataSourceRuns() {
        String sql = "select count(*), 0 from umida_dict_data_domain";
        assertEquals(List.of(FIELD_VIOLATIONS_SQL), fields(customSql(sql, "update umida_dict_data_domain set code = code")));
        assertEquals(List.of(FIELD_VIOLATIONS_SQL), fields(customSql(sql, "select * from umida_dict_data_domain; select 1")));
        assertEquals(List.of(FIELD_VIOLATIONS_SQL), fields(customSql(sql, "select * from no_such_table")));
        // any columns will do
        assertTrue(fields(customSql(sql, "select short_name, 'x' as note from umida_dict_data_domain")).isEmpty());
    }

    // ----- not implemented yet: accept anything -----

    @Test
    void placeholderTypesReportNoErrors() {
        assertTrue(fields(rule(DqRuleType.CROSS_SOURCE_AGGREGATED, "{}")).isEmpty());
    }
}
