package uz.kapitalbank.umida.view.orgstructure;

import uz.kapitalbank.umida.dto.orgstructure.OrgChartNode;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.metamodel.datatype.DatatypeFormatter;
import io.jmix.flowui.component.image.JmixImage;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Read-only employee card opened as a dialog when a d3-org-chart node is clicked in
 * {@code OrgStructureView}. Call {@link #setNode(OrgChartNode)} before opening — the
 * card is filled from the clicked chart node, only the assignment history on the career tab
 * is loaded by the employee id of the node.
 */
@Route(value = "employee-card", layout = MainView.class)
@ViewController(id = "umida_EmployeeCardView")
@ViewDescriptor(path = "employee-card-view.xml")
@DialogMode(width = "720px", height = "480px")
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
    @ViewComponent
    private CollectionLoader<OrgStructureAssignments> assignmentsDl;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private DatatypeFormatter datatypeFormatter;

    public void setNode(OrgChartNode node) {
        if (node == null) {
            return;
        }
        employeeNameLabel.setText(node.getName());
        jobTitleLabel.setText(node.getPosition());
        subdivisionLabel.setText(node.getOrgLevelName());
        contactsLabel.setText(node.getEmail() != null ? node.getEmail() : "email@gmail.com");

        setPhoto(node);
        loadAssignments(node);
    }

    /** A vacant position has no employee, its career tab stays empty. */
    private void loadAssignments(OrgChartNode node) {
        if (node.getEmployeeId() == null) {
            return;
        }
        assignmentsDl.setParameter("employeeId", node.getEmployeeId());
        assignmentsDl.load();
    }

    @Supply(to = "assignmentsDataGrid.period", subject = "renderer")
    private Renderer<OrgStructureAssignments> assignmentsDataGridPeriodRenderer() {
        return new TextRenderer<>(assignment -> datatypeFormatter.formatLocalDate(assignment.getStartDate())
                + " — "
                + (assignment.getEndDate() != null
                        ? datatypeFormatter.formatLocalDate(assignment.getEndDate())
                        : messageBundle.getMessage("employeeCardView.assignments.present")));
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
