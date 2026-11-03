package uz.kapitalbank.umida.enums.report;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


/**
 * How the list of reports shows them: one paged list, or a tree of the reports under the value of
 * one of their attributes.
 */
public enum ReportGrouping implements EnumClass<String> {

    ALL("all", null),
    GROUP("group", "group"),
    DOMAIN("domain", "domain");

    private final String id;
    private final String propertyPath;

    ReportGrouping(String id, @Nullable String propertyPath) {
        this.id = id;
        this.propertyPath = propertyPath;
    }

    public String getId() {
        return id;
    }

    /** The {@code ReportCustom} attribute the reports are grouped by; none for {@link #ALL}. */
    @Nullable
    public String getPropertyPath() {
        return propertyPath;
    }

    @Nullable
    public static ReportGrouping fromId(String id) {
        for (ReportGrouping at : ReportGrouping.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
