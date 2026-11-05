package uz.kapitalbank.umida.enums.report;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

/** Where a {@link uz.kapitalbank.umida.entity.report.ReportAccessRequest} stands. */
public enum ReportAccessRequestStatus implements EnumClass<String> {

    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    /** Withdrawn by the employee who made it. */
    CANCELLED("cancelled");

    private final String id;

    ReportAccessRequestStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static ReportAccessRequestStatus fromId(String id) {
        for (ReportAccessRequestStatus value : ReportAccessRequestStatus.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
