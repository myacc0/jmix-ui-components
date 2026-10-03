package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "employees/:id", layout = MainView.class)
@ViewController(id = "umida_OrgStructureEmployee.detail")
@ViewDescriptor(path = "org-structure-employee-detail-view.xml")
@EditedEntityContainer("employeeDc")
public class OrgStructureEmployeeDetailView extends StandardDetailView<OrgStructureEmployee> {
}