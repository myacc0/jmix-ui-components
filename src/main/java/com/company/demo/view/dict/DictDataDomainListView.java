package com.company.demo.view.dict;

import com.company.demo.component.i18n.LocalizedColumns;
import com.company.demo.entity.dict.DictDataDomain;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "dict-data-domains", layout = MainView.class)
@ViewController(id = "demo_DictDataDomain.list")
@ViewDescriptor(path = "dict-data-domain-list-view.xml")
@LookupComponent("dictDataDomainsDataGrid")
@DialogMode(width = "64em")
public class DictDataDomainListView extends StandardListView<DictDataDomain> {

    @ViewComponent
    private TreeDataGrid<DictDataDomain> dictDataDomainsDataGrid;

    @Autowired
    private LocalizedColumns localizedColumns;

    @Subscribe
    public void onInit(final InitEvent event) {
        localizedColumns.showCurrentLanguage(dictDataDomainsDataGrid);
    }
}
