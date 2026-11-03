package uz.kapitalbank.umida.dto.report;

import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import uz.kapitalbank.umida.entity.report.ReportCustom;

import java.util.UUID;

/**
 * Non-persistent row of the grouped list of reports: either a group — the value the reports are grouped
 * by, with the number of its reports — or a report under its group. A report row has the id of its report,
 * a group row an id derived from the grouping and the value, so that both stay the same across reloads.
 */
@JmixEntity(name = "umida_ReportTreeItem", annotatedPropertiesOnly = true)
public class ReportTreeItem {

    @JmixId
    @JmixProperty(mandatory = true)
    private UUID id;

    /** The group of a report row; none for a group row. */
    @JmixProperty
    private ReportTreeItem parent;

    /** The group caption with the number of its reports, or the localized name of the report. */
    @InstanceName
    @JmixProperty
    private String caption;

    /** The reports of a group row; none for a report row. */
    @JmixProperty
    private Integer count;

    /** The report of a report row; none for a group row. */
    @JmixProperty
    private ReportCustom report;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ReportTreeItem getParent() {
        return parent;
    }

    public void setParent(ReportTreeItem parent) {
        this.parent = parent;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public ReportCustom getReport() {
        return report;
    }

    public void setReport(ReportCustom report) {
        this.report = report;
    }

    public boolean isGroup() {
        return report == null;
    }
}
