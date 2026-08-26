package com.company.demo.dq;

import com.company.demo.DemoApplication;
import com.company.demo.entity.dq.DqDataDomain;
import com.company.demo.entity.dq.DqRule;
import com.company.demo.entity.User;
import com.company.demo.enums.DqDimension;
import com.company.demo.enums.DqRuleType;
import com.company.demo.enums.DqSeverity;
import com.company.demo.service.DqRuleValidator;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.company.demo.view.dqrule.DqRuleDetailView;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.textfield.JmixNumberField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The sample size accepted by the rule editor. The bounds are enforced twice — the input refuses to
 * offer a value outside them, and the validator rejects one that arrives anyway — so both are
 * checked here against the range the validator publishes.
 */
@UiTest
@SpringBootTest(classes = {DemoApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqRuleDetailUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    private DqDataDomain domain;
    private DqRule rule;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        domain = dataManager.create(DqDataDomain.class);
        domain.setCode("dq-sample-" + suffix);
        domain.setShortName("DQ SMP " + suffix);
        domain.setName("DQ sample size UI test domain " + suffix);
        domain.setCreatedAt(LocalDateTime.now());
        domain = dataManager.save(domain);

        rule = dataManager.create(DqRule.class);
        rule.setName("dq sample size ui test rule " + suffix);
        rule.setDataSource("main");
        rule.setTableName("demo_dq_data_domain");
        rule.setColumnName("name");
        rule.setDimension(DqDimension.COMPLETENESS);
        rule.setRuleType(DqRuleType.NOT_NULL);
        rule.setRuleConfig("{\"sampleSize\": 10}");
        rule.setSeverity(DqSeverity.MEDIUM);
        rule.setActive(true);
        rule.setDomain(domain);
        rule = dataManager.save(rule);
    }

    @Test
    void sampleSizeFieldIsBoundedAndOutOfRangeValuesBlockTheSave() {
        DqRuleDetailView detailView = openDetailView();

        JmixNumberField sampleSizeField = UiTestUtils.getComponent(detailView, "sampleSizeField");
        assertEquals(DqRuleValidator.MIN_SAMPLE_SIZE, sampleSizeField.getMin(),
                "the input refuses anything below the accepted range");
        assertEquals(DqRuleValidator.MAX_SAMPLE_SIZE, sampleSizeField.getMax(),
                "the input refuses anything above the accepted range");

        assertTrue(UiTestUtils.validateView(detailView).isEmpty(), "the stored sample size is valid");

        sampleSizeField.setValue(101d);
        assertFalse(UiTestUtils.validateView(detailView).isEmpty(), "101 is past the top of the range");

        sampleSizeField.setValue(0d);
        assertFalse(UiTestUtils.validateView(detailView).isEmpty(), "0 is below the bottom of the range");

        sampleSizeField.setValue(100d);
        assertTrue(UiTestUtils.validateView(detailView).isEmpty(), "100 is the top of the range");

        sampleSizeField.setValue(1d);
        assertTrue(UiTestUtils.validateView(detailView).isEmpty(), "1 is the bottom of the range");
    }

    /** The form no longer asks for an owner; saving stamps the session user on an unowned rule. */
    @Test
    void savingAnUnownedRuleGivesItToTheCurrentUser() {
        assertNull(rule.getOwner(), "the rule starts without an owner");

        DqRuleDetailView detailView = openDetailView();
        assertThrows(IllegalArgumentException.class,
                () -> UiTestUtils.getComponent(detailView, "ownerField"),
                "the owner is not part of the form any more");

        JmixButton saveAndCloseButton = UiTestUtils.getComponent(detailView, "saveAndCloseButton");
        saveAndCloseButton.click();

        DqRule stored = dataManager.load(DqRule.class)
                .id(rule.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.BASE))
                .one();
        assertNotNull(stored.getOwner(), "saving assigns an owner");
        assertEquals("admin", stored.getOwner().getUsername());
    }

    /** An owner already on the rule is left alone: editing does not transfer it. */
    @Test
    void savingAnOwnedRuleKeepsItsOwner() {
        User otherOwner = dataManager.load(User.class)
                .query("select u from demo_User u where u.username <> :username")
                .parameter("username", "admin")
                .fetchPlan(FetchPlan.BASE)
                .maxResults(1)
                .optional()
                .orElse(null);
        assumeTrue(otherOwner != null, "needs a second user to prove the owner is not overwritten");

        rule.setOwner(otherOwner);
        rule = dataManager.save(rule);

        DqRuleDetailView detailView = openDetailView();
        JmixButton saveAndCloseButton = UiTestUtils.getComponent(detailView, "saveAndCloseButton");
        saveAndCloseButton.click();

        DqRule stored = dataManager.load(DqRule.class)
                .id(rule.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.BASE))
                .one();
        assertEquals(otherOwner.getUsername(), stored.getOwner().getUsername());
    }

    /** A rule whose config never mentioned a sample size still opens the editor with one. */
    @Test
    void sampleSizeFallsBackToTheDefaultWhenTheConfigHasNone() {
        rule.setRuleConfig("{}");
        rule = dataManager.save(rule);

        DqRuleDetailView detailView = openDetailView();

        JmixNumberField sampleSizeField = UiTestUtils.getComponent(detailView, "sampleSizeField");
        assertEquals(DqRuleValidator.DEFAULT_SAMPLE_SIZE, sampleSizeField.getValue(),
                "the editor offers the default sample size");
        assertTrue(UiTestUtils.validateView(detailView).isEmpty(), "the default is inside the accepted range");
    }

    private DqRuleDetailView openDetailView() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqRule.class)
                .editEntity(rule)
                .withViewClass(DqRuleDetailView.class)
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    @AfterEach
    void tearDown() {
        // the view saves through its own data context, so the instance held here may be a version behind
        dataManager.load(DqRule.class).id(rule.getId()).optional().ifPresent(dataManager::remove);
        dataManager.remove(domain);
    }
}
