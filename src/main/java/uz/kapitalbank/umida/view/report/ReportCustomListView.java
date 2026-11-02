package uz.kapitalbank.umida.view.report;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.kit.component.ComponentUtils;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.reportsflowui.view.report.ReportListView;
import uz.kapitalbank.umida.view.main.MainView;

/**
 * The list of reports of the add-on with a plain create button and the domain of a report, next to the
 * add-on's {@code report_Report.list}: the grid actions open {@link ReportCustomDetailView} by id.
 *
 * <p>The descriptor extends the one of the add-on and takes its message group, which the inherited
 * layout and the controller rely on. What the controller of the add-on needs stays in the layout,
 * hidden: the dropdown create button and the action of the wizard.
 */
@Route(value = "custom-reports", layout = MainView.class)
@ViewController(id = "umida_ReportCustom.list")
@ViewDescriptor(path = "report-custom-list-view.xml")
@LookupComponent("reportsDataGrid")
@DialogMode(width = "60em")
public class ReportCustomListView extends ReportListView {

    /** The add-on takes the icon off the action on init: there it is an item of the dropdown button. */
    @Subscribe
    public void onReady(final ReadyEvent event) {
        reportsDataGridCreate.setIconComponent(ComponentUtils.convertToIcon(VaadinIcon.PLUS));
    }
}
