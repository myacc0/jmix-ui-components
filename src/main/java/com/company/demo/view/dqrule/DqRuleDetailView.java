package com.company.demo.view.dqrule;

import com.company.demo.dto.DqRuleConfig;
import com.company.demo.dto.SelectDto;
import com.company.demo.entity.DqRule;
import com.company.demo.enums.DqRuleType;
import com.company.demo.repository.DqRuleRepository;
import com.company.demo.service.DqDataSourceProvider;
import com.company.demo.view.main.MainView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.formlayout.JmixFormLayout;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.JmixNumberField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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

    @ViewComponent
    private JmixSelect<String> dataSourceField;

    @ViewComponent
    private JmixSelect<String> tableNameField;

    @ViewComponent
    private JmixSelect<String> columnNameField;

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

    /**
     * Guards against feedback loops while the controller sets field values
     * programmatically (populating fields from the stored JSON, clearing on
     * type switch). Field value-change listeners skip JSON rebuild while true.
     */
    private boolean populating;

    @Install(to = "dqRuleDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private Optional<DqRule> loadDelegate(UUID id, FetchPlan fetchPlan) {
        return repository.findById(id, fetchPlan);
    }

    @Subscribe
    public void onInit(final InitEvent event) {
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
        DqRuleConfig config = parseConfig(getEditedEntity().getRuleConfig());
        if (config == null || type == null) {
            return;
        }
        switch (type) {
            case RANGE_NUMBER -> {
                minNumberField.setValue(toDouble(config.getMin()));
                maxNumberField.setValue(toDouble(config.getMax()));
                minIncludedField.setValue(Boolean.TRUE.equals(config.getMinIncluded()));
                maxIncludedField.setValue(Boolean.TRUE.equals(config.getMaxIncluded()));
            }
            case RANGE_DATE -> {
                minDateField.setValue(toDate(config.getMin()));
                maxDateField.setValue(toDate(config.getMax()));
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
                case REGEXP -> config.setRegexp(emptyToNull(regexpField.getValue()));
                default -> {
                    // NOT_NULL and not-yet-implemented types serialize to an empty object.
                }
            }
        }
        try {
            getEditedEntity().setRuleConfig(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize rule config", e);
        }
    }

    private DqRuleConfig parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DqRuleConfig.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private LocalDate toDate(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            try {
                return LocalDate.parse(s);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}
