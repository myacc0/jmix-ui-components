package uz.kapitalbank.umida.security;

import io.jmix.reportsflowui.role.ReportsFullAccessRole;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;
import uz.kapitalbank.umida.entity.dict.DictDataDomainSteward;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.entity.report.ReportGroupCustom;
import uz.kapitalbank.umida.entity.report.ReportInputParameterCustom;

/**
 * The authors of the reports: the subdivision that creates and changes the reports, runs all of them and
 * manages the access to every one. {@code reports-full-access} of the add-on with the views of the
 * application around it — the list, the editor and the groups of the reports — and what
 * {@link ReportViewerRole} reads besides.
 *
 * <p>Being an author is being allowed to change the reports: this is what
 * {@link uz.kapitalbank.umida.service.report.ReportAccessService#isAuthor} asks. An author needs no
 * {@link ReportViewerRowsRole}, and is not narrowed down by it either.
 */
@ResourceRole(name = "Reports: author", code = ReportAuthorRole.CODE)
public interface ReportAuthorRole extends ReportsFullAccessRole, ReportViewerRole {

    String CODE = "report-author";

    @EntityAttributePolicy(entityClass = ReportCustom.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ReportCustom.class, actions = EntityPolicyAction.ALL)
    void authorReport();

    @EntityAttributePolicy(entityClass = ReportGroupCustom.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ReportGroupCustom.class, actions = EntityPolicyAction.ALL)
    void authorReportGroup();

    @EntityAttributePolicy(entityClass = ReportInputParameterCustom.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ReportInputParameterCustom.class, actions = EntityPolicyAction.ALL)
    void authorReportInputParameter();

    /** The domains are picked from their list in the editor of a report, which shows the stewards too. */
    @EntityAttributePolicy(entityClass = DictDataDomainSteward.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DictDataDomainSteward.class, actions = EntityPolicyAction.READ)
    void authorDictDataDomainSteward();

    @ViewPolicy(viewIds = {
            "umida_ReportCustom.detail",
            "umida_ReportGroupCustom.list",
            "umida_ReportGroupCustom.detail",
            "umida_DictDataDomain.list",
            // the business owner of a report is picked from the list of the subdivisions
            "umida_OrgStructureSubdivision.list"
    })
    @MenuPolicy(menuIds = "umida_ReportGroupCustom.list")
    void authorViews();
}
