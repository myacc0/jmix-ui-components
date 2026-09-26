package com.company.demo.view.dict;

import com.company.demo.entity.dict.DictDataProduct;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "dict-data-products/:id", layout = MainView.class)
@ViewController(id = "demo_DictDataProduct.detail")
@ViewDescriptor(path = "dict-data-product-detail-view.xml")
@EditedEntityContainer("dictDataProductDc")
public class DictDataProductDetailView extends StandardDetailView<DictDataProduct> {

    @ViewComponent
    private JmixTabSheet tabSheet;
    @ViewComponent
    private DictDataProductStewardListFragment stewardsFragment;

    @Autowired
    private EntityStates entityStates;

    @Subscribe
    public void onReady(final ReadyEvent event) {
        updateStewardsTab();
    }

    @Subscribe
    public void onAfterSave(final AfterSaveEvent event) {
        updateStewardsTab();
    }

    /**
     * Stewards reference the product, so their tab is available only once the product is saved.
     */
    private void updateStewardsTab() {
        boolean saved = !entityStates.isNew(getEditedEntity());
        tabSheet.getTab(stewardsFragment).setEnabled(saved);
        stewardsFragment.setProduct(saved ? getEditedEntity() : null);
    }
}
