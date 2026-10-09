package uz.kapitalbank.umida.view.orgstructure;

import uz.kapitalbank.umida.dto.orgstructure.DataAssetChartNode;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.core.metamodel.datatype.DatatypeFormatter;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.image.JmixImage;
import io.jmix.flowui.view.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Read-only card of a data domain or a data product, opened as a dialog when a d3-org-chart
 * node is clicked in {@code DataDomainStructureView} or {@code DataProductStructureView}.
 * Call {@link #setNode(DataAssetChartNode)} before opening — the card is filled from the
 * clicked chart node, not from a data container.
 * <p>
 * The dialog title is the domain / product name, the header shows the head of the business
 * owner, and the body lists the remaining details of the domain / product.
 */
@Route(value = "data-asset-card", layout = MainView.class)
@ViewController(id = "umida_DataAssetCardView")
@ViewDescriptor(path = "data-asset-card-view.xml")
// no fixed height: the dialog grows with the description and the steward list
@DialogMode(width = "44em", resizable = true)
public class DataAssetCardView extends StandardView {

    private static final String EMPTY_VALUE = "—";

    @ViewComponent
    private JmixImage<?> headPhotoImage;
    @ViewComponent
    private Span headPhotoPlaceholder;
    @ViewComponent
    private H4 headNameLabel;
    @ViewComponent
    private Span headJobTitleLabel;
    @ViewComponent
    private Span headSubdivisionLabel;
    @ViewComponent
    private Span headContactsBox;
    @ViewComponent
    private Span headEmailLabel;

    @ViewComponent
    private Span longNameValue;
    @ViewComponent
    private Span descriptionValue;
    @ViewComponent
    private Span createdDateValue;
    @ViewComponent
    private Span assignDateValue;
    @ViewComponent
    private Span businessOwnerValue;
    @ViewComponent
    private Span stewardsValue;

    @Autowired
    private MessageBundle messageBundle;
    @Autowired
    private DatatypeFormatter datatypeFormatter;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private UiComponents uiComponents;

    public void setNode(DataAssetChartNode node) {
        if (node == null) {
            return;
        }
        // shown as the dialog title, above the head header
        setPageTitle(node.getName());

        setHead(node);
        setDetails(node);
    }

    private void setHead(DataAssetChartNode node) {
        boolean hasHead = StringUtils.isNotBlank(node.getHeadName());
        headNameLabel.setText(hasHead ? node.getHeadName() : messageBundle.getMessage("dataAssetCardView.noHead"));
        headJobTitleLabel.setText(StringUtils.defaultIfBlank(node.getHeadJobTitle(), EMPTY_VALUE));
        headSubdivisionLabel.setText(StringUtils.defaultIfBlank(node.getBusinessOwnerName(), EMPTY_VALUE));

        headContactsBox.setVisible(StringUtils.isNotBlank(node.getHeadEmail()));
        headEmailLabel.setText(StringUtils.defaultString(node.getHeadEmail()));

        setPhoto(node);
    }

    /**
     * Shows the photo of the head when there is one in the file storage, and the initials of
     * the domain / product name otherwise — the same fallback the chart node uses.
     */
    private void setPhoto(DataAssetChartNode node) {
        boolean hasPhoto = StringUtils.isNotBlank(node.getImage());

        headPhotoImage.setVisible(hasPhoto);
        headPhotoPlaceholder.setVisible(!hasPhoto);

        if (hasPhoto) {
            headPhotoImage.setSrc(node.getImage());
            headPhotoImage.setAlt(StringUtils.defaultString(node.getHeadName()));
        } else {
            headPhotoPlaceholder.setText(initials(node.getName()));
        }
    }

    private void setDetails(DataAssetChartNode node) {
        longNameValue.setText(StringUtils.defaultIfBlank(node.getLongName(), EMPTY_VALUE));
        descriptionValue.setText(StringUtils.defaultIfBlank(node.getDescription(), EMPTY_VALUE));
        // date and time in the user's time zone, as the date-time columns of the grids show it
        createdDateValue.setText(node.getCreatedDate() != null
                ? datatypeFormatter.formatLocalDateTime(node.getCreatedDate()
                        .atZoneSameInstant(currentAuthentication.getTimeZone().toZoneId())
                        .toLocalDateTime())
                : EMPTY_VALUE);
        assignDateValue.setText(node.getAssignDate() != null
                ? datatypeFormatter.formatLocalDate(node.getAssignDate())
                : EMPTY_VALUE);
        businessOwnerValue.setText(StringUtils.defaultIfBlank(node.getBusinessOwnerName(), EMPTY_VALUE));

        stewardsValue.removeAll();
        if (node.getStewardNames().isEmpty()) {
            stewardsValue.setText(EMPTY_VALUE);
        } else {
            String stewardNames = StringUtils.join(node.getStewardNames(), ", ");
            stewardsValue.setText(stewardNames);
        }
    }

    private String initials(String name) {
        return Arrays.stream(StringUtils.defaultString(name).split("\\s+"))
                .filter(StringUtils::isNotBlank)
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());
    }
}
