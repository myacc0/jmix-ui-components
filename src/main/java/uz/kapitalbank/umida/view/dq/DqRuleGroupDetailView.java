package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.entity.dq.DqRuleGroup;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "dq-rule-groups/:id", layout = MainView.class)
@ViewController(id = "umida_DqRuleGroup.detail")
@ViewDescriptor(path = "dq-rule-group-detail-view.xml")
@EditedEntityContainer("dqRuleGroupDc")
public class DqRuleGroupDetailView extends StandardDetailView<DqRuleGroup> {
}