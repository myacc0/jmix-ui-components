package uz.kapitalbank.umida.dq;

import uz.kapitalbank.umida.dto.dq.DqRuleQueries;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSqlDialect;
import uz.kapitalbank.umida.service.dq.DqSqlQueryBuilder;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.Disabled;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                queries.violations().sql());
        assertEquals(List.of(), queries.metrics().params());
    }

    // ----- NOT_BLANK -----

    @Test
    void notBlankCountsNullsAndWhitespaceOnlyStrings() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_BLANK, "{}"), DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT COUNT(*) AS total_count," +
                        " COUNT(CASE WHEN (t.\"first_name\" IS NULL OR t.\"first_name\" ~ '^[[:space:]]*$')" +
                        " THEN 1 END) AS failed_count FROM \"employee\" t",
                queries.metrics().sql());
        assertEquals("SELECT t.* FROM \"employee\" t" +
                        " WHERE (t.\"first_name\" IS NULL OR t.\"first_name\" ~ '^[[:space:]]*$')",
                queries.violations().sql());
        // the pattern is a constant and is inlined, so nothing is bound
        assertEquals(List.of(), queries.metrics().params());
    }

    @Test
    void notBlankUsesTheMysqlOperator() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_BLANK, "{}"), DqSqlDialect.MYSQL);

        assertEquals("SELECT t.* FROM `employee` t" +
                        " WHERE (t.`first_name` IS NULL OR t.`first_name` REGEXP '^[[:space:]]*$')",
                queries.violations().sql());
    }

    @Test
    void notBlankTestsNullSeparatelyInOracle() {
        // Oracle stores '' as NULL, and REGEXP_LIKE of NULL is never true
        DqRuleQueries queries = build(rule(DqRuleType.NOT_BLANK, "{}"), DqSqlDialect.ORACLE);

        assertEquals("SELECT t.* FROM \"employee\" t" +
                        " WHERE (t.\"first_name\" IS NULL OR REGEXP_LIKE(t.\"first_name\", '^[[:space:]]*$'))",
                queries.violations().sql());
    }

    @Test
    void schemaQualifiedTableIsQuotedPerPart() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{}", "public.employee", "first_name"),
                DqSqlDialect.POSTGRESQL);

        assertTrue(queries.metrics().sql().endsWith("FROM \"public\".\"employee\" t"),
                queries.metrics().sql());
    }

    // ----- violations query, per dialect -----

    @Test
    void theViolationsAreNotCappedSoThatTheyCanBePagedThrough() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{}"), DqSqlDialect.POSTGRESQL);
        assertFalse(queries.violations().sql().contains("LIMIT"), queries.violations().sql());
    }

    @Test
    void theViolationsUseBacktickQuotingInMysql() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{}"), DqSqlDialect.MYSQL);

        assertEquals("SELECT t.* FROM `employee` t WHERE (t.`first_name` IS NULL)",
                queries.violations().sql());
    }

    @Test
    void theViolationsAreNotCappedInOracle() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{}"), DqSqlDialect.ORACLE);

        assertEquals("SELECT t.* FROM \"employee\" t WHERE (t.\"first_name\" IS NULL)",
                queries.violations().sql());
    }

    @Test
    void pageClausePerDialect() {
        assertEquals("LIMIT 50 OFFSET 100", DqSqlDialect.POSTGRESQL.pageClause(100, 50));
        assertEquals("LIMIT 50 OFFSET 0", DqSqlDialect.MYSQL.pageClause(0, 50));
        assertEquals("OFFSET 100 ROWS FETCH NEXT 50 ROWS ONLY", DqSqlDialect.ORACLE.pageClause(100, 50));
        assertThrows(IllegalArgumentException.class, () -> DqSqlDialect.POSTGRESQL.pageClause(-1, 50));
        assertThrows(IllegalArgumentException.class, () -> DqSqlDialect.POSTGRESQL.pageClause(0, 0));
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
                queries.violations().sql());
    }

    // ----- REGEXP -----

    @Test
    void regexpBindsThePatternAndIgnoresNulls() {
        DqRuleQueries queries = build(rule(DqRuleType.REGEXP, "{\"regexp\": \"^[A-Z]+$\"}"),
                DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT t.* FROM \"employee\" t" +
                        " WHERE (t.\"first_name\" IS NOT NULL AND NOT (t.\"first_name\" ~ ?))",
                queries.violations().sql());
        assertEquals(List.of("^[A-Z]+$"), queries.violations().params());
    }

    @Test
    void regexpUsesTheMysqlOperator() {
        DqRuleQueries queries = build(rule(DqRuleType.REGEXP, "{\"regexp\": \"^[A-Z]+$\"}"), DqSqlDialect.MYSQL);

        assertTrue(queries.violations().sql().contains("NOT (t.`first_name` REGEXP ?)"),
                queries.violations().sql());
    }

    @Test
    void regexpUsesTheOracleFunction() {
        DqRuleQueries queries = build(rule(DqRuleType.REGEXP, "{\"regexp\": \"^[A-Z]+$\"}"), DqSqlDialect.ORACLE);

        assertTrue(queries.violations().sql().contains("NOT (REGEXP_LIKE(t.\"first_name\", ?))"),
                queries.violations().sql());
    }

    @Test
    void regexpWithoutPatternIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.REGEXP, "{}"), DqSqlDialect.POSTGRESQL));
    }

    // ----- RANGE_NUMBER -----

    @Test
    void inclusiveBoundsFlagOnlyValuesStrictlyOutsideTheRange() {
        DqRuleQueries queries = build(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 10, \"max\": 20}",
                "employee", "salary"), DqSqlDialect.POSTGRESQL);

        // bounds default to inclusive, so the allowed values are 10 <= salary <= 20; the generated
        // condition selects the VIOLATING rows, which is the negation: salary < 10 OR salary > 20.
        // salary == 10 and salary == 20 must not be selected.
        assertEquals("SELECT t.* FROM \"employee\" t" +
                        " WHERE (t.\"salary\" IS NOT NULL AND (t.\"salary\" < ? OR t.\"salary\" > ?))",
                queries.violations().sql());
        assertEquals(List.of(BigDecimal.valueOf(10d), BigDecimal.valueOf(20d)), queries.violations().params());
    }

    @Test
    void excludedBoundsAlsoFlagTheBoundaryValuesThemselves() {
        DqRuleQueries queries = build(rule(DqRuleType.RANGE_NUMBER,
                        "{\"min\": 10, \"max\": 20, \"minIncluded\": false, \"maxIncluded\": false}",
                        "employee", "salary"),
                DqSqlDialect.POSTGRESQL);

        // allowed is now 10 < salary < 20, so salary == 10 and salary == 20 become violations —
        // this is the only case in which the comparison includes equality
        assertTrue(queries.violations().sql().contains("(t.\"salary\" <= ? OR t.\"salary\" >= ?)"),
                queries.violations().sql());
    }

    @Test
    void oneSidedNumberRangeBindsOnlyThatBound() {
        DqRuleQueries queries = build(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 10}", "employee", "salary"),
                DqSqlDialect.POSTGRESQL);

        assertTrue(queries.violations().sql().contains("(t.\"salary\" < ?)"), queries.violations().sql());
        assertEquals(1, queries.violations().params().size());
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
                queries.violations().params());
        assertTrue(queries.violations().sql().contains("(t.\"hire_date\" < ? OR t.\"hire_date\" > ?)"),
                queries.violations().sql());
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

        assertTrue(queries.violations().toDisplayString(DqSqlDialect.POSTGRESQL).contains("t.\"hire_date\" < DATE '2020-01-01'"),
                queries.violations().toDisplayString(DqSqlDialect.POSTGRESQL));
    }

    @Test
    void displayStringEscapesQuotesInAPattern() {
        DqRuleQueries queries = build(rule(DqRuleType.REGEXP, "{\"regexp\": \"o'brien\"}"),
                DqSqlDialect.POSTGRESQL);

        assertTrue(queries.violations().toDisplayString(DqSqlDialect.POSTGRESQL).contains("~ 'o''brien'"),
                queries.violations().toDisplayString(DqSqlDialect.POSTGRESQL));
    }

    @Test
    void displayStringDoublesBackslashesInAMysqlPattern() {
        DqRuleQueries queries = build(rule(DqRuleType.REGEXP, "{\"regexp\": \"^\\\\d+$\"}"),
                DqSqlDialect.MYSQL);

        assertTrue(queries.violations().toDisplayString(DqSqlDialect.MYSQL).contains("REGEXP '^\\\\d+$'"),
                queries.violations().toDisplayString(DqSqlDialect.MYSQL));
        assertTrue(queries.violations().toDisplayString(DqSqlDialect.POSTGRESQL).contains("'^\\d+$'"),
                queries.violations().toDisplayString(DqSqlDialect.POSTGRESQL));
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

    // ----- samples projection -----

    @Test
    void samplesSelectTheWholeRowWhenNoColumnsAreConfigured() {
        DqRuleQueries queries = build(rule(DqRuleType.NOT_NULL, "{}"), DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT t.* FROM \"employee\" t WHERE (t.\"first_name\" IS NULL)",
                queries.violations().sql());
    }

    @Test
    void samplesSelectTheWholeRowForTheAllColumnsMarker() {
        DqRuleQueries queries = build(
                rule(DqRuleType.NOT_NULL, "{\"samplesQueryColumns\": [\"*\"]}"), DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT t.* FROM \"employee\" t WHERE (t.\"first_name\" IS NULL)",
                queries.violations().sql());
    }

    @Test
    void samplesAreProjectedOntoTheConfiguredColumns() {
        DqRuleQueries queries = build(
                rule(DqRuleType.NOT_NULL, "{\"samplesQueryColumns\": [\"id\", \"first_name\"]}"),
                DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT t.\"id\", t.\"first_name\" FROM \"employee\" t" +
                        " WHERE (t.\"first_name\" IS NULL)",
                queries.violations().sql());
        // the projection never reaches the metrics query: it counts rows, not values
        assertTrue(queries.metrics().sql().startsWith("SELECT COUNT(*) AS total_count"),
                queries.metrics().sql());
    }

    @Test
    void theProjectionIsQuotedInTheDialectOfTheDataSource() {
        DqRuleQueries queries = build(
                rule(DqRuleType.NOT_NULL, "{\"samplesQueryColumns\": [\"id\", \"first_name\"]}"),
                DqSqlDialect.MYSQL);

        assertEquals("SELECT t.`id`, t.`first_name` FROM `employee` t WHERE (t.`first_name` IS NULL)",
                queries.violations().sql());
    }

    @Test
    void blankAndDuplicateProjectionEntriesAreDropped() {
        DqRuleQueries queries = build(
                rule(DqRuleType.NOT_NULL, "{\"samplesQueryColumns\": [\" id \", \"id\", \"\"]}"),
                DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT t.\"id\" FROM \"employee\" t WHERE (t.\"first_name\" IS NULL)",
                queries.violations().sql());
    }

    @Test
    void anEmptyProjectionFallsBackToTheWholeRow() {
        DqRuleQueries queries = build(
                rule(DqRuleType.NOT_NULL, "{\"samplesQueryColumns\": []}"), DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT t.* FROM \"employee\" t WHERE (t.\"first_name\" IS NULL)",
                queries.violations().sql());
    }

    @Test
    void aProjectionColumnThatIsNotAnIdentifierIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.NOT_NULL, "{\"samplesQueryColumns\": [\"id; DROP TABLE employee\"]}"),
                        DqSqlDialect.POSTGRESQL));
    }

    // ----- REFERENTIAL -----

    private static final String REGION_REFERENCE =
            "{\"refSchema\": \"dict\", \"refTable\": \"region\", \"refColumn\": \"code\"}";

    @Test
    void referentialFlagsValuesMissingFromTheReferenceAndIgnoresNullsByDefault() {
        DqRuleQueries queries = build(rule(DqRuleType.REFERENTIAL, REGION_REFERENCE, "client", "region_code"),
                DqSqlDialect.POSTGRESQL);

        String from = " FROM \"client\" t" +
                " LEFT JOIN (SELECT DISTINCT \"code\" FROM \"dict\".\"region\") r" +
                " ON r.\"code\" = t.\"region_code\"";
        String missing = "t.\"region_code\" IS NOT NULL AND r.\"code\" IS NULL";
        assertEquals("SELECT COUNT(*) AS total_count," +
                        " COUNT(CASE WHEN (" + missing + ") THEN 1 END) AS failed_count" + from,
                queries.metrics().sql());
        // the projection keeps to the checked table, and rows sharing a value stay adjacent
        assertEquals("SELECT t.*" + from + " WHERE (" + missing + ") ORDER BY t.\"region_code\"",
                queries.violations().sql());
        assertEquals(List.of(), queries.metrics().params());
    }

    @Test
    void referentialFlagsNullsWhenTheyAreNotAllowed() {
        DqRuleQueries queries = build(rule(DqRuleType.REFERENTIAL,
                        "{\"refSchema\": \"dict\", \"refTable\": \"region\", \"refColumn\": \"code\"," +
                                " \"allowNull\": false}", "client", "region_code"),
                DqSqlDialect.POSTGRESQL);

        assertTrue(queries.violations().sql().contains(
                        " WHERE (t.\"region_code\" IS NULL OR r.\"code\" IS NULL)"),
                queries.violations().sql());
    }

    @Test
    void referentialCanReferToTheCheckedTableItself() {
        DqRule rule = rule(DqRuleType.REFERENTIAL,
                "{\"refSchema\": \"public\", \"refTable\": \"department\", \"refColumn\": \"id\"}",
                "department", "parent_id");
        rule.setDbSchema("public");

        DqRuleQueries queries = build(rule, DqSqlDialect.POSTGRESQL);

        assertEquals("SELECT t.* FROM \"public\".\"department\" t" +
                        " LEFT JOIN (SELECT DISTINCT \"id\" FROM \"public\".\"department\") r" +
                        " ON r.\"id\" = t.\"parent_id\"" +
                        " WHERE (t.\"parent_id\" IS NOT NULL AND r.\"id\" IS NULL) ORDER BY t.\"parent_id\"",
                queries.violations().sql());
    }

    @Test
    void referentialUsesBacktickQuotingInMysql() {
        DqRuleQueries queries = build(rule(DqRuleType.REFERENTIAL, REGION_REFERENCE, "client", "region_code"),
                DqSqlDialect.MYSQL);

        assertEquals("SELECT t.* FROM `client` t" +
                        " LEFT JOIN (SELECT DISTINCT `code` FROM `dict`.`region`) r ON r.`code` = t.`region_code`" +
                        " WHERE (t.`region_code` IS NOT NULL AND r.`code` IS NULL) ORDER BY t.`region_code`",
                queries.violations().sql());
    }

    @Test
    void referentialAliasesTheReferenceWithoutAsInOracle() {
        // Oracle rejects AS in front of a table alias
        DqRuleQueries queries = build(rule(DqRuleType.REFERENTIAL, REGION_REFERENCE, "client", "region_code"),
                DqSqlDialect.ORACLE);

        assertTrue(queries.metrics().sql().endsWith(
                        " FROM \"client\" t LEFT JOIN (SELECT DISTINCT \"code\" FROM \"dict\".\"region\") r" +
                                " ON r.\"code\" = t.\"region_code\""),
                queries.metrics().sql());
    }

    @Test
    void referentialWithoutReferenceTableOrColumnIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.REFERENTIAL, "{\"refColumn\": \"code\"}"), DqSqlDialect.POSTGRESQL));
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.REFERENTIAL, "{\"refTable\": \"region\"}"), DqSqlDialect.POSTGRESQL));
    }

    @Test
    void referenceNamesThatAreNotPlainIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.REFERENTIAL,
                        "{\"refTable\": \"region r; DROP TABLE client\", \"refColumn\": \"code\"}"),
                        DqSqlDialect.POSTGRESQL));
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.REFERENTIAL,
                        "{\"refTable\": \"region\", \"refColumn\": \"code) r\"}"),
                        DqSqlDialect.POSTGRESQL));
    }

    // ----- CUSTOM_SQL -----

    @Test
    void customSqlQueriesAreTakenAsWritten() {
        DqRuleQueries queries = build(rule(DqRuleType.CUSTOM_SQL,
                "{\"sql\": \"select count(*), count(*) filter (where a is null) from t;\","
                        + " \"violationsSql\": \"select id, a from t where a is null order by id;\"}"),
                DqSqlDialect.POSTGRESQL);

        assertEquals("select count(*), count(*) filter (where a is null) from t", queries.metrics().sql());
        assertTrue(queries.metrics().params().isEmpty());
        assertEquals("select id, a from t where a is null order by id", queries.violations().sql());
        assertTrue(queries.violations().params().isEmpty());
    }

    @Test
    void customSqlThatIsNotASingleReadQueryIsRejected() {
        String violations = ", \"violationsSql\": \"select * from t\"}";
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.CUSTOM_SQL, "{}"), DqSqlDialect.POSTGRESQL));
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.CUSTOM_SQL, "{\"sql\": \"delete from t\"" + violations),
                        DqSqlDialect.POSTGRESQL));
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.CUSTOM_SQL, "{\"sql\": \"select 1, 2; delete from t\"" + violations),
                        DqSqlDialect.POSTGRESQL));
        assertThrows(IllegalArgumentException.class,
                () -> build(rule(DqRuleType.CUSTOM_SQL, "{\"sql\": \"select 1, 2\"}"), DqSqlDialect.POSTGRESQL),
                "the violations query is required too");
    }

    // ----- not implemented yet -----

    @Test
    void pendingRuleTypesFailExplicitly() {
        assertThrows(UnsupportedOperationException.class,
                () -> build(rule(DqRuleType.CROSS_SOURCE_AGGREGATED, "{}"), DqSqlDialect.POSTGRESQL));
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

    // pass actual rule ID for test work properly
    @Disabled
    @ParameterizedTest
    @CsvSource({"019fb34d-8d41-7193-bd9d-e6dd7510220b", "019fb361-5dc9-7d85-a2f8-85ef2a4f70af"})
    void print_dqRule(String id) throws IOException {
        UUID uuid = UUID.fromString(id);
        DqRule rule = dataManager.load(DqRule.class).id(uuid).one();

        DqRuleQueries queries = queryBuilder.buildQueries(rule);
        System.out.println("Metrics: ");
        System.out.println(queries.metrics().sql());
        System.out.println(queries.metrics().params());

        System.out.println("\nViolations: ");
        System.out.println(queries.violations().sql());
        System.out.println(queries.violations().params());
    }

}
