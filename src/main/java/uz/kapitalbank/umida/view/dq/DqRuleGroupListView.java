package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.entity.dq.DqRuleGroup;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "dq-rule-groups", layout = MainView.class)
@ViewController(id = "umida_DqRuleGroup.list")
@ViewDescriptor(path = "dq-rule-group-list-view.xml")
@LookupComponent("dqRuleGroupsDataGrid")
@DialogMode(width = "64em")
public class DqRuleGroupListView extends StandardListView<DqRuleGroup> {
}