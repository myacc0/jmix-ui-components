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
        @Index(name = "IDX_UMIDA_ORG_STRUCTURE_POSITION_JOB_TITLE", columnList = "JOBTITLE_ID")
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

    @Column(name = "LVL", length = 50)
    private String lvl;

    @NumberFormat(pattern = "#")
    @Column(name = "ISHEADOFSUBDIVISION")
    private Integer isheadofsubdivision;

    @Column(name = "STATUS", length = 50)
    private String status;

    @Column(name = "IS_ACTIVE", columnDefinition = "integer default 1")
    private Integer isActive;

    @Column(name = "END_DATE")
    private LocalDate endDate;

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
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