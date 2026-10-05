package uz.kapitalbank.umida.view.catalog;

import uz.kapitalbank.umida.entity.dict.DictDataDomainSteward;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

/**
 * Opened as a dialog from {@link DictDataDomainStewardListFragment}; the domain is set
 * by the fragment's create action and is not shown in the form.
 */
@Route(value = "dict-data-domain-stewards/:id", layout = MainView.class)
@ViewController(id = "umida_DictDataDomainSteward.detail")
@ViewDescriptor(path = "dict-data-domain-steward-detail-view.xml")
@EditedEntityContainer("dictDataDomainStewardDc")
@DialogMode(width = "40em")
public class DictDataDomainStewardDetailView extends StandardDetailView<DictDataDomainSteward> {
}
