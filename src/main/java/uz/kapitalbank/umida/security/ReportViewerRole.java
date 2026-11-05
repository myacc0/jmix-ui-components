package uz.kapitalbank.umida.security;

import io.jmix.reports.entity.ReportExecution;
import io.jmix.reports.entity.ReportTemplate;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;
import uz.kapitalbank.umida.dto.report.ReportOwnerMemberItem;
import uz.kapitalbank.umida.dto.report.ReportTreeItem;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportAccessGrant;
import uz.kapitalbank.umida.entity.report.ReportAccessRequest;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.entity.report.ReportGroupCustom;
import uz.kapitalbank.umida.entity.report.ReportInputParameterCustom;

/**
 * The reports for everyone who is not an author: the list of all the reports, and what it takes to run a
 * report, to see the history of the runs and to manage the access. <em>Whether</em> the user may run a
 * report, see its history or manage its access is not decided here but by
 * {@link uz.kapitalbank.umida.service.report.ReportAccessService}, from the business owner, the domains
 * and the grants of the report; the views ask it before they offer an action.
 *
 * <p>Assigned together with {@link ReportViewerRowsRole}, which narrows the history, the grants and the
 * requests down to the rows of the user. Nothing is changed through this role but the runs the add-on
 * records: the requests and the grants are written by the service.
 *
 * <p>Takes from {@code ReportsRunRole} of the add-on what running needs — the dialog of the parameters, the
 * table output, the templates, the records of the runs — but not its view {@code report_ReportRunView},
 * which lists the reports by the roles of the add-on and would bypass the rules of the access. The
 * structure of a report ({@code xml}, bands, data sets) is not shown: the role has no detail view of the
 * report and no attribute policy on it.
 */
@ResourceRole(name = "Reports: view the list, run with access", code = ReportViewerRole.CODE)
public interface ReportViewerRole {

    String CODE = "report-viewer";

    @EntityAttributePolicy(entityClass = ReportCustom.class, attributes = {
            "name", "localeNames", "code", "description", "reportType", "group", "updateTs", "system",
            "domain", "secondaryDomains", "dataProducts", "businessOwner",
            "personalData", "confidentialData", "commercialData", "bankData"
    }, action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ReportCustom.class, actions = EntityPolicyAction.READ)
    void report();

    /** The groups of the list: its tree, its tiles and their folders. */
    @EntityAttributePolicy(entityClass = ReportGroupCustom.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ReportGroupCustom.class, actions = EntityPolicyAction.READ)
    void reportGroup();

    @EntityAttributePolicy(entityClass = ReportTreeItem.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ReportTreeItem.class, actions = EntityPolicyAction.READ)
    void reportTreeItem();

    /** The templates a run picks the output from, as {@code ReportsRunRole} reads them. */
    @EntityAttributePolicy(entityClass = ReportTemplate.class, attributes = {
            "code", "name", "customDefinition", "custom", "alterable", "reportOutputType"
    }, action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ReportTemplate.class, actions = EntityPolicyAction.READ)
    void reportTemplate();

    /** The parameters asked for before a run. */
    @EntityAttributePolicy(entityClass = ReportInputParameterCustom.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ReportInputParameterCustom.class, actions = EntityPolicyAction.READ)
    void reportInputParameter();

    /**
     * The records of the runs: written by the add-on on every run of the user, read in the history, which
     * {@link ReportViewerRowsRole} narrows down. No output documents are stored.
     */
    @EntityAttributePolicy(entityClass = ReportExecution.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ReportExecution.class, actions = {
            EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE
    })
    void reportExecution();

    /** The captions of the classification and of the business owner in the list. */
    @EntityAttributePolicy(entityClass = DictDataDomain.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DictDataDomain.class, actions = EntityPolicyAction.READ)
    void dictDataDomain();

    @EntityAttributePolicy(entityClass = DictDataProduct.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DictDataProduct.class, actions = EntityPolicyAction.READ)
    void dictDataProduct();

    @EntityAttributePolicy(entityClass = OrgStructureSubdivision.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureSubdivision.class, actions = EntityPolicyAction.READ)
    void orgStructureSubdivision();

    /** The position of the employee of a grant or a request, its job title and subdivision in the lists. */
    @EntityAttributePolicy(entityClass = OrgStructurePosition.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructurePosition.class, actions = EntityPolicyAction.READ)
    void orgStructurePosition();

    @EntityAttributePolicy(entityClass = OrgStructureJobTitle.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureJobTitle.class, actions = EntityPolicyAction.READ)
    void orgStructureJobTitle();

    /** The employees of the grants and the requests, and those the owner picks to give access to. */
    @EntityAttributePolicy(entityClass = OrgStructureEmployee.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureEmployee.class, actions = EntityPolicyAction.READ)
    void orgStructureEmployee();

    /** Read only: the service gives, revokes and decides; {@link ReportViewerRowsRole} narrows the rows. */
    @EntityAttributePolicy(entityClass = ReportAccessGrant.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ReportAccessGrant.class, actions = EntityPolicyAction.READ)
    void reportAccessGrant();

    @EntityAttributePolicy(entityClass = ReportAccessRequest.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ReportAccessRequest.class, actions = EntityPolicyAction.READ)
    void reportAccessRequest();

    /** The employees of a subdivision to give access to, in the dialog of the access management. */
    @EntityAttributePolicy(entityClass = ReportOwnerMemberItem.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ReportOwnerMemberItem.class, actions = EntityPolicyAction.READ)
    void reportOwnerMemberItem();

    /**
     * The list of the reports; the dialog of the parameters and the table output of a run; the history of
     * the runs, opened as a dialog from the list. The access management and its dialog of a grant: open to
     * everyone, but the menu item is shown, and the view shows anything, only to those who manage the access
     * to a report — see {@link uz.kapitalbank.umida.view.main.MainView}.
     */
    @ViewPolicy(viewIds = {
            "umida_ReportCustom.list",
            "report_InputParametersDialogView",
            "report_ReportTableView",
            "report_ReportExecution.list",
            "umida_ReportAccessManagementView",
            "umida_ReportAccessGrantView"
    })
    @MenuPolicy(menuIds = {"umida_ReportCustom.list", "reportAccessManagement"})
    void views();
}
