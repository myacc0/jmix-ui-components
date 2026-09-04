package com.company.demo.data;

import io.jmix.core.JmixOrder;
import io.jmix.core.Metadata;
import io.jmix.core.MetadataTools;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.data.impl.jpql.generator.ConditionGenerationContext;
import io.jmix.data.impl.jpql.generator.PropertyConditionGenerator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Generates {@code LIKE}-based conditions without the trailing {@code ESCAPE '\'} clause for entities
 * that live in a store which cannot parse it (see {@link LikeEscapeSupport}).
 * <p>
 * Applies to every string attribute and every {@code LIKE}-based operation — "contains",
 * "doesn't contain", "starts with", "ends with" — no matter where the condition comes from: a
 * {@code filterable="true"} data grid header filter, a {@code genericFilter}, a {@code propertyFilter},
 * or a hand-built {@link PropertyCondition}.
 * <p>
 * Registered with a higher precedence than the framework's {@link PropertyConditionGenerator}
 * ({@link JmixOrder#LOWEST_PRECEDENCE}) so that {@code data_ConditionGeneratorResolver} picks this one
 * first; everything else is inherited unchanged, so conditions for the other stores keep their
 * {@code ESCAPE} clause.
 * <p>
 * Note: key-value queries ({@code ValueLoadContext}) are not covered — their generation context carries
 * no entity name, so the store cannot be resolved and the framework generator handles them as before.
 */
@Component("demo_NoLikeEscapePropertyConditionGenerator")
@Order(JmixOrder.LOWEST_PRECEDENCE - 5)
public class NoLikeEscapePropertyConditionGenerator extends PropertyConditionGenerator {

    protected final LikeEscapeSupport likeEscapeSupport;

    public NoLikeEscapePropertyConditionGenerator(MetadataTools metadataTools,
                                                  Metadata metadata,
                                                  LikeEscapeSupport likeEscapeSupport) {
        super(metadataTools, metadata);
        this.likeEscapeSupport = likeEscapeSupport;
    }

    @Override
    public boolean supports(ConditionGenerationContext context) {
        return super.supports(context)
                && likeEscapeSupport.isEscapeClauseUnsupported(context.getEntityName());
    }

    @Override
    protected String getLikeEscapeClause(PropertyCondition propertyCondition) {
        return "";
    }
}
