package com.company.demo.view.jobtitle;

import com.company.demo.entity.orgstructure.JobTitle;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "job-titles/:id", layout = MainView.class)
@ViewController(id = "demo_JobTitle.detail")
@ViewDescriptor(path = "job-title-detail-view.xml")
@EditedEntityContainer("jobTitleDc")
public class JobTitleDetailView extends StandardDetailView<JobTitle> {
}