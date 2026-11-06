package uz.kapitalbank.umida.view.report.access;

import com.google.common.base.Strings;
import com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.jmix.core.Messages;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.fragment.Fragment;
import io.jmix.flowui.fragment.FragmentDescriptor;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Supply;
import io.jmix.flowui.view.ViewComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import uz.kapitalbank.umida.dto.report.ReportOwnerMemberItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The list of the business owner of a report: the employees of the owner and of the subdivisions below it,
 * ticked to run the report and to manage the access to it, with a search and a check box ticking all the rows
 * shown. Shared by the tab Business owner of {@link uz.kapitalbank.umida.view.report.ReportCustomDetailView}
 * and the tab Owners of {@link ReportAccessManagementView}.
 *
 * <p>The host hands the rows over with {@link #setItems}, built by
 * {@link uz.kapitalbank.umida.service.report.ReportOwnerMemberService}, and saves them itself: whole with
 * {@link #getItems}, or those the user changed since with {@link #getChangedItems}. The row of the head is
 * locked, and so is the one of the employee given to {@link #setLockedEmployeeId}: the own row of a head or a
 * delegate, who would take away the own right to manage.
 */
@FragmentDescriptor("report-owner-members-fragment.xml")
public class ReportOwnerMembersFragment extends Fragment<VerticalLayout> {

    private static final String MESSAGE_GROUP = "uz.kapitalbank.umida.view.report";

    /** The rights of a row as handed over. */
    private record Rights(boolean run, boolean manage) {
    }

    @ViewComponent
    private CollectionContainer<ReportOwnerMemberItem> ownerMembersDc;
    @ViewComponent
    private TypedTextField<String> ownerMembersSearchField;
    @ViewComponent
    private JmixCheckbox checkAllRunField;

    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private Messages messages;

    /** All the rows; the container holds those the search lets through. */
    private List<ReportOwnerMemberItem> items = new ArrayList<>();
    /** The rights of each row as handed over, by the id of the row. */
    private final Map<UUID, Rights> handedOver = new HashMap<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private boolean editable = true;
    @Nullable
    private String lockedEmployeeId;

    /** The rows to show, as they are saved or as they are to be: the changes are counted from them on. */
    public void setItems(List<ReportOwnerMemberItem> items) {
        this.items = new ArrayList<>(items);
        handedOver.clear();
        items.forEach(item -> handedOver.put(item.getId(), rightsOf(item)));
        showItems();
        fireChange();
    }

    /** All the rows, those the search hides too. */
    public List<ReportOwnerMemberItem> getItems() {
        return List.copyOf(items);
    }

    /** The rows whose rights the user changed since they were handed over. */
    public List<ReportOwnerMemberItem> getChangedItems() {
        return items.stream()
                .filter(item -> !rightsOf(item).equals(handedOver.get(item.getId())))
                .toList();
    }

    public boolean hasChanges() {
        return !getChangedItems().isEmpty();
    }

    /** Puts every row back as it was handed over. */
    public void discardChanges() {
        items.forEach(item -> {
            Rights rights = handedOver.get(item.getId());
            if (rights != null) {
                item.setRun(rights.run());
                item.setManage(rights.manage());
            }
        });
        showItems();
        fireChange();
    }

    /** Whether the user ticks the rows; read-only otherwise. */
    public void setEditable(boolean editable) {
        this.editable = editable;
        showItems();
    }

    /** The employee whose row the user does not change, besides the head: the user's own one. */
    public void setLockedEmployeeId(@Nullable String lockedEmployeeId) {
        this.lockedEmployeeId = lockedEmployeeId;
        showItems();
    }

    /** Called on every tick and whenever the rows are handed over or put back. */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    @Subscribe("ownerMembersSearchField")
    public void onOwnerMembersSearchFieldValueChange(
            final ComponentValueChangeEvent<TypedTextField<String>, String> event) {
        showItems();
    }

    /** Ticks or unticks the right to run for the rows shown, the locked ones aside, which keep theirs. */
    @Subscribe("checkAllRunField")
    public void onCheckAllRunFieldValueChange(final ComponentValueChangeEvent<JmixCheckbox, Boolean> event) {
        if (!event.isFromClient()) {
            return;
        }
        boolean value = Boolean.TRUE.equals(event.getValue());
        ownerMembersDc.getItems().stream()
                .filter(item -> !isLocked(item))
                .forEach(item -> setRight(item, true, value));
        refreshCheckAllRun();
        fireChange();
    }

    /** The name, marked for the head of the owner and for the user's own row. */
    @Supply(to = "ownerMembersDataGrid.employeeName", subject = "renderer")
    private Renderer<ReportOwnerMemberItem> ownerMembersDataGridEmployeeNameRenderer() {
        return new ComponentRenderer<>(item -> {
            Span name = new Span(Strings.nullToEmpty(item.getEmployeeName()));
            if (item.getHead()) {
                name.add(badge(message("ownerMembers.head"), "badge success small"));
            }
            if (isOwnRow(item)) {
                name.add(badge(message("ownerMembers.you"), "badge small"));
            }
            return name;
        });
    }

    /** The subdivision, or a mark for a member who has left the subdivisions of the owner. */
    @Supply(to = "ownerMembersDataGrid.subdivisionName", subject = "renderer")
    private Renderer<ReportOwnerMemberItem> ownerMembersDataGridSubdivisionNameRenderer() {
        return new ComponentRenderer<>(item -> item.getOutside()
                ? badge(message("ownerMembers.outside"), "badge contrast small")
                : new Span(Strings.nullToEmpty(item.getSubdivisionName())));
    }

    @Supply(to = "ownerMembersDataGrid.run", subject = "renderer")
    private Renderer<ReportOwnerMemberItem> ownerMembersDataGridRunRenderer() {
        return new ComponentRenderer<>(item -> rightCheckbox(item, true));
    }

    @Supply(to = "ownerMembersDataGrid.manage", subject = "renderer")
    private Renderer<ReportOwnerMemberItem> ownerMembersDataGridManageRenderer() {
        return new ComponentRenderer<>(item -> rightCheckbox(item, false));
    }

    private void showItems() {
        String search = Strings.nullToEmpty(ownerMembersSearchField.getValue()).trim().toLowerCase(Locale.ROOT);
        ownerMembersDc.setItems(search.isEmpty() ? items : items.stream()
                .filter(item -> contains(item.getEmployeeName(), search)
                        || contains(item.getSubdivisionName(), search)
                        || contains(item.getPositionName(), search))
                .toList());
        refreshCheckAllRun();
    }

    private static boolean contains(@Nullable String text, String search) {
        return Strings.nullToEmpty(text).toLowerCase(Locale.ROOT).contains(search);
    }

    /**
     * The check box "tick all" follows the rows shown, the locked ones aside: ticked when all of them may run,
     * half ticked when some, and off with nobody to tick.
     */
    private void refreshCheckAllRun() {
        List<ReportOwnerMemberItem> members = ownerMembersDc.getItems().stream()
                .filter(item -> !item.getHead() && !isOwnRow(item))
                .toList();
        long running = members.stream().filter(item -> Boolean.TRUE.equals(item.getRun())).count();
        checkAllRunField.setValue(!members.isEmpty() && running == members.size());
        checkAllRunField.setIndeterminate(running > 0 && running < members.size());
        checkAllRunField.setEnabled(!members.isEmpty() && editable);
    }

    /** A check box for the right to run, or to manage; a locked row keeps its ticks. */
    private JmixCheckbox rightCheckbox(ReportOwnerMemberItem item, boolean run) {
        JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
        checkbox.setValue(run ? item.getRun() : item.getManage());
        checkbox.setEnabled(!isLocked(item));
        checkbox.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                setRight(item, run, Boolean.TRUE.equals(event.getValue()));
                refreshCheckAllRun();
                fireChange();
            }
        });
        return checkbox;
    }

    /** Managing the access implies running: unticking run drops manage, ticking manage ticks run. */
    private static void setRight(ReportOwnerMemberItem item, boolean run, boolean value) {
        if (run) {
            item.setRun(value);
            if (!value) {
                item.setManage(false);
            }
        } else {
            item.setManage(value);
            if (value) {
                item.setRun(true);
            }
        }
    }

    private boolean isLocked(ReportOwnerMemberItem item) {
        return !editable || item.getHead() || isOwnRow(item);
    }

    private boolean isOwnRow(ReportOwnerMemberItem item) {
        return lockedEmployeeId != null && lockedEmployeeId.equals(item.getEmployee().getId());
    }

    private static Rights rightsOf(ReportOwnerMemberItem item) {
        return new Rights(Boolean.TRUE.equals(item.getRun()), Boolean.TRUE.equals(item.getManage()));
    }

    private void fireChange() {
        changeListeners.forEach(Runnable::run);
    }

    private Span badge(String text, String themeNames) {
        Span badge = new Span(text);
        badge.getElement().getThemeList().addAll(List.of(themeNames.split(" ")));
        badge.addClassNames(LumoUtility.Margin.Left.SMALL);
        return badge;
    }

    private String message(String key) {
        return messages.getMessage(MESSAGE_GROUP, key);
    }
}
