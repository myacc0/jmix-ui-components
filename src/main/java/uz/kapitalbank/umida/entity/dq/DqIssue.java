package uz.kapitalbank.umida.entity.dq;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import uz.kapitalbank.umida.enums.dq.DqIssueClosingReason;
import uz.kapitalbank.umida.enums.dq.DqIssueStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "UMIDA_DQ_ISSUE", indexes = {
        @Index(name = "IDX_UMIDA_DQ_ISSUE_CHECK_RESULT", columnList = "CHECK_RESULT_ID"),
        @Index(name = "IDX_UMIDA_DQ_ISSUE_RULE", columnList = "RULE_ID")
})
@Entity(name = "umida_DqIssue")
public class DqIssue {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "CODE", nullable = false, length = 50)
    @NotNull
    private String code;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "CHECK_RESULT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private DqCheckRunResult checkResult;

    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "RULE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DqRule rule;

    /** An open issue has no closing reason and no closing time; a closed one has both. */
    @NotNull
    @Column(name = "STATUS", nullable = false, length = 40, columnDefinition = "VARCHAR(40) DEFAULT 'open'")
    private String status;

    @Column(name = "CLOSING_REASON", length = 40)
    private String closingReason;

    @Column(name = "DESCRIPTION", length = 1000, columnDefinition = "text")
    private String description;

    @Column(name = "DUE_DATE")
    private LocalDate dueDate;

    @Column(name = "CLOSED_AT", columnDefinition = "TIMESTAMP")
    private OffsetDateTime closedAt;

    @Column(name = "CLOSING_NOTES", columnDefinition = "text")
    private String closingNotes;

    @NotNull
    @Column(name = "CREATED_AT", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", columnDefinition = "TIMESTAMP")
    private OffsetDateTime updatedAt;

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getClosingNotes() {
        return closingNotes;
    }

    public void setClosingNotes(String closingNotes) {
        this.closingNotes = closingNotes;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DqIssueStatus getStatus() {
        return status == null ? null : DqIssueStatus.fromId(status);
    }

    public void setStatus(DqIssueStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public DqIssueClosingReason getClosingReason() {
        return closingReason == null ? null : DqIssueClosingReason.fromId(closingReason);
    }

    public void setClosingReason(DqIssueClosingReason closingReason) {
        this.closingReason = closingReason == null ? null : closingReason.getId();
    }

    public DqRule getRule() {
        return rule;
    }

    public void setRule(DqRule rule) {
        this.rule = rule;
    }

    public DqCheckRunResult getCheckResult() {
        return checkResult;
    }

    public void setCheckResult(DqCheckRunResult checkResult) {
        this.checkResult = checkResult;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}