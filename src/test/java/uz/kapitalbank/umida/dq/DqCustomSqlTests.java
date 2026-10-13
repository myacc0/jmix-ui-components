package uz.kapitalbank.umida.dq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.kapitalbank.umida.service.dq.DqCustomSql;
import uz.kapitalbank.umida.service.dq.DqCustomSql.Problem;

import static org.junit.jupiter.api.Assertions.*;

/** The text-level rules for the statement of a custom SQL rule; no database involved. */
public class DqCustomSqlTests {

    @Test
    void theTrailingSemicolonIsDropped() {
        assertEquals("select 1, 2", DqCustomSql.normalize("  select 1, 2 ;\n "));
        assertEquals("select 1, 2", DqCustomSql.normalize("select 1, 2;;"));
        assertEquals("", DqCustomSql.normalize(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "select count(*), count(*) filter (where parent_id is null) from umida_dict_data_domain",
            "SELECT 10, 4;",
            "  (select 1, 2)",
            "with d as (select * from umida_dict_data_domain) select count(*), 0 from d",
            // a semicolon or a keyword inside a literal, a quoted name or a comment is no code
            "select count(*), 0 from t where name <> 'a; delete' -- ; update\n",
            "select count(*), 0 from t /* insert; */ where \"into\" is null",
            "select count(*), 0 from t where body <> $tag$ ; delete $tag$",
            // words that merely contain a forbidden one
            "select count(*), sum(updated_count) from t where created_at is not null"
    })
    void acceptsASingleReadQuery(String sql) {
        assertNull(DqCustomSql.check(sql), sql);
    }

    @Test
    void rejectsAnEmptyStatement() {
        assertEquals(Problem.BLANK, DqCustomSql.check("  -- nothing\n ").problem());
        assertEquals(Problem.BLANK, DqCustomSql.check(null).problem());
    }

    @Test
    void rejectsMoreThanOneStatement() {
        assertEquals(Problem.MULTIPLE_STATEMENTS,
                DqCustomSql.check("select 1, 2; delete from t").problem());
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete from t", "update t set a = 1", "insert into t values (1)", "drop table t",
            "call p()", "values (1, 2)"})
    void rejectsAStatementThatIsNotASelect(String sql) {
        assertEquals(Problem.NOT_SELECT, DqCustomSql.check(sql).problem(), sql);
    }

    @Test
    void rejectsAReadQueryThatWrites() {
        DqCustomSql.Violation cte = DqCustomSql.check(
                "with d as (delete from t returning id) select count(*), 0 from d");
        assertEquals(Problem.MODIFYING_KEYWORD, cte.problem());
        assertEquals("DELETE", cte.word());

        assertEquals("INTO", DqCustomSql.check("select 1, 2 into copy_table").word());
        assertEquals("UPDATE", DqCustomSql.check("select count(*), 0 from t for update").word());
    }
}
