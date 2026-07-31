package com.company.demo.view.dqcheckrunnew;


import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "dq-check-run-new-view", layout = MainView.class)
@ViewController(id = "demo_DqCheckRunNewView")
@ViewDescriptor(path = "dq-check-run-new-view.xml")
public class DqCheckRunNewView extends StandardView {
}