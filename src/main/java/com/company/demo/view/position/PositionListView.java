package com.company.demo.view.position;

import com.company.demo.entity.orgstructure.Position;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "positions", layout = MainView.class)
@ViewController(id = "demo_Position.list")
@ViewDescriptor(path = "position-list-view.xml")
@LookupComponent("positionsDataGrid")
@DialogMode(width = "64em")
public class PositionListView extends StandardListView<Position> {
}