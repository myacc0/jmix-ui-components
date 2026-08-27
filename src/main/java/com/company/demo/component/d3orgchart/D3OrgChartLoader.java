package com.company.demo.component.d3orgchart;

import io.jmix.flowui.xml.layout.loader.AbstractComponentLoader;

public class D3OrgChartLoader extends AbstractComponentLoader<D3OrgChart> {

    @Override
    protected D3OrgChart createComponent() {
        return factory.create(D3OrgChart.class);
    }

    @Override
    public void loadComponent() {
        componentLoader().loadSizeAttributes(resultComponent, element);
        componentLoader().loadClassNames(resultComponent, element);
    }
}
