package uz.kapitalbank.umida.component.reportgroup;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import uz.kapitalbank.umida.service.report.ReportGroupCustomService;

/**
 * The icon of a report group on the background of its color. The look is in
 * {@code themes/umida/component/report-group.css}; the size follows the {@code --report-group-badge-size}
 * variable, so a grid cell and a preview can show the same badge at different sizes.
 */
public class ReportGroupBadge extends Div {

    private final Icon icon = new Icon();

    public ReportGroupBadge() {
        addClassName("report-group-badge");
        icon.addClassName("report-group-badge-icon");
        add(icon);
    }

    public ReportGroupBadge(String iconName, String color) {
        this();
        setIconName(iconName);
        setColor(color);
    }

    /** A name of the "umida-icons" iconset, without the iconset prefix. */
    public void setIconName(String iconName) {
        icon.setIcon(ReportGroupCustomService.ICONSET, iconName);
    }

    /** A CSS color, {@code #RRGGBB}. */
    public void setColor(String color) {
        getStyle().setBackgroundColor(color);
    }
}
