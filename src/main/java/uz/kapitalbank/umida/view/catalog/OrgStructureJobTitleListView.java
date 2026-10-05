package uz.kapitalbank.umida.view.catalog;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import uz.kapitalbank.umida.view.main.MainView;


@Route(value = "org-structure-job-titles", layout = MainView.class)
@ViewController(id = "umida_OrgStructureJobTitle.list")
@ViewDescriptor(path = "org-structure-job-title-list-view.xml")
@LookupComponent("orgStructureJobTitlesDataGrid")
@DialogMode(width = "64em")
public class OrgStructureJobTitleListView extends StandardListView<OrgStructureJobTitle> {
}