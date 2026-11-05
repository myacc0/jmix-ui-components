package uz.kapitalbank.umida.enums.report;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

/** How a {@link uz.kapitalbank.umida.entity.report.ReportAccessGrant} came about. */
public enum ReportAccessGrantSource implements EnumClass<String> {

    /** The author put the employee on the list of the business owner of the report. */
    OWNER("owner"),
    /** The owner approved the employee's request. */
    REQUEST("request"),
    /** The owner gave access to the employee without a request. */
    DIRECT("direct"),
    /** The owner gave access to the employees of a subdivision at once. */
    SUBDIVISION("subdivision");

    private final String id;

    ReportAccessGrantSource(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static ReportAccessGrantSource fromId(String id) {
        for (ReportAccessGrantSource value : ReportAccessGrantSource.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
