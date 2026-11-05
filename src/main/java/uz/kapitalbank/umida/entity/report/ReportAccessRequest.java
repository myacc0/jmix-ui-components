package uz.kapitalbank.umida.entity.report;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.enums.report.ReportAccessRequestStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An employee's request to run a report. It goes to the head and the delegates of the business owner of
 * the report, or to the author when the report has no one to decide on it, see
 * {@link uz.kapitalbank.umida.service.report.ReportAccessService}. An approved request gives a
 * {@link ReportAccessGrant} of the source {@code REQUEST}.
 *
 * <p>The requester is an employee, not a user: so are the holders of the grants, whom the owner also picks
 * among employees who may never have logged in. An employee has at most one pending request per report,
 * which a partial unique index of the database also keeps.
 */
@JmixEntity
@Table(name = "UMIDA_REPORT_ACCESS_REQUEST", indexes = {
        @Index(name = "IDX_UMIDA_REPORT_ACCESS_REQUEST_REPORT", columnList = "REPORT_ID"),
        @Index(name = "IDX_UMIDA_REPORT_ACCESS_REQUEST_EMPLOYEE", columnList = "EMPLOYEE_ID"),
        @Index(name = "IDX_UMIDA_REPORT_ACCESS_REQUEST_POSITION", columnList = "POSITION_ID")
})
@Entity(name = "umida_ReportAccessRequest")
public class ReportAccessRequest {

    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "REPORT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReportCustom report;

    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "EMPLOYEE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private OrgStructureEmployee employee;

    /**
     * The position the employee held when the request was made, kept as it was then: the job title and the
     * subdivision in the lists of the access management. None if the employee held no position.
     */
    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "POSITION_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructurePosition position;

    /** Why the employee needs the report, as they put it. */
    @Column(name = "REASON", columnDefinition = "text")
    private String reason;

    @NotNull
    @Column(name = "STATUS", nullable = false, length = 40)
    private String status = ReportAccessRequestStatus.PENDING.getId();

    /** The username of whoever approved or rejected the request; of the requester for a cancelled one. */
    @Column(name = "DECIDED_BY")
    private String decidedBy;

    @Column(name = "DECIDED_AT")
    private OffsetDateTime decidedAt;

    @Column(name = "DECISION_COMMENT", columnDefinition = "text")
    private String decisionComment;

    @CreatedBy
    @Column(name = "CREATED_BY")
    private String createdBy;

    /** When the request was made. */
    @CreatedDate
    @Column(name = "CREATED_DATE")
    private OffsetDateTime createdDate;

    @LastModifiedBy
    @Column(name = "LAST_MODIFIED_BY")
    private String lastModifiedBy;

    @LastModifiedDate
    @Column(name = "LAST_MODIFIED_DATE")
    private OffsetDateTime lastModifiedDate;

    @InstanceName
    @DependsOnProperties({"employee", "report"})
    public String getInstanceName() {
        return (employee != null ? employee.getFullName() : "") + " → " + (report != null ? report.getName() : "");
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public ReportCustom getReport() {
        return report;
    }

    public void setReport(ReportCustom report) {
        this.report = report;
    }

    public OrgStructureEmployee getEmployee() {
        return employee;
    }

    public void setEmployee(OrgStructureEmployee employee) {
        this.employee = employee;
    }

    public OrgStructurePosition getPosition() {
        return position;
    }

    public void setPosition(OrgStructurePosition position) {
        this.position = position;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public ReportAccessRequestStatus getStatus() {
        return status == null ? null : ReportAccessRequestStatus.fromId(status);
    }

    public void setStatus(ReportAccessRequestStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public void setDecisionComment(String decisionComment) {
        this.decisionComment = decisionComment;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public OffsetDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(OffsetDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
}
