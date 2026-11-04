package uz.kapitalbank.umida.entity.report;

import io.jmix.core.entity.annotation.ReplaceEntity;
import io.jmix.core.entity.annotation.SystemLevel;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import io.jmix.reports.entity.ReportInputParameter;
import org.springframework.lang.Nullable;
import uz.kapitalbank.umida.enums.report.ReportCustomParameterType;

/**
 * The input parameter of a report with the parameter types of our own, see {@link ReportCustomParameterType}.
 * It replaces {@link ReportInputParameter} everywhere, the add-on included.
 *
 * <p>The parameters are not stored in a table of their own: the add-on serializes them into the structure of
 * the report (the {@code XML} column of {@code REPORT_REPORT}) with all of their attributes, so the ones added
 * here go there as well and need no changelog. The structures stored earlier read as before, with no custom
 * type.
 *
 * <p>A parameter of a custom type keeps the {@link ReportCustomParameterType#getBaseType() base type} in
 * {@code type}, so the run of the report, the REST API and the scheduled runs see a standard parameter.
 */
@JmixEntity(name = ReportInputParameterCustom.ENTITY_NAME, annotatedPropertiesOnly = true)
@ReplaceEntity(ReportInputParameter.class)
@SystemLevel
public class ReportInputParameterCustom extends ReportInputParameter {

    public static final String ENTITY_NAME = "umida_ReportInputParameterCustom";

    private static final long serialVersionUID = -2784710263117826409L;

    @JmixProperty
    protected String customType;

    /** The query of a {@link ReportCustomParameterType#SQL_ENUMERATION} parameter. */
    @JmixProperty
    protected String sqlQuery;

    /** The data store the query of a {@link ReportCustomParameterType#SQL_ENUMERATION} parameter runs in. */
    @JmixProperty
    protected String sqlDataStore;

    @Nullable
    public ReportCustomParameterType getCustomType() {
        return ReportCustomParameterType.fromId(customType);
    }

    public void setCustomType(@Nullable ReportCustomParameterType customType) {
        this.customType = customType == null ? null : customType.getId();
    }

    public String getSqlQuery() {
        return sqlQuery;
    }

    /** An empty query is none: the code editor writes back an empty text for a cleared value. */
    public void setSqlQuery(String sqlQuery) {
        this.sqlQuery = sqlQuery == null || sqlQuery.isEmpty() ? null : sqlQuery;
    }

    public String getSqlDataStore() {
        return sqlDataStore;
    }

    public void setSqlDataStore(String sqlDataStore) {
        this.sqlDataStore = sqlDataStore;
    }
}
