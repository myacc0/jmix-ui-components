package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.JobTitle;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "job-titles", layout = MainView.class)
@ViewController(id = "umida_JobTitle.list")
@ViewDescriptor(path = "job-title-list-view.xml")
@LookupComponent("jobTitlesDataGrid")
@DialogMode(width = "64em")
public class JobTitleListView extends StandardListView<JobTitle> {
}