package com.company.demo.view.dqrule;

import com.company.demo.component.DqBadges;
import com.company.demo.entity.DqRule;
import com.company.demo.repository.DqRuleRepository;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

@Route(value = "dq-rules", layout = MainView.class)
@ViewController(id = "demo_DqRule.list")
@ViewDescriptor(path = "dq-rule-list-view.xml")
@LookupComponent("dqRulesDataGrid")
@DialogMode(width = "64em")
public class DqRuleListView extends StandardListView<DqRule> {

    @Autowired
    private DqRuleRepository repository;

    @Autowired
    private DqBadges dqBadges;

    @Supply(to = "dqRulesDataGrid.dimension", subject = "renderer")
    private Renderer<DqRule> dqRulesDataGridDimensionRenderer() {
        return dqBadges.renderer(DqBadges.DIMENSION, DqRule::getDimension);
    }

    @Supply(to = "dqRulesDataGrid.severity", subject = "renderer")
    private Renderer<DqRule> dqRulesDataGridSeverityRenderer() {
        return dqBadges.renderer(DqBadges.SEVERITY, DqRule::getSeverity);
    }

    @Install(to = "dqRulesDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<DqRule> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return repository.findAllSlice(pageable, context).getContent();
    }

    @Install(to = "dqRulesDataGrid.removeAction", subject = "delegate")
    private void dqRulesDataGridRemoveDelegate(final Collection<DqRule> collection) {
        repository.deleteAll(collection);
    }

    @Install(to = "pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return repository.count(context);
    }
}