package uz.kapitalbank.umida.security;

import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;
import uz.kapitalbank.umida.dto.dq.DqIssueTreeItem;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.dq.DqRuleGroup;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;

/**
 * Browsing the data quality issues, their violating rows, and closing an issue by hand. Which issues are
 * seen is narrowed by {@link DqIssueOwnSubdivisionRole}, assigned together with this one: without it
 * every issue is visible. Issues are never created or deleted by hand, only closed.
 */
@ResourceRole(name = "DQ: issues of the subdivision", code = DqIssueAssigneeRole.CODE)
public interface DqIssueAssigneeRole {

    String CODE = "dq-issue-assignee";

    @EntityAttributePolicy(entityClass = DqIssue.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqIssue.class,
            actions = {EntityPolicyAction.READ, EntityPolicyAction.UPDATE})
    void dqIssue();

    /** What the close dialog and the bookkeeping of the closing change. */
    @EntityAttributePolicy(entityClass = DqIssue.class,
            attributes = {"status", "closingReason", "closingNotes", "closedAt", "closedBy", "updatedAt"},
            action = EntityAttributePolicyAction.MODIFY)
    void dqIssueClosing();

    /**
     * The rule columns of the issue screens, which the issue list is also grouped by; the type decides
     * what the issue page shows of the check.
     */
    @EntityAttributePolicy(entityClass = DqRule.class,
            attributes = {"name", "code", "dataSource", "severity", "ruleType", "group", "domain"},
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqRule.class, actions = EntityPolicyAction.READ)
    void dqRule();

    /** {@code DqRule.group}. */
    @EntityAttributePolicy(entityClass = DqRuleGroup.class,
            attributes = "name",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqRuleGroup.class, actions = EntityPolicyAction.READ)
    void dqRuleGroup();

    /** {@code DqRule.domain}. */
    @EntityAttributePolicy(entityClass = DictDataDomain.class,
            attributes = "shortName",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DictDataDomain.class, actions = EntityPolicyAction.READ)
    void dictDataDomain();

    /**
     * The latest failed check of the issue and the query that reads its violating rows, or what a
     * cross-source rule compared, with the data source the violations query runs against.
     */
    @EntityAttributePolicy(entityClass = DqCheckRunResult.class,
            attributes = {"status", "failedRecords", "violationsQuery", "reconciliation", "dataSource", "checkRun"},
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqCheckRunResult.class, actions = EntityPolicyAction.READ)
    void dqCheckRunResult();

    /** The run a result belongs to, referenced by {@code DqCheckRunResult.checkRun}. */
    @EntityPolicy(entityClass = DqCheckRun.class, actions = EntityPolicyAction.READ)
    void dqCheckRun();

    /** The rows of the grouped issue list: a group or an issue under it. */
    @EntityAttributePolicy(entityClass = DqIssueTreeItem.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqIssueTreeItem.class, actions = EntityPolicyAction.READ)
    void dqIssueTreeItem();

    /** {@code DqIssue.assignee}. */
    @EntityAttributePolicy(entityClass = OrgStructureSubdivision.class,
            attributes = "name",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureSubdivision.class, actions = EntityPolicyAction.READ)
    void orgStructureSubdivision();

    /** {@code DqIssue.closedBy}. */
    @EntityAttributePolicy(entityClass = OrgStructureEmployee.class,
            attributes = "fullName",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureEmployee.class, actions = EntityPolicyAction.READ)
    void orgStructureEmployee();

    @ViewPolicy(viewIds = {
            "umida_DqIssue.list",
            "umida_DqIssue.detail",
            "umida_DqIssue.close",
            "umida_DqCheckRunViolationsView",
            "umida_DqCheckRunReconciliationView"
    })
    @MenuPolicy(menuIds = "umida_DqIssue.list")
    void screens();
}
