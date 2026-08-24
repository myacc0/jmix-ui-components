package com.company.demo.view.operation;

import com.company.demo.entity.Operation;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "operations/:id", layout = MainView.class)
@ViewController(id = "demo_Operation.detail")
@ViewDescriptor(path = "operation-detail-view.xml")
@EditedEntityContainer("operationDc")
public class OperationDetailView extends StandardDetailView<Operation> {
}