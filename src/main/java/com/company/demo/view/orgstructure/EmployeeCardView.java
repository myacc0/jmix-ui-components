package com.company.demo.view.orgstructure;

import com.company.demo.dto.orgstructure.OrgChartNode;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

/**
 * Read-only employee card opened as a dialog when a d3-org-chart node is clicked in
 * {@code OrgStructureView}. Call {@link #setNode(OrgChartNode)} before opening — the
 * card is filled from the clicked chart node, not from a data container.
 */
@Route(value = "employee-card", layout = MainView.class)
@ViewController(id = "demo_EmployeeCardView")
@ViewDescriptor(path = "employee-card-view.xml")
@DialogMode(width = "40em", height = "32em", resizable = true)
public class EmployeeCardView extends StandardView {

    @ViewComponent
    private H3 employeeNameLabel;
    @ViewComponent
    private Span jobTitleLabel;
    @ViewComponent
    private Span departmentLabel;

    public void setNode(OrgChartNode node) {
        if (node == null) {
            return;
        }
        employeeNameLabel.setText(node.getName());
        jobTitleLabel.setText(node.getPosition());
        departmentLabel.setText(node.getOrgLevelName());
    }
}
