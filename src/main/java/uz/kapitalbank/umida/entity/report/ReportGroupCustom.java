package uz.kapitalbank.umida.entity.report;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.entity.annotation.ReplaceEntity;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportGroup;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.util.List;

/**
 * The report group of the Reports add-on with a place in a hierarchy and a look of its own.
 * It replaces {@link ReportGroup} everywhere, the add-on included, and is stored in the same
 * {@code REPORT_GROUP} table, which tells the two apart by the {@code DTYPE} column.
 *
 * <p>The icon and the color are optional: a group without them is shown with the defaults of
 * {@link uz.kapitalbank.umida.service.report.ReportGroupCustomService}.
 *
 * <p>A removed group leaves its subgroups and its reports in place, without a group: they are unlinked
 * from it, in this app and in the add-on alike.
 */
@JmixEntity
@Entity(name = ReportGroupCustom.ENTITY_NAME)
@DiscriminatorValue(ReportGroupCustom.ENTITY_NAME)
@ReplaceEntity(ReportGroup.class)
public class ReportGroupCustom extends ReportGroup {

    public static final String ENTITY_NAME = "umida_ReportGroupCustom";

    @JoinColumn(name = "PARENT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ReportGroupCustom parent;

    @OnDelete(DeletePolicy.UNLINK)
    @OneToMany(mappedBy = "parent")
    private List<ReportGroupCustom> children;

    @OnDelete(DeletePolicy.UNLINK)
    @OneToMany(mappedBy = "group")
    private List<Report> reports;

    /** A name of the "umida-icons" iconset, without the iconset prefix: {@code folder}, not {@code umida-icons:folder}. */
    @Column(name = "ICON", length = 100)
    private String icon;

    /** A color of the palette as {@code #RRGGBB}. */
    @Column(name = "COLOR", length = 7)
    private String color;

    public ReportGroupCustom getParent() {
        return parent;
    }

    public void setParent(ReportGroupCustom parent) {
        this.parent = parent;
    }

    public List<ReportGroupCustom> getChildren() {
        return children;
    }

    public void setChildren(List<ReportGroupCustom> children) {
        this.children = children;
    }

    public List<Report> getReports() {
        return reports;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
