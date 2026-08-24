package com.company.demo.view.operation;

import com.company.demo.entity.Operation;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "operations", layout = MainView.class)
@ViewController(id = "demo_Operation.list")
@ViewDescriptor(path = "operation-list-view.xml")
@LookupComponent("operationsDataGrid")
@DialogMode(width = "64em")
public class OperationListView extends StandardListView<Operation> {
}