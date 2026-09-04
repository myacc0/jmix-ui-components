package com.company.demo.starrocks;

import com.company.demo.data.NoLikeEscapePropertyConditionGenerator;
import com.company.demo.entity.starrocks.Product;
import io.jmix.core.QueryUtils;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.data.impl.jpql.generator.ConditionGenerationContext;
import io.jmix.data.impl.jpql.generator.ConditionGenerator;
import io.jmix.data.impl.jpql.generator.ConditionGeneratorResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StarRocks cannot parse the trailing {@code escape '\'} clause that Jmix appends to every
 * {@code LIKE}-based condition; such a query dies with
 * "Unexpected input 'ESCAPE', the most similar input is {&lt;EOF&gt;, ';'}".
 * <p>
 * These tests cover the filtering done by the {@code filterable="true"} data grid header filters
 * (and by {@code genericFilter} / {@code propertyFilter}, which build the same
 * {@link PropertyCondition}s) against the string columns of the {@code starrocks} store, plus the
 * guarantee that the other stores keep their {@code ESCAPE} clause.
 * <p>
 * Requires the jmix-starrocks container to be running; reads only.
 */
@SpringBootTest
public class StarrocksLikeFilterTests {

    @Autowired
    UnconstrainedDataManager dataManager;

    @Autowired
    ConditionGeneratorResolver conditionGeneratorResolver;

    @Test
    void contains_filter_on_a_string_column_runs_on_starrocks() {
        List<Product> products = loadByName(PropertyCondition.contains("name", "Galaxy"));

        assertThat(products).isNotEmpty();
        assertThat(products).allMatch(p -> p.getName().contains("Galaxy"));
    }

    @Test
    void contains_filter_is_case_insensitive() {
        assertThat(loadByName(PropertyCondition.contains("name", "galaxy")))
                .isNotEmpty();
    }

    @Test
    void not_contains_filter_runs_on_starrocks() {
        List<Product> products = loadByName(
                PropertyCondition.createWithValue("name", PropertyCondition.Operation.NOT_CONTAINS, "Galaxy"));

        assertThat(products).isNotEmpty();
        assertThat(products).noneMatch(p -> p.getName().contains("Galaxy"));
    }

    @Test
    void starts_with_filter_runs_on_starrocks() {
        List<Product> products = loadByName(PropertyCondition.startsWith("name", "Samsung"));

        assertThat(products).isNotEmpty();
        assertThat(products).allMatch(p -> p.getName().startsWith("Samsung"));
    }

    @Test
    void ends_with_filter_runs_on_starrocks() {
        String suffix = dataManager.load(Product.class).all().list().getFirst().getName();
        suffix = suffix.substring(suffix.length() - 4);

        List<Product> products = loadByName(PropertyCondition.endsWith("name", suffix));

        assertThat(products).isNotEmpty();
        String expectedSuffix = suffix;
        assertThat(products).allMatch(p -> p.getName().endsWith(expectedSuffix));
    }

    @Test
    void filter_on_a_nullable_text_column_runs_on_starrocks() {
        assertThat(loadByName(PropertyCondition.contains("description", "Samsung")))
                .isNotEmpty();
    }

    /**
     * The UI escapes {@code %} and {@code _} in the entered value ({@code PropertyFilter} calls
     * {@link QueryUtils#escapeForLike(String)}). Without the {@code ESCAPE} clause StarRocks still
     * honours the backslash as the default escape character, so the wildcard must stay literal.
     */
    @Test
    void escaped_wildcards_are_matched_literally_without_the_escape_clause() {
        assertThat(loadByName(PropertyCondition.contains("name", QueryUtils.escapeForLike("Galaxy%"))))
                .isEmpty();
        assertThat(loadByName(PropertyCondition.contains("name", QueryUtils.escapeForLike("Galax_"))))
                .isEmpty();
        // sanity: the same search without a wildcard does find rows
        assertThat(loadByName(PropertyCondition.contains("name", QueryUtils.escapeForLike("Galaxy"))))
                .isNotEmpty();
    }

    @Test
    void starrocks_entities_get_the_where_clause_without_the_escape_clause() {
        assertThat(generateWhere("demo_StarrocksProduct", "name"))
                .isEqualTo("e.name like :nameParam");
    }

    @Test
    void entities_of_the_other_stores_keep_the_escape_clause() {
        assertThat(generateWhere("demo_User", "username"))
                .isEqualTo("e.username like :nameParam escape '\\'");
    }

    private List<Product> loadByName(PropertyCondition condition) {
        return dataManager.load(Product.class)
                .condition(condition)
                .list();
    }

    private String generateWhere(String entityName, String property) {
        PropertyCondition condition = PropertyCondition.createWithParameterName(
                property, PropertyCondition.Operation.CONTAINS, "nameParam");
        ConditionGenerationContext context = new ConditionGenerationContext(condition);
        context.setEntityName(entityName);
        context.setEntityAlias("e");

        ConditionGenerator<?> generator = conditionGeneratorResolver.getConditionGenerator(context);
        if ("demo_StarrocksProduct".equals(entityName)) {
            assertThat(generator).isInstanceOf(NoLikeEscapePropertyConditionGenerator.class);
        } else {
            assertThat(generator).isNotInstanceOf(NoLikeEscapePropertyConditionGenerator.class);
        }
        return generator.generateWhere(context);
    }
}
