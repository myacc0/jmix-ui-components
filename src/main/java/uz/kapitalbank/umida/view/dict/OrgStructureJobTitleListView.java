package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "job-titles", layout = MainView.class)
@ViewController(id = "umida_OrgStructureJobTitle.list")
@ViewDescriptor(path = "org-structure-job-title-list-view.xml")
@LookupComponent("jobTitlesDataGrid")
@DialogMode(width = "64em")
public class OrgStructureJobTitleListView extends StandardListView<OrgStructureJobTitle> {
}