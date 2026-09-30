package uz.kapitalbank.umida.view.workdisciplineexception;

import uz.kapitalbank.umida.entity.hr.WorkDisciplineException;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "work-discipline-exceptions", layout = MainView.class)
@ViewController(id = "umida_WorkDisciplineException.list")
@ViewDescriptor(path = "work-discipline-exception-list-view.xml")
@LookupComponent("workDisciplineExceptionsDataGrid")
@DialogMode(width = "64em")
public class WorkDisciplineExceptionListView extends StandardListView<WorkDisciplineException> {
}