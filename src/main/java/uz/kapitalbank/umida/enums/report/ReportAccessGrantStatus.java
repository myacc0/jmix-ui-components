package uz.kapitalbank.umida.enums.report;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

/**
 * Where a {@link uz.kapitalbank.umida.entity.report.ReportAccessGrant} stands today, as the journal shows it.
 * Not stored: it follows from the dates, the revocation and the dismissal of the employee, see
 * {@link uz.kapitalbank.umida.service.report.ReportAccessService#grantStatus}.
 */
public enum ReportAccessGrantStatus implements EnumClass<String> {

    ACTIVE("active"),
    /** Given with a start in the future. */
    NOT_STARTED("not_started"),
    EXPIRED("expired"),
    REVOKED("revoked"),
    /** The employee is dismissed. */
    DISMISSED("dismissed");

    private final String id;

    ReportAccessGrantStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static ReportAccessGrantStatus fromId(String id) {
        for (ReportAccessGrantStatus value : ReportAccessGrantStatus.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
