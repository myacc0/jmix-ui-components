package uz.kapitalbank.umida.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

/** Why a {@link DqIssueStatus#CLOSED} issue was closed. An open issue has no reason. */
public enum DqIssueClosingReason implements EnumClass<String> {

    FIXED("fixed"),
    WONTFIX("wontfix"),
    FALSE_POSITIVE("false_positive"),
    RULE_DATA_SOURCE_CHANGED("rule_data_source_changed");

    private final String id;

    DqIssueClosingReason(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqIssueClosingReason fromId(String id) {
        for (DqIssueClosingReason at : DqIssueClosingReason.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
