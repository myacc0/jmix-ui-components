package com.company.demo.dq;

import com.company.demo.DemoApplication;
import com.company.demo.dto.SelectDto;
import com.company.demo.entity.dq.DqRule;
import com.company.demo.service.DqDataSourceProvider;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.company.demo.view.dqrule.DqRuleListView;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.DataGridColumn;
import io.jmix.flowui.component.grid.headerfilter.DataGridHeaderFilter;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.lang.Nullable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data source, schema and table columns filter on values that live in the databases, so their
 * header filters offer a combo box of the real values instead of a free-text field. Each list is
 * loaded from the filter above it and stays empty until that one is set.
 */
@UiTest
@SpringBootTest(classes = {DemoApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqRuleListFilterUiTest {

    private static final String DATA_SOURCE = "dataSource";
    private static final String DB_SCHEMA = "dbSchema";
    private static final String TABLE_NAME = "tableName";

    @Autowired
    ViewNavigators viewNavigators;

    @Autowired
    DqDataSourceProvider dataSourceProvider;

    @Test
    void onlyTheDataSourceFilterHasOptionsBeforeAnythingIsChosen() {
        DataGrid<DqRule> grid = openRuleGrid();

        assertEquals(dataSourceProvider.getDataSourceList().stream().map(SelectDto::getId).toList(),
                filterOptions(grid, DATA_SOURCE),
                "the data source filter offers the configured stores");
        assertTrue(filterOptions(grid, DB_SCHEMA).isEmpty(),
                "the schema filter has nothing to offer without a data source");
        assertTrue(filterOptions(grid, TABLE_NAME).isEmpty(),
                "the table filter has nothing to offer without a schema");
    }

    @Test
    void schemasLoadFromTheChosenDataSourceAndTablesFromTheChosenSchema() {
        DataGrid<DqRule> grid = openRuleGrid();
        String dataSource = "dwh";

        setFilter(grid, DATA_SOURCE, dataSource);

        List<String> schemas = dataSourceProvider.getDataSourceSchemas(dataSource);
        assertFalse(schemas.isEmpty(), "the test needs a store with at least one schema");
        assertEquals(schemas, filterOptions(grid, DB_SCHEMA));
        assertTrue(filterOptions(grid, TABLE_NAME).isEmpty(),
                "the table filter waits for a schema");

        String schema = schemas.get(0);
        setFilter(grid, DB_SCHEMA, schema);
        assertEquals(dataSourceProvider.getDataSourceTables(dataSource, schema), filterOptions(grid, TABLE_NAME));
    }

    @Test
    void clearingTheDataSourceEmptiesTheFiltersBelowIt() {
        DataGrid<DqRule> grid = openRuleGrid();
        setFilter(grid, DATA_SOURCE, "dwh");
        setFilter(grid, DB_SCHEMA, dataSourceProvider.getDataSourceSchemas("dwh").get(0));
        assertFalse(filterOptions(grid, TABLE_NAME).isEmpty());

        setFilter(grid, DATA_SOURCE, null);

        assertTrue(filterOptions(grid, DB_SCHEMA).isEmpty());
        assertTrue(filterOptions(grid, TABLE_NAME).isEmpty());
    }

    @Test
    void clearingTheDataSourceResetsTheValuesBelowIt() {
        DataGrid<DqRule> grid = openRuleGrid();
        String schema = dataSourceProvider.getDataSourceSchemas("dwh").get(0);
        setFilter(grid, DATA_SOURCE, "dwh");
        setFilter(grid, DB_SCHEMA, schema);
        setFilter(grid, TABLE_NAME, filterOptions(grid, TABLE_NAME).get(0));

        setFilter(grid, DATA_SOURCE, null);

        assertNull(filterField(grid, DB_SCHEMA).getValue(),
                "a schema of the previous data source is not left behind");
        assertNull(filterField(grid, TABLE_NAME).getValue(),
                "a table of the previous schema is not left behind");
    }

    @Test
    void aValueTheNewOptionsStillContainIsKept() {
        DataGrid<DqRule> grid = openRuleGrid();
        String schema = dataSourceProvider.getDataSourceSchemas("dwh").get(0);
        setFilter(grid, DATA_SOURCE, "dwh");
        setFilter(grid, DB_SCHEMA, schema);
        String table = filterOptions(grid, TABLE_NAME).get(0);
        setFilter(grid, TABLE_NAME, table);

        // dwholap holds the same schema and tables, so nothing the user chose became stale
        setFilter(grid, DATA_SOURCE, "dwholap");

        assertEquals(schema, filterField(grid, DB_SCHEMA).getValue());
        assertEquals(table, filterField(grid, TABLE_NAME).getValue());
    }

    private DataGrid<DqRule> openRuleGrid() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqRuleListView.class).navigate();
        DqRuleListView view = UiTestUtils.getCurrentView();
        return UiTestUtils.getComponent(view, "dqRulesDataGrid");
    }

    private DataGridHeaderFilter headerFilter(DataGrid<DqRule> grid, String columnKey) {
        DataGridColumn<DqRule> column = grid.getColumnByKey(columnKey);
        return assertInstanceOf(DataGridHeaderFilter.class, column.getHeaderComponent(),
                columnKey + " has a header filter");
    }

    private JmixComboBox<String> filterField(DataGrid<DqRule> grid, String columnKey) {
        //noinspection unchecked
        return assertInstanceOf(JmixComboBox.class, headerFilter(grid, columnKey).getPropertyFilter().getValueComponent(),
                columnKey + " is filtered with a combo box");
    }

    private List<String> filterOptions(DataGrid<DqRule> grid, String columnKey) {
        return filterField(grid, columnKey).getListDataView().getItems().toList();
    }

    /**
     * Sets a filter the way the user does — the options below it follow the value, not the apply
     * button, so that cancelling the popup puts them back in step with the applied value.
     */
    private void setFilter(DataGrid<DqRule> grid, String columnKey, @Nullable String value) {
        filterField(grid, columnKey).setValue(value);
    }
}
