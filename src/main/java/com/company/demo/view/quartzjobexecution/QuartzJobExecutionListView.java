package com.company.demo.view.quartzjobexecution;

import com.company.demo.entity.quartz.QuartzJobExecution;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "quartz-job-executions", layout = MainView.class)
@ViewController(id = "demo_QuartzJobExecution.list")
@ViewDescriptor(path = "quartz-job-execution-list-view.xml")
@LookupComponent("quartzJobExecutionsDataGrid")
public class QuartzJobExecutionListView extends StandardListView<QuartzJobExecution> {
}
