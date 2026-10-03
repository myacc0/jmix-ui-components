package uz.kapitalbank.umida.view.dict;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.view.main.MainView;


@Route(value = "org-structure-employees", layout = MainView.class)
@ViewController(id = "umida_OrgStructureEmployee.list")
@ViewDescriptor(path = "org-structure-employee-list-view.xml")
@LookupComponent("orgStructureEmployeesDataGrid")
@DialogMode(width = "64em")
public class OrgStructureEmployeeListView extends StandardListView<OrgStructureEmployee> {
}