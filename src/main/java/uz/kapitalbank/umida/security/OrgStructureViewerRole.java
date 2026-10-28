package uz.kapitalbank.umida.security;

import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataDomainSteward;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.dict.DictDataProductSteward;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;

/**
 * Read-only access to the org structure section: the org chart of the subdivisions with the employee
 * card, and the structures of the data domains and data products with the asset card. The charts are
 * built by {@link uz.kapitalbank.umida.service.orgstructure.OrgStructureService} and
 * {@link uz.kapitalbank.umida.service.orgstructure.DataStructureService} through the secured data
 * manager, so every entity they load is readable here. Nothing is created, changed or deleted.
 */
@ResourceRole(name = "Org structure: view only", code = OrgStructureViewerRole.CODE)
public interface OrgStructureViewerRole {

    String CODE = "orgstructure-viewer";

    @EntityAttributePolicy(entityClass = OrgStructureSubdivision.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureSubdivision.class, actions = EntityPolicyAction.READ)
    void orgStructureSubdivision();

    @EntityAttributePolicy(entityClass = OrgStructurePosition.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructurePosition.class, actions = EntityPolicyAction.READ)
    void orgStructurePosition();

    @EntityAttributePolicy(entityClass = OrgStructureJobTitle.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureJobTitle.class, actions = EntityPolicyAction.READ)
    void orgStructureJobTitle();

    @EntityAttributePolicy(entityClass = OrgStructureAssignments.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureAssignments.class, actions = EntityPolicyAction.READ)
    void orgStructureAssignments();

    @EntityAttributePolicy(entityClass = OrgStructureEmployee.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = OrgStructureEmployee.class, actions = EntityPolicyAction.READ)
    void orgStructureEmployee();

    @EntityAttributePolicy(entityClass = DictDataDomain.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DictDataDomain.class, actions = EntityPolicyAction.READ)
    void dictDataDomain();

    /** The stewards listed on the asset card of a domain. */
    @EntityAttributePolicy(entityClass = DictDataDomainSteward.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DictDataDomainSteward.class, actions = EntityPolicyAction.READ)
    void dictDataDomainSteward();

    @EntityAttributePolicy(entityClass = DictDataProduct.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DictDataProduct.class, actions = EntityPolicyAction.READ)
    void dictDataProduct();

    /** The stewards listed on the asset card of a data product. */
    @EntityAttributePolicy(entityClass = DictDataProductSteward.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = DictDataProductSteward.class, actions = EntityPolicyAction.READ)
    void dictDataProductSteward();

    /**
     * The three structures of the section, and the cards they open as dialogs on a click on a chart
     * node: the employee card from the org chart, the asset card from the domain and product charts.
     */
    @ViewPolicy(viewIds = {
            "umida_OrgStructureView",
            "umida_EmployeeCardView",
            "umida_DataDomainStructure",
            "umida_DataProductStructure",
            "umida_DataAssetCardView"
    })
    @MenuPolicy(menuIds = {
            "umida_OrgStructureView",
            "umida_DataDomainStructure",
            "umida_DataProductStructure"
    })
    void screens();
}
