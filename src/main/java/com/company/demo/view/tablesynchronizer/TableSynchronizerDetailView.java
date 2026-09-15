package com.company.demo.view.tablesynchronizer;

import com.company.demo.entity.tablesync.TableCol;
import com.company.demo.entity.tablesync.TableSynchronizer;
import com.company.demo.service.tablesync.TableColConfigService;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detail view of a table synchronizer.
 *
 * <p>All attributes but {@code tableColConfig} are edited by standard form fields bound to the
 * entity. {@code tableColConfig} holds a JSON document, so it is edited by a dedicated area of
 * column rows built here: the stored JSON is read into {@link TableCol} records at open, the user
 * adds and deletes rows, and the rows are written back as JSON on save.
 */
@Route(value = "table-synchronizers/:id", layout = MainView.class)
@ViewController(id = "demo_TableSynchronizer.detail")
@ViewDescriptor(path = "table-synchronizer-detail-view.xml")
@EditedEntityContainer("tableSynchronizerDc")
public class TableSynchronizerDetailView extends StandardDetailView<TableSynchronizer> {

    // Column-row geometry: the three text/type inputs share the free width, the last two are fixed.
    private static final double NAME_FLEX_GROW = 3;
    private static final double JAVA_TYPE_FLEX_GROW = 3;
    private static final double SQL_TYPE_FLEX_GROW = 3;
    private static final String NULLABLE_WIDTH = "6em";
    private static final String FOREIGN_KEY_WIDTH = "6em";
    private static final String REMOVE_WIDTH = "3em";

    @Autowired
    private UiComponents uiComponents;

    @Autowired
    private Messages messages;

    @Autowired
    private TableColConfigService tableColConfigService;

    @ViewComponent
    private VerticalLayout columnsBox;

    /** One entry per column row currently rendered in {@link #columnsBox}, in display order. */
    private final List<ColumnRow> columnRows = new ArrayList<>();

    @Subscribe
    public void onReady(final ReadyEvent event) {
        columnsBox.removeAll();
        columnRows.clear();
        columnsBox.add(createHeaderRow());

        List<TableCol> columns = tableColConfigService.parseColumns(getEditedEntity().getTableColConfig());
        if (columns.isEmpty()) {
            // A synchronizer always needs at least one column, so start the user off with a row.
            addColumnRow(null);
        } else {
            columns.forEach(this::addColumnRow);
        }
    }

    @Subscribe(id = "addColumnButton", subject = "clickListener")
    public void onAddColumnButtonClick(final ClickEvent<JmixButton> event) {
        addColumnRow(null).nameField.focus();
    }

    /** Every column must be complete and named once, otherwise the JSON built on save is unusable. */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        ValidationErrors errors = new ValidationErrors();
        if (columnRows.isEmpty()) {
            errors.add(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.emptyError"));
        }
        Set<String> names = new HashSet<>();
        for (ColumnRow row : columnRows) {
            String name = normalizedName(row);
            if (name == null) {
                errors.add(row.nameField, messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.nameRequired"));
            } else if (!names.add(name.toLowerCase())) {
                errors.add(row.nameField,
                        messages.formatMessage(TableSynchronizerDetailView.class, "tableColumns.duplicateName", name));
            }
            if (row.javaTypeField.getValue() == null || row.javaTypeField.getValue().isBlank()) {
                errors.add(row.javaTypeField, messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.javaTypeRequired"));
            }
            if (row.sqlTypeField.getValue() == null) {
                errors.add(row.sqlTypeField, messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.sqlTypeRequired"));
            }
        }
        event.addErrors(errors);
    }

    /** Converts the column rows to the JSON stored in {@code tableColConfig}. */
    @Subscribe
    public void onBeforeSave(final BeforeSaveEvent event) {
        getEditedEntity().setTableColConfig(tableColConfigService.toJson(collectColumns()));
    }

    private List<TableCol> collectColumns() {
        List<TableCol> columns = new ArrayList<>(columnRows.size());
        for (ColumnRow row : columnRows) {
            // validation ran first and blocks the save while a name or a type is missing
            columns.add(new TableCol(
                    normalizedName(row),
                    row.javaTypeField.getValue(),
                    row.sqlTypeField.getValue(),
                    Boolean.TRUE.equals(row.nullableField.getValue()),
                    Boolean.TRUE.equals(row.foreignKeyField.getValue())));
        }
        return columns;
    }

    @Nullable
    private String normalizedName(ColumnRow row) {
        String name = row.nameField.getValue();
        return name == null || name.isBlank() ? null : name.trim();
    }

    private HorizontalLayout createHeaderRow() {
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("table-col-header");
        header.setWidthFull();
        header.setPadding(false);
        header.add(headerLabel("tableColumns.name", NAME_FLEX_GROW));
        header.add(headerLabel("tableColumns.javaType", JAVA_TYPE_FLEX_GROW));
        header.add(headerLabel("tableColumns.sqlType", SQL_TYPE_FLEX_GROW));

        Span nullableLabel = new Span(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.nullable"));
        nullableLabel.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        nullableLabel.setWidth(NULLABLE_WIDTH);
        header.add(nullableLabel);

        Span foreignKeyLabel = new Span(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.foreignKey"));
        foreignKeyLabel.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        foreignKeyLabel.setWidth(FOREIGN_KEY_WIDTH);
        header.add(foreignKeyLabel);

        // keeps the header columns aligned with the rows, which end with a remove button
        Span removeSpacer = new Span();
        removeSpacer.setWidth(REMOVE_WIDTH);
        header.add(removeSpacer);
        return header;
    }

    private Span headerLabel(String messageKey, double flexGrow) {
        Span label = new Span(messages.getMessage(TableSynchronizerDetailView.class, messageKey));
        label.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        label.setWidth("0");
        label.getElement().getStyle().set("flex-grow", String.valueOf(flexGrow));
        return label;
    }

    private ColumnRow addColumnRow(@Nullable TableCol column) {
        ColumnRow row = new ColumnRow(column);
        columnRows.add(row);
        columnsBox.add(row.layout);
        return row;
    }

    private void removeColumnRow(ColumnRow row) {
        columnRows.remove(row);
        columnsBox.remove(row.layout);
    }

    /** One editable column of the synchronized table: the inputs plus the layout holding them. */
    private final class ColumnRow {

        private final HorizontalLayout layout;
        private final TypedTextField<String> nameField;
        private final JmixComboBox<String> javaTypeField;
        private final JmixComboBox<Integer> sqlTypeField;
        private final JmixCheckbox nullableField;
        private final JmixCheckbox foreignKeyField;

        private ColumnRow(@Nullable TableCol column) {
            nameField = createNameField(column);
            javaTypeField = createJavaTypeField(column);
            sqlTypeField = createSqlTypeField(column);
            nullableField = createNullableField(column);
            foreignKeyField = createForeignKeyField(column);

            // Picking a java type fills in the JDBC type it usually maps to, unless the user
            // already chose one; typing over it afterwards always wins.
            javaTypeField.addValueChangeListener(e -> {
                if (!e.isFromClient() || sqlTypeField.getValue() != null) {
                    return;
                }
                Integer suggested = tableColConfigService.suggestSqlType(e.getValue());
                if (suggested != null) {
                    sqlTypeField.setValue(suggested);
                }
            });

            layout = new HorizontalLayout();
            layout.addClassName("table-col-row");
            layout.setWidthFull();
            layout.setPadding(false);
            layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            layout.add(nameField, javaTypeField, sqlTypeField, nullableField, foreignKeyField, createRemoveButton());
            layout.setFlexGrow(NAME_FLEX_GROW, nameField);
            layout.setFlexGrow(JAVA_TYPE_FLEX_GROW, javaTypeField);
            layout.setFlexGrow(SQL_TYPE_FLEX_GROW, sqlTypeField);
        }

        @SuppressWarnings("unchecked")
        private TypedTextField<String> createNameField(@Nullable TableCol column) {
            TypedTextField<String> field = uiComponents.create(TypedTextField.class);
            field.setWidth("0");
            field.setPlaceholder(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.name"));
            if (column != null) {
                field.setValue(column.name());
            }
            return field;
        }

        @SuppressWarnings("unchecked")
        private JmixComboBox<String> createJavaTypeField(@Nullable TableCol column) {
            JmixComboBox<String> field = uiComponents.create(JmixComboBox.class);
            field.setWidth("0");
            field.setPlaceholder(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.javaType"));
            // the curated list covers the common types; anything else can be typed in
            List<String> items = new ArrayList<>(TableColConfigService.JAVA_TYPES);
            String value = column != null ? column.javaType() : null;
            if (value != null && !items.contains(value)) {
                items.add(value);
            }
            field.setItems(items);
            field.setAllowCustomValue(true);
            field.addCustomValueSetListener(e -> {
                String custom = e.getDetail();
                if (custom == null || custom.isBlank()) {
                    return;
                }
                if (!items.contains(custom)) {
                    items.add(custom);
                    field.setItems(items);
                }
                field.setValue(custom);
            });
            if (value != null) {
                field.setValue(value);
            }
            return field;
        }

        @SuppressWarnings("unchecked")
        private JmixComboBox<Integer> createSqlTypeField(@Nullable TableCol column) {
            JmixComboBox<Integer> field = uiComponents.create(JmixComboBox.class);
            field.setWidth("0");
            field.setPlaceholder(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.sqlType"));
            List<Integer> items = new ArrayList<>(tableColConfigService.getSqlTypes().keySet());
            Integer value = column != null ? column.sqlType() : null;
            // a code stored by an older config that the picker does not list must stay selectable
            if (value != null && !items.contains(value)) {
                items.add(value);
            }
            field.setItems(items);
            field.setItemLabelGenerator(code -> {
                String name = tableColConfigService.getSqlTypes().get(code);
                return name != null ? name : String.valueOf(code);
            });
            if (value != null) {
                field.setValue(value);
            }
            return field;
        }

        private JmixCheckbox createNullableField(@Nullable TableCol column) {
            JmixCheckbox field = uiComponents.create(JmixCheckbox.class);
            field.setWidth(NULLABLE_WIDTH);
            field.setValue(column != null && column.nullable());
            return field;
        }

        private JmixCheckbox createForeignKeyField(@Nullable TableCol column) {
            JmixCheckbox field = uiComponents.create(JmixCheckbox.class);
            field.setWidth(FOREIGN_KEY_WIDTH);
            field.setValue(column != null && column.foreignKey());
            return field;
        }

        private JmixButton createRemoveButton() {
            JmixButton button = uiComponents.create(JmixButton.class);
            button.setIcon(VaadinIcon.TRASH.create());
            button.setTitle(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.remove"));
            button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
            button.setWidth(REMOVE_WIDTH);
            button.addClickListener(e -> removeColumnRow(this));
            return button;
        }
    }
}
