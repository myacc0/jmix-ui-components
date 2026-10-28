package uz.kapitalbank.umida.view.report;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.action.entitypicker.EntityLookupAction;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.component.reportgroup.ReportGroupBadge;
import uz.kapitalbank.umida.entity.report.ReportGroupCustom;
import uz.kapitalbank.umida.service.report.ReportGroupCustomService;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates and edits a report group, next to the add-on's {@code report_ReportGroup.detail}, which stays the default
 * editor of report groups: this one is opened by {@link ReportGroupCustomListView}.
 * The code and the localized titles of the add-on are not edited here.
 *
 * <p>The icon and the color are picked from palettes rather than typed: an icon tile picked again
 * clears the icon, and no color picked means the default one, which the palette then shows picked.
 */
@Route(value = "report-groups/:id", layout = MainView.class)
@ViewController(id = "umida_ReportGroupCustom.detail")
@ViewDescriptor(path = "report-group-custom-detail-view.xml")
@EditedEntityContainer("reportGroupDc")
@DialogMode(width = "50em")
public class ReportGroupCustomDetailView extends StandardDetailView<ReportGroupCustom> {

    private static final String SELECTED_CLASS = "selected";

    @Autowired
    private ReportGroupCustomService reportGroupCustomService;

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private Div iconPalette;
    @ViewComponent
    private Div colorPalette;
    @ViewComponent
    private Div preview;
    @ViewComponent
    private Span previewTitle;
    @ViewComponent("parentField.entityLookupAction")
    private EntityLookupAction<ReportGroupCustom> parentLookupAction;

    /** The tiles by icon name and by color, for marking the picked one. */
    private final Map<String, Div> iconTiles = new LinkedHashMap<>();
    private final Map<String, Div> colorTiles = new LinkedHashMap<>();
    private final ReportGroupBadge previewBadge = new ReportGroupBadge();

    @Subscribe
    public void onInit(final InitEvent event) {
        reportGroupCustomService.getIcons().forEach(this::addIconTile);
        ReportGroupCustomService.COLORS.forEach(this::addColorTile);
        preview.addComponentAsFirst(previewBadge);

        // The parent must not be the group itself or a group below it: the hierarchy would get a cycle.
        parentLookupAction.setViewClass(ReportGroupCustomListView.class);
        parentLookupAction.<ReportGroupCustomListView>setViewConfigurer(lookupView -> lookupView.setExcludedGroupIds(
                reportGroupCustomService.getSelfAndDescendantIds(getEditedEntity())));
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        refreshLook();
    }

    @Subscribe(id = "reportGroupDc", target = Target.DATA_CONTAINER)
    public void onReportGroupDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<ReportGroupCustom> event) {
        refreshLook();
    }

    @Subscribe
    public void onValidation(final ValidationEvent event) {
        ReportGroupCustom group = getEditedEntity();
        if (reportGroupCustomService.isCyclicParent(group, group.getParent())) {
            event.getErrors().add(messageBundle.getMessage("cyclicParent"));
        }
    }

    private void addIconTile(String iconName) {
        Div tile = createTile("report-group-icon-tile", iconName);
        tile.add(new Icon(ReportGroupCustomService.ICONSET, iconName));
        tile.addClickListener(e -> pickIcon(iconName));
        tile.getElement().addEventListener("keydown", e -> pickIcon(iconName))
                .setFilter("event.key === 'Enter' || event.key === ' '");
        iconTiles.put(iconName, tile);
        iconPalette.add(tile);
    }

    private void addColorTile(String color) {
        Div tile = createTile("report-group-color-tile", color);
        tile.getStyle().setBackgroundColor(color);
        Icon checkmark = new Icon(ReportGroupCustomService.ICONSET, "checkmark");
        checkmark.addClassName("report-group-color-tile-checkmark");
        tile.add(checkmark);
        tile.addClickListener(e -> pickColor(color));
        tile.getElement().addEventListener("keydown", e -> pickColor(color))
                .setFilter("event.key === 'Enter' || event.key === ' '");
        colorTiles.put(color, tile);
        colorPalette.add(tile);
    }

    /** A focusable radio-like tile, titled with what it picks. */
    private Div createTile(String className, String title) {
        Div tile = new Div();
        tile.addClassName(className);
        tile.getElement().setAttribute("title", title);
        tile.getElement().setAttribute("role", "radio");
        tile.getElement().setAttribute("tabindex", "0");
        return tile;
    }

    private void pickIcon(String iconName) {
        if (isReadOnly()) {
            return;
        }
        ReportGroupCustom group = getEditedEntity();
        group.setIcon(iconName.equals(group.getIcon()) ? null : iconName);
    }

    private void pickColor(String color) {
        if (isReadOnly()) {
            return;
        }
        getEditedEntity().setColor(color);
    }

    /** Marks the picked tiles and shows the group as the list will. */
    private void refreshLook() {
        ReportGroupCustom group = getEditedEntity();
        String color = reportGroupCustomService.getColorOrDefault(group.getColor());

        iconTiles.forEach((iconName, tile) -> markSelected(tile, iconName.equals(group.getIcon())));
        colorTiles.forEach((tileColor, tile) -> markSelected(tile, tileColor.equalsIgnoreCase(color)));

        previewBadge.setIconName(reportGroupCustomService.getIconOrDefault(group.getIcon()));
        previewBadge.setColor(color);
        String title = group.getTitle();
        previewTitle.setText(title == null || title.isBlank() ? messageBundle.getMessage("previewTitle.empty") : title);
        previewTitle.getClassNames().set("report-group-preview-title-empty", title == null || title.isBlank());
    }

    private void markSelected(Div tile, boolean selected) {
        tile.getClassNames().set(SELECTED_CLASS, selected);
        tile.getElement().setAttribute("aria-checked", String.valueOf(selected));
    }
}
