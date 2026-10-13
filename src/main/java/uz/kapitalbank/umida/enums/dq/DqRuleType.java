package uz.kapitalbank.umida.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum DqRuleType implements EnumClass<String> {

    NOT_NULL("not_null"),
    NOT_BLANK("not_blank"),
    UNIQUENESS("uniqueness"),
    REGEXP("regexp"),
    RANGE_NUMBER("range_number"),
    RANGE_DATE("range_date"),
    REFERENTIAL("referential"),
    CUSTOM_SQL("custom_sql"),
    CROSS_SOURCE_AGGREGATED("cross_source_aggregated");

    private final String id;

    DqRuleType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * Whether the rule checks one column of one table and is edited in the standard rule form.
     * Custom SQL and cross-source rules are not bound to a single column and have forms of their own.
     */
    public boolean isStandard() {
        return this != CUSTOM_SQL && this != CROSS_SOURCE_AGGREGATED;
    }

    @Nullable
    public static DqRuleType fromId(String id) {
        for (DqRuleType at : DqRuleType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}