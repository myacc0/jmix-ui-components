package uz.kapitalbank.umida.view.catalog;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.view.main.MainView;


@Route(value = "org-structure-subdivisions", layout = MainView.class)
@ViewController(id = "umida_OrgStructureSubdivision.list")
@ViewDescriptor(path = "org-structure-subdivision-list-view.xml")
@LookupComponent("orgStructureSubdivisionsDataGrid")
@DialogMode(width = "64em")
public class OrgStructureSubdivisionListView extends StandardListView<OrgStructureSubdivision> {
}