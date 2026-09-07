package com.company.demo.view.products;

import com.company.demo.entity.ProductCategory;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "product-categories", layout = MainView.class)
@ViewController(id = "demo_ProductCategory.list")
@ViewDescriptor(path = "product-category-list-view.xml")
@LookupComponent("productCategoriesDataGrid")
@DialogMode(width = "64em")
public class ProductCategoryListView extends StandardListView<ProductCategory> {
}