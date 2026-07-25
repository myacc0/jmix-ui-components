package com.company.demo.view.tipinfo;

import com.company.demo.entity.TipInfo;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "tip-infoes", layout = MainView.class)
@ViewController(id = "demo_TipInfo.list")
@ViewDescriptor(path = "tip-info-list-view.xml")
@LookupComponent("tipInfoesDataGrid")
@DialogMode(width = "64em")
public class TipInfoListView extends StandardListView<TipInfo> {
}