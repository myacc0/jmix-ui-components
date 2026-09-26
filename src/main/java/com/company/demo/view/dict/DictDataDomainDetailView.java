package com.company.demo.view.dict;

import com.company.demo.entity.dict.DictDataDomain;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "dict-data-domains/:id", layout = MainView.class)
@ViewController(id = "demo_DictDataDomain.detail")
@ViewDescriptor(path = "dict-data-domain-detail-view.xml")
@EditedEntityContainer("dictDataDomainDc")
public class DictDataDomainDetailView extends StandardDetailView<DictDataDomain> {

    @ViewComponent
    private JmixTabSheet tabSheet;
    @ViewComponent
    private DictDataDomainStewardListFragment stewardsFragment;

    @Autowired
    private EntityStates entityStates;

    @Subscribe
    public void onReady(final ReadyEvent event) {
        updateStewardsTab();
    }

    @Subscribe
    public void onAfterSave(final AfterSaveEvent event) {
        updateStewardsTab();
    }

    /**
     * Stewards reference the domain, so their tab is available only once the domain is saved.
     */
    private void updateStewardsTab() {
        boolean saved = !entityStates.isNew(getEditedEntity());
        tabSheet.getTab(stewardsFragment).setEnabled(saved);
        stewardsFragment.setDomain(saved ? getEditedEntity() : null);
    }
}
