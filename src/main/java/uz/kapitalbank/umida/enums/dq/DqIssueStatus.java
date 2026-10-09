package uz.kapitalbank.umida.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum DqIssueStatus implements EnumClass<String> {

    OPEN("open"),
    RESOLVED("resolved"),
    WONTFIX("wontfix"),
    FALSE_POSITIVE("false_positive"),
    RULE_DATA_SOURCE_CHANGED("rule_data_source_changed");

    private final String id;

    DqIssueStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqIssueStatus fromId(String id) {
        for (DqIssueStatus at : DqIssueStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}