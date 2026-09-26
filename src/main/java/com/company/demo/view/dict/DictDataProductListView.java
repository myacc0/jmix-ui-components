package com.company.demo.view.dict;

import com.company.demo.entity.dict.DictDataProduct;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "dict-data-products", layout = MainView.class)
@ViewController(id = "demo_DictDataProduct.list")
@ViewDescriptor(path = "dict-data-product-list-view.xml")
@LookupComponent("dictDataProductsDataGrid")
@DialogMode(width = "64em")
public class DictDataProductListView extends StandardListView<DictDataProduct> {
}
