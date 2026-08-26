package com.company.demo.view.dqrulegroup;

import com.company.demo.entity.dq.DqRuleGroup;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "dq-rule-groups", layout = MainView.class)
@ViewController(id = "demo_DqRuleGroup.list")
@ViewDescriptor(path = "dq-rule-group-list-view.xml")
@LookupComponent("dqRuleGroupsDataGrid")
@DialogMode(width = "64em")
public class DqRuleGroupListView extends StandardListView<DqRuleGroup> {
}