package com.company.demo.view.workdisciplineexception;

import com.company.demo.entity.WorkDisciplineException;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "work-discipline-exceptions", layout = MainView.class)
@ViewController(id = "demo_WorkDisciplineException.list")
@ViewDescriptor(path = "work-discipline-exception-list-view.xml")
@LookupComponent("workDisciplineExceptionsDataGrid")
@DialogMode(width = "64em")
public class WorkDisciplineExceptionListView extends StandardListView<WorkDisciplineException> {
}