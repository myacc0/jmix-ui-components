package uz.kapitalbank.umida.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


/**
 * How the issue list shows the issues: one paged list, or a tree of the issues under the value of
 * one of their attributes.
 */
public enum DqIssueGrouping implements EnumClass<String> {

    ALL("all", null),
    GROUP("group", "rule.group"),
    DOMAIN("domain", "rule.domain"),
    /** {@code DqIssue.assignee}, the subdivision the issue is assigned to — not the current one of its rule. */
    ASSIGNEE("assignee", "assignee");

    private final String id;
    private final String propertyPath;

    DqIssueGrouping(String id, @Nullable String propertyPath) {
        this.id = id;
        this.propertyPath = propertyPath;
    }

    public String getId() {
        return id;
    }

    /** The {@code DqIssue} attribute the issues are grouped by; none for {@link #ALL}. */
    @Nullable
    public String getPropertyPath() {
        return propertyPath;
    }

    @Nullable
    public static DqIssueGrouping fromId(String id) {
        for (DqIssueGrouping at : DqIssueGrouping.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
