package com.company.demo.view.dict;

import com.company.demo.entity.dict.DictDataProduct;
import com.company.demo.entity.dict.DictDataProductSteward;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.flowui.fragment.Fragment;
import io.jmix.flowui.fragment.FragmentDescriptor;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.Install;
import io.jmix.flowui.view.ViewComponent;

/**
 * List of the data stewards of one product, embedded as a tab into {@link DictDataProductDetailView}.
 * Call {@link #setProduct(DictDataProduct)} with a saved product to load its stewards;
 * the stewards are created and edited in {@link DictDataProductStewardDetailView} opened as a dialog.
 */
@FragmentDescriptor("dict-data-product-steward-list-fragment.xml")
public class DictDataProductStewardListFragment extends Fragment<VerticalLayout> {
    @ViewComponent
    private CollectionContainer<DictDataProductSteward> stewardsDc;
    @ViewComponent
    private CollectionLoader<DictDataProductSteward> stewardsDl;

    private DictDataProduct product;

    public void setProduct(DictDataProduct product) {
        this.product = product;
        if (product == null) {
            stewardsDc.getMutableItems().clear();
            return;
        }
        stewardsDl.setParameter("product", product);
        stewardsDl.load();
    }

    @Install(to = "stewardsDataGrid.createAction", subject = "initializer")
    private void stewardsDataGridCreateActionInitializer(final DictDataProductSteward steward) {
        steward.setProduct(product);
    }
}
