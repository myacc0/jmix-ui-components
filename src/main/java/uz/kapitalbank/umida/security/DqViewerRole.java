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
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.dq.DqCheck;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqMetricsHistory;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.dq.DqRuleGroup;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;

/**
 * Read-only access to the whole data quality module: rule groups, rules, checks, their runs and results,
 * the violating rows, and the issues. Nothing is created, changed or deleted: the forms open read-only,
 * and neither a check is run nor an issue closed.
 */
@ResourceRole(name = "DQ: view only", code = DqViewerRole.CODE)
public interface DqViewerRole {

    String CODE = "dq-viewer";

    @EntityAttributePolicy(entityClass = DqRuleGroup.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqRuleGroup.class, actions = EntityPolicyAction.READ)
    void dqRuleGroup();

    @EntityAttributePolicy(entityClass = DqRule.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqRule.class, actions = EntityPolicyAction.READ)
    void dqRule();

    @EntityAttributePolicy(entityClass = DqCheck.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqCheck.class, actions = EntityPolicyAction.READ)
    void dqCheck();

    @EntityAttributePolicy(entityClass = DqCheckRun.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqCheckRun.class, actions = EntityPolicyAction.READ)
    void dqCheckRun();

    @EntityAttributePolicy(entityClass = DqCheckRunResult.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqCheckRunResult.class, actions = EntityPolicyAction.READ)
    void dqCheckRunResult();

    @EntityAttributePolicy(entityClass = DqIssue.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqIssue.class, actions = EntityPolicyAction.READ)
    void dqIssue();

    @EntityAttributePolicy(entityClass = DqMetricsHistory.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqMetricsHistory.class, actions = EntityPolicyAction.READ)
    void dqMetricsHistory();

    /** The rows of the grouped issue list: a group or an issue under it. */
    @EntityAttributePolicy(entityClass = DqIssueTreeItem.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DqIssueTreeItem.class, actions = EntityPolicyAction.READ)
    void dqIssueTreeItem();

    /** {@code DqRule.domain}. */
    @EntityAttributePolicy(entityClass = DictDataDomain.class, attributes = "shortName", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DictDataDomain.class, actions = EntityPolicyAction.READ)
    void dictDataDomain();

    /** {@code DqRule.dataProduct}. */
    @EntityAttributePolicy(entityClass = DictDataProduct.class, attributes = "shortName", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DictDataProduct.class, actions = EntityPolicyAction.READ)
    void dictDataProduct();

    /** {@code DqRule.assignee} and {@code DqIssue.assignee}. */
    @EntityAttributePolicy(entityClass = OrgStructureSubdivision.class, attributes = "name", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureSubdivision.class, actions = EntityPolicyAction.READ)
    void orgStructureSubdivision();

    /** {@code DqCheckRun.triggeredBy} and {@code DqIssue.closedBy}. */
    @EntityAttributePolicy(entityClass = OrgStructureEmployee.class, attributes = "fullName", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureEmployee.class, actions = EntityPolicyAction.READ)
    void orgStructureEmployee();

    /**
     * Every screen of the module except the ones that only change data: closing an issue
     * ({@code umida_DqIssue.close}) and picking rules into a check ({@code umida_DqRule.lookup}).
     * The SQL editor is the full-screen view of a rule's query.
     */
    @ViewPolicy(viewIds = {
            "umida_DqRuleGroup.list",
            "umida_DqRuleGroup.detail",
            "umida_DqRule.list",
            "umida_DqRule.detail",
            "umida_DqRule.customSqlDetail",
            "umida_DqRule.crossSourceDetail",
            "umida_DqSqlEditorView",
            "umida_DqCheckRun.list",
            "umida_DqCheckRun.detail",
            "umida_DqCheck.detail",
            "umida_DqCheckRunViolationsView",
            "umida_DqCheckRunReconciliationView",
            "umida_DqIssue.list",
            "umida_DqIssue.detail"
    })
    @MenuPolicy(menuIds = {
            "umida_DqRuleGroup.list",
            "umida_DqRule.list",
            "umida_DqCheckRun.list",
            "umida_DqIssue.list"
    })
    void screens();
}
