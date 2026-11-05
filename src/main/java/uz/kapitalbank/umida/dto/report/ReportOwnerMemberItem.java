package uz.kapitalbank.umida.dto.report;

import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;

import java.util.UUID;

/**
 * A row of the list of the business owner of a report in its editor: an employee of the owner subdivision or
 * of a subdivision below it, with the rights the author ticks — run, and manage the access. The head of the
 * owner has both without a grant, so the row of the head is ticked and cannot be changed. A member who has
 * left the subdivisions of the owner keeps a row until the author removes the rights.
 *
 * <p>Built and saved by {@link uz.kapitalbank.umida.service.report.ReportOwnerMemberService}; the id is
 * derived from the employee, so a row stays the same across reloads.
 */
@JmixEntity(name = "umida_ReportOwnerMemberItem", annotatedPropertiesOnly = true)
public class ReportOwnerMemberItem {

    @JmixId
    @JmixProperty(mandatory = true)
    private UUID id;

    @JmixProperty(mandatory = true)
    private OrgStructureEmployee employee;

    @InstanceName
    @JmixProperty
    private String employeeName;

    /** The job title of the position the employee holds in the subdivisions of the owner. */
    @JmixProperty
    private String positionName;

    /** The subdivision of that position; none for a member who has left the subdivisions of the owner. */
    @JmixProperty
    private String subdivisionName;

    /** The current head of the owner subdivision itself. */
    @JmixProperty(mandatory = true)
    private Boolean head = false;

    /** A member of the list who no longer holds a position in the subdivisions of the owner. */
    @JmixProperty(mandatory = true)
    private Boolean outside = false;

    @JmixProperty(mandatory = true)
    private Boolean run = false;

    /** A delegate of the owner; implies {@code run}. */
    @JmixProperty(mandatory = true)
    private Boolean manage = false;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public OrgStructureEmployee getEmployee() {
        return employee;
    }

    public void setEmployee(OrgStructureEmployee employee) {
        this.employee = employee;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public String getSubdivisionName() {
        return subdivisionName;
    }

    public void setSubdivisionName(String subdivisionName) {
        this.subdivisionName = subdivisionName;
    }

    public Boolean getHead() {
        return head;
    }

    public void setHead(Boolean head) {
        this.head = head;
    }

    public Boolean getOutside() {
        return outside;
    }

    public void setOutside(Boolean outside) {
        this.outside = outside;
    }

    public Boolean getRun() {
        return run;
    }

    public void setRun(Boolean run) {
        this.run = run;
    }

    public Boolean getManage() {
        return manage;
    }

    public void setManage(Boolean manage) {
        this.manage = manage;
    }
}
