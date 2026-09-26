package com.company.demo.view.dict;

import com.company.demo.component.i18n.LocalizedColumns;
import com.company.demo.entity.dict.DictDataDomain;
import com.company.demo.entity.dict.DictDataDomainSteward;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.fragment.Fragment;
import io.jmix.flowui.fragment.FragmentDescriptor;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.Install;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * List of the data stewards of one domain, embedded as a tab into {@link DictDataDomainDetailView}.
 * Call {@link #setDomain(DictDataDomain)} with a saved domain to load its stewards;
 * the stewards are created and edited in {@link DictDataDomainStewardDetailView} opened as a dialog.
 */
@FragmentDescriptor("dict-data-domain-steward-list-fragment.xml")
public class DictDataDomainStewardListFragment extends Fragment<VerticalLayout> {

    @ViewComponent
    private DataGrid<DictDataDomainSteward> stewardsDataGrid;
    @ViewComponent
    private CollectionContainer<DictDataDomainSteward> stewardsDc;
    @ViewComponent
    private CollectionLoader<DictDataDomainSteward> stewardsDl;

    @Autowired
    private LocalizedColumns localizedColumns;

    private DictDataDomain domain;

    @Subscribe
    public void onReady(final ReadyEvent event) {
        localizedColumns.showCurrentLanguage(stewardsDataGrid);
    }

    public void setDomain(DictDataDomain domain) {
        this.domain = domain;
        if (domain == null) {
            stewardsDc.getMutableItems().clear();
            return;
        }
        stewardsDl.setParameter("domain", domain);
        stewardsDl.load();
    }

    @Install(to = "stewardsDataGrid.createAction", subject = "initializer")
    private void stewardsDataGridCreateActionInitializer(final DictDataDomainSteward steward) {
        steward.setDomain(domain);
    }
}
