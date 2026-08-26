package com.company.demo.view.dqdatadomain;

import com.company.demo.entity.dq.DqDataDomain;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "dq-data-domains", layout = MainView.class)
@ViewController(id = "demo_DqDataDomain.list")
@ViewDescriptor(path = "dq-data-domain-list-view.xml")
@LookupComponent("dqDataDomainsDataGrid")
@DialogMode(width = "64em")
public class DqDataDomainListView extends StandardListView<DqDataDomain> {
}