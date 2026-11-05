package uz.kapitalbank.umida.security;

import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;
import uz.kapitalbank.umida.entity.User;

/**
 * The resource role every LDAP user gets at the login ({@code jmix.ldap.default-roles}). A resource role takes
 * only the resource policies of the interfaces it extends: the row-level ones, {@link ReportViewerRowsRole}
 * among them, are lost there, so that role is given at the login next to this one, by its own code.
 */
@ResourceRole(name = "LDAP minimal role", code = LdapMinimalRole.CODE, scope = SecurityScope.UI)
public interface LdapMinimalRole extends UiMinimalRole, DqViewerRole,
        OrgStructureViewerRole, ReportViewerRole {
    String CODE = "ldap-minimal-role";

    @ViewPolicy(viewIds = "umida_LoginView")
    @SpecificPolicy(resources = {"ui.loginToUi", "ui.showExceptionDetails"})
    void login();

    @MenuPolicy(menuIds = {"main"})
    @ViewPolicy(viewIds = {"umida_MainView"})
    void screens();

    @EntityAttributePolicy(entityClass = User.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.READ)
    void user();
}
