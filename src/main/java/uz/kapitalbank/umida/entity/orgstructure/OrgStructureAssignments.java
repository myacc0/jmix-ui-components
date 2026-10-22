package uz.kapitalbank.umida.entity.orgstructure;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@JmixEntity
@Table(name = "UMIDA_ORG_STRUCTURE_ASSIGNMENTS", indexes = {
        @Index(name = "IDX_UMIDA_ORG_STRUCTURE_ASSIGNMENTS_EMPLOYEE", columnList = "EMPLOYEE_ID"),
        @Index(name = "IDX_UMIDA_ORG_STRUCTURE_ASSIGNMENTS_POSITION", columnList = "POSITION_ID")
})
@Entity(name = "umida_OrgStructureAssignments")
public class OrgStructureAssignments {
    @Id
    @Column(name = "ID", nullable = false, length = 36)
    private String id;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "EMPLOYEE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructureEmployee employee;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "POSITION_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructurePosition position;

    @NotNull
    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "END_DATE")
    private LocalDate endDate;

    @Column(name = "END_REASON", length = 1000)
    private String endReason;

    public String getEndReason() {
        return endReason;
    }

    public void setEndReason(String endReason) {
        this.endReason = endReason;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public OrgStructurePosition getPosition() {
        return position;
    }

    public void setPosition(OrgStructurePosition position) {
        this.position = position;
    }

    public OrgStructureEmployee getEmployee() {
        return employee;
    }

    public void setEmployee(OrgStructureEmployee employee) {
        this.employee = employee;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}