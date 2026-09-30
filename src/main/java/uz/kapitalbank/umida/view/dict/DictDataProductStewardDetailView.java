package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.dict.DictDataProductSteward;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

/**
 * Opened as a dialog from {@link DictDataProductStewardListFragment}; the product is set
 * by the fragment's create action and is not shown in the form.
 */
@Route(value = "dict-data-product-stewards/:id", layout = MainView.class)
@ViewController(id = "umida_DictDataProductSteward.detail")
@ViewDescriptor(path = "dict-data-product-steward-detail-view.xml")
@EditedEntityContainer("dictDataProductStewardDc")
@DialogMode(width = "40em")
public class DictDataProductStewardDetailView extends StandardDetailView<DictDataProductSteward> {
}
