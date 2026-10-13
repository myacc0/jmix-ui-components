package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.component.slider.Slider;
import uz.kapitalbank.umida.dto.SelectDto;
import uz.kapitalbank.umida.dto.dq.DqRuleConfig;
import uz.kapitalbank.umida.dto.dq.DqRuleValidationError;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.service.dq.DqDataSourceProvider;
import uz.kapitalbank.umida.utils.JsonUtils;
import uz.kapitalbank.umida.utils.StringUtils;
import uz.kapitalbank.umida.view.main.MainView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * The form of a {@link DqRuleType#CUSTOM_SQL} rule: two user-written read queries against the rule's
 * data source. The metrics query returns the total and the failed counts, from which the check derives
 * the pass rate; the violations query returns the failed records, which are paged through when the
 * violations of an issue are viewed.
 */
@Route(value = "dq-rules/custom-sql/:id", layout = MainView.class)
@ViewController(id = "umida_DqRule.customSqlDetail")
@ViewDescriptor(path = "dq-rule-custom-sql-detail-view.xml")
@EditedEntityContainer("dqRuleDc")
public class DqRuleCustomSqlDetailView extends AbstractDqRuleDetailView {

    @Autowired
    private DqDataSourceProvider dataSourceProvider;

    @Autowired
    private DialogWindows dialogWindows;

    @ViewComponent
    private JmixSelect<String> dataSourceField;

    @ViewComponent
    private CodeEditor sqlField;

    @ViewComponent
    private CodeEditor violationsSqlField;

    @ViewComponent
    private Slider thresholdField;

    /**
     * Guards against rewriting the ruleConfig JSON while the controller fills the config fields from
     * it: their value-change listeners skip the rebuild while true.
     */
    private boolean populating;

    @Subscribe
    public void onInitEntity(final InitEntityEvent<DqRule> event) {
        DqRule rule = event.getEntity();
        rule.setRuleType(DqRuleType.CUSTOM_SQL);
        rule.setRuleConfig("{}");
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        List<SelectDto> dataSources = dataSourceProvider.getDataSourceList();
        dataSourceField.setItems(dataSources.stream().map(SelectDto::getId).toList());
        dataSourceField.setItemLabelGenerator(id -> dataSources.stream()
                .filter(dto -> dto.getId().equals(id))
                .map(SelectDto::getName)
                .findFirst()
                .orElse(id));

        // Any change to a config field rewrites the ruleConfig JSON.
        sqlField.addValueChangeListener(e -> onConfigFieldChange());
        violationsSqlField.addValueChangeListener(e -> onConfigFieldChange());
        thresholdField.addValueChangeListener(e -> onConfigFieldChange());
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        DqRule rule = getEditedEntity();
        if (rule.getRuleConfig() == null) {
            rule.setRuleConfig("{}");
        }
        populating = true;
        try {
            populateFieldsFromConfig();
        } finally {
            populating = false;
        }
    }

    @Subscribe(id = "sqlFullScreenButton", subject = "clickListener")
    public void onSqlFullScreenButtonClick(final ClickEvent<JmixButton> event) {
        openFullScreen(sqlField, "ruleConfig.sql");
    }

    @Subscribe(id = "violationsSqlFullScreenButton", subject = "clickListener")
    public void onViolationsSqlFullScreenButtonClick(final ClickEvent<JmixButton> event) {
        openFullScreen(violationsSqlField, "ruleConfig.violationsSql");
    }

    /** Opens a query in a large editor; the text comes back only when the user applies it. */
    private void openFullScreen(CodeEditor field, String titleKey) {
        dialogWindows.view(this, DqSqlEditorView.class)
                .withViewConfigurer(editor -> {
                    editor.setPageTitle(messageBundle.getMessage(titleKey));
                    editor.setValue(field.getValue());
                    editor.setApplyHandler(field::setValue);
                })
                .open();
    }

    private void onConfigFieldChange() {
        if (populating) {
            return;
        }
        rebuildRuleConfig();
    }

    private void populateFieldsFromConfig() {
        DqRuleConfig config = JsonUtils.parseConfig(getEditedEntity().getRuleConfig(), DqRuleConfig.class, objectMapper);

        sqlField.setValue(config != null && config.getSql() != null ? config.getSql() : "");
        violationsSqlField.setValue(config != null && config.getViolationsSql() != null ? config.getViolationsSql() : "");
        Double threshold = config != null ? config.getThreshold() : null;
        thresholdField.setValue(threshold != null ? (int) Math.round(threshold) : 100);
    }

    private void rebuildRuleConfig() {
        DqRuleConfig config = new DqRuleConfig();
        config.setSql(StringUtils.emptyToNull(sqlField.getValue()));
        config.setViolationsSql(StringUtils.emptyToNull(violationsSqlField.getValue()));
        config.setThreshold(thresholdField.getValue() != null ? thresholdField.getValue().doubleValue() : null);
        try {
            getEditedEntity().setRuleConfig(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize rule config", e);
        }
    }

    @Nullable
    @Override
    protected Component resolveComponent(String field) {
        return switch (field) {
            case DqRuleValidationError.FIELD_SQL -> sqlField;
            case DqRuleValidationError.FIELD_VIOLATIONS_SQL -> violationsSqlField;
            case DqRuleValidationError.FIELD_THRESHOLD -> thresholdField;
            default -> null;
        };
    }
}
