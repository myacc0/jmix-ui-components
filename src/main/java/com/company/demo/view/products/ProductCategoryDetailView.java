package com.company.demo.view.products;

import com.company.demo.entity.ProductCategory;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "product-categories/:id", layout = MainView.class)
@ViewController(id = "demo_ProductCategory.detail")
@ViewDescriptor(path = "product-category-detail-view.xml")
@EditedEntityContainer("productCategoryDc")
public class ProductCategoryDetailView extends StandardDetailView<ProductCategory> {
}