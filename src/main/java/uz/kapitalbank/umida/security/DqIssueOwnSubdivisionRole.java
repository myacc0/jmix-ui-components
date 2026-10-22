package uz.kapitalbank.umida.security;

import io.jmix.security.model.RowLevelBiPredicate;
import io.jmix.security.model.RowLevelPolicyAction;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.PredicateRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;
import org.springframework.context.ApplicationContext;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.service.dq.DqIssueService;

/**
 * Narrows the data quality issues to the ones assigned to a subdivision in which the employee linked to
 * the user is currently assigned to a position ({@code DqIssue.assignee}) — the same test as
 * {@link DqIssueService#isInAssigneeSubdivision}. Only the subdivision itself counts, not its parents or
 * children. A user without an employee, or an issue without an assignee, never qualifies.
 * Assigned together with {@link DqIssueAssigneeRole}.
 */
@RowLevelRole(name = "DQ: only the issues of the own subdivision", code = DqIssueOwnSubdivisionRole.CODE)
public interface DqIssueOwnSubdivisionRole {

    String CODE = "dq-issue-own-subdivision";

    /**
     * The employee is looked up by username: the user object of the session may hold
     * {@code User.employee} unfetched. Only a current assignment to an active position counts, the
     * conditions of {@link uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql} written out.
     */
    @JpqlRowLevelPolicy(entityClass = DqIssue.class,
            where = "exists (select a.id from umida_OrgStructureAssignments a, umida_User u" +
                    " where u.username = :current_user_username" +
                    " and a.employee = u.employee and a.position.subdivision = {E}.assignee" +
                    " and a.startDate <= CURRENT_DATE and (a.endDate is null or a.endDate >= CURRENT_DATE)" +
                    " and (a.position.isActive is null or a.position.isActive = 1))")
    void dqIssue();

    /** An issue is changed only by closing it, which is the assignee subdivision's to do. */
    @PredicateRowLevelPolicy(entityClass = DqIssue.class, actions = RowLevelPolicyAction.UPDATE)
    default RowLevelBiPredicate<DqIssue, ApplicationContext> dqIssueUpdate() {
        return (issue, applicationContext) ->
                applicationContext.getBean(DqIssueService.class).isInAssigneeSubdivision(issue);
    }
}
