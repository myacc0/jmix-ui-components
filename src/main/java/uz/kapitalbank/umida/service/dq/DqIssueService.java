package uz.kapitalbank.umida.service.dq;

import io.jmix.core.FetchPlan;
import io.jmix.core.Id;
import io.jmix.core.SaveContext;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.enums.dq.DqCheckResultStatus;
import uz.kapitalbank.umida.enums.dq.DqIssueStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The lifecycle of a {@link DqIssue}.
 * <ul>
 *     <li>A check run opens an issue for a rule that failed, keeps refreshing it while the rule keeps
 *     failing, and closes it as {@link DqIssueStatus#RESOLVED} once the rule passes again.</li>
 *     <li>A user of the subdivision the rule is assigned to closes it by hand as
 *     {@link DqIssueStatus#WONTFIX} or {@link DqIssueStatus#FALSE_POSITIVE}; until the key fields of
 *     the rule change, its failures then open no new issue.</li>
 *     <li>Moving a rule to another data source, schema, table or column closes its open issues as
 *     {@link DqIssueStatus#RULE_DATA_SOURCE_CHANGED}: they describe data the rule no longer checks.</li>
 * </ul>
 * Reads are unconstrained: this is bookkeeping of the check runs and of the issue screens, whatever
 * the current user may browse of rules, users and positions.
 */
@Service
public class DqIssueService {

    public static final String CODE_PREFIX = "DQI-";

    /** The outcomes a user may close an issue with. */
    public static final List<DqIssueStatus> MANUAL_CLOSING_STATUSES =
            List.of(DqIssueStatus.WONTFIX, DqIssueStatus.FALSE_POSITIVE);

    /** Created by Liquibase; the start value here only matters if the sequence is missing. */
    private static final Sequence CODE_SEQUENCE = Sequence.withName("UMIDA_DQ_ISSUE_CODE_SEQ")
            .setStartValue(100)
            .setIncrement(1);

    private final UnconstrainedDataManager dataManager;
    private final Sequences sequences;
    private final CurrentAuthentication currentAuthentication;

    public DqIssueService(UnconstrainedDataManager dataManager,
                          Sequences sequences,
                          CurrentAuthentication currentAuthentication) {
        this.dataManager = dataManager;
        this.sequences = sequences;
        this.currentAuthentication = currentAuthentication;
    }

    /** The next free issue code: {@code DQI-100}, {@code DQI-101}, … */
    public String generateCode() {
        return CODE_PREFIX + sequences.createNextValue(CODE_SEQUENCE);
    }

    /**
     * Fills in what an issue is not asked for: a new one gets its code, creation time and the open
     * status, a stored one the time of the change.
     */
    public void prepareForSave(DqIssue issue, boolean isNew) {
        if (isNew) {
            if (issue.getCode() == null) {
                issue.setCode(generateCode());
            }
            if (issue.getCreatedAt() == null) {
                issue.setCreatedAt(OffsetDateTime.now());
            }
            if (issue.getStatus() == null) {
                issue.setStatus(DqIssueStatus.OPEN);
            }
        } else {
            issue.setUpdatedAt(OffsetDateTime.now());
        }
    }

    /** Records that the issue was just closed. Called before the change is written. */
    public void markClosed(DqIssue issue) {
        issue.setResolvedAt(OffsetDateTime.now());
    }

    // ---------------------------------------------------------------------
    // check runs
    // ---------------------------------------------------------------------

    /**
     * The issues a check result changes, to be saved together with the result:
     * <ul>
     *     <li>a failed result refreshes the open issue of the rule, or opens a new one — unless the
     *     rule failures were dismissed by hand since its key fields last changed;</li>
     *     <li>a passed result resolves the open issues of the rule: the data is fixed;</li>
     *     <li>a skipped one says nothing about the data and changes nothing.</li>
     * </ul>
     *
     * @param result             a result not saved yet; its rule is loaded with {@code _base}
     * @param failureDescription what the issue of a failed result is to say
     */
    public List<DqIssue> issuesFor(DqCheckRunResult result, @Nullable String failureDescription) {
        DqRule rule = result.getRule();
        DqCheckResultStatus status = result.getStatus();
        if (status == DqCheckResultStatus.FAILED) {
            Optional<DqIssue> openIssue = findOpenIssues(rule).stream().findFirst();
            if (openIssue.isEmpty() && isSuppressed(rule)) {
                return List.of();
            }
            DqIssue issue = openIssue.orElseGet(() -> newIssue(rule));
            issue.setCheckResult(result);
            issue.setDescription(failureDescription);
            return List.of(issue);
        }
        if (status == DqCheckResultStatus.PASSED) {
            List<DqIssue> resolved = findOpenIssues(rule);
            resolved.forEach(issue -> close(issue, DqIssueStatus.RESOLVED));
            return resolved;
        }
        return List.of();
    }

    /**
     * Whether the failures of the rule were closed by hand as not worth an issue after its key fields
     * last changed: the latest such closing is compared with {@code DqRule.keyFieldsChangedAt}.
     */
    public boolean isSuppressed(DqRule rule) {
        Optional<DqIssue> lastDismissed = dataManager.load(DqIssue.class)
                .query("select i from umida_DqIssue i" +
                        " where i.rule = :rule and i.status in :statuses and i.resolvedAt is not null" +
                        " order by i.resolvedAt desc")
                .parameter("rule", rule)
                .parameter("statuses", MANUAL_CLOSING_STATUSES.stream().map(DqIssueStatus::getId).toList())
                .fetchPlanProperties("resolvedAt")
                .maxResults(1)
                .optional();
        return lastDismissed.isPresent()
                && (rule.getKeyFieldsChangedAt() == null
                || lastDismissed.get().getResolvedAt().isAfter(rule.getKeyFieldsChangedAt()));
    }

    /** A new open issue of the rule, due {@code DqRule.dueDays} days from today. */
    private DqIssue newIssue(DqRule rule) {
        DqIssue issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setStatus(DqIssueStatus.OPEN);
        issue.setCreatedAt(OffsetDateTime.now());
        if (rule.getDueDays() != null) {
            issue.setDueDate(LocalDate.now().plusDays(rule.getDueDays()));
        }
        return issue;
    }

    // ---------------------------------------------------------------------
    // rule changes
    // ---------------------------------------------------------------------

    /** The open issues of the rule, the newest first. */
    public List<DqIssue> findOpenIssues(DqRule rule) {
        return dataManager.load(DqIssue.class)
                .query("select i from umida_DqIssue i where i.rule = :rule and i.status = :status" +
                        " order by i.createdAt desc")
                .parameter("rule", rule)
                .parameter("status", DqIssueStatus.OPEN.getId())
                .fetchPlan(FetchPlan.BASE)
                .list();
    }

    /**
     * Closes the open issues of a rule whose data source, schema, table or column has changed. Runs
     * within the transaction that saves the rule.
     */
    public void closeOnRuleDataSourceChanged(Id<DqRule> ruleId) {
        DqRule rule = dataManager.getReference(ruleId);
        List<DqIssue> issues = findOpenIssues(rule);
        if (issues.isEmpty()) {
            return;
        }
        SaveContext saveContext = new SaveContext();
        issues.forEach(issue -> saveContext.saving(close(issue, DqIssueStatus.RULE_DATA_SOURCE_CHANGED)));
        dataManager.save(saveContext);
    }

    private DqIssue close(DqIssue issue, DqIssueStatus status) {
        issue.setStatus(status);
        markClosed(issue);
        return issue;
    }

    // ---------------------------------------------------------------------
    // closing by hand
    // ---------------------------------------------------------------------

    /** Whether the current user may close the issue: it is open, and the user is of its rule assignee. */
    public boolean canClose(@Nullable DqIssue issue) {
        return issue != null
                && issue.getStatus() == DqIssueStatus.OPEN
                && isInAssigneeSubdivision(issue);
    }

    /**
     * Whether the employee linked to the current user holds a position in the subdivision the rule of
     * the issue is assigned to ({@code DqRule.assignee}). A user without an employee, such as
     * {@code admin}, or a rule without an assignee, never qualifies.
     */
    public boolean isInAssigneeSubdivision(DqIssue issue) {
        if (issue.getId() == null) {
            return false;
        }
        return dataManager.load(OrgStructurePosition.class)
                .query("select p from umida_OrgStructurePosition p, umida_User u, umida_DqIssue i" +
                        " where i.id = :issueId and u.username = :username" +
                        " and p.subdivision = i.rule.assignee and p.employee = u.employee")
                .parameter("issueId", issue.getId())
                .parameter("username", currentAuthentication.getUser().getUsername())
                .fetchPlanProperties("id")
                .maxResults(1)
                .optional()
                .isPresent();
    }
}
