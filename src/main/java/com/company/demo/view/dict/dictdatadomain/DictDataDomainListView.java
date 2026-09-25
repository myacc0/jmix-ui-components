package com.company.demo.view.dict.dictdatadomain;

import com.company.demo.entity.dict.DictDataDomain;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "dict-data-domains", layout = MainView.class)
@ViewController(id = "demo_DictDataDomain.list")
@ViewDescriptor(path = "dict-data-domain-list-view.xml")
@LookupComponent("dictDataDomainsDataGrid")
@DialogMode(width = "64em")
public class DictDataDomainListView extends StandardListView<DictDataDomain> {
}