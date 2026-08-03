package com.company.demo.view.dqcheckrunnew;


import com.company.demo.dto.DqRuleFilter;
import com.company.demo.dto.SelectDto;
import com.company.demo.entity.DqRule;
import com.company.demo.service.DqDataSourceProvider;
import com.company.demo.service.DqRuleFilterService;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.Metadata;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Route(value = "dq-check-run-new-view", layout = MainView.class)
@ViewController(id = "demo_DqCheckRunNewView")
@ViewDescriptor(path = "dq-check-run-new-view.xml")
public class DqCheckRunNewView extends StandardView {

    @Autowired
    private Metadata metadata;

    @Autowired
    private DqDataSourceProvider dataSourceProvider;

    @Autowired
    private DqRuleFilterService ruleFilterService;

    @Autowired
    private Notifications notifications;

    @ViewComponent
    private MessageBundle messageBundle;

    @ViewComponent
    private InstanceContainer<DqRuleFilter> ruleFilterDc;

    @ViewComponent
    private CollectionContainer<DqRule> dqRulesDc;

    @ViewComponent
    private CollectionLoader<DqRule> dqRulesDl;

    @ViewComponent
    private JmixSelect<String> dataSourceField;

    @ViewComponent
    private JmixComboBox<String> tableNameField;

    @ViewComponent
    private JmixComboBox<String> columnNameField;

    @ViewComponent
    private JmixButton runButton;

    /**
     * Guards against re-entrancy while the controller resets dependent filter attributes
     * (table on data source change, column on table change): those writes fire their own
     * item-property-change events which must not cascade further.
     */
    private boolean cascading;

    @Subscribe
    public void onInit(final InitEvent event) {
        List<SelectDto> dataSources = dataSourceProvider.getDataSourceList();
        dataSourceField.setItems(dataSources.stream().map(SelectDto::getId).collect(Collectors.toList()));
        dataSourceField.setItemLabelGenerator(id -> dataSources.stream()
                .filter(dto -> dto.getId().equals(id))
                .map(SelectDto::getName)
                .findFirst()
                .orElse(id));

        // The condition is assigned once; filtering is driven by the loader parameters.
        dqRulesDl.setCondition(ruleFilterService.createRuleCondition());

        ruleFilterDc.setItem(metadata.create(DqRuleFilter.class));
    }

    @Subscribe(id = "ruleFilterDc", target = Target.DATA_CONTAINER)
    public void onRuleFilterDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<DqRuleFilter> event) {
        if (cascading) {
            return;
        }
        cascading = true;
        try {
            switch (event.getProperty()) {
                case "dataSource" -> applyDataSource((String) event.getValue());
                case "tableName" -> applyTableName((String) event.getValue());
                default -> {
                    // the remaining attributes only narrow down the query
                }
            }
        } finally {
            cascading = false;
        }
        reloadRules();
    }

    /**
     * There is nothing to run while the filter yields no rules, so the button follows the grid
     * content instead of validating on click. It starts disabled (see the descriptor) because the
     * grid is empty until a data source is chosen.
     */
    @Subscribe(id = "dqRulesDc", target = Target.DATA_CONTAINER)
    public void onDqRulesDcCollectionChange(final CollectionContainer.CollectionChangeEvent<DqRule> event) {
        runButton.setEnabled(!event.getSource().getItems().isEmpty());
    }

    @Subscribe(id = "runButton", subject = "clickListener")
    public void onRunButtonClick(final ClickEvent<JmixButton> event) {
        // TODO: start the check run for the filtered rules once the run semantics are defined
        notifications.create(messageBundle.getMessage("dqCheckRunNewView.runNotImplemented"))
                .withType(Notifications.Type.DEFAULT)
                .show();
    }

    /**
     * Table names come from the metadata of the selected data source, so a data source change
     * invalidates the table and column already chosen.
     */
    private void applyDataSource(String dataSource) {
        tableNameField.setItems(dataSource != null
                ? dataSourceProvider.getDataSourceTables(dataSource)
                : List.of());
        columnNameField.setItems(List.of());

        DqRuleFilter filter = ruleFilterDc.getItem();
        filter.setTableName(null);
        filter.setColumnName(null);
    }

    private void applyTableName(String tableName) {
        String dataSource = ruleFilterDc.getItem().getDataSource();
        columnNameField.setItems(dataSource != null && tableName != null
                ? dataSourceProvider.getTableColumns(dataSource, tableName)
                : List.of());

        ruleFilterDc.getItem().setColumnName(null);
    }

    /**
     * Reloads the rule grid for the current filter. The data source is mandatory: until it is
     * chosen there is nothing to run a check against, so the grid stays empty.
     */
    private void reloadRules() {
        DqRuleFilter filter = ruleFilterDc.getItem();
        if (filter.getDataSource() == null) {
            dqRulesDc.setItems(Collections.emptyList());
            return;
        }
        dqRulesDl.setParameters(ruleFilterService.createRuleParameters(filter));
        // a changed filter yields a different result set, so start from the first page again
        dqRulesDl.setFirstResult(0);
        dqRulesDl.load();
    }
}
