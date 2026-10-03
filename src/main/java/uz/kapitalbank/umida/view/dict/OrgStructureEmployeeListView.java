package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "employees", layout = MainView.class)
@ViewController(id = "umida_OrgStructureEmployee.list")
@ViewDescriptor(path = "org-structure-employee-list-view.xml")
@LookupComponent("employeesDataGrid")
@DialogMode(width = "64em")
public class OrgStructureEmployeeListView extends StandardListView<OrgStructureEmployee> {
}