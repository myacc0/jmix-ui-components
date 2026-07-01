package com.company.demo.view.dqrule;

import com.company.demo.entity.DqRule;
import com.company.demo.repository.DqRuleRepository;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Route(value = "dq-rules/:id", layout = MainView.class)
@ViewController(id = "demo_DqRule.detail")
@ViewDescriptor(path = "dq-rule-detail-view.xml")
@EditedEntityContainer("dqRuleDc")
public class DqRuleDetailView extends StandardDetailView<DqRule> {

    @Autowired
    private DqRuleRepository repository;

    @Install(to = "dqRuleDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private Optional<DqRule> loadDelegate(UUID id, FetchPlan fetchPlan) {
        return repository.findById(id, fetchPlan);
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}