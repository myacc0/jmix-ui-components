package com.company.demo.view.dqdataproduct;

import com.company.demo.entity.DqDataProduct;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "dq-data-products", layout = MainView.class)
@ViewController(id = "demo_DqDataProduct.list")
@ViewDescriptor(path = "dq-data-product-list-view.xml")
@LookupComponent("dqDataProductsDataGrid")
@DialogMode(width = "64em")
public class DqDataProductListView extends StandardListView<DqDataProduct> {
}