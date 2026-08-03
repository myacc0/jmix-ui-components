package com.company.demo.view.dqcheckrun;

import com.company.demo.entity.DqCheckRun;
import com.company.demo.view.dqcheckrunnew.DqCheckRunNewView;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "dq-check-runs", layout = MainView.class)
@ViewController(id = "demo_DqCheckRun.list")
@ViewDescriptor(path = "dq-check-run-list-view.xml")
@LookupComponent("dqCheckRunsDataGrid")
@DialogMode(width = "64em")
public class DqCheckRunListView extends StandardListView<DqCheckRun> {
    @Autowired
    private ViewNavigators viewNavigators;

    @Subscribe(id = "runCheckButton", subject = "clickListener")
    public void onRunCheckButtonClick(final ClickEvent<JmixButton> event) {
        // registers this view as the return target, so closing the new-run page comes back here
        // instead of falling back to the parent layout
        viewNavigators.view(this, DqCheckRunNewView.class)
                .withBackwardNavigation(true)
                .navigate();
    }
}