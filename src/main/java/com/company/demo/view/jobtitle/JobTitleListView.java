package com.company.demo.view.jobtitle;

import com.company.demo.entity.orgstructure.JobTitle;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "job-titles", layout = MainView.class)
@ViewController(id = "demo_JobTitle.list")
@ViewDescriptor(path = "job-title-list-view.xml")
@LookupComponent("jobTitlesDataGrid")
@DialogMode(width = "64em")
public class JobTitleListView extends StandardListView<JobTitle> {
}