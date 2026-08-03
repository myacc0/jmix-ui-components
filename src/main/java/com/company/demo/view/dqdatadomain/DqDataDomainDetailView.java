package com.company.demo.view.dqdatadomain;

import com.company.demo.entity.DqDataDomain;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "dq-data-domains/:id", layout = MainView.class)
@ViewController(id = "demo_DqDataDomain.detail")
@ViewDescriptor(path = "dq-data-domain-detail-view.xml")
@EditedEntityContainer("dqDataDomainDc")
public class DqDataDomainDetailView extends StandardDetailView<DqDataDomain> {
}