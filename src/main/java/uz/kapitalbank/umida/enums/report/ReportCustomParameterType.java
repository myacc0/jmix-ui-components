package uz.kapitalbank.umida.enums.report;

import io.jmix.core.metamodel.datatype.EnumClass;
import io.jmix.reports.entity.ParameterType;
import org.springframework.lang.Nullable;

/**
 * The parameter types of our own, next to the {@link ParameterType} of the Reports add-on, which is an enum
 * and cannot be extended. A parameter of such a type is stored as a parameter of its {@link #getBaseType()}
 * with this type in {@link uz.kapitalbank.umida.entity.report.ReportInputParameterCustom#getCustomType()}:
 * everything that does not know the custom type, the run of the report included, sees a standard parameter.
 */
public enum ReportCustomParameterType implements EnumClass<String> {

    /**
     * A value picked from the list returned by a user-written SQL query. The value is a text, as a
     * {@link ParameterType#TEXT} parameter.
     */
    SQL_ENUMERATION("SQL_ENUMERATION", ParameterType.TEXT);

    private final String id;
    private final ParameterType baseType;

    ReportCustomParameterType(String id, ParameterType baseType) {
        this.id = id;
        this.baseType = baseType;
    }

    @Override
    public String getId() {
        return id;
    }

    /** The standard type the parameter is stored and run as. */
    public ParameterType getBaseType() {
        return baseType;
    }

    @Nullable
    public static ReportCustomParameterType fromId(@Nullable String id) {
        for (ReportCustomParameterType value : ReportCustomParameterType.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
