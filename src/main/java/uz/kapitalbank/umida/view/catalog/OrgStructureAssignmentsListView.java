package uz.kapitalbank.umida.view.catalog;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
import uz.kapitalbank.umida.view.main.MainView;


@Route(value = "org-structure-assignments", layout = MainView.class)
@ViewController(id = "umida_OrgStructureAssignments.list")
@ViewDescriptor(path = "org-structure-assignments-list-view.xml")
@LookupComponent("orgStructureAssignmentsDataGrid")
@DialogMode(width = "64em")
public class OrgStructureAssignmentsListView extends StandardListView<OrgStructureAssignments> {
}
