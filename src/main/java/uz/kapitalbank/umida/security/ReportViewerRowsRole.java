package uz.kapitalbank.umida.security;

import io.jmix.reports.entity.ReportExecution;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;
import uz.kapitalbank.umida.entity.report.ReportAccessGrant;
import uz.kapitalbank.umida.entity.report.ReportAccessRequest;

/**
 * Narrows what {@link ReportViewerRole} reads down to the rows of the user:
 * <ul>
 *     <li>the <b>runs</b>: the user's own, and every run of the reports the user manages;</li>
 *     <li>the <b>grants</b>: those of the reports the user manages;</li>
 *     <li>the <b>requests</b>: the user's own, and those for the reports the user manages.</li>
 * </ul>
 * The user <em>manages</em> a report as the current head of its active business owner, or as a delegate —
 * a grant in force with {@code canManage} — while the owner is active: the rules of
 * {@link uz.kapitalbank.umida.service.report.ReportAccessService#canManageAccess} written out in JPQL, the
 * conditions of {@link uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql} included, since an
 * annotation needs a constant. Whether the user may open the history of a report at all is the service's
 * to say, see {@code ReportAccessService#canViewHistory}.
 *
 * <p>An author sees every row, should the role be assigned to an author too: a user with the author role
 * ({@link ReportAuthorRole}, {@code reports-full-access} of the add-on, or full access) assigned in the
 * application passes every policy here.
 */
@RowLevelRole(name = "Reports: only the own runs, grants and requests", code = ReportViewerRowsRole.CODE)
public interface ReportViewerRowsRole {

    String CODE = "report-viewer-rows";

    /** The current user is an author by an assignment of the application. */
    String AUTHOR = "exists (select ra.id from sec_RoleAssignmentEntity ra" +
            " where ra.username = :current_user_username" +
            " and ra.roleCode in ('" + ReportAuthorRole.CODE + "', 'reports-full-access', '" + FullAccessRole.CODE + "'))";

    /**
     * The current user manages the report of the row ({@code {E}.report}): heads its active owner, or is a
     * delegate while the owner is active. The report goes by id: {@code ReportExecution.report} is typed as
     * the report of the add-on, which has no business owner.
     */
    String MANAGES_REPORT = "(exists (select r.id from umida_ReportCustom r, umida_User u, umida_OrgStructureAssignments a" +
            " where r.id = {E}.report.id and u.username = :current_user_username" +
            " and a.employee = u.employee and a.position.subdivision = r.businessOwner" +
            " and a.position.isheadofsubdivision = 1" +
            " and (a.position.isActive is null or a.position.isActive = 1)" +
            " and a.startDate <= CURRENT_DATE and (a.endDate is null or a.endDate >= CURRENT_DATE)" +
            " and (r.businessOwner.isActive is null or r.businessOwner.isActive = 1))" +
            " or exists (select g.id from umida_ReportAccessGrant g, umida_User u" +
            " where g.report.id = {E}.report.id and u.username = :current_user_username" +
            " and g.employee = u.employee and g.canManage = true and g.revokedAt is null" +
            " and g.validFrom <= CURRENT_DATE and (g.validTo is null or g.validTo >= CURRENT_DATE)" +
            " and (g.report.businessOwner.isActive is null or g.report.businessOwner.isActive = 1)))";

    @JpqlRowLevelPolicy(entityClass = ReportExecution.class,
            where = "({E}.username = :current_user_username or " + MANAGES_REPORT + " or " + AUTHOR + ")")
    void reportExecution();

    @JpqlRowLevelPolicy(entityClass = ReportAccessGrant.class,
            where = "(" + MANAGES_REPORT + " or " + AUTHOR + ")")
    void reportAccessGrant();

    @JpqlRowLevelPolicy(entityClass = ReportAccessRequest.class,
            where = "(exists (select u.id from umida_User u where u.username = :current_user_username and u.employee = {E}.employee)" +
                    " or " + MANAGES_REPORT + " or " + AUTHOR + ")")
    void reportAccessRequest();
}
