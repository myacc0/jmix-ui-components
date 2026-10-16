package uz.kapitalbank.umida.view.dq;


import uz.kapitalbank.umida.dto.dq.DqRuleFilter;
import uz.kapitalbank.umida.dto.SelectDto;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.service.dq.DqDataSourceProvider;
import uz.kapitalbank.umida.service.dq.DqRuleFilterService;
import com.vaadin.flow.data.renderer.Renderer;
import io.jmix.core.Metadata;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Picks the rules to add to a check: the active rules narrowed down by the filter, several at a time.
 * The rules the check already holds are left out, see {@link #setExcludedRuleIds}.
 */
@ViewController(id = "umida_DqRule.lookup")
@ViewDescriptor(path = "dq-rule-lookup-view.xml")
@LookupComponent("dqRulesDataGrid")
@DialogMode(width = "80em", resizable = true)
public class DqRuleLookupView extends StandardListView<DqRule> {

    @Autowired
    private Metadata metadata;

    @Autowired
    private DqDataSourceProvider dataSourceProvider;

    @Autowired
    private DqRuleFilterService ruleFilterService;

    @Autowired
    private DqBadges dqBadges;

    @ViewComponent
    private InstanceContainer<DqRuleFilter> ruleFilterDc;

    @ViewComponent
    private CollectionLoader<DqRule> dqRulesDl;

    @ViewComponent
    private JmixSelect<String> dataSourceField;

    @ViewComponent
    private JmixComboBox<String> tableNameField;

    private Set<UUID> excludedRuleIds = Set.of();

    /**
     * Guards against re-entrancy while the controller resets dependent filter attributes
     * (table on data source change): those writes fire their own
     * item-property-change events which must not cascade further.
     */
    private boolean cascading;

    /** The rules not to offer, as the check holds them already. Set before the view is shown. */
    public void setExcludedRuleIds(Collection<UUID> excludedRuleIds) {
        this.excludedRuleIds = Set.copyOf(excludedRuleIds);
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        List<SelectDto> dataSources = dataSourceProvider.getDataSourceList();
        dataSourceField.setItems(dataSources.stream().map(SelectDto::getId).collect(Collectors.toList()));
        // the data source is optional: the empty item is labeled too, and must not be labeled null
        dataSourceField.setItemLabelGenerator(id -> id == null ? "" : dataSources.stream()
                .filter(dto -> dto.getId().equals(id))
                .map(SelectDto::getName)
                .findFirst()
                .orElse(id));

        // The condition is assigned once; filtering is driven by the loader parameters.
        dqRulesDl.setCondition(ruleFilterService.createRuleCondition());

        ruleFilterDc.setItem(metadata.create(DqRuleFilter.class));
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        reloadRules();
    }

    @Subscribe(id = "ruleFilterDc", target = Target.DATA_CONTAINER)
    public void onRuleFilterDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<DqRuleFilter> event) {
        if (cascading) {
            return;
        }
        cascading = true;
        try {
            // the remaining attributes only narrow down the query
            if ("dataSource".equals(event.getProperty())) {
                applyDataSource((String) event.getValue());
            }
        } finally {
            cascading = false;
        }
        reloadRules();
    }

    @Supply(to = "dqRulesDataGrid.dimension", subject = "renderer")
    private Renderer<DqRule> dqRulesDataGridDimensionRenderer() {
        return dqBadges.renderer(DqBadges.DIMENSION, DqRule::getDimension);
    }

    @Supply(to = "dqRulesDataGrid.severity", subject = "renderer")
    private Renderer<DqRule> dqRulesDataGridSeverityRenderer() {
        return dqBadges.renderer(DqBadges.SEVERITY, DqRule::getSeverity);
    }

    /**
     * Table names come from the metadata of the selected data source, so a data source change
     * invalidates the table already chosen.
     */
    private void applyDataSource(String dataSource) {
        tableNameField.setItems(dataSource != null
                ? dataSourceProvider.getDataSourceTables(dataSource)
                : List.of());

        ruleFilterDc.getItem().setTableName(null);
    }

    /** Reloads the rule grid for the current filter. */
    private void reloadRules() {
        dqRulesDl.setParameters(ruleFilterService.createRuleParameters(ruleFilterDc.getItem(), excludedRuleIds));
        // a changed filter yields a different result set, so start from the first page again
        dqRulesDl.setFirstResult(0);
        dqRulesDl.load();
    }
}
