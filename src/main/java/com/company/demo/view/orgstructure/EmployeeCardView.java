package com.company.demo.view.orgstructure;

import com.company.demo.dto.orgstructure.OrgChartNode;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.image.JmixImage;
import io.jmix.flowui.view.*;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

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

    /** Class name put on the dialog overlay by {@code OrgStructureView}, see employee-card-view.css. */
    public static final String DIALOG_CLASS_NAME = "employee-card-dialog";

    @ViewComponent
    private JmixImage<?> employeePhotoImage;
    @ViewComponent
    private Span employeePhotoPlaceholder;
    @ViewComponent
    private H4 employeeNameLabel;
    @ViewComponent
    private Span jobTitleLabel;
    @ViewComponent
    private Span subdivisionLabel;
    @ViewComponent
    private Span contactsLabel;

    public void setNode(OrgChartNode node) {
        if (node == null) {
            return;
        }
        employeeNameLabel.setText(node.getName());
        jobTitleLabel.setText(node.getPosition());
        subdivisionLabel.setText(node.getOrgLevelName());
        contactsLabel.setText(node.getEmail() != null ? node.getEmail() : "email@gmail.com");

        setPhoto(node);
    }

    /**
     * Shows the photo when the employee has one in the file storage, and the initials
     * placeholder otherwise — the same fallback the chart node uses.
     */
    private void setPhoto(OrgChartNode node) {
        boolean hasPhoto = StringUtils.isNotBlank(node.getImage());

        employeePhotoImage.setVisible(hasPhoto);
        employeePhotoPlaceholder.setVisible(!hasPhoto);

        if (hasPhoto) {
            employeePhotoImage.setSrc(node.getImage());
            employeePhotoImage.setAlt(StringUtils.defaultString(node.getName()));
        } else {
            employeePhotoPlaceholder.setText(initials(node.getName()));
        }
    }

    private String initials(String fullName) {
        return Arrays.stream(StringUtils.defaultString(fullName).split("\\s+"))
                .filter(StringUtils::isNotBlank)
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());
    }
}
