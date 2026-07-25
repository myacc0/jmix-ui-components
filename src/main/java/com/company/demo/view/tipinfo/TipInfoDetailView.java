package com.company.demo.view.tipinfo;

import com.company.demo.entity.TipInfo;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "tip-infoes/:id", layout = MainView.class)
@ViewController(id = "demo_TipInfo.detail")
@ViewDescriptor(path = "tip-info-detail-view.xml")
@EditedEntityContainer("tipInfoDc")
public class TipInfoDetailView extends StandardDetailView<TipInfo> {
}