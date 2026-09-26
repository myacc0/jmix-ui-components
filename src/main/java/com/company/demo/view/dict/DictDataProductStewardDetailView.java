package com.company.demo.view.dict;

import com.company.demo.entity.dict.DictDataProductSteward;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

/**
 * Opened as a dialog from {@link DictDataProductStewardListFragment}; the product is set
 * by the fragment's create action and is not shown in the form.
 */
@Route(value = "dict-data-product-stewards/:id", layout = MainView.class)
@ViewController(id = "demo_DictDataProductSteward.detail")
@ViewDescriptor(path = "dict-data-product-steward-detail-view.xml")
@EditedEntityContainer("dictDataProductStewardDc")
@DialogMode(width = "40em")
public class DictDataProductStewardDetailView extends StandardDetailView<DictDataProductSteward> {
}
