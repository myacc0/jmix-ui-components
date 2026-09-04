package com.company.demo.data;

import io.jmix.core.Metadata;
import io.jmix.core.metamodel.model.MetaClass;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tells whether a data store understands the trailing {@code ESCAPE '\'} clause that Jmix appends
 * to every {@code LIKE}-based condition ("contains", "doesn't contain", "starts with", "ends with").
 * <p>
 * StarRocks does not: it parses {@code LIKE} without the optional {@code ESCAPE} part and fails with
 * <em>"Unexpected input 'ESCAPE', the most similar input is &#123;&lt;EOF&gt;, ';'&#125;"</em>. It does,
 * however, treat a backslash as the default escape character inside the pattern (MySQL semantics), so
 * dropping the clause keeps the wildcard escaping done by
 * {@link io.jmix.core.QueryUtils#escapeForLike(String)} working unchanged.
 * <p>
 * Stores are listed in {@code demo.jpql.like-escape-unsupported-stores} (comma-separated store names).
 *
 * @see NoLikeEscapePropertyConditionGenerator
 */
@Component("demo_LikeEscapeSupport")
public class LikeEscapeSupport {

    protected final Metadata metadata;
    protected final Set<String> unsupportedStores;

    public LikeEscapeSupport(Metadata metadata,
                             @Value("${demo.jpql.like-escape-unsupported-stores:}") String unsupportedStores) {
        this.metadata = metadata;
        this.unsupportedStores = unsupportedStores.isBlank()
                ? Collections.emptySet()
                : Arrays.stream(unsupportedStores.split(","))
                .map(String::trim)
                .filter(store -> !store.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @param entityName name of the entity the condition is generated for, may be {@code null}
     *                   (key-value queries do not carry one)
     * @return true if the store of the given entity cannot parse an {@code ESCAPE} clause
     */
    public boolean isEscapeClauseUnsupported(@Nullable String entityName) {
        if (entityName == null || unsupportedStores.isEmpty()) {
            return false;
        }
        MetaClass metaClass = metadata.findClass(entityName);
        return metaClass != null && unsupportedStores.contains(metaClass.getStore().getName());
    }
}
