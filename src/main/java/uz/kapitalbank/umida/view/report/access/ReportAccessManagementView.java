package uz.kapitalbank.umida.view.report.access;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.component.html.DescriptionList;
import com.vaadin.flow.component.html.DescriptionList.Description;
import com.vaadin.flow.component.html.DescriptionList.Term;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.jmix.core.Messages;
import io.jmix.core.TimeSource;
import io.jmix.core.metamodel.datatype.DatatypeFormatter;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.action.SecuredBaseAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.InputDialog;
import io.jmix.flowui.app.inputdialog.InputParameter;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
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
import uz.kapitalbank.umida.entity.report.ReportAccessGrant;
import uz.kapitalbank.umida.entity.report.ReportAccessRequest;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.enums.report.ReportAccessRequestStatus;
import uz.kapitalbank.umida.service.report.ReportAccessException;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.service.report.ReportAccessService.RevokeResult;
import uz.kapitalbank.umida.view.main.MainView;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql.TODAY_PARAM;

/**
 * The access management of the reports, for the authors and for the heads and the delegates of the owners:
 * <ul>
 *     <li><b>Access</b> — the grants in force, given here to an employee or to a subdivision and revoked here,
 *     one by one or all those selected;</li>
 *     <li><b>Journal</b> — every grant with where it stands: in force, expired, revoked;</li>
 *     <li><b>Requests</b> — the requests of the employees, accepted or refused here; the number of the
 *     pending ones on the tab.</li>
 * </ul>
 * An author sees every report, the others those they manage: the rows are narrowed by
 * {@link uz.kapitalbank.umida.security.ReportViewerRowsRole}, the operations checked by
 * {@link ReportAccessService}. Every tab is filtered by the filters in the headers of its columns; the
 * position and the subdivision are those the employee held when the grant was given or the request made.
 * The requests, the pending ones first, and their number follow the changes made by anyone, see
 * {@link ReportAccessUiEventBridge}; a tab loads its list again when selected.
 */
@Route(value = "report-access", layout = MainView.class)
@ViewController(id = "umida_ReportAccessManagementView")
@ViewDescriptor(path = "report-access-management-view.xml")
public class ReportAccessManagementView extends StandardView {

    private static final String MANAGED_REPORTS_PARAM = "managedReportIds";

    @ViewComponent
    private CollectionLoader<ReportAccessGrant> grantsDl;
    @ViewComponent
    private CollectionLoader<ReportAccessGrant> journalDl;
    @ViewComponent
    private CollectionLoader<ReportAccessRequest> requestsDl;
    @ViewComponent
    private DataGrid<ReportAccessGrant> grantsDataGrid;
    @ViewComponent
    private DataGrid<ReportAccessRequest> requestsDataGrid;
    @ViewComponent
    private JmixTabSheet tabSheet;
    @ViewComponent("tabSheet.requestsTab")
    private Tab requestsTab;
    @ViewComponent
    private Span noAccessText;
    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private ReportAccessService accessService;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private Notifications notifications;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private Messages messages;
    @Autowired
    private TimeSource timeSource;
    @Autowired
    private DatatypeFormatter datatypeFormatter;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private ReportAccessStatusBadges statusBadges;

    private final Span requestsBadge = new Span();
    /** The names of the users who gave, revoked and decided, by the username: the lists show the names. */
    private final Map<String, String> userNames = new HashMap<>();
    /** The authentication of the user of the view: the events arrive without a reliable one. */
    private Authentication authentication;

    @Subscribe
    public void onInit(final InitEvent event) {
        authentication = currentAuthentication.getAuthentication();
        requestsBadge.getElement().getThemeList().addAll(List.of("badge", "pill", "small", "primary"));
        requestsBadge.addClassNames(LumoUtility.Margin.Left.SMALL);
        requestsTab.add(requestsBadge);

        ((SecuredBaseAction) requestsDataGrid.getAction("approve")).addEnabledRule(this::pendingSelected);
        ((SecuredBaseAction) requestsDataGrid.getAction("reject")).addEnabledRule(this::pendingSelected);
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        if (!accessService.canOpenAccessManagement()) {
            tabSheet.setVisible(false);
            noAccessText.setVisible(true);
            return;
        }
        reloadAll();
    }

    /** A change made by anyone: the requests and their number again, as the user of this view sees them. */
    @EventListener
    public void onRequestsChanged(final ReportAccessUiEventBridge.RequestsChangedUiEvent event) {
        if (tabSheet.isVisible()) {
            ReportAccessUiEventBridge.runAs(authentication, this::loadRequests);
        }
    }

    /** Each tab shows its list as it is now: the others may have changed it since it was loaded. */
    @Subscribe("tabSheet")
    public void onTabSheetSelectedChange(final JmixTabSheet.SelectedChangeEvent event) {
        if (event.isInitialSelection() || !tabSheet.isVisible() || event.getSelectedTab() == null) {
            return;
        }
        String tabId = event.getSelectedTab().getId().orElse("");
        switch (tabId) {
            case "grantsTab" -> loadGrants();
            case "journalTab" -> loadJournal();
            case "requestsTab" -> loadRequests();
            default -> {
            }
        }
    }

    /** The journal says where each grant stands today. */
    @Supply(to = "journalDataGrid.status", subject = "renderer")
    private Renderer<ReportAccessGrant> journalDataGridStatusRenderer() {
        return new ComponentRenderer<>(grant -> statusBadges.grant(accessService.grantStatus(grant)));
    }

    @Supply(to = "requestsDataGrid.status", subject = "renderer")
    private Renderer<ReportAccessRequest> requestsDataGridStatusRenderer() {
        return new ComponentRenderer<>(request -> statusBadges.request(request.getStatus()));
    }

    /** The pending requests stand out: they wait for a decision. */
    @Install(to = "requestsDataGrid", subject = "partNameGenerator")
    private String requestsDataGridPartNameGenerator(final ReportAccessRequest request) {
        return request.getStatus() == ReportAccessRequestStatus.PENDING ? "pending-request-row" : null;
    }

    // who gave, revoked and decided: the names, not the usernames

    @Supply(to = "grantsDataGrid.createdBy", subject = "renderer")
    private Renderer<ReportAccessGrant> grantsDataGridCreatedByRenderer() {
        return new TextRenderer<>(grant -> userName(grant.getCreatedBy()));
    }

    @Supply(to = "journalDataGrid.createdBy", subject = "renderer")
    private Renderer<ReportAccessGrant> journalDataGridCreatedByRenderer() {
        return new TextRenderer<>(grant -> userName(grant.getCreatedBy()));
    }

    @Supply(to = "journalDataGrid.revokedBy", subject = "renderer")
    private Renderer<ReportAccessGrant> journalDataGridRevokedByRenderer() {
        return new TextRenderer<>(grant -> userName(grant.getRevokedBy()));
    }

    @Supply(to = "requestsDataGrid.decidedBy", subject = "renderer")
    private Renderer<ReportAccessRequest> requestsDataGridDecidedByRenderer() {
        return new TextRenderer<>(request -> userName(request.getDecidedBy()));
    }

    @Subscribe(id = "grantsDc", target = Target.DATA_CONTAINER)
    public void onGrantsDcCollectionChange(final CollectionContainer.CollectionChangeEvent<ReportAccessGrant> event) {
        resolveUserNames(event.getSource().getItems(), ReportAccessGrant::getCreatedBy);
    }

    @Subscribe(id = "journalDc", target = Target.DATA_CONTAINER)
    public void onJournalDcCollectionChange(final CollectionContainer.CollectionChangeEvent<ReportAccessGrant> event) {
        resolveUserNames(event.getSource().getItems(), ReportAccessGrant::getCreatedBy,
                ReportAccessGrant::getRevokedBy);
    }

    @Subscribe(id = "requestsDc", target = Target.DATA_CONTAINER)
    public void onRequestsDcCollectionChange(final CollectionContainer.CollectionChangeEvent<ReportAccessRequest> event) {
        resolveUserNames(event.getSource().getItems(), ReportAccessRequest::getDecidedBy);
    }

    /** Looks up the names of the usernames of the rows not known yet; the grid renders them afterwards. */
    @SafeVarargs
    private <E> void resolveUserNames(Collection<E> items, Function<E, String>... usernameGetters) {
        List<String> unknown = items.stream()
                .flatMap(item -> Stream.of(usernameGetters).map(getter -> getter.apply(item)))
                .filter(Objects::nonNull)
                .filter(username -> !userNames.containsKey(username))
                .distinct()
                .toList();
        if (unknown.isEmpty()) {
            return;
        }
        Map<String, String> found = accessService.userNames(unknown);
        // a username of no user is shown as it is, and not looked up again
        unknown.forEach(username -> userNames.put(username, found.getOrDefault(username, username)));
    }

    private String userName(@Nullable String username) {
        return username == null ? "" : userNames.getOrDefault(username, username);
    }

    // ---------------------------------------------------------------------
    // grants
    // ---------------------------------------------------------------------

    @Subscribe(id = "grantBtn", subject = "clickListener")
    public void onGrantBtnClick(final ClickEvent<JmixButton> event) {
        dialogWindows.view(this, ReportAccessGrantView.class)
                .withViewConfigurer(view -> view.setReport(selectedReport()))
                .withAfterCloseListener(close -> {
                    if (close.closedWith(StandardOutcome.SAVE)) {
                        reloadAll();
                    }
                })
                .open();
    }

    /** The report of the grants selected in the list, if they are of one report: the dialog offers it. */
    @Nullable
    private ReportCustom selectedReport() {
        List<ReportCustom> reports = grantsDataGrid.getSelectedItems().stream()
                .map(ReportAccessGrant::getReport)
                .distinct()
                .toList();
        return reports.size() == 1 ? reports.get(0) : null;
    }

    /** Revokes the selected grants, one or several, with one reason. */
    @Subscribe("grantsDataGrid.revoke")
    public void onGrantsDataGridRevoke(final ActionPerformedEvent event) {
        Set<ReportAccessGrant> grants = grantsDataGrid.getSelectedItems();
        if (grants.isEmpty()) {
            return;
        }
        dialogs.createInputDialog(this)
                .withHeader(messageBundle.formatMessage("revokeDialog.header", grants.size()))
                .withLabelsPosition(Dialogs.InputDialogBuilder.LabelsPosition.TOP)
                .withParameter(InputParameter.stringParameter("reason")
                        .withField(() -> textArea(messageBundle.getMessage("revokeDialog.reason"))))
                .withActions(DialogActions.OK_CANCEL, result -> {
                    if (result.getCloseAction() == InputDialog.INPUT_DIALOG_OK_ACTION) {
                        revoke(grants, result.getValue("reason"));
                    }
                })
                .open();
    }

    private void revoke(Set<ReportAccessGrant> grants, @Nullable String reason) {
        try {
            RevokeResult result = accessService.revoke(grants, reason);
            notifications.create(messageBundle.formatMessage("revoked.text", result.revoked(), result.skipped()))
                    .withType(result.revoked() > 0 ? Notifications.Type.SUCCESS : Notifications.Type.WARNING)
                    .show();
        } catch (AccessDeniedException e) {
            showDenied();
        }
        reloadAll();
    }

    // ---------------------------------------------------------------------
    // requests
    // ---------------------------------------------------------------------

    /** Accepts the request, with an optional last day of the access and a comment. */
    @Subscribe("requestsDataGrid.approve")
    public void onRequestsDataGridApprove(final ActionPerformedEvent event) {
        ReportAccessRequest request = requestsDataGrid.getSingleSelectedItem();
        if (request == null) {
            return;
        }
        openDecisionDialog(request, messageBundle.getMessage("approveDialog.header"), result -> {
                    if (result.getCloseAction() == InputDialog.INPUT_DIALOG_OK_ACTION) {
                        decide(() -> accessService.approve(request, result.getValue("validTo"),
                                result.getValue("comment")), "approved.text");
                    }
                },
                InputParameter.parameter("validTo")
                        .withLabel(messages.getMessage("uz.kapitalbank.umida.entity.report/ReportAccessGrant.validTo"))
                        .withField(() -> {
                            DatePicker validTo = new DatePicker();
                            validTo.setWidthFull();
                            validTo.setMin(today());
                            // a date picked by mistake is cleared: no date is an access without an end
                            validTo.setClearButtonVisible(true);
                            validTo.setHelperText(messageBundle.getMessage("approveDialog.validToHelper"));
                            return validTo;
                        }),
                InputParameter.stringParameter("comment")
                        .withLabel(messageBundle.getMessage("decisionDialog.comment"))
                        .withField(() -> textArea(null)));
    }

    /** Refuses the request; the requester is told why: the comment is required. */
    @Subscribe("requestsDataGrid.reject")
    public void onRequestsDataGridReject(final ActionPerformedEvent event) {
        ReportAccessRequest request = requestsDataGrid.getSingleSelectedItem();
        if (request == null) {
            return;
        }
        openDecisionDialog(request, messageBundle.getMessage("rejectDialog.header"), result -> {
                    if (result.getCloseAction() == InputDialog.INPUT_DIALOG_OK_ACTION) {
                        decide(() -> accessService.reject(request, result.getValue("comment")), "rejected.text");
                    }
                },
                InputParameter.stringParameter("comment")
                        .withLabel(messageBundle.getMessage("decisionDialog.comment"))
                        .withField(() -> {
                            JmixTextArea comment = textArea(null);
                            comment.setRequired(true);
                            comment.setRequiredMessage(messageBundle.getMessage("rejectDialog.commentRequired"));
                            return comment;
                        }));
    }

    /**
     * The dialog of a decision on a request: the request read-only on top, then the fields of the decision, one
     * under another, the width of the dialog. The request is no parameter of the dialog: the dialog takes the
     * value of each of its parameters when closed, and a list of the details has none.
     */
    private void openDecisionDialog(ReportAccessRequest request, String header,
                                    Consumer<InputDialog.InputDialogResult> resultHandler,
                                    InputParameter... parameters) {
        InputDialog dialog = dialogs.createInputDialog(this)
                .withHeader(header)
                .withLabelsPosition(Dialogs.InputDialogBuilder.LabelsPosition.TOP)
                .withResponsiveSteps(List.of(new ResponsiveStep("0", 1, ResponsiveStep.LabelsPosition.TOP)))
                .withParameters(parameters)
                .withActions(DialogActions.OK_CANCEL, resultHandler)
                .open();
        dialog.getFormLayout().getParent()
                .map(HasComponents.class::cast)
                .ifPresent(content -> content.addComponentAsFirst(requestDetails(request)));
    }

    /** The request decided on, as "label: value" rows. */
    private DescriptionList requestDetails(ReportAccessRequest request) {
        Optional<OrgStructurePosition> position = Optional.ofNullable(request.getPosition());
        DescriptionList details = new DescriptionList();
        details.addClassName("property-list");
        details.setWidthFull();
        addDetail(details, "decisionDialog.reportCode", request.getReport().getCode());
        addDetail(details, "decisionDialog.reportName", request.getReport().getName());
        addDetail(details, "decisionDialog.employee",
                request.getEmployee() == null ? null : request.getEmployee().getFullName());
        addDetail(details, "decisionDialog.position", position
                .map(OrgStructurePosition::getJobTitle).map(OrgStructureJobTitle::getName).orElse(null));
        addDetail(details, "decisionDialog.subdivision", position
                .map(OrgStructurePosition::getSubdivision).map(OrgStructureSubdivision::getName).orElse(null));
        addDetail(details, "decisionDialog.reason", request.getReason());
        addDetail(details, "decisionDialog.createdDate",
                datatypeFormatter.formatOffsetDateTime(request.getCreatedDate()));
        return details;
    }

    /** A row of the details; one without a value shows a dash, see property-list.css. */
    private void addDetail(DescriptionList details, String labelKey, @Nullable String value) {
        details.add(new Term(messageBundle.getMessage(labelKey)), new Description(Objects.toString(value, "")));
    }

    /** Runs a decision on a request, reporting what came of it; someone else may have decided meanwhile. */
    private void decide(Runnable decision, String doneMessageKey) {
        try {
            decision.run();
            notifications.create(messageBundle.getMessage(doneMessageKey))
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } catch (ReportAccessException e) {
            notifications.create(e.getMessage()).withType(Notifications.Type.WARNING).show();
        } catch (AccessDeniedException e) {
            showDenied();
        }
        reloadAll();
    }

    private boolean pendingSelected() {
        ReportAccessRequest request = requestsDataGrid.getSingleSelectedItem();
        return request != null && request.getStatus() == ReportAccessRequestStatus.PENDING;
    }

    // ---------------------------------------------------------------------
    // loading
    // ---------------------------------------------------------------------

    private void reloadAll() {
        if (!tabSheet.isVisible() || authentication == null) {
            return;
        }
        loadGrants();
        loadJournal();
        loadRequests();
    }

    private void loadGrants() {
        grantsDl.setQuery("select g from umida_ReportAccessGrant g where " + ReportAccessService.inForce("g")
                + managedReportsOnly(grantsDl, "g", " and ") + " order by g.report.name, g.employee.fullName");
        grantsDl.setParameter(TODAY_PARAM, today());
        grantsDl.load();
    }

    private void loadJournal() {
        journalDl.setQuery("select g from umida_ReportAccessGrant g" + managedReportsOnly(journalDl, "g", " where ")
                + " order by g.createdDate desc");
        journalDl.load();
    }

    private void loadRequests() {
        // the pending ones first, the newest first among them and among the others
        requestsDl.setQuery("select q from umida_ReportAccessRequest q" + managedReportsOnly(requestsDl, "q", " where ")
                + " order by case when q.status = '" + ReportAccessRequestStatus.PENDING.getId() + "' then 0 else 1 end,"
                + " q.createdDate desc");
        requestsDl.load();
        refreshRequestsBadge();
    }

    /**
     * The condition keeping the rows of the reports the user manages, after {@code keyword} ({@code where} or
     * {@code and}), its parameter set on the loader; none for an author. The roles let the user read the own requests too, made for the reports of the others:
     * they are not the user's to decide on and stay out of the lists.
     */
    private String managedReportsOnly(CollectionLoader<?> loader, String alias, String keyword) {
        Optional<Set<UUID>> managed = accessService.managedReportIdsUnlessAuthor();
        if (managed.isEmpty()) {
            loader.removeParameter(MANAGED_REPORTS_PARAM);
            return "";
        }
        // no report left to manage: an id of no report, an empty list is no valid parameter of "in"
        loader.setParameter(MANAGED_REPORTS_PARAM, managed.get().isEmpty() ? Set.of(new UUID(0, 0)) : managed.get());
        return keyword + alias + ".report.id in :" + MANAGED_REPORTS_PARAM;
    }

    /** The number of the requests waiting for this user's decision, on the tab of the requests. */
    private void refreshRequestsBadge() {
        long pending = accessService.countPendingRequests();
        requestsBadge.setText(String.valueOf(pending));
        // not setVisible: the display of the Lumo badge overrides the hidden attribute
        if (pending > 0) {
            requestsBadge.getStyle().remove("display");
        } else {
            requestsBadge.getStyle().setDisplay(Style.Display.NONE);
        }
    }

    private JmixTextArea textArea(@Nullable String label) {
        JmixTextArea textArea = uiComponents.create(JmixTextArea.class);
        if (label != null) {
            textArea.setLabel(label);
        }
        textArea.setWidthFull();
        textArea.setMinHeight("6em");
        return textArea;
    }

    private void showDenied() {
        notifications.create(messageBundle.getMessage("operationDenied.text"))
                .withType(Notifications.Type.ERROR)
                .show();
    }

    private LocalDate today() {
        return timeSource.now().toLocalDate();
    }
}
