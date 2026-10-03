package uz.kapitalbank.umida.entity.orgstructure;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import uz.kapitalbank.umida.enums.orgstructure.OrgStructurePositionStatus;

import java.time.LocalDate;

@JmixEntity
@Table(name = "UMIDA_ORG_STRUCTURE_POSITIONS", indexes = {
        @Index(name = "IDX_UMIDA_ORG_STRUCTURE_POSITION_SUBDIVISION", columnList = "SUBDIVISION_ID"),
        @Index(name = "IDX_UMIDA_ORG_STRUCTURE_POSITION_JOB_TITLE", columnList = "JOBTITLE_ID"),
        @Index(name = "IDX_UMIDA_ORG_STRUCTURE_POSITION_EMPLOYEE", columnList = "EMPLOYEE_ID")
})
@Entity(name = "umida_OrgStructurePosition")
public class OrgStructurePosition {
    @Column(name = "ID", nullable = false, length = 36)
    @Id
    private String id;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "SUBDIVISION_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructureSubdivision subdivision;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "JOBTITLE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructureJobTitle jobTitle;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "EMPLOYEE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructureEmployee employee;

    @Column(name = "LVL", length = 50)
    private String lvl;

    @NumberFormat(pattern = "#")
    @Column(name = "ISHEADOFSUBDIVISION")
    private Integer isheadofsubdivision;

    @Column(name = "STATUS", length = 50)
    private String status;

    @Column(name = "RECRUITMENT_DATE")
    private LocalDate recruitmentDate;

    @Column(name = "DISMISSAL_DATE")
    private LocalDate dismissalDate;

    public LocalDate getDismissalDate() {
        return dismissalDate;
    }

    public void setDismissalDate(LocalDate dismissalDate) {
        this.dismissalDate = dismissalDate;
    }

    public LocalDate getRecruitmentDate() {
        return recruitmentDate;
    }

    public void setRecruitmentDate(LocalDate recruitmentDate) {
        this.recruitmentDate = recruitmentDate;
    }

    public OrgStructurePositionStatus getStatus() {
        return status == null ? null : OrgStructurePositionStatus.fromId(status);
    }

    public void setStatus(OrgStructurePositionStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public String getLvl() {
        return lvl;
    }

    public void setLvl(String lvl) {
        this.lvl = lvl;
    }

    public Integer getIsheadofsubdivision() {
        return isheadofsubdivision;
    }

    public void setIsheadofsubdivision(Integer isheadofsubdivision) {
        this.isheadofsubdivision = isheadofsubdivision;
    }

    public OrgStructureEmployee getEmployee() {
        return employee;
    }

    public void setEmployee(OrgStructureEmployee orgStructureEmployee) {
        this.employee = orgStructureEmployee;
    }

    public OrgStructureJobTitle getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(OrgStructureJobTitle jobTitle) {
        this.jobTitle = jobTitle;
    }

    public OrgStructureSubdivision getSubdivision() {
        return subdivision;
    }

    public void setSubdivision(OrgStructureSubdivision subdivision) {
        this.subdivision = subdivision;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}