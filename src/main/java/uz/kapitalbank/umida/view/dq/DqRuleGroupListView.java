package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.entity.dq.DqRuleGroup;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.router.Route;
import io.jmix.core.querycondition.Condition;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "dq-rule-groups", layout = MainView.class)
@ViewController(id = "umida_DqRuleGroup.list")
@ViewDescriptor(path = "dq-rule-group-list-view.xml")
@LookupComponent("dqRuleGroupsDataGrid")
@DialogMode(width = "64em")
public class DqRuleGroupListView extends StandardListView<DqRuleGroup> {

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @ViewComponent
    private CollectionLoader<DqRuleGroup> dqRuleGroupsDl;

    /**
     * The "mine" filter: the groups created by the current user. It carries no value while the
     * checkbox is clear, and the loader then skips it.
     */
    private final PropertyCondition mineCondition = PropertyCondition.equal("createdBy", null).skipNullOrEmpty();

    /** Adds the "mine" filter next to the conditions of the column header filters, which share the root condition. */
    @Subscribe
    public void onInit(final InitEvent event) {
        Condition root = dqRuleGroupsDl.getCondition();
        LogicalCondition and = root instanceof LogicalCondition logical ? logical : LogicalCondition.and();
        if (root != null && root != and) {
            and.add(root);
        }
        and.add(mineCondition);
        dqRuleGroupsDl.setCondition(and);
    }

    @Subscribe("mineField")
    public void onMineFieldValueChange(final AbstractField.ComponentValueChangeEvent<JmixCheckbox, Boolean> event) {
        mineCondition.setParameterValue(Boolean.TRUE.equals(event.getValue())
                ? currentAuthentication.getUser().getUsername()
                : null);
        dqRuleGroupsDl.load();
    }
}
