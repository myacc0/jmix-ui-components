package com.company.demo.view.tablesynchronizer;

import com.company.demo.entity.tablesync.TableCol;
import com.company.demo.entity.tablesync.TableSyncDirection;
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
import io.jmix.flowui.model.InstanceContainer;
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
    private static final double NAME_FLEX_GROW = 2;
    private static final double JAVA_TYPE_FLEX_GROW = 3;
    private static final double SQL_TYPE_FLEX_GROW = 3;
    private static final double FOREIGN_TABLE_GROW = 2;
    private static final double FOREIGN_TABLE_COL_GROW = 2;
    private static final String PRIMARY_KEY_WIDTH = "30px";
    private static final String SELF_REFERENCED_WIDTH = "30px";
    private static final String NULLABLE_WIDTH = "30px";
    private static final String REMOVE_WIDTH = "30px";

    @Autowired
    private UiComponents uiComponents;

    @Autowired
    private Messages messages;

    @Autowired
    private TableColConfigService tableColConfigService;

    @ViewComponent
    private VerticalLayout columnsBox;

    @ViewComponent
    private Span uuidSqlTypeNote;

    /** One entry per column row currently rendered in {@link #columnsBox}, in display order. */
    private final List<ColumnRow> columnRows = new ArrayList<>();

    @Subscribe
    public void onReady(final ReadyEvent event) {
        uuidSqlTypeNote.getStyle().set("color", "var(--lumo-error-text-color)");
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

    @Subscribe(id = "tableSynchronizerDc", target = Target.DATA_CONTAINER)
    public void onTableSynchronizerDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<TableSynchronizer> e) {
        if ("direction".equals(e.getProperty())) {
            updateUuidSqlTypeNote();
        }
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
            String name = normalizedTextField(row.nameField);
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
                    normalizedTextField(row.nameField),
                    row.javaTypeField.getValue(),
                    row.sqlTypeField.getValue(),
                    normalizedTextField(row.foreignTableField),
                    normalizedTextField(row.foreignTableColumnField),
                    Boolean.TRUE.equals(row.primaryKeyField.getValue()),
                    Boolean.TRUE.equals(row.selfReferencedField.getValue()),
                    Boolean.TRUE.equals(row.nullableField.getValue())));
        }
        return columns;
    }

    @Nullable
    private String normalizedTextField(TypedTextField<String> field) {
        String val = field.getValue();
        return val == null || val.isBlank() ? null : val.trim();
    }

    private HorizontalLayout createHeaderRow() {
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("table-col-header");
        header.setWidthFull();
        header.add(headerLabel("tableColumns.name", NAME_FLEX_GROW));
        header.add(headerLabel("tableColumns.javaType", JAVA_TYPE_FLEX_GROW));
        header.add(headerLabel("tableColumns.sqlType", SQL_TYPE_FLEX_GROW));
        header.add(headerLabel("tableColumns.foreignTable", FOREIGN_TABLE_GROW));
        header.add(headerLabel("tableColumns.foreignTableColumn", FOREIGN_TABLE_COL_GROW));

        Span primaryKeyLabel = new Span(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.primaryKey"));
        primaryKeyLabel.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        primaryKeyLabel.setWidth(PRIMARY_KEY_WIDTH);
        header.add(primaryKeyLabel);

        Span selfReferencedLabel = new Span(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.selfReferencedShort"));
        selfReferencedLabel.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        selfReferencedLabel.setWidth(SELF_REFERENCED_WIDTH);
        selfReferencedLabel.setTitle(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.selfReferenced"));
        header.add(selfReferencedLabel);

        Span nullableLabel = new Span(messages.getMessage(TableSynchronizerDetailView.class, "tableColumns.nullable"));
        nullableLabel.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        nullableLabel.setWidth(NULLABLE_WIDTH);
        header.add(nullableLabel);

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
        updateUuidSqlTypeNote();
        return row;
    }

    private void removeColumnRow(ColumnRow row) {
        columnRows.remove(row);
        columnsBox.remove(row.layout);
        updateUuidSqlTypeNote();
    }

    /** Shows the note while any UUID column of a main-to-starrocks synchronizer is not bound as VARCHAR. */
    private void updateUuidSqlTypeNote() {
        TableSyncDirection direction = getEditedEntity().getDirection();
        uuidSqlTypeNote.setVisible(columnRows.stream().anyMatch(row -> tableColConfigService.requiresVarcharSqlType(
                direction, row.javaTypeField.getValue(), row.sqlTypeField.getValue())));
    }

    /** One editable column of the synchronized table: the inputs plus the layout holding them. */
    private final class ColumnRow {

        private final HorizontalLayout layout;
        private final TypedTextField<String> nameField;
        private final JmixComboBox<String> javaTypeField;
        private final JmixComboBox<Integer> sqlTypeField;
        private final TypedTextField<String> foreignTableField;
        private final TypedTextField<String> foreignTableColumnField;
        private final JmixCheckbox primaryKeyField;
        private final JmixCheckbox selfReferencedField;
        private final JmixCheckbox nullableField;

        private ColumnRow(@Nullable TableCol column) {
            nameField = createTextField(column != null ? column.name() : "", "tableColumns.name");
            javaTypeField = createJavaTypeField(column);
            sqlTypeField = createSqlTypeField(column);
            primaryKeyField = createCheckboxField(column != null && column.primaryKey(), PRIMARY_KEY_WIDTH);
            selfReferencedField = createCheckboxField(column != null && column.selfReferenced(), SELF_REFERENCED_WIDTH);
            nullableField = createCheckboxField(column != null && column.nullable(), NULLABLE_WIDTH);
            foreignTableField = createTextField(column != null ? column.foreignTable() : "", "tableColumns.foreignTable");
            foreignTableColumnField = createTextField(column != null ? column.foreignTableColumn() : "", "tableColumns.foreignTableColumn");

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
            javaTypeField.addValueChangeListener(e -> updateUuidSqlTypeNote());
            sqlTypeField.addValueChangeListener(e -> updateUuidSqlTypeNote());
            primaryKeyField.addValueChangeListener(e -> applyPrimaryKeyState());
            applyPrimaryKeyState();

            layout = new HorizontalLayout();
            layout.addClassName("table-col-row");
            layout.setWidthFull();
            layout.setPadding(false);
            layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            layout.add(
                    nameField,
                    javaTypeField,
                    sqlTypeField,
                    foreignTableField,
                    foreignTableColumnField,
                    primaryKeyField,
                    selfReferencedField,
                    nullableField,
                    createRemoveButton());
            layout.setFlexGrow(NAME_FLEX_GROW, nameField);
            layout.setFlexGrow(JAVA_TYPE_FLEX_GROW, javaTypeField);
            layout.setFlexGrow(SQL_TYPE_FLEX_GROW, sqlTypeField);
            layout.setFlexGrow(FOREIGN_TABLE_GROW, foreignTableField);
            layout.setFlexGrow(FOREIGN_TABLE_COL_GROW, foreignTableColumnField);
        }

        /** A primary key column is never nullable and never references a foreign table. */
        private void applyPrimaryKeyState() {
            boolean primaryKey = Boolean.TRUE.equals(primaryKeyField.getValue());
            if (primaryKey) {
                nullableField.setValue(false);
                selfReferencedField.setValue(false);
                foreignTableField.clear();
                foreignTableColumnField.clear();
            }
            nullableField.setEnabled(!primaryKey);
            selfReferencedField.setEnabled(!primaryKey);
            foreignTableField.setEnabled(!primaryKey);
            foreignTableColumnField.setEnabled(!primaryKey);
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

        private TypedTextField<String> createTextField(@Nullable String name, String messageKey) {
            TypedTextField<String> field = uiComponents.create(TypedTextField.class);
            field.setWidth("0");
            field.setPlaceholder(messages.getMessage(TableSynchronizerDetailView.class, messageKey));
            field.setValue(name == null ? "" : name);
            return field;
        }

        private JmixCheckbox createCheckboxField(boolean value, String width) {
            JmixCheckbox field = uiComponents.create(JmixCheckbox.class);
            field.setWidth(width);
            field.setValue(value);
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
