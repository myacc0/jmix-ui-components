package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "dict-data-products", layout = MainView.class)
@ViewController(id = "umida_DictDataProduct.list")
@ViewDescriptor(path = "dict-data-product-list-view.xml")
@LookupComponent("dictDataProductsDataGrid")
@DialogMode(width = "64em")
public class DictDataProductListView extends StandardListView<DictDataProduct> {
}
