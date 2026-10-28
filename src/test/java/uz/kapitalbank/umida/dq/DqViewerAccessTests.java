package uz.kapitalbank.umida.dq;

import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Metadata;
import io.jmix.core.accesscontext.CrudEntityContext;
import io.jmix.core.accesscontext.EntityAttributeContext;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.accesscontext.UiShowViewContext;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqCheck;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqMetricsHistory;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.dq.DqRuleGroup;
import uz.kapitalbank.umida.enums.dq.DqIssueStatus;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.security.DqViewerRole;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A user with {@link DqViewerRole} reads everything of the data quality module and changes nothing.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqViewerAccessTests {

    private static final List<Class<?>> DQ_ENTITIES = List.of(DqRuleGroup.class, DqRule.class, DqCheck.class,
            DqCheckRun.class, DqCheckRunResult.class, DqIssue.class, DqMetricsHistory.class);

    @Autowired
    DataManager dataManager;

    @Autowired
    AccessManager accessManager;

    @Autowired
    Metadata metadata;

    @Autowired
    SystemAuthenticator systemAuthenticator;

    private DqTestData testData;
    private DqRule rule;
    private DqIssue issue;
    private User viewer;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        DictDataDomain domain = testData.createDomain();
        rule = testData.createRule(domain, "viewer access", DqRuleType.NOT_NULL, "short_name", "{}");
        issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setStatus(DqIssueStatus.OPEN);
        issue = testData.track(dataManager.save(issue));
        viewer = createViewer();
    }

    @Test
    void aViewerReadsTheRulesAndIssues() {
        DqIssue loaded = systemAuthenticator.withUser(viewer.getUsername(),
                () -> dataManager.load(DqIssue.class).id(issue.getId())
                        .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                                .add("rule", r -> r.addFetchPlan(FetchPlan.BASE)
                                        .add("domain", FetchPlan.INSTANCE_NAME)))
                        .one());

        assertEquals(rule.getId(), loaded.getRule().getId());
        assertEquals(rule.getName(), loaded.getRule().getName());
        assertNotNull(loaded.getRule().getDomain().getShortName(), "the domain of the rule is readable");
    }

    @Test
    void aViewerOnlyReadsTheEntitiesOfTheModule() {
        systemAuthenticator.runWithUser(viewer.getUsername(), () -> {
            for (Class<?> entityClass : DQ_ENTITIES) {
                CrudEntityContext context = new CrudEntityContext(metadata.getClass(entityClass));
                accessManager.applyRegisteredConstraints(context);
                String name = entityClass.getSimpleName();

                assertTrue(context.isReadPermitted(), name + " is readable");
                assertFalse(context.isCreatePermitted(), name + " is not creatable");
                assertFalse(context.isUpdatePermitted(), name + " is not updatable");
                assertFalse(context.isDeletePermitted(), name + " is not deletable");
            }

            EntityAttributeContext nameContext = new EntityAttributeContext(metadata.getClass(DqRule.class), "name");
            accessManager.applyRegisteredConstraints(nameContext);
            assertTrue(nameContext.canView());
            assertFalse(nameContext.canModify(), "the fields of a rule are read-only");
        });
    }

    @Test
    void aViewerCannotSaveARule() {
        DqRule loaded = dataManager.load(DqRule.class).id(rule.getId()).one();
        loaded.setName("changed by viewer");

        assertThrows(AccessDeniedException.class,
                () -> systemAuthenticator.runWithUser(viewer.getUsername(), () -> dataManager.save(loaded)));
        assertEquals(rule.getName(), dataManager.load(DqRule.class).id(rule.getId()).one().getName());
    }

    @Test
    void aViewerOpensTheScreensOfTheModuleButNotTheChangingOnes() {
        systemAuthenticator.runWithUser(viewer.getUsername(), () -> {
            for (String viewId : List.of("umida_DqRuleGroup.list", "umida_DqRuleGroup.detail",
                    "umida_DqRule.list", "umida_DqRule.detail", "umida_DqRule.customSqlDetail",
                    "umida_DqRule.crossSourceDetail", "umida_DqSqlEditorView", "umida_DqCheckRun.list",
                    "umida_DqCheckRun.detail", "umida_DqCheck.detail", "umida_DqCheckRunViolationsView",
                    "umida_DqCheckRunReconciliationView", "umida_DqIssue.list", "umida_DqIssue.detail")) {
                assertTrue(canOpen(viewId), viewId + " opens");
            }
            assertFalse(canOpen("umida_DqIssue.close"), "an issue is not closed");
            assertFalse(canOpen("umida_DqRule.lookup"), "rules are not picked into a check");
        });
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private boolean canOpen(String viewId) {
        UiShowViewContext context = new UiShowViewContext(viewId);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    private User createViewer() {
        User user = dataManager.create(User.class);
        user.setUsername("dq-viewer-test-" + UUID.randomUUID());
        user.setFirstName("Dq");
        user.setLastName("Viewer");
        user = testData.track(dataManager.save(user));

        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(user.getUsername());
        assignment.setRoleCode(DqViewerRole.CODE);
        assignment.setRoleType(RoleAssignmentRoleType.RESOURCE);
        testData.track(dataManager.save(assignment));
        return user;
    }

    @AfterEach
    void tearDown() {
        testData.cleanup();
    }
}
