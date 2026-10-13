package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.dto.dq.DqRuleValidationError;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.repository.DqRuleRepository;
import uz.kapitalbank.umida.service.dq.DqRuleValidator;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The form of a {@link DqRuleType#CUSTOM_SQL} rule, which checks the rows returned by a user-written query.
 * The rule-specific settings are not implemented yet: the form only holds the general information.
 */
@Route(value = "dq-rules/custom-sql/:id", layout = MainView.class)
@ViewController(id = "umida_DqRule.customSqlDetail")
@ViewDescriptor(path = "dq-rule-custom-sql-detail-view.xml")
@EditedEntityContainer("dqRuleDc")
public class DqRuleCustomSqlDetailView extends StandardDetailView<DqRule> {

    @Autowired
    private DqRuleRepository repository;

    @Autowired
    private DqRuleValidator ruleValidator;

    @ViewComponent
    private DqRuleGeneralInfoFragment generalInfoFragment;

    @Install(to = "dqRuleDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private Optional<DqRule> loadDelegate(UUID id, FetchPlan fetchPlan) {
        return repository.findById(id, fetchPlan);
    }

    @Subscribe
    public void onInitEntity(final InitEntityEvent<DqRule> event) {
        DqRule rule = event.getEntity();
        rule.setRuleType(DqRuleType.CUSTOM_SQL);
        rule.setRuleConfig("{}");
    }

    @Subscribe
    public void onValidation(final ValidationEvent event) {
        ValidationErrors errors = new ValidationErrors();
        for (DqRuleValidationError error : ruleValidator.validate(getEditedEntity())) {
            errors.add(DqRuleValidationError.FIELD_DUE_DAYS.equals(error.field())
                    ? generalInfoFragment.getDueDaysField()
                    : null, error.message());
        }
        event.addErrors(errors);
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}
