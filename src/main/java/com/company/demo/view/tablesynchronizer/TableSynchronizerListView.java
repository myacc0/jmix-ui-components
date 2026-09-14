package com.company.demo.view.tablesynchronizer;

import com.company.demo.entity.tablesync.TableSynchronizer;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "table-synchronizers", layout = MainView.class)
@ViewController(id = "demo_TableSynchronizer.list")
@ViewDescriptor(path = "table-synchronizer-list-view.xml")
@LookupComponent("tableSynchronizersDataGrid")
@DialogMode(width = "64em")
public class TableSynchronizerListView extends StandardListView<TableSynchronizer> {
}