package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.dto.SelectDto;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.repository.DqRuleRepository;
import uz.kapitalbank.umida.service.dq.DqDataSourceProvider;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.querycondition.Condition;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.DataGridColumn;
import io.jmix.flowui.component.grid.headerfilter.DataGridHeaderFilter;
import io.jmix.flowui.component.propertyfilter.PropertyFilter;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;

@Route(value = "dq-rules", layout = MainView.class)
@ViewController(id = "umida_DqRule.list")
@ViewDescriptor(path = "dq-rule-list-view.xml")
@LookupComponent("dqRulesDataGrid")
@DialogMode(width = "64em")
public class DqRuleListView extends StandardListView<DqRule> {

    @Autowired
    private DqRuleRepository repository;

    @Autowired
    private DqBadges dqBadges;

    @Autowired
    private DqDataSourceProvider dataSourceProvider;

    @Autowired
    private UiComponents uiComponents;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @ViewComponent
    private DataGrid<DqRule> dqRulesDataGrid;

    @ViewComponent
    private CollectionLoader<DqRule> dqRulesDl;

    /**
     * The "mine" filter: the rules created by the current user. It carries no value while the
     * checkbox is clear, and the loader then skips it.
     */
    private final PropertyCondition mineCondition = PropertyCondition.equal("createdBy", null).skipNullOrEmpty();

    // The customized header filters of the three location columns.
    private ComboBoxFilter dataSourceFilter;
    private ComboBoxFilter dbSchemaFilter;
    private ComboBoxFilter tableNameFilter;

    /** A column header filter whose value is picked from a combo box. */
    private record ComboBoxFilter(JmixComboBox<String> field, DataGridHeaderFilter headerFilter) {
    }

    /**
     * Guards the cascade while a filter is being reloaded: replacing the items of a combo box
     * clears its value, and putting the value back would otherwise read as a user's change and
     * reset the filters below it.
     */
    private boolean reloading;

    /**
     * The location columns filter on values that only the databases know, so their header filters
     * offer what the data sources actually contain instead of a free-text field. Each list is
     * loaded from the filter above it: the schemas of the chosen data source, the tables of the
     * chosen schema. A filter whose parent is not chosen yet has nothing to offer and stays empty,
     * which also keeps the view from querying the metadata of every store on open.
     */
    @Subscribe
    public void onInit(final InitEvent event) {
        dataSourceFilter = installComboBoxFilter("dataSource", () -> {
            reloadSchemaOptions();
            reloadTableOptions();
        });
        dbSchemaFilter = installComboBoxFilter("dbSchema", this::reloadTableOptions);
        tableNameFilter = installComboBoxFilter("tableName", null);

        List<SelectDto> dataSources = dataSourceProvider.getDataSourceList();
        dataSourceFilter.field().setItems(dataSources.stream().map(SelectDto::getId).toList());
        dataSourceFilter.field().setItemLabelGenerator(id -> dataSources.stream()
                .filter(dto -> dto.getId().equals(id))
                .map(SelectDto::getName)
                .findFirst()
                .orElse(id));

        reloadSchemaOptions();
        installMineCondition();
    }

    /** Adds the "mine" filter next to the conditions of the column header filters, which share the root condition. */
    private void installMineCondition() {
        Condition root = dqRulesDl.getCondition();
        LogicalCondition and = root instanceof LogicalCondition logical ? logical : LogicalCondition.and();
        if (root != null && root != and) {
            and.add(root);
        }
        and.add(mineCondition);
        dqRulesDl.setCondition(and);
    }

    @Subscribe("mineField")
    public void onMineFieldValueChange(final AbstractField.ComponentValueChangeEvent<JmixCheckbox, Boolean> event) {
        mineCondition.setParameterValue(Boolean.TRUE.equals(event.getValue())
                ? currentAuthentication.getUser().getUsername()
                : null);
        dqRulesDl.load();
    }

    /**
     * Replaces the text field of a column's header filter with a combo box, which filters its
     * items as the user types. The filter regenerates its value component whenever the operation
     * changes kind (a value operation to {@code is set}, say), so the combo box is put back each
     * time the new operation still takes a value.
     *
     * @param onValueChange called when the filter value changes, for reloading the filter below
     *                      it. Cancelling the popup restores the applied value and so re-runs it.
     */
    private ComboBoxFilter installComboBoxFilter(String columnKey, @Nullable Runnable onValueChange) {
        DataGridColumn<DqRule> column = dqRulesDataGrid.getColumnByKey(columnKey);
        if (column == null || !(column.getHeaderComponent() instanceof DataGridHeaderFilter headerFilter)) {
            throw new IllegalStateException("Column '" + columnKey + "' has no header filter to customize");
        }

        JmixComboBox<String> comboBox = uiComponents.create(JmixComboBox.class);
        comboBox.setClearButtonVisible(true);
        comboBox.setWidthFull();

        @SuppressWarnings("unchecked")
        PropertyFilter<String> propertyFilter = (PropertyFilter<String>) headerFilter.getPropertyFilter();
        propertyFilter.setValueComponent(comboBox);
        propertyFilter.addOperationChangeListener(e -> {
            if (e.getNewOperation().getType() == PropertyFilter.Operation.Type.VALUE
                    && propertyFilter.getValueComponent() != comboBox) {
                propertyFilter.setValueComponent(comboBox);
            }
        });

        if (onValueChange != null) {
            comboBox.addValueChangeListener(e -> {
                if (!reloading) {
                    onValueChange.run();
                }
            });
        }
        return new ComboBoxFilter(comboBox, headerFilter);
    }

    /** The schemas of the filtered data source, or nothing while no data source is filtered on. */
    private void reloadSchemaOptions() {
        String dataSource = dataSourceFilter.field().getValue();
        reload(dbSchemaFilter, dataSource != null
                ? dataSourceProvider.getDataSourceSchemas(dataSource)
                : List.of());
    }

    /** The tables of the filtered schema, or nothing while no schema is filtered on. */
    private void reloadTableOptions() {
        String dataSource = dataSourceFilter.field().getValue();
        String schema = dbSchemaFilter.field().getValue();
        reload(tableNameFilter, dataSource != null && schema != null
                ? dataSourceProvider.getDataSourceTables(dataSource, schema)
                : List.of());
    }

    /**
     * Offers a filter its new options. A value the new options still contain is kept — the same
     * table can live in several stores, and re-picking it would be busywork. A value they do not
     * contain belongs to the filter above it as it was before, so the filter is reset, and the
     * reset is pushed to the loader: otherwise the grid would stay filtered by a value its header
     * no longer shows.
     */
    private void reload(ComboBoxFilter filter, List<String> options) {
        String current = filter.field().getValue();
        boolean stale = current != null && !options.contains(current);

        reloading = true;
        try {
            filter.field().setItems(options);
            if (current != null && !stale) {
                filter.field().setValue(current);
            }
        } finally {
            reloading = false;
        }

        if (stale) {
            filter.field().clear();
            filter.headerFilter().apply();
        }
    }

    @Supply(to = "dqRulesDataGrid.dimension", subject = "renderer")
    private Renderer<DqRule> dqRulesDataGridDimensionRenderer() {
        return dqBadges.renderer(DqBadges.DIMENSION, DqRule::getDimension);
    }

    @Supply(to = "dqRulesDataGrid.severity", subject = "renderer")
    private Renderer<DqRule> dqRulesDataGridSeverityRenderer() {
        return dqBadges.renderer(DqBadges.SEVERITY, DqRule::getSeverity);
    }

    @Install(to = "dqRulesDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<DqRule> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return repository.findAllSlice(pageable, context).getContent();
    }

    @Install(to = "dqRulesDataGrid.removeAction", subject = "delegate")
    private void dqRulesDataGridRemoveDelegate(final Collection<DqRule> collection) {
        repository.deleteAll(collection);
    }

    @Install(to = "pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return repository.count(context);
    }
}