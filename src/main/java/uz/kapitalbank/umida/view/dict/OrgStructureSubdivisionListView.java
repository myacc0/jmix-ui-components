package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "subdivisions", layout = MainView.class)
@ViewController(id = "umida_OrgStructureSubdivision.list")
@ViewDescriptor(path = "org-structure-subdivision-list-view.xml")
@LookupComponent("subdivisionsDataGrid")
@DialogMode(width = "64em")
public class OrgStructureSubdivisionListView extends StandardListView<OrgStructureSubdivision> {
}