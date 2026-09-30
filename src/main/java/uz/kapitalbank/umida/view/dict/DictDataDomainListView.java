package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "dict-data-domains", layout = MainView.class)
@ViewController(id = "umida_DictDataDomain.list")
@ViewDescriptor(path = "dict-data-domain-list-view.xml")
@LookupComponent("dictDataDomainsDataGrid")
@DialogMode(width = "64em")
public class DictDataDomainListView extends StandardListView<DictDataDomain> {
}
