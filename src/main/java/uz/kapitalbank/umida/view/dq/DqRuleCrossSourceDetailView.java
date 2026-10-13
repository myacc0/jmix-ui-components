package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import org.springframework.lang.Nullable;

/**
 * The form of a {@link DqRuleType#CROSS_SOURCE_AGGREGATED} rule, which compares aggregates of two data sources.
 * The rule-specific settings are not implemented yet: the form only holds the general information.
 */
@Route(value = "dq-rules/cross-source/:id", layout = MainView.class)
@ViewController(id = "umida_DqRule.crossSourceDetail")
@ViewDescriptor(path = "dq-rule-cross-source-detail-view.xml")
@EditedEntityContainer("dqRuleDc")
public class DqRuleCrossSourceDetailView extends AbstractDqRuleDetailView {

    @Subscribe
    public void onInitEntity(final InitEntityEvent<DqRule> event) {
        DqRule rule = event.getEntity();
        rule.setRuleType(DqRuleType.CROSS_SOURCE_AGGREGATED);
        rule.setRuleConfig("{}");
    }

    @Nullable
    @Override
    protected Component resolveComponent(String field) {
        return null;
    }
}
