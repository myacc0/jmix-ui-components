package com.company.demo.view.dqcheckrun;

import com.company.demo.entity.DqCheckRun;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "dq-check-runs/:id", layout = MainView.class)
@ViewController(id = "demo_DqCheckRun.detail")
@ViewDescriptor(path = "dq-check-run-detail-view.xml")
@EditedEntityContainer("dqCheckRunDc")
public class DqCheckRunDetailView extends StandardDetailView<DqCheckRun> {
}