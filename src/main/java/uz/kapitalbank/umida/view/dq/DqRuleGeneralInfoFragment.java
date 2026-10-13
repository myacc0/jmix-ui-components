package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqSeverity;
import uz.kapitalbank.umida.service.dq.DqRuleValidator;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import io.jmix.core.EntityStates;
import io.jmix.core.Messages;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.fragment.Fragment;
import io.jmix.flowui.fragment.FragmentDescriptor;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The "general information" block shared by every rule form: what the rule is, how it is grouped
 * and who resolves its issues. Edits the {@code dqRuleDc} container of the host view.
 */
@FragmentDescriptor("dq-rule-general-info-fragment.xml")
public class DqRuleGeneralInfoFragment extends Fragment<HorizontalLayout> {

    @Autowired
    private EntityStates entityStates;

    @Autowired
    private Messages messages;

    @ViewComponent
    private MessageBundle messageBundle;

    @ViewComponent
    private InstanceContainer<DqRule> dqRuleDc;

    @ViewComponent
    private FormLayout.FormItem codeFormItem;

    @ViewComponent
    private TypedTextField<String> codeField;

    @ViewComponent
    private JmixSelect<DqSeverity> severityField;

    @ViewComponent
    private JmixIntegerField dueDaysField;

    @Subscribe
    public void onReady(final ReadyEvent event) {
        severityField.setItemLabelGenerator(severity -> severity == null ? ""
                : messageBundle.formatMessage("severityItemLabel", messages.getMessage(severity), severity.getDayCost()));

        // the same bounds the validator enforces
        dueDaysField.setMin(DqRuleValidator.MIN_DUE_DAYS);
        dueDaysField.setMax(DqRuleValidator.MAX_DUE_DAYS);
    }

    @Subscribe(target = Target.HOST_CONTROLLER)
    public void onHostReady(final View.ReadyEvent event) {
        // The code is generated on the first save, so a new rule has none to show yet.
        codeFormItem.setVisible(!entityStates.isNew(dqRuleDc.getItem()));
        // generated, never entered: the required marker the binding puts on it would only mislead
        codeField.setRequired(false);
    }

    /** Picking a severity proposes its resolution period, which the user may still correct. */
    @Subscribe(id = "dqRuleDc", target = Target.DATA_CONTAINER)
    public void onDqRuleDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<DqRule> event) {
        if ("severity".equals(event.getProperty()) && event.getValue() instanceof DqSeverity severity) {
            event.getItem().setDueDays(severity.getDayCost());
        }
    }

    /** The input of the resolution period, for attaching a validation error to it. */
    public JmixIntegerField getDueDaysField() {
        return dueDaysField;
    }
}
