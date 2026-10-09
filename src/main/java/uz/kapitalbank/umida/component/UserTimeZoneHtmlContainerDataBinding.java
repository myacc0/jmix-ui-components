package uz.kapitalbank.umida.component;

import com.vaadin.flow.component.HtmlContainer;
import com.vaadin.flow.shared.Registration;
import io.jmix.core.AccessManager;
import io.jmix.core.MetadataTools;
import io.jmix.core.common.event.Subscription;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.metamodel.model.MetaProperty;
import io.jmix.flowui.data.binding.impl.HtmlContainerReadonlyDataBindingImpl;
import io.jmix.flowui.model.InstanceContainer;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Shows the property an HTML component of a descriptor is bound to ({@code <span>}, {@code <description>}
 * … with {@code dataContainer} and {@code property}) the way a field or a grid column shows it.
 * <p>
 * The framework binding formats the bare value, so an {@code OffsetDateTime} keeps the offset it was
 * loaded with instead of being shifted to the user's time zone. This one formats the value with its
 * meta property, which applies the user's time zone and {@code @IgnoreUserTimeZone}.
 */
@Primary
@Component("umida_UserTimeZoneHtmlContainerDataBinding")
public class UserTimeZoneHtmlContainerDataBinding extends HtmlContainerReadonlyDataBindingImpl {

    public UserTimeZoneHtmlContainerDataBinding(MetadataTools metadataTools, AccessManager accessManager) {
        super(metadataTools, accessManager);
    }

    @Override
    public Registration bind(HtmlContainer htmlContainer, InstanceContainer<?> dataContainer, String property) {
        MetaProperty metaProperty = metadataTools
                .resolveMetaPropertyPath(dataContainer.getEntityMetaClass(), property)
                .getMetaProperty();

        Object item = dataContainer.getItemOrNull();
        if (item != null) {
            updateText(htmlContainer, EntityValues.getValueEx(item, property), metaProperty);
        }

        Subscription propertyChangeSubscription = dataContainer.addItemPropertyChangeListener(event -> {
            if (property.equals(event.getProperty())) {
                updateText(htmlContainer, event.getValue(), metaProperty);
            }
        });

        Subscription itemChangeSubscription = dataContainer.addItemChangeListener(event ->
                updateText(htmlContainer,
                        event.getItem() != null ? EntityValues.getValueEx(event.getItem(), property) : null,
                        metaProperty));

        checkPermissions(htmlContainer, dataContainer, property);

        return () -> {
            propertyChangeSubscription.remove();
            itemChangeSubscription.remove();
        };
    }

    private void updateText(HtmlContainer htmlContainer, @Nullable Object value, MetaProperty metaProperty) {
        htmlContainer.setText(metadataTools.format(value, metaProperty));
    }
}
