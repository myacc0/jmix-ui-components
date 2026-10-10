package uz.kapitalbank.umida.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

/** Whether an issue is open. Why a closed one was closed is its {@link DqIssueClosingReason}. */
public enum DqIssueStatus implements EnumClass<String> {

    OPEN("open"),
    CLOSED("closed");

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