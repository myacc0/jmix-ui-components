package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.Employee;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "employees/:id", layout = MainView.class)
@ViewController(id = "umida_Employee.detail")
@ViewDescriptor(path = "employee-detail-view.xml")
@EditedEntityContainer("employeeDc")
public class EmployeeDetailView extends StandardDetailView<Employee> {
}