package uz.kapitalbank.umida.view.report.access;

import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.action.SecuredBaseAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.InputDialog;
import io.jmix.flowui.app.inputdialog.InputParameter;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportAccessRequest;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.enums.report.ReportAccessRequestStatus;
import uz.kapitalbank.umida.service.report.ReportAccessException;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.service.report.ReportAccessService.Requester;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The requests of the current user for the access to one report, the newest first and the pending one on top,
 * page by page. A new request is sent from here — not while one waits for a decision, nor once the user may
 * run the report — and the pending request cancelled. Opened as a dialog from the list of the reports
 * ({@code ReportCustomListView}); the rules are {@link ReportAccessService}'s. The list follows the decisions
 * made meanwhile, see {@link ReportAccessUiEventBridge}.
 */
@ViewController(id = "umida_ReportAccessRequestsView")
@ViewDescriptor(path = "report-access-requests-view.xml")
@DialogMode(width = "64em")
public class ReportAccessRequestsView extends StandardView {

    @ViewComponent
    private CollectionLoader<ReportAccessRequest> requestsDl;
    @ViewComponent
    private CollectionContainer<ReportAccessRequest> requestsDc;
    @ViewComponent
    private DataGrid<ReportAccessRequest> requestsDataGrid;
    @ViewComponent
    private Span reportText;
    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private ReportAccessService accessService;
    @Autowired
    private ReportAccessStatusBadges statusBadges;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private Notifications notifications;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private CurrentAuthentication currentAuthentication;

    private ReportCustom report;
    /** Whether a new request may be sent: asked of the service at each load of the list. */
    private boolean canRequest;
    /** The names of the users who decided, by the username. */
    private final Map<String, String> userNames = new HashMap<>();
    /** The authentication of the user of the view: the events arrive without a reliable one. */
    private Authentication authentication;

    /** The report whose requests are shown; set before the dialog opens. */
    public void setReport(ReportCustom report) {
        this.report = report;
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        authentication = currentAuthentication.getAuthentication();
        ((SecuredBaseAction) requestsDataGrid.getAction("sendRequest")).addEnabledRule(() -> canRequest);
        ((SecuredBaseAction) requestsDataGrid.getAction("cancelRequest")).addEnabledRule(this::pendingSelected);
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Objects.requireNonNull(report, "The report is set before the dialog opens");
        reportText.setText(messageBundle.formatMessage("reportText.text", report.getCode(), report.getName()));
        loadRequests();
    }

    /** A decision made meanwhile, by anyone: the list and what may be done again. */
    @EventListener
    public void onRequestsChanged(final ReportAccessUiEventBridge.RequestsChangedUiEvent event) {
        if (report != null && authentication != null) {
            ReportAccessUiEventBridge.runAs(authentication, this::loadRequests);
        }
    }

    @Supply(to = "requestsDataGrid.status", subject = "renderer")
    private Renderer<ReportAccessRequest> requestsDataGridStatusRenderer() {
        return new ComponentRenderer<>(request -> statusBadges.request(request.getStatus()));
    }

    @Supply(to = "requestsDataGrid.decidedBy", subject = "renderer")
    private Renderer<ReportAccessRequest> requestsDataGridDecidedByRenderer() {
        return new TextRenderer<>(request -> request.getDecidedBy() == null ? ""
                : userNames.getOrDefault(request.getDecidedBy(), request.getDecidedBy()));
    }

    @Subscribe(id = "requestsDc", target = Target.DATA_CONTAINER)
    public void onRequestsDcCollectionChange(final CollectionContainer.CollectionChangeEvent<ReportAccessRequest> event) {
        List<String> unknown = event.getSource().getItems().stream()
                .map(ReportAccessRequest::getDecidedBy)
                .filter(Objects::nonNull)
                .filter(username -> !userNames.containsKey(username))
                .distinct()
                .toList();
        if (!unknown.isEmpty()) {
            Map<String, String> found = accessService.userNames(unknown);
            unknown.forEach(username -> userNames.put(username, found.getOrDefault(username, username)));
        }
    }

    // ---------------------------------------------------------------------
    // a new request
    // ---------------------------------------------------------------------

    /** Shows the report and the requester, asks why the report is needed and sends the request. */
    @Subscribe("requestsDataGrid.sendRequest")
    public void onRequestsDataGridSendRequest(final ActionPerformedEvent event) {
        Optional<Requester> requester = accessService.currentRequester();
        Optional<OrgStructurePosition> position = requester.map(Requester::position);
        dialogs.createInputDialog(this)
                .withHeader(messageBundle.getMessage("requestAccessDialog.header"))
                .withLabelsPosition(Dialogs.InputDialogBuilder.LabelsPosition.TOP)
                // one field under another, the width of the dialog
                .withResponsiveSteps(List.of(new ResponsiveStep("0", 1, ResponsiveStep.LabelsPosition.TOP)))
                .withParameters(
                        readOnlyParameter("reportCode", "requestAccessDialog.reportCode", report.getCode()),
                        readOnlyParameter("reportName", "requestAccessDialog.reportName", report.getName()),
                        readOnlyParameter("employee", "requestAccessDialog.employee",
                                requester.map(r -> r.employee().getFullName()).orElse(null)),
                        readOnlyParameter("position", "requestAccessDialog.position", position
                                .map(OrgStructurePosition::getJobTitle).map(OrgStructureJobTitle::getName)
                                .orElse(null)),
                        readOnlyParameter("subdivision", "requestAccessDialog.subdivision", position
                                .map(OrgStructurePosition::getSubdivision).map(OrgStructureSubdivision::getName)
                                .orElse(null)),
                        InputParameter.stringParameter("reason")
                                .withLabel(messageBundle.getMessage("requestAccessDialog.reason"))
                                .withField(() -> {
                                    JmixTextArea reason = uiComponents.create(JmixTextArea.class);
                                    reason.setWidthFull();
                                    reason.setMinHeight("8em");
                                    return reason;
                                }))
                .withActions(DialogActions.OK_CANCEL, result -> {
                    if (result.getCloseAction() == InputDialog.INPUT_DIALOG_OK_ACTION) {
                        sendRequest(result.getValue("reason"));
                    }
                })
                .open();
    }

    /**
     * A read-only text of the request dialog. The label goes on the item of the form, not on the field: a label on
     * the field leaves the empty label of the item above it.
     */
    @SuppressWarnings("unchecked")
    private InputParameter readOnlyParameter(String id, String labelKey, @Nullable String value) {
        return InputParameter.stringParameter(id).withLabel(messageBundle.getMessage(labelKey)).withField(() -> {
            TypedTextField<String> field = uiComponents.create(TypedTextField.class);
            field.setValue(value == null ? "" : value);
            field.setReadOnly(true);
            field.setWidthFull();
            return field;
        });
    }

    private void sendRequest(@Nullable String reason) {
        try {
            accessService.requestAccess(report, reason);
            notifications.create(messageBundle.getMessage("requestSent.text"))
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } catch (ReportAccessException e) {
            notifications.create(e.getMessage()).withType(Notifications.Type.WARNING).show();
        }
        loadRequests();
    }

    // ---------------------------------------------------------------------
    // cancelling
    // ---------------------------------------------------------------------

    @Subscribe("requestsDataGrid.cancelRequest")
    public void onRequestsDataGridCancelRequest(final ActionPerformedEvent event) {
        ReportAccessRequest request = requestsDataGrid.getSingleSelectedItem();
        if (request == null || request.getStatus() != ReportAccessRequestStatus.PENDING) {
            return;
        }
        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("cancelRequestDialog.header"))
                .withText(messageBundle.getMessage("cancelRequestDialog.text"))
                .withActions(
                        new DialogAction(DialogAction.Type.YES).withHandler(e -> cancel(request)),
                        new DialogAction(DialogAction.Type.NO))
                .open();
    }

    private void cancel(ReportAccessRequest request) {
        try {
            accessService.cancelRequest(request);
            notifications.create(messageBundle.getMessage("requestCancelled.text"))
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } catch (ReportAccessException e) {
            // decided on meanwhile
            notifications.create(e.getMessage()).withType(Notifications.Type.WARNING).show();
        } catch (AccessDeniedException e) {
            notifications.create(messageBundle.getMessage("operationDenied.text"))
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
        loadRequests();
    }

    private boolean pendingSelected() {
        ReportAccessRequest request = requestsDataGrid.getSingleSelectedItem();
        return request != null && request.getStatus() == ReportAccessRequestStatus.PENDING;
    }

    // ---------------------------------------------------------------------
    // loading
    // ---------------------------------------------------------------------

    /**
     * The requests of the user for the report, and what may be done: the pending request, first in the list,
     * is selected for its cancel to be at hand.
     */
    private void loadRequests() {
        // a user without an employee has no requests: an id of no employee
        requestsDl.setParameter("employeeId", accessService.currentEmployeeId().orElse(""));
        requestsDl.setParameter("reportId", report.getId());
        requestsDl.load();
        canRequest = accessService.canRequestAccess(report);

        requestsDc.getItems().stream()
                .filter(request -> request.getStatus() == ReportAccessRequestStatus.PENDING)
                .findFirst()
                .ifPresentOrElse(requestsDataGrid::select, requestsDataGrid::deselectAll);
        requestsDataGrid.getActions().forEach(action -> action.refreshState());
    }
}
