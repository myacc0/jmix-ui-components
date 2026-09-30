package uz.kapitalbank.umida.view.dict;

import uz.kapitalbank.umida.entity.orgstructure.JobTitle;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "job-titles/:id", layout = MainView.class)
@ViewController(id = "umida_JobTitle.detail")
@ViewDescriptor(path = "job-title-detail-view.xml")
@EditedEntityContainer("jobTitleDc")
public class JobTitleDetailView extends StandardDetailView<JobTitle> {
}