package com.company.demo.view.dqdataproduct;

import com.company.demo.entity.DqDataProduct;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "dq-data-products/:id", layout = MainView.class)
@ViewController(id = "demo_DqDataProduct.detail")
@ViewDescriptor(path = "dq-data-product-detail-view.xml")
@EditedEntityContainer("dqDataProductDc")
public class DqDataProductDetailView extends StandardDetailView<DqDataProduct> {
}