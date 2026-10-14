package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.dto.SelectDto;
import uz.kapitalbank.umida.dto.dq.DqRuleConfig;
import uz.kapitalbank.umida.dto.dq.DqRuleConfig.CrossColumnPair;
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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The form of a {@link DqRuleType#CROSS_SOURCE_AGGREGATED} rule: a read query against the rule's data
 * source and another against a second data source, each returning one row of aggregates, and the pairs
 * of columns that must hold the same value, each column named by its alias in its query.
 * <p>
 * There is at least one pair: the form starts with an empty one, and the last one cannot be removed.
 * The rule has no threshold — it passes only when every pair matches — so the form sets none.
 */
@Route(value = "dq-rules/cross-source/:id", layout = MainView.class)
@ViewController(id = "umida_DqRule.crossSourceDetail")
@ViewDescriptor(path = "dq-rule-cross-source-detail-view.xml")
@EditedEntityContainer("dqRuleDc")
public class DqRuleCrossSourceDetailView extends AbstractDqRuleDetailView {

    @Autowired
    private DqDataSourceProvider dataSourceProvider;

    @Autowired
    private DialogWindows dialogWindows;

    @Autowired
    private UiComponents uiComponents;

    @ViewComponent
    private JmixSelect<String> dataSourceField;

    @ViewComponent
    private JmixSelect<String> crossDataSourceField;

    @ViewComponent
    private CodeEditor primarySqlField;

    @ViewComponent
    private CodeEditor secondarySqlField;

    @ViewComponent
    private VerticalLayout columnPairRowsBox;

    private final List<ColumnPairRow> columnPairRows = new ArrayList<>();

    /**
     * Guards against rewriting the ruleConfig JSON while the controller fills the config fields from
     * it: their value-change listeners skip the rebuild while true.
     */
    private boolean populating;

    @Subscribe
    public void onInitEntity(final InitEntityEvent<DqRule> event) {
        DqRule rule = event.getEntity();
        rule.setRuleType(DqRuleType.CROSS_SOURCE_AGGREGATED);
        rule.setRuleConfig("{}");
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        List<SelectDto> dataSources = dataSourceProvider.getDataSourceList();
        for (JmixSelect<String> field : List.of(dataSourceField, crossDataSourceField)) {
            field.setItems(dataSources.stream().map(SelectDto::getId).toList());
            field.setItemLabelGenerator(id -> dataSources.stream()
                    .filter(dto -> dto.getId().equals(id))
                    .map(SelectDto::getName)
                    .findFirst()
                    .orElse(id));
        }

        // Any change to a config field rewrites the ruleConfig JSON.
        crossDataSourceField.addValueChangeListener(e -> onConfigFieldChange());
        primarySqlField.addValueChangeListener(e -> onConfigFieldChange());
        secondarySqlField.addValueChangeListener(e -> onConfigFieldChange());
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

    @Subscribe(id = "primarySqlFullScreenButton", subject = "clickListener")
    public void onPrimarySqlFullScreenButtonClick(final ClickEvent<JmixButton> event) {
        openFullScreen(primarySqlField, "ruleConfig.crossPrimarySql");
    }

    @Subscribe(id = "secondarySqlFullScreenButton", subject = "clickListener")
    public void onSecondarySqlFullScreenButtonClick(final ClickEvent<JmixButton> event) {
        openFullScreen(secondarySqlField, "ruleConfig.crossDataSourceSql");
    }

    @Subscribe(id = "addColumnPairButton", subject = "clickListener")
    public void onAddColumnPairButtonClick(final ClickEvent<JmixButton> event) {
        addColumnPairRow(null, null);
        onConfigFieldChange();
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

        crossDataSourceField.setValue(config != null ? config.getCrossDataSource() : null);
        primarySqlField.setValue(config != null && config.getCrossPrimarySql() != null ? config.getCrossPrimarySql() : "");
        secondarySqlField.setValue(config != null && config.getCrossDataSourceSql() != null
                ? config.getCrossDataSourceSql() : "");

        List<CrossColumnPair> pairs = config != null && config.getCrossColumnPairs() != null
                ? config.getCrossColumnPairs() : List.of();
        if (pairs.isEmpty()) {
            addColumnPairRow(null, null);
        } else {
            pairs.forEach(pair -> addColumnPairRow(pair.getPrimaryAlias(), pair.getSecondaryAlias()));
        }
    }

    private void rebuildRuleConfig() {
        DqRuleConfig config = new DqRuleConfig();
        config.setCrossPrimarySql(StringUtils.emptyToNull(primarySqlField.getValue()));
        config.setCrossDataSource(crossDataSourceField.getValue());
        config.setCrossDataSourceSql(StringUtils.emptyToNull(secondarySqlField.getValue()));
        // an incomplete pair is kept, for the validator to point at
        config.setCrossColumnPairs(columnPairRows.stream()
                .map(row -> new CrossColumnPair(StringUtils.emptyToNull(row.primaryAliasField().getValue()),
                        StringUtils.emptyToNull(row.secondaryAliasField().getValue())))
                .toList());
        try {
            getEditedEntity().setRuleConfig(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize rule config", e);
        }
    }

    // ---------------------------------------------------------------------
    // rows of column pairs
    // ---------------------------------------------------------------------

    /** Appends a row of two alias fields and a button removing it. */
    private void addColumnPairRow(@Nullable String primaryAlias, @Nullable String secondaryAlias) {
        TypedTextField<String> primaryAliasField = createAliasField("dqRuleCrossSourceDetailView.primaryAlias",
                primaryAlias);
        TypedTextField<String> secondaryAliasField = createAliasField("dqRuleCrossSourceDetailView.secondaryAlias",
                secondaryAlias);

        Span equalsSign = uiComponents.create(Span.class);
        equalsSign.setText("=");

        JmixButton removeButton = uiComponents.create(JmixButton.class);
        removeButton.setIcon(VaadinIcon.TRASH.create());
        removeButton.addThemeNames("tertiary-inline", "error");
        removeButton.setTooltipText(messageBundle.getMessage("dqRuleCrossSourceDetailView.removeColumnPair"));

        HorizontalLayout layout = uiComponents.create(HorizontalLayout.class);
        layout.setWidthFull();
        layout.setPadding(false);
        layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        layout.add(primaryAliasField, equalsSign, secondaryAliasField, removeButton);
        layout.setFlexGrow(1, primaryAliasField, secondaryAliasField);

        ColumnPairRow row = new ColumnPairRow(layout, primaryAliasField, secondaryAliasField, removeButton);
        removeButton.addClickListener(e -> removeColumnPairRow(row));
        columnPairRows.add(row);
        columnPairRowsBox.add(layout);
        updateRemoveButtons();
    }

    private TypedTextField<String> createAliasField(String placeholderKey, @Nullable String value) {
        @SuppressWarnings("unchecked")
        TypedTextField<String> field = uiComponents.create(TypedTextField.class);
        field.setPlaceholder(messageBundle.getMessage(placeholderKey));
        field.setAriaLabel(messageBundle.getMessage(placeholderKey));
        field.setValue(value != null ? value : "");
        field.addValueChangeListener(e -> onConfigFieldChange());
        return field;
    }

    private void removeColumnPairRow(ColumnPairRow row) {
        if (columnPairRows.size() <= 1) {
            return;
        }
        columnPairRows.remove(row);
        columnPairRowsBox.remove(row.layout());
        updateRemoveButtons();
        onConfigFieldChange();
    }

    /** The last remaining row stays: a rule compares at least one pair. */
    private void updateRemoveButtons() {
        boolean removable = columnPairRows.size() > 1;
        columnPairRows.forEach(row -> row.removeButton().setEnabled(removable));
    }

    private record ColumnPairRow(HorizontalLayout layout,
                                 TypedTextField<String> primaryAliasField,
                                 TypedTextField<String> secondaryAliasField,
                                 JmixButton removeButton) {
    }

    @Nullable
    @Override
    protected Component resolveComponent(String field) {
        return switch (field) {
            case DqRuleValidationError.FIELD_CROSS_PRIMARY_SQL -> primarySqlField;
            case DqRuleValidationError.FIELD_CROSS_DATA_SOURCE -> crossDataSourceField;
            case DqRuleValidationError.FIELD_CROSS_DATA_SOURCE_SQL -> secondarySqlField;
            case DqRuleValidationError.FIELD_CROSS_COLUMN_PAIRS -> firstAliasFieldToFix();
            default -> null;
        };
    }

    /** The first empty alias field, or the first one when all are filled. */
    @Nullable
    private Component firstAliasFieldToFix() {
        return columnPairRows.stream()
                .flatMap(row -> Stream.of(row.primaryAliasField(), row.secondaryAliasField()))
                .filter(field -> field.getValue() == null || field.getValue().isBlank())
                .findFirst()
                .map(Component.class::cast)
                .orElseGet(() -> columnPairRows.isEmpty() ? null : columnPairRows.get(0).primaryAliasField());
    }
}
