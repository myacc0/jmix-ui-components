package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.Department;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "departments", layout = MainView.class)
@ViewController(id = "umida_Department.list")
@ViewDescriptor(path = "department-list-view.xml")
@LookupComponent("departmentsDataGrid")
@DialogMode(width = "64em")
public class DepartmentListView extends StandardListView<Department> {
}