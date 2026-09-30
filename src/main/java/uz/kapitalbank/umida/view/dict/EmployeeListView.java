package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.Employee;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "employees", layout = MainView.class)
@ViewController(id = "umida_Employee.list")
@ViewDescriptor(path = "employee-list-view.xml")
@LookupComponent("employeesDataGrid")
@DialogMode(width = "64em")
public class EmployeeListView extends StandardListView<Employee> {
}