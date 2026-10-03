package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "subdivisions/:id", layout = MainView.class)
@ViewController(id = "umida_OrgStructureSubdivision.detail")
@ViewDescriptor(path = "org-structure-subdivision-detail-view.xml")
@EditedEntityContainer("subdivisionDc")
public class OrgStructureSubdivisionDetailView extends StandardDetailView<OrgStructureSubdivision> {
}