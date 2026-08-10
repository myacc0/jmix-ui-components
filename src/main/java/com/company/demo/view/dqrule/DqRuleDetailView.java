package com.company.demo.view.dqrule;

import com.company.demo.component.Slider;
import com.company.demo.dto.DqRuleConfig;
import com.company.demo.dto.DqRuleValidationError;
import com.company.demo.dto.SelectDto;
import com.company.demo.entity.DqRule;
import com.company.demo.enums.DqRuleType;
import com.company.demo.repository.DqRuleRepository;
import com.company.demo.service.*;
import com.company.demo.utils.DateUtils;
import com.company.demo.utils.JsonUtils;
import com.company.demo.utils.NumberUtils;
import com.company.demo.utils.StringUtils;
import com.company.demo.view.main.MainView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.formlayout.JmixFormLayout;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.JmixNumberField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.view.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Route(value = "dq-rules/:id", layout = MainView.class)
@ViewController(id = "demo_DqRule.detail")
@ViewDescriptor(path = "dq-rule-detail-view.xml")
@EditedEntityContainer("dqRuleDc")
public class DqRuleDetailView extends StandardDetailView<DqRule> {
    private static final Logger log = LoggerFactory.getLogger(DqRuleDetailView.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DqRuleRepository repository;

    @Autowired
    private DqDataSourceProvider dataSourceProvider;

    @Autowired
    private DqRuleValidator ruleValidator;

    @Autowired
    private DqRuleService ruleService;

    @ViewComponent
    private JmixSelect<String> dataSourceField;

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
    private JmixFormLayout regexpBox;

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

    // ----- always-visible config fields (independent of rule type) -----
    @ViewComponent
    private Slider thresholdField;
    @ViewComponent
    private JmixNumberField sampleSizeField;

    /**
     * Guards against feedback loops while the controller sets field values
     * programmatically (populating fields from the stored JSON, clearing on
     * type switch). Field value-change listeners skip JSON rebuild while true.
     */
    private boolean populating;

    @Install(to = "dqRuleDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private Optional<DqRule> loadDelegate(UUID id, FetchPlan fetchPlan) {
        // Reformat the stored (compact) ruleConfig to pretty JSON for display.
        // Done before the entity enters the DataContext so it is not flagged as a change.
        return repository.findById(id, fetchPlan)
                .map(rule -> {
                    rule.setRuleConfig(JsonUtils.prettifyJson(rule.getRuleConfig(), objectMapper));
                    return rule;
                });
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        // the same bounds the validator enforces, so the field cannot offer a value it would reject
        sampleSizeField.setMin(DqRuleValidator.MIN_SAMPLE_SIZE);
        sampleSizeField.setMax(DqRuleValidator.MAX_SAMPLE_SIZE);
        sampleSizeField.setStep(1);

        List<SelectDto> dataSources = dataSourceProvider.getDataSourceList();
        dataSourceField.setItems(dataSources.stream().map(SelectDto::getId).collect(Collectors.toList()));
        dataSourceField.setItemLabelGenerator(id -> dataSources.stream()
                .filter(dto -> dto.getId().equals(id))
                .map(SelectDto::getName)
                .findFirst()
                .orElse(id));

        dataSourceField.addValueChangeListener(e -> {
            String dataSource = e.getValue();
            tableNameField.setItems(dataSource != null
                    ? dataSourceProvider.getDataSourceTables(dataSource)
                    : List.of());
            if (e.isFromClient()) {
                tableNameField.setValue(null);
                columnNameField.setItems(List.of());
                columnNameField.setValue(null);
            }
        });

        tableNameField.addValueChangeListener(e -> {
            String dataSource = dataSourceField.getValue();
            String tableName = e.getValue();
            columnNameField.setItems(dataSource != null && tableName != null
                    ? dataSourceProvider.getTableColumns(dataSource, tableName)
                    : List.of());
            if (e.isFromClient()) {
                columnNameField.setValue(null);
            }
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
        thresholdField.addValueChangeListener(e -> onDynamicFieldChange());
        sampleSizeField.addValueChangeListener(e -> onDynamicFieldChange());
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
    }

    private void clearDynamicFields() {
        minNumberField.clear();
        maxNumberField.clear();
        minDateField.clear();
        maxDateField.clear();
        minIncludedField.setValue(Boolean.FALSE);
        maxIncludedField.setValue(Boolean.FALSE);
        regexpField.clear();
    }

    private void populateFieldsFromConfig(DqRuleType type) {
        clearDynamicFields();
        DqRuleConfig config = JsonUtils.parseConfig(getEditedEntity().getRuleConfig(), DqRuleConfig.class, objectMapper);

        // Always-visible config fields, independent of rule type.
        Double threshold = config != null ? config.getThreshold() : null;
        thresholdField.setValue(threshold != null ? (int) Math.round(threshold) : 100);
        Integer sampleSize = config != null ? config.getSampleSize() : null;
        sampleSizeField.setValue(sampleSize != null
                ? sampleSize.doubleValue()
                : (double) DqRuleValidator.DEFAULT_SAMPLE_SIZE);

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
            default -> {
                // NOT_NULL and not-yet-implemented types have no editable fields.
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
                default -> {
                    // NOT_NULL and not-yet-implemented types serialize to an empty object.
                }
            }
        }
        // Always-visible config fields, independent of rule type.
        config.setThreshold(thresholdField.getValue() != null ? thresholdField.getValue().doubleValue() : null);
        Double sampleSize = sampleSizeField.getValue();
        config.setSampleSize(sampleSize != null ? (int) Math.round(sampleSize) : null);
        try {
            getEditedEntity().setRuleConfig(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize rule config", e);
        }
    }

    /**
     * Runs on every save (create and update): the rule config must match the selected rule type.
     * Reported errors abort the save and are shown next to the offending field.
     */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        ValidationErrors errors = new ValidationErrors();
        for (DqRuleValidationError error : ruleValidator.validate(getEditedEntity())) {
            errors.add(resolveComponent(error.field()), error.message());
        }
        event.addErrors(errors);
    }

    /** The owner is not asked for: a new rule belongs to whoever is creating it. */
    @Subscribe
    public void onBeforeSave(final BeforeSaveEvent event) {
        ruleService.assignOwner(getEditedEntity());
    }

    /**
     * Maps a logical config field name to the input that edits it, so the error is attached to it.
     * Returns null when no single input owns the value — the error is then shown unattached.
     */
    @Nullable
    private Component resolveComponent(@Nullable String field) {
        if (field == null) {
            return null;
        }
        boolean rangeDate = ruleTypeField.getValue() == DqRuleType.RANGE_DATE;
        return switch (field) {
            case DqRuleValidationError.FIELD_TABLE_NAME -> tableNameField;
            case DqRuleValidationError.FIELD_COLUMN_NAME -> columnNameField;
            case DqRuleValidationError.FIELD_RULE_TYPE -> ruleTypeField;
            case DqRuleValidationError.FIELD_THRESHOLD -> thresholdField;
            case DqRuleValidationError.FIELD_SAMPLE_SIZE -> sampleSizeField;
            case DqRuleValidationError.FIELD_REGEXP -> regexpField;
            case DqRuleValidationError.FIELD_MIN -> rangeDate ? minDateField : minNumberField;
            case DqRuleValidationError.FIELD_MAX -> rangeDate ? maxDateField : maxNumberField;
            default -> null;
        };
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}
