package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "job-titles/:id", layout = MainView.class)
@ViewController(id = "umida_OrgStructureJobTitle.detail")
@ViewDescriptor(path = "org-structure-job-title-detail-view.xml")
@EditedEntityContainer("jobTitleDc")
public class OrgStructureJobTitleDetailView extends StandardDetailView<OrgStructureJobTitle> {
}