package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.component.slider.Slider;
import uz.kapitalbank.umida.dto.dq.DqRuleConfig;
import uz.kapitalbank.umida.dto.dq.DqRuleValidationError;
import uz.kapitalbank.umida.dto.SelectDto;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.service.dq.DqDataSourceProvider;
import uz.kapitalbank.umida.service.dq.DqSqlQueryBuilder;
import uz.kapitalbank.umida.utils.DateUtils;
import uz.kapitalbank.umida.utils.JsonUtils;
import uz.kapitalbank.umida.utils.NumberUtils;
import uz.kapitalbank.umida.utils.StringUtils;
import uz.kapitalbank.umida.view.main.MainView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.multiselectcombobox.JmixMultiSelectComboBox;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.JmixNumberField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Route(value = "dq-rules/:id", layout = MainView.class)
@ViewController(id = "umida_DqRule.detail")
@ViewDescriptor(path = "dq-rule-detail-view.xml")
@EditedEntityContainer("dqRuleDc")
public class DqRuleDetailView extends AbstractDqRuleDetailView {
    private static final Logger log = LoggerFactory.getLogger(DqRuleDetailView.class);

    @Autowired
    private DqDataSourceProvider dataSourceProvider;

    @ViewComponent
    private JmixSelect<String> dataSourceField;

    @ViewComponent
    private JmixComboBox<String> dbSchemaField;

    @ViewComponent
    private JmixComboBox<String> tableNameField;

    @ViewComponent
    private JmixComboBox<String> columnNameField;

    @ViewComponent
    private JmixSelect<DqRuleType> ruleTypeField;

    // ----- dynamic ruleConfig containers -----
    @ViewComponent
    private VerticalLayout rangeBox;
    @ViewComponent
    private HorizontalLayout regexpBox;
    @ViewComponent
    private VerticalLayout referentialBox;


    // ----- dynamic ruleConfig fields -----
    @ViewComponent
    private JmixNumberField minNumberField;
    @ViewComponent
    private JmixNumberField maxNumberField;
    @ViewComponent
    private TypedDatePicker<LocalDate> minDateField;
    @ViewComponent
    private TypedDatePicker<LocalDate> maxDateField;
    @ViewComponent
    private JmixCheckbox minIncludedField;
    @ViewComponent
    private JmixCheckbox maxIncludedField;
    @ViewComponent
    private TypedTextField<String> regexpField;
    @ViewComponent
    private JmixComboBox<String> refSchemaField;
    @ViewComponent
    private JmixComboBox<String> refTableField;
    @ViewComponent
    private JmixComboBox<String> refColumnField;
    @ViewComponent
    private JmixCheckbox allowNullField;

    // ----- always-visible config fields (independent of rule type) -----
    @ViewComponent
    private Slider thresholdField;
    @ViewComponent
    private JmixMultiSelectComboBox<String> samplesQueryColumnsField;

    /** Columns of the currently selected table — the item set of {@link #samplesQueryColumnsField}. */
    private List<String> availableColumns = List.of();

    /**
     * Guards against feedback loops while the controller sets field values
     * programmatically (populating fields from the stored JSON, clearing on
     * type switch). Field value-change listeners skip JSON rebuild while true.
     */
    private boolean populating;

    @Subscribe
    public void onInit(final InitEvent event) {
        // custom SQL and cross-source rules are created and edited in forms of their own
        ruleTypeField.setItems(Arrays.stream(DqRuleType.values()).filter(DqRuleType::isStandard).toList());

        List<SelectDto> dataSources = dataSourceProvider.getDataSourceList();
        dataSourceField.setItems(dataSources.stream().map(SelectDto::getId).collect(Collectors.toList()));
        dataSourceField.setItemLabelGenerator(id -> dataSources.stream()
                .filter(dto -> dto.getId().equals(id))
                .map(SelectDto::getName)
                .findFirst()
                .orElse(id));

        // The three location fields form a chain: a data source offers its schemas, a schema its
        // tables, a table its columns. Each step reloads the items of the ones below it; a change
        // made by the user also drops their values, which no longer belong to the new parent.
        dataSourceField.addValueChangeListener(e -> {
            String dataSource = e.getValue();
            List<String> schemas = dataSource != null
                    ? dataSourceProvider.getDataSourceSchemas(dataSource)
                    : List.of();
            dbSchemaField.setItems(schemas);
            // the reference table of a REFERENTIAL rule is looked up in the same data source
            refSchemaField.setItems(schemas);
            if (e.isFromClient()) {
                dbSchemaField.setValue(null);
                tableNameField.setValue(null);
                columnNameField.setValue(null);
                samplesQueryColumnsField.clear();
                refSchemaField.setValue(null);
                refTableField.setValue(null);
                refColumnField.setValue(null);
            }
            reloadTables();
            reloadRefTables();
        });

        dbSchemaField.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                tableNameField.setValue(null);
                columnNameField.setValue(null);
                samplesQueryColumnsField.clear();
            }
            reloadTables();
        });

        tableNameField.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                columnNameField.setValue(null);
                samplesQueryColumnsField.clear();
            }
            reloadColumns();
        });

        // The reference location of a REFERENTIAL rule is the same kind of chain, within the rule's
        // data source.
        refSchemaField.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                refTableField.setValue(null);
                refColumnField.setValue(null);
            }
            reloadRefTables();
            onDynamicFieldChange();
        });

        refTableField.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                refColumnField.setValue(null);
            }
            reloadRefColumns();
            onDynamicFieldChange();
        });

        // Render the config fields matching the selected rule type.
        ruleTypeField.addValueChangeListener(e -> {
            if (populating) {
                return;
            }
            updateVisibility(e.getValue());
            if (e.isFromClient()) {
                // Switching type discards the previous type's config values.
                populating = true;
                try {
                    clearDynamicFields();
                    if (e.getValue() == DqRuleType.REFERENTIAL) {
                        // a reference table is most often found next to the checked one
                        refSchemaField.setValue(dbSchemaField.getValue());
                    }
                } finally {
                    populating = false;
                }
                rebuildRuleConfig();
            }
        });

        // Any change to a dynamic field rewrites the ruleConfig JSON.
        minNumberField.addValueChangeListener(e -> onDynamicFieldChange());
        maxNumberField.addValueChangeListener(e -> onDynamicFieldChange());
        minDateField.addValueChangeListener(e -> onDynamicFieldChange());
        maxDateField.addValueChangeListener(e -> onDynamicFieldChange());
        minIncludedField.addValueChangeListener(e -> onDynamicFieldChange());
        maxIncludedField.addValueChangeListener(e -> onDynamicFieldChange());
        regexpField.addValueChangeListener(e -> onDynamicFieldChange());
        refColumnField.addValueChangeListener(e -> onDynamicFieldChange());
        allowNullField.addValueChangeListener(e -> onDynamicFieldChange());
        thresholdField.addValueChangeListener(e -> onDynamicFieldChange());
        samplesQueryColumnsField.addValueChangeListener(e -> onDynamicFieldChange());
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        DqRule rule = getEditedEntity();
        if (rule.getRuleConfig() == null) {
            rule.setRuleConfig("{}");
        }
        populating = true;
        try {
            DqRuleType type = ruleTypeField.getValue();
            updateVisibility(type);
            populateFieldsFromConfig(type);
        } finally {
            populating = false;
        }
    }

    /**
     * The tables offered for the selected data source and schema. A rule without a schema — one
     * created before the schema became selectable — keeps listing the connection's default schema,
     * so its stored table name is still among the items.
     */
    private void reloadTables() {
        String dataSource = dataSourceField.getValue();
        tableNameField.setItems(dataSource != null
                ? dataSourceProvider.getDataSourceTables(dataSource, dbSchemaField.getValue())
                : List.of());
        reloadColumns();
    }

    /**
     * The columns offered for the selected table, shared by the checked-column picker and the
     * sample-projection picker.
     */
    private void reloadColumns() {
        String dataSource = dataSourceField.getValue();
        String tableName = tableNameField.getValue();
        List<String> columns = dataSource != null && tableName != null
                ? dataSourceProvider.getTableColumns(dataSource, dbSchemaField.getValue(), tableName)
                : List.of();
        availableColumns = columns;
        columnNameField.setItems(columns);
        samplesQueryColumnsField.setItems(columns);
    }

    /**
     * The tables offered for the reference schema. Unlike the rule's own table, a reference table
     * is always picked from an explicitly selected schema.
     */
    private void reloadRefTables() {
        String dataSource = dataSourceField.getValue();
        String refSchema = refSchemaField.getValue();
        refTableField.setItems(dataSource != null && refSchema != null
                ? dataSourceProvider.getDataSourceTables(dataSource, refSchema)
                : List.of());
        reloadRefColumns();
    }

    private void reloadRefColumns() {
        String dataSource = dataSourceField.getValue();
        String refSchema = refSchemaField.getValue();
        String refTable = refTableField.getValue();
        refColumnField.setItems(dataSource != null && refSchema != null && refTable != null
                ? dataSourceProvider.getTableColumns(dataSource, refSchema, refTable)
                : List.of());
    }

    private void onDynamicFieldChange() {
        if (populating) {
            return;
        }
        rebuildRuleConfig();
    }

    private void updateVisibility(DqRuleType type) {
        boolean rangeNumber = type == DqRuleType.RANGE_NUMBER;
        boolean rangeDate = type == DqRuleType.RANGE_DATE;
        rangeBox.setVisible(rangeNumber || rangeDate);
        minNumberField.setVisible(rangeNumber);
        maxNumberField.setVisible(rangeNumber);
        minDateField.setVisible(rangeDate);
        maxDateField.setVisible(rangeDate);
        regexpBox.setVisible(type == DqRuleType.REGEXP);
        referentialBox.setVisible(type == DqRuleType.REFERENTIAL);
    }

    private void clearDynamicFields() {
        minNumberField.clear();
        maxNumberField.clear();
        minDateField.clear();
        maxDateField.clear();
        minIncludedField.setValue(Boolean.FALSE);
        maxIncludedField.setValue(Boolean.FALSE);
        regexpField.clear();
        refSchemaField.clear();
        refTableField.clear();
        refColumnField.clear();
        allowNullField.setValue(Boolean.TRUE);
    }

    private void populateFieldsFromConfig(DqRuleType type) {
        clearDynamicFields();
        DqRuleConfig config = JsonUtils.parseConfig(getEditedEntity().getRuleConfig(), DqRuleConfig.class, objectMapper);

        // Always-visible config fields, independent of rule type.
        Double threshold = config != null ? config.getThreshold() : null;
        thresholdField.setValue(threshold != null ? (int) Math.round(threshold) : 100);
        List<String> samplesQueryColumns = config != null ? config.getSamplesQueryColumns() : null;
        // "*" is the stored form of "every column"; the picker shows it as an empty selection, which
        // reads as the placeholder rather than as a chip per column on a table that has hundreds
        samplesQueryColumnsField.setValue(samplesQueryColumns == null
                        || samplesQueryColumns.contains(DqSqlQueryBuilder.ALL_COLUMNS)
                ? Set.<String>of()
                : new LinkedHashSet<>(samplesQueryColumns));

        if (config == null || type == null) {
            return;
        }
        switch (type) {
            case RANGE_NUMBER -> {
                minNumberField.setValue(NumberUtils.toDouble(config.getMin()));
                maxNumberField.setValue(NumberUtils.toDouble(config.getMax()));
                minIncludedField.setValue(Boolean.TRUE.equals(config.getMinIncluded()));
                maxIncludedField.setValue(Boolean.TRUE.equals(config.getMaxIncluded()));
            }
            case RANGE_DATE -> {
                minDateField.setValue(DateUtils.toDate(config.getMin()));
                maxDateField.setValue(DateUtils.toDate(config.getMax()));
                minIncludedField.setValue(Boolean.TRUE.equals(config.getMinIncluded()));
                maxIncludedField.setValue(Boolean.TRUE.equals(config.getMaxIncluded()));
            }
            case REGEXP -> regexpField.setValue(config.getRegexp() != null ? config.getRegexp() : "");
            case REFERENTIAL -> {
                // in chain order: each value reloads the items the next one is picked from
                refSchemaField.setValue(config.getRefSchema());
                refTableField.setValue(config.getRefTable());
                refColumnField.setValue(config.getRefColumn());
                allowNullField.setValue(!Boolean.FALSE.equals(config.getAllowNull()));
            }
            default -> {
                // NOT_NULL, NOT_BLANK and not-yet-implemented types have no editable fields.
            }
        }
    }

    private void rebuildRuleConfig() {
        DqRuleType type = ruleTypeField.getValue();
        DqRuleConfig config = new DqRuleConfig();
        if (type != null) {
            switch (type) {
                case RANGE_NUMBER -> {
                    config.setMin(minNumberField.getValue());
                    config.setMax(maxNumberField.getValue());
                    config.setMinIncluded(minIncludedField.getValue());
                    config.setMaxIncluded(maxIncludedField.getValue());
                }
                case RANGE_DATE -> {
                    LocalDate min = minDateField.getValue();
                    LocalDate max = maxDateField.getValue();
                    config.setMin(min != null ? min.toString() : null);
                    config.setMax(max != null ? max.toString() : null);
                    config.setMinIncluded(minIncludedField.getValue());
                    config.setMaxIncluded(maxIncludedField.getValue());
                }
                case REGEXP -> config.setRegexp(StringUtils.emptyToNull(regexpField.getValue()));
                case REFERENTIAL -> {
                    config.setRefSchema(refSchemaField.getValue());
                    config.setRefTable(refTableField.getValue());
                    config.setRefColumn(refColumnField.getValue());
                    config.setAllowNull(allowNullField.getValue());
                }
                default -> {
                    // NOT_NULL, NOT_BLANK and not-yet-implemented types serialize to an empty object.
                }
            }
        }
        // Always-visible config fields, independent of rule type.
        config.setThreshold(thresholdField.getValue() != null ? thresholdField.getValue().doubleValue() : null);
        config.setSamplesQueryColumns(samplesProjection());
        try {
            getEditedEntity().setRuleConfig(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize rule config", e);
        }
    }

    /**
     * The sample projection to store. Selecting nothing and selecting every column both mean "the
     * whole row", so both collapse to {@link DqSqlQueryBuilder#ALL_COLUMNS} rather than to a literal
     * column list that the next table change would leave pointing at columns that no longer exist.
     */
    private List<String> samplesProjection() {
        Set<String> selected = samplesQueryColumnsField.getValue();
        if (selected == null || selected.isEmpty()
                || (!availableColumns.isEmpty() && selected.containsAll(availableColumns))) {
            return List.of(DqSqlQueryBuilder.ALL_COLUMNS);
        }
        return List.copyOf(selected);
    }

    /**
     * Maps a logical config field name to the input that edits it, so the error is attached to it.
     * Returns null when no single input owns the value — the error is then shown unattached.
     */
    @Nullable
    @Override
    protected Component resolveComponent(String field) {
        boolean rangeDate = ruleTypeField.getValue() == DqRuleType.RANGE_DATE;
        return switch (field) {
            case DqRuleValidationError.FIELD_TABLE_NAME -> tableNameField;
            case DqRuleValidationError.FIELD_COLUMN_NAME -> columnNameField;
            case DqRuleValidationError.FIELD_RULE_TYPE -> ruleTypeField;
            case DqRuleValidationError.FIELD_THRESHOLD -> thresholdField;
            case DqRuleValidationError.FIELD_REGEXP -> regexpField;
            case DqRuleValidationError.FIELD_MIN -> rangeDate ? minDateField : minNumberField;
            case DqRuleValidationError.FIELD_MAX -> rangeDate ? maxDateField : maxNumberField;
            case DqRuleValidationError.FIELD_REF_SCHEMA -> refSchemaField;
            case DqRuleValidationError.FIELD_REF_TABLE -> refTableField;
            case DqRuleValidationError.FIELD_REF_COLUMN -> refColumnField;
            default -> null;
        };
    }
}
