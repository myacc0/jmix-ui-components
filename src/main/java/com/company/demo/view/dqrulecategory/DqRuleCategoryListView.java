package com.company.demo.view.dqrulecategory;

import com.company.demo.entity.DqRuleCategory;
import com.company.demo.repository.DqRuleCategoryRepository;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

@Route(value = "dq-rule-categories", layout = MainView.class)
@ViewController(id = "demo_DqRuleCategory.list")
@ViewDescriptor(path = "dq-rule-category-list-view.xml")
@LookupComponent("dqRuleCategoriesDataGrid")
@DialogMode(width = "64em")
public class DqRuleCategoryListView extends StandardListView<DqRuleCategory> {

    @Autowired
    private DqRuleCategoryRepository repository;

    @Install(to = "dqRuleCategoriesDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<DqRuleCategory> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return repository.findAllSlice(pageable, context).getContent();
    }

    @Install(to = "dqRuleCategoriesDataGrid.removeAction", subject = "delegate")
    private void dqRuleCategoriesDataGridRemoveDelegate(final Collection<DqRuleCategory> collection) {
        repository.deleteAll(collection);
    }

    @Install(to = "pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return repository.count(context);
    }
}