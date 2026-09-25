package com.company.demo.view.dict;

import com.company.demo.entity.dict.DictDataDomain;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "dict-data-domains/:id", layout = MainView.class)
@ViewController(id = "demo_DictDataDomain.detail")
@ViewDescriptor(path = "dict-data-domain-detail-view.xml")
@EditedEntityContainer("dictDataDomainDc")
public class DictDataDomainDetailView extends StandardDetailView<DictDataDomain> {
}