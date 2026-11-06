package uz.kapitalbank.umida.view.report.access;

import com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.component.html.DescriptionList;
import com.vaadin.flow.component.html.DescriptionList.Description;
import com.vaadin.flow.component.html.DescriptionList.Term;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.BeforeLeaveEvent;
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
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.util.UnknownOperationResult;
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
import uz.kapitalbank.umida.enums.report.ReportAccessGrantSource;
import uz.kapitalbank.umida.enums.report.ReportAccessRequestStatus;
import uz.kapitalbank.umida.service.report.ReportAccessException;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.service.report.ReportAccessService.RevokeResult;
import uz.kapitalbank.umida.service.report.ReportOwnerMemberService;
import uz.kapitalbank.umida.view.main.MainView;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
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
 *     <li><b>Owners</b> — the list of the business owner of the report picked, {@link ReportOwnerMembersFragment}:
 *     who runs the report on behalf of the owner and who manages the access to it, the delegates. The rows
 *     changed are saved with the button, row by row; a head or a delegate does not change the own row. The
 *     changes not saved are asked about before another report is picked or the view is left;</li>
 *     <li><b>Access</b> — the grants in force but those of the owner lists, given here to an employee or to a subdivision and revoked here,
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
    @ViewComponent
    private CollectionContainer<ReportCustom> ownerReportsDc;
    @ViewComponent
    private EntityComboBox<ReportCustom> ownersReportField;
    @ViewComponent
    private ReportOwnerMembersFragment ownersFragment;
    @ViewComponent
    private Span ownersNoReportsText;
    @ViewComponent
    private HorizontalLayout ownersButtonsPanel;
    @ViewComponent
    private JmixButton saveOwnersBtn;
    @ViewComponent
    private JmixButton cancelOwnersBtn;

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
    @Autowired
    private ReportOwnerMemberService ownerMemberService;
    @Autowired
    private ViewValidation viewValidation;

    private final Span requestsBadge = new Span();
    /** The names of the users who gave, revoked and decided, by the username: the lists show the names. */
    private final Map<String, String> userNames = new HashMap<>();
    /** The authentication of the user of the view: the events arrive without a reliable one. */
    private Authentication authentication;
    /** The report of the owner list is being set by the view, not picked by the user. */
    private boolean settingOwnersReport;

    @Subscribe
    public void onInit(final InitEvent event) {
        authentication = currentAuthentication.getAuthentication();
        requestsBadge.getElement().getThemeList().addAll(List.of("badge", "pill", "small", "primary"));
        requestsBadge.addClassNames(LumoUtility.Margin.Left.SMALL);
        requestsTab.add(requestsBadge);

        ((SecuredBaseAction) requestsDataGrid.getAction("approve")).addEnabledRule(this::pendingSelected);
        ((SecuredBaseAction) requestsDataGrid.getAction("reject")).addEnabledRule(this::pendingSelected);

        ownersReportField.setItemLabelGenerator(ReportAccessManagementView::reportCaption);
        ownersFragment.addChangeListener(this::refreshOwnersButtons);
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        if (!accessService.canOpenAccessManagement()) {
            tabSheet.setVisible(false);
            noAccessText.setVisible(true);
            return;
        }
        // the head and the delegates keep their own rows, an author changes them all
        ownersFragment.setLockedEmployeeId(accessService.isAuthor() ? null : accessService.currentEmployeeId().orElse(null));
        loadOwnerReports();
        reloadAll();
    }

    /** Leaving the view with the owner list changed and not saved asks whether to drop the changes. */
    @Subscribe
    public void onBeforeClose(final BeforeCloseEvent event) {
        CloseAction action = event.getCloseAction();
        if (!(action instanceof ChangeTrackerCloseAction tracker) || !tracker.isCheckForUnsavedChanges()
                || !tabSheet.isVisible() || !ownersFragment.hasChanges()) {
            return;
        }
        UnknownOperationResult result = new UnknownOperationResult();
        if (action instanceof NavigateCloseAction navigateAction) {
            BeforeLeaveEvent.ContinueNavigationAction navigation = navigateAction.getBeforeLeaveEvent().postpone();
            viewValidation.showUnsavedChangesDialog(this)
                    .onDiscard(() -> {
                        ownersFragment.discardChanges();
                        navigation.proceed();
                        fireEvent(new AfterCloseEvent(this, action));
                        result.success();
                    })
                    .onCancel(() -> {
                        // the router waits for the navigation to be cancelled explicitly
                        navigation.cancel();
                        result.fail();
                    });
        } else {
            viewValidation.showUnsavedChangesDialog(this)
                    .onDiscard(() -> {
                        ownersFragment.discardChanges();
                        result.resume(close(action));
                    })
                    .onCancel(result::fail);
        }
        event.preventClose(result);
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
            // the changes not saved stay as they are
            case "ownersTab" -> {
                if (!ownersFragment.hasChanges()) {
                    loadOwnerReports();
                }
            }
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
    // owners
    // ---------------------------------------------------------------------

    /** Another report: its list, once the changes of the list shown are saved or dropped. */
    @Subscribe("ownersReportField")
    public void onOwnersReportFieldValueChange(
            final ComponentValueChangeEvent<EntityComboBox<ReportCustom>, ReportCustom> event) {
        if (settingOwnersReport) {
            return;
        }
        if (!ownersFragment.hasChanges()) {
            loadOwners();
            return;
        }
        ReportCustom picked = event.getValue();
        setOwnersReport(event.getOldValue());
        viewValidation.showUnsavedChangesDialog(this)
                .onDiscard(() -> {
                    setOwnersReport(picked);
                    loadOwners();
                });
    }

    @Subscribe(id = "saveOwnersBtn", subject = "clickListener")
    public void onSaveOwnersBtnClick(final ClickEvent<JmixButton> event) {
        ReportCustom report = ownersReportField.getValue();
        if (report == null) {
            return;
        }
        try {
            ownerMemberService.saveChanges(report, ownersFragment.getChangedItems());
            notifications.create(messageBundle.getMessage("ownersSaved.text"))
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } catch (ReportAccessException e) {
            notifications.create(e.getMessage()).withType(Notifications.Type.WARNING).show();
        } catch (AccessDeniedException e) {
            showDenied();
        }
        loadOwners();
        reloadAll();
    }

    /** Back to the list as it is saved now. */
    @Subscribe(id = "cancelOwnersBtn", subject = "clickListener")
    public void onCancelOwnersBtnClick(final ClickEvent<JmixButton> event) {
        loadOwners();
    }

    /**
     * The reports whose owner list the user keeps, the one picked before staying picked while the user keeps
     * it, the first one by the code otherwise.
     */
    private void loadOwnerReports() {
        ReportCustom picked = ownersReportField.getValue();
        List<ReportCustom> reports = accessService.ownerListManagedReports();
        ownerReportsDc.setItems(reports);
        boolean any = !reports.isEmpty();
        ownersNoReportsText.setVisible(!any);
        ownersReportField.setVisible(any);
        ownersFragment.setVisible(any);
        ownersButtonsPanel.setVisible(any);
        setOwnersReport(reports.stream()
                .filter(report -> picked != null && report.getId().equals(picked.getId()))
                .findFirst()
                .orElse(any ? reports.getFirst() : null));
        loadOwners();
    }

    /** The rows of the owner list of the report picked, as they are saved. */
    private void loadOwners() {
        ReportCustom report = ownersReportField.getValue();
        ownersFragment.setItems(report == null ? List.of()
                : ownerMemberService.items(report, report.getBusinessOwner(), false));
    }

    private void setOwnersReport(@Nullable ReportCustom report) {
        settingOwnersReport = true;
        try {
            ownersReportField.setValue(report);
        } finally {
            settingOwnersReport = false;
        }
    }

    private void refreshOwnersButtons() {
        boolean changed = ownersFragment.hasChanges();
        saveOwnersBtn.setEnabled(changed);
        cancelOwnersBtn.setEnabled(changed);
    }

    /** The code and the name: the names of the reports may repeat, the codes do not. */
    private static String reportCaption(ReportCustom report) {
        return report.getCode() == null ? Objects.toString(report.getName(), "")
                : report.getCode() + " — " + Objects.toString(report.getName(), "");
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
        InputDialog dialog = dialogs.createInputDialog(this)
                .withHeader(messageBundle.formatMessage("revokeDialog.header", grants.size()))
                .withWidth("70em")
                .withLabelsPosition(Dialogs.InputDialogBuilder.LabelsPosition.TOP)
                .withResponsiveSteps(List.of(new ResponsiveStep("0", 1, ResponsiveStep.LabelsPosition.TOP)))
                // the label of the parameter, not of the field: the form item of a parameter without a label
                // keeps its empty label, a gap above the field
                .withParameter(InputParameter.stringParameter("reason")
                        .withLabel(messageBundle.getMessage("revokeDialog.reason"))
                        .withField(() -> textArea()))
                .withActions(DialogActions.OK_CANCEL, result -> {
                    if (result.getCloseAction() == InputDialog.INPUT_DIALOG_OK_ACTION) {
                        revoke(grants, result.getValue("reason"));
                    }
                })
                .open();
        addAboveFields(dialog, revokedGrantsGrid(grants));
    }

    /** The grants being revoked, read-only: whose access to which report goes. */
    private Grid<ReportAccessGrant> revokedGrantsGrid(Collection<ReportAccessGrant> grants) {
        Grid<ReportAccessGrant> grid = new Grid<>();
        grid.setId("revokeGrantsGrid");
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.setWidthFull();
        addTextColumn(grid, "decisionDialog.reportCode", grant -> grant.getReport().getCode());
        addTextColumn(grid, "decisionDialog.reportName", grant -> grant.getReport().getName());
        addTextColumn(grid, "decisionDialog.employee", grant -> grant.getEmployee().getFullName());
        addTextColumn(grid, "decisionDialog.position", grant -> jobTitleName(grant.getPosition()));
        addTextColumn(grid, "decisionDialog.subdivision", grant -> subdivisionName(grant.getPosition()));
        grid.setItems(grants.stream()
                .sorted(Comparator.comparing((ReportAccessGrant grant) -> grant.getReport().getName(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(grant -> grant.getEmployee().getFullName(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList());
        // a few grants are shown whole, more of them scroll
        if (grants.size() <= 8) {
            grid.setAllRowsVisible(true);
        } else {
            grid.setHeight("20em");
        }
        return grid;
    }

    private void addTextColumn(Grid<ReportAccessGrant> grid, String headerKey,
                               Function<ReportAccessGrant, String> value) {
        grid.addColumn(value::apply)
                .setHeader(messageBundle.getMessage(headerKey))
                .setAutoWidth(true)
                .setResizable(true);
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
                        .withField(() -> textArea()));
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
                            JmixTextArea comment = textArea();
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
        addAboveFields(dialog, requestDetails(request));
    }

    /**
     * Puts a component on top of the input dialog, above its fields: it is no parameter of the dialog, which
     * takes the value of each of its parameters when closed.
     */
    private static void addAboveFields(InputDialog dialog, Component component) {
        dialog.getFormLayout().getParent()
                .map(HasComponents.class::cast)
                .ifPresent(content -> content.addComponentAsFirst(component));
    }

    /** The request decided on, as "label: value" rows. */
    private DescriptionList requestDetails(ReportAccessRequest request) {
        DescriptionList details = new DescriptionList();
        details.addClassName("property-list");
        details.setWidthFull();
        addDetail(details, "decisionDialog.reportCode", request.getReport().getCode());
        addDetail(details, "decisionDialog.reportName", request.getReport().getName());
        addDetail(details, "decisionDialog.employee",
                request.getEmployee() == null ? null : request.getEmployee().getFullName());
        addDetail(details, "decisionDialog.position", jobTitleName(request.getPosition()));
        addDetail(details, "decisionDialog.subdivision", subdivisionName(request.getPosition()));
        addDetail(details, "decisionDialog.reason", request.getReason());
        addDetail(details, "decisionDialog.createdDate",
                datatypeFormatter.formatOffsetDateTime(request.getCreatedDate()));
        return details;
    }

    /** The job title of the position held when the grant was given or the request made, if any. */
    @Nullable
    private static String jobTitleName(@Nullable OrgStructurePosition position) {
        return Optional.ofNullable(position)
                .map(OrgStructurePosition::getJobTitle).map(OrgStructureJobTitle::getName).orElse(null);
    }

    @Nullable
    private static String subdivisionName(@Nullable OrgStructurePosition position) {
        return Optional.ofNullable(position)
                .map(OrgStructurePosition::getSubdivision).map(OrgStructureSubdivision::getName).orElse(null);
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
        // the grants of the owner lists change on the tab Owners
        grantsDl.setQuery("select g from umida_ReportAccessGrant g where " + ReportAccessService.inForce("g")
                + " and g.source <> :ownerSource"
                + managedReportsOnly(grantsDl, "g", " and ") + " order by g.report.name, g.employee.fullName");
        grantsDl.setParameter(TODAY_PARAM, today());
        grantsDl.setParameter("ownerSource", ReportAccessGrantSource.OWNER.getId());
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

    /** A field of a text of several lines; its label is that of the parameter of the dialog. */
    private JmixTextArea textArea() {
        JmixTextArea textArea = uiComponents.create(JmixTextArea.class);
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
