package com.company.demo.component.d3orgchart;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;

@Tag("d3-org-chart")
@JsModule("./src/component/d3orgchart/orgchart.js")
@NpmPackage(value = "d3-org-chart", version = "3.1.1")
public class D3OrgChart extends Component implements HasSize {

    public void setData(String json) {
        getElement().callJsFunction("setData", json);
    }
}
