package com.company.demo.view.dqcheckrun;

import com.company.demo.entity.DqCheckRun;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "dq-check-runs", layout = MainView.class)
@ViewController(id = "demo_DqCheckRun.list")
@ViewDescriptor(path = "dq-check-run-list-view.xml")
@LookupComponent("dqCheckRunsDataGrid")
@DialogMode(width = "64em")
public class DqCheckRunListView extends StandardListView<DqCheckRun> {
}