package uz.kapitalbank.umida.component.reportgroup;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * A tile of the list of reports shown as tiles: the badge — the icon on the background of the color — and
 * the caption side by side, the left border in the color. The look is in {@code themes/umida/view/reports.css}.
 */
public class ReportTile extends Div {

    public static final String SELECTED_CLASS_NAME = "selected";

    public ReportTile(String caption, String iconName, String color, String cssClassName) {
        addClassName("report-tile");
        if (cssClassName != null && !cssClassName.isEmpty()) {
            addClassName(cssClassName);
        }
        getStyle().set("--report-tile-color", color);
        setTitle(caption);

        Span captionSpan = new Span(caption);
        captionSpan.addClassName("report-tile-caption");
        add(new ReportGroupBadge(iconName, color), captionSpan);
    }

    public void setSelected(boolean selected) {
        setClassName(SELECTED_CLASS_NAME, selected);
    }

    public boolean isSelected() {
        return hasClassName(SELECTED_CLASS_NAME);
    }
}
