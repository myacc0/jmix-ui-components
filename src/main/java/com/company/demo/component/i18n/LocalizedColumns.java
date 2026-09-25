package com.company.demo.component.i18n;

import com.company.demo.service.i18n.LocalizedValues;
import com.vaadin.flow.component.grid.Grid;
import io.jmix.core.MessageTools;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.flowui.component.ListDataComponent;
import io.jmix.flowui.data.EntityDataUnit;
import org.springframework.stereotype.Component;

/**
 * Shows only the columns of multilingual attributes ({@code xxxRu} / {@code xxxUz})
 * that match the current user's language.
 */
@Component
public class LocalizedColumns {

    private final LocalizedValues localizedValues;
    private final MessageTools messageTools;

    public LocalizedColumns(LocalizedValues localizedValues, MessageTools messageTools) {
        this.localizedValues = localizedValues;
        this.messageTools = messageTools;
    }

    /**
     * Hides the columns of other languages. If the entity message bundle has a key without
     * the language suffix (e.g. {@code DictDataDomain.shortName}), it becomes the header
     * of the visible column.
     */
    public void showCurrentLanguage(Grid<?> grid) {
        MetaClass metaClass = grid instanceof ListDataComponent<?> dataComponent
                && dataComponent.getItems() instanceof EntityDataUnit dataUnit
                ? dataUnit.getEntityMetaClass()
                : null;
        String currentSuffix = localizedValues.currentSuffix();

        for (Grid.Column<?> column : grid.getColumns()) {
            String key = column.getKey();
            if (key == null) {
                continue;
            }
            String suffix = key.endsWith(LocalizedValues.SUFFIX_RU) ? LocalizedValues.SUFFIX_RU
                    : key.endsWith(LocalizedValues.SUFFIX_UZ) ? LocalizedValues.SUFFIX_UZ
                    : null;
            if (suffix == null) {
                continue;
            }
            boolean current = suffix.equals(currentSuffix);
            column.setVisible(current);
            if (current && metaClass != null) {
                setNeutralHeader(column, metaClass, key.substring(0, key.length() - suffix.length()));
            }
        }
    }

    private void setNeutralHeader(Grid.Column<?> column, MetaClass metaClass, String baseName) {
        String caption = messageTools.getPropertyCaption(metaClass, baseName);
        // getPropertyCaption returns "Entity.property" when there is no such message
        if (!caption.equals(metaClass.getJavaClass().getSimpleName() + "." + baseName)) {
            column.setHeader(caption);
        }
    }
}
