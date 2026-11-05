package uz.kapitalbank.umida.entity.report;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.enums.report.ReportAccessGrantSource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The right of an employee to run a report, and with {@code canManage} also to manage the access to it: the
 * employee is then a delegate of the business owner. The head of the owner subdivision has both rights
 * without a grant, see {@link uz.kapitalbank.umida.service.report.ReportAccessService}.
 *
 * <p>A grant is in force from {@code validFrom} up to and including {@code validTo}, which may be left
 * open, until it is revoked, and only while its employee is not dismissed. A revoked grant is kept, with
 * who revoked it, when and why: the grants make the journal of the access to the reports. The changes of
 * a grant are made by revoking it and giving a new one, so a grant never changes but for its revocation.
 *
 * <p>The holder is an employee, not a user: the owner picks employees, who may never have logged in.
 */
@JmixEntity
@Table(name = "UMIDA_REPORT_ACCESS_GRANT", indexes = {
        @Index(name = "IDX_UMIDA_REPORT_ACCESS_GRANT_REPORT", columnList = "REPORT_ID"),
        @Index(name = "IDX_UMIDA_REPORT_ACCESS_GRANT_EMPLOYEE", columnList = "EMPLOYEE_ID"),
        @Index(name = "IDX_UMIDA_REPORT_ACCESS_GRANT_POSITION", columnList = "POSITION_ID"),
        @Index(name = "IDX_UMIDA_REPORT_ACCESS_GRANT_REQUEST", columnList = "REQUEST_ID")
})
@Entity(name = "umida_ReportAccessGrant")
public class ReportAccessGrant {

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
     * The position the employee held when the grant was given, kept as it was then: the job title and the
     * subdivision in the lists of the access management. None if the employee held no position.
     */
    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "POSITION_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructurePosition position;

    /** The employee also manages the access to the report: a delegate of the business owner. */
    @NotNull
    @Column(name = "CAN_MANAGE", nullable = false)
    private Boolean canManage = false;

    @NotNull
    @Column(name = "SOURCE", nullable = false, length = 40)
    private String source;

    /** The request the grant was given on, for the source {@code REQUEST}. */
    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "REQUEST_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ReportAccessRequest request;

    @NotNull
    @Column(name = "VALID_FROM", nullable = false)
    private LocalDate validFrom;

    /** The last day of the grant; none for a grant without an end. */
    @Column(name = "VALID_TO")
    private LocalDate validTo;

    /** The username of whoever revoked the grant; none while it is not revoked. */
    @Column(name = "REVOKED_BY")
    private String revokedBy;

    @Column(name = "REVOKED_AT")
    private OffsetDateTime revokedAt;

    @Column(name = "REVOKE_REASON", columnDefinition = "text")
    private String revokeReason;

    /** The username of whoever gave the grant. */
    @CreatedBy
    @Column(name = "CREATED_BY")
    private String createdBy;

    @CreatedDate
    @Column(name = "CREATED_DATE")
    private OffsetDateTime createdDate;

    @LastModifiedBy
    @Column(name = "LAST_MODIFIED_BY")
    private String lastModifiedBy;

    @LastModifiedDate
    @Column(name = "LAST_MODIFIED_DATE")
    private OffsetDateTime lastModifiedDate;

    @PostConstruct
    public void postConstruct() {
        validFrom = LocalDate.now();
    }

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

    public Boolean getCanManage() {
        return canManage;
    }

    public void setCanManage(Boolean canManage) {
        this.canManage = canManage;
    }

    public ReportAccessGrantSource getSource() {
        return source == null ? null : ReportAccessGrantSource.fromId(source);
    }

    public void setSource(ReportAccessGrantSource source) {
        this.source = source == null ? null : source.getId();
    }

    public ReportAccessRequest getRequest() {
        return request;
    }

    public void setRequest(ReportAccessRequest request) {
        this.request = request;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(String revokedBy) {
        this.revokedBy = revokedBy;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
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
