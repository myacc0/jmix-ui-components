package uz.kapitalbank.umida.view.catalog;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.view.main.MainView;


@Route(value = "org-structure-positions", layout = MainView.class)
@ViewController(id = "umida_OrgStructurePosition.list")
@ViewDescriptor(path = "org-structure-position-list-view.xml")
@LookupComponent("orgStructurePositionsDataGrid")
@DialogMode(width = "64em")
public class OrgStructurePositionListView extends StandardListView<OrgStructurePosition> {
}