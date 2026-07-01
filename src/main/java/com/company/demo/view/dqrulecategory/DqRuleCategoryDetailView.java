package com.company.demo.view.dqrulecategory;

import com.company.demo.entity.DqRuleCategory;
import com.company.demo.repository.DqRuleCategoryRepository;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Route(value = "dq-rule-categories/:id", layout = MainView.class)
@ViewController(id = "demo_DqRuleCategory.detail")
@ViewDescriptor(path = "dq-rule-category-detail-view.xml")
@EditedEntityContainer("dqRuleCategoryDc")
public class DqRuleCategoryDetailView extends StandardDetailView<DqRuleCategory> {

    @Autowired
    private DqRuleCategoryRepository repository;

    @Install(to = "dqRuleCategoryDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private Optional<DqRuleCategory> loadDelegate(UUID id, FetchPlan fetchPlan) {
        return repository.findById(id, fetchPlan);
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}