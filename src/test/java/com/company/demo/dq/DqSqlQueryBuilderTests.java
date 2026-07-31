package com.company.demo.dq;

import com.company.demo.dto.DqRuleQueries;
import com.company.demo.entity.DqRule;
import com.company.demo.enums.DqRuleType;
import com.company.demo.enums.DqSqlDialect;
import com.company.demo.service.DqSqlQueryBuilder;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.ObjectWrapper;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts the generated SQL text for every implemented rule type and dialect. Nothing is executed
 * against a database: the explicit-dialect overload is used throughout, so MySQL and Oracle output is
 * covered even though this project only has PostgreSQL stores.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqSqlQueryBuilderTests {

    @Autowired
    DqSqlQueryBuilder queryBuilder;

    @Autowired
    DataManager dataManager;

    private DqRule rule(DqRuleType type, String config) {
        return rule(type, config, "employee", "first_name");
    }

    private DqRule rule(DqRuleType type, String config, String table, String column) {
        DqRule rule = dataManager.create(DqRule.class);
        rule.setName("test rule");
        rule.setDataSource("main");
        rule.setTableName(table);
        rule.setColumnName(column);
        rule.setRuleType(type);
        rule.setRuleConfig(config);
        return rule;
    }

    private DqRuleQueries build(DqRule rule, DqSqlDialect dialect) {
        return queryBuilder.buildQueries(rule, dialect);
    }

    // ----- NOT_NULL -----

    @Test
    void notNullCountsNullsAndSelectsThem() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{}"), DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT COUNT(*) AS total_count," +
                        " COUNT(CASE WHEN (t.\"first_name\" IS NULL) THEN 1 END) AS failed_count" +
                        " FROM \"employee\" t",
                queries.metrics().sql());
        assertEquals("SELECT t.* FROM \"employee\" t WHERE (t.\"first_name\" IS NULL)",
                queries.samples().sql());
        assertEquals(List.of(), queries.metrics().params());
    }

    @Test
    void schemaQualifiedTableIsQuotedPerPart() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{}", "public.employee", "first_name"),
                DqSqlDialect.POSTGRESQL);

        assertTrue(queries.metrics().sql().endsWith("FROM \"public\".\"employee\" t"),
                queries.metrics().sql());
    }

    // ----- row limit, per dialect -----

    @Test
    void rowsLimitIsRenderedInPostgresSyntax() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{\"rowsLimit\": 25}"), DqSqlDialect.POSTGRESQL);
        assertTrue(queries.samples().sql().endsWith(" LIMIT 25"), queries.samples().sql());
    }

    @Test
    void rowsLimitIsRenderedInMysqlSyntaxWithBacktickQuoting() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{\"rowsLimit\": 25}"), DqSqlDialect.MYSQL);

        assertEquals("SELECT t.* FROM `employee` t WHERE (t.`first_name` IS NULL) LIMIT 25",
                queries.samples().sql());
    }

    @Test
    void rowsLimitIsRenderedInOracleSyntax() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{\"rowsLimit\": 25}"), DqSqlDialect.ORACLE);

        assertEquals("SELECT t.* FROM \"employee\" t WHERE (t.\"first_name\" IS NULL)" +
                " FETCH FIRST 25 ROWS ONLY", queries.samples().sql());
    }

    @ParameterizedTest
    @EnumSource(DqSqlDialect.class)
    void missingRowsLimitLeavesTheSamplesUncapped(DqSqlDialect dialect) {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{}"), dialect);

        String sql = queries.samples().sql();
        assertTrue(!sql.contains("LIMIT") && !sql.contains("FETCH"), sql);
    }

    // ----- UNIQUENESS -----

    @Test
    void uniquenessFlagsEveryMemberOfADuplicateGroup() {
        DqRuleQueries queries = build(rule(DqRuleType.UNIQUENESS, "{}"), DqSqlDialect.POSTGRESQL);

        String duplicates = "t.\"first_name\" IN (SELECT d.\"first_name\" FROM \"employee\" d" +
                " GROUP BY d.\"first_name\" HAVING COUNT(*) > 1)";
        assertEquals("SELECT COUNT(*) AS total_count," +
                        " COUNT(CASE WHEN (" + duplicates + ") THEN 1 END) AS failed_count" +
                        " FROM \"employee\" t",
                queries.metrics().sql());
        assertEquals("SELECT t.* FROM \"employee\" t WHERE (" + duplicates + ")" +
                        " ORDER BY t.\"first_name\"",
                queries.samples().sql());
    }

    // ----- REGEXP -----

    @Test
    void regexpBindsThePatternAndIgnoresNulls() {
        DqRuleQueries queries = build(rule(DqRuleType.REGEXP, "{\"regexp\": \"^[A-Z]+$\"}"),
                DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT t.* FROM \"employee\" t" +
                        " WHERE (t.\"first_name\" IS NOT NULL AND NOT (t.\"first_name\" ~ ?))",
                queries.samples().sql());
        assertEquals(List.of("^[A-Z]+$"), queries.samples().params());
    }

    @Test
    void regexpUsesTheMysqlOperator() {
        DqRuleQueries queries = build(rule(DqRuleType.REGEXP, "{\"regexp\": \"^[A-Z]+$\"}"), DqSqlDialect.MYSQL);

        assertTrue(queries.samples().sql().contains("NOT (t.`first_name` REGEXP ?)"),
                queries.samples().sql());
    }

    @Test
    void regexpUsesTheOracleFunction() {
        DqRuleQueries queries = build(rule(DqRuleType.REGEXP, "{\"regexp\": \"^[A-Z]+$\"}"), DqSqlDialect.ORACLE);

        assertTrue(queries.samples().sql().contains("NOT (REGEXP_LIKE(t.\"first_name\", ?))"),
                queries.samples().sql());
    }

    @Test
    void regexpWithoutPatternIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.REGEXP, "{}"), DqSqlDialect.POSTGRESQL));
    }

    // ----- RANGE_NUMBER -----

    @Test
    void numberRangeBoundsAreInclusiveByDefault() {
        DqRuleQueries queries = build(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 10, \"max\": 20}",
                "employee", "salary"), DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT t.* FROM \"employee\" t" +
                        " WHERE (t.\"salary\" IS NOT NULL AND (t.\"salary\" < ? OR t.\"salary\" > ?))",
                queries.samples().sql());
        assertEquals(List.of(BigDecimal.valueOf(10d), BigDecimal.valueOf(20d)), queries.samples().params());
    }

    @Test
    void excludedNumberBoundsShiftTheComparison() {
        DqRuleQueries queries = build(rule(DqRuleType.RANGE_NUMBER,
                        "{\"min\": 10, \"max\": 20, \"minIncluded\": false, \"maxIncluded\": false}",
                        "employee", "salary"),
                DqSqlDialect.POSTGRESQL);

        assertTrue(queries.samples().sql().contains("(t.\"salary\" <= ? OR t.\"salary\" >= ?)"),
                queries.samples().sql());
    }

    @Test
    void oneSidedNumberRangeBindsOnlyThatBound() {
        DqRuleQueries queries = build(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 10}", "employee", "salary"),
                DqSqlDialect.POSTGRESQL);

        assertTrue(queries.samples().sql().contains("(t.\"salary\" < ?)"), queries.samples().sql());
        assertEquals(1, queries.samples().params().size());
    }

    @Test
    void unparsableNumberBoundIsRejectedRatherThanDropped() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.RANGE_NUMBER, "{\"min\": \"ten\"}", "employee", "salary"),
                        DqSqlDialect.POSTGRESQL));
    }

    @Test
    void rangeWithoutAnyBoundIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.RANGE_NUMBER, "{}", "employee", "salary"), DqSqlDialect.POSTGRESQL));
    }

    // ----- RANGE_DATE -----

    @Test
    void dateRangeBindsSqlDates() {
        DqRuleQueries queries = build(rule(DqRuleType.RANGE_DATE,
                        "{\"min\": \"2020-01-01\", \"max\": \"2020-12-31\"}", "employee", "hire_date"),
                DqSqlDialect.ORACLE);

        assertEquals(List.of(Date.valueOf("2020-01-01"), Date.valueOf("2020-12-31")),
                queries.samples().params());
        assertTrue(queries.samples().sql().contains("(t.\"hire_date\" < ? OR t.\"hire_date\" > ?)"),
                queries.samples().sql());
    }

    @Test
    void unparsableDateBoundIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.RANGE_DATE, "{\"min\": \"01.01.2020\"}", "employee", "hire_date"),
                        DqSqlDialect.POSTGRESQL));
    }

    // ----- display rendering -----

    @Test
    void displayStringInlinesTheBoundValues() {
        DqRuleQueries queries = build(rule(DqRuleType.RANGE_DATE, "{\"min\": \"2020-01-01\"}",
                "employee", "hire_date"), DqSqlDialect.POSTGRESQL);

        assertTrue(queries.samples().toDisplayString().contains("t.\"hire_date\" < DATE '2020-01-01'"),
                queries.samples().toDisplayString());
    }

    @Test
    void displayStringEscapesQuotesInAPattern() {
        DqRuleQueries queries = build(rule(DqRuleType.REGEXP, "{\"regexp\": \"o'brien\"}"),
                DqSqlDialect.POSTGRESQL);

        assertTrue(queries.samples().toDisplayString().contains("~ 'o''brien'"),
                queries.samples().toDisplayString());
    }

    // ----- rejected input -----

    @Test
    void identifierThatIsNotAPlainNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.NOT_NULL, "{}", "employee", "first_name\"; DROP TABLE employee--"),
                        DqSqlDialect.POSTGRESQL));
    }

    @Test
    void missingTableNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.NOT_NULL, "{}", null, "first_name"), DqSqlDialect.POSTGRESQL));
    }

    @Test
    void missingRuleTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(null, "{}"), DqSqlDialect.POSTGRESQL));
    }

    @Test
    void invalidConfigJsonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.NOT_NULL, "{not json"), DqSqlDialect.POSTGRESQL));
    }

    // ----- not implemented yet -----

    @ParameterizedTest
    @EnumSource(value = DqRuleType.class,
            names = {"REFERENTIAL", "CUSTOM_SQL", "CROSS_SOURCE_AGGREGATED"})
    void pendingRuleTypesFailExplicitly(DqRuleType type) {
        assertThrows(UnsupportedOperationException.class,
                () -> build(rule(type, "{}"), DqSqlDialect.POSTGRESQL));
    }

    // ----- dialect detection -----

    @Test
    void dialectIsDetectedFromTheProductName() {
        assertEquals(DqSqlDialect.POSTGRESQL, DqSqlDialect.fromProductName("PostgreSQL"));
        assertEquals(DqSqlDialect.MYSQL, DqSqlDialect.fromProductName("MySQL"));
        assertEquals(DqSqlDialect.MYSQL, DqSqlDialect.fromProductName("MariaDB"));
        assertEquals(DqSqlDialect.ORACLE, DqSqlDialect.fromProductName("Oracle"));
        assertNull(DqSqlDialect.fromProductName("HSQL Database Engine"));
    }

    @ParameterizedTest
    @CsvSource({"019fb34d-8d41-7193-bd9d-e6dd7510220b", "019fb361-5dc9-7d85-a2f8-85ef2a4f70af"})
    void print_dqRule(String id) throws IOException {
        UUID uuid = UUID.fromString(id);
        DqRule rule = dataManager.load(DqRule.class).id(uuid).one();

        DqRuleQueries queries = queryBuilder.buildQueries(rule);
        System.out.println("Metrics: ");
        System.out.println(queries.metrics().sql());
        System.out.println(queries.metrics().params());

        System.out.println("\nSamples: ");
        System.out.println(queries.samples().sql());
        System.out.println(queries.samples().params());
    }

}
