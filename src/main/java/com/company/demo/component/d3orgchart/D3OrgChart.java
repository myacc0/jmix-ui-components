package com.company.demo.component.d3orgchart;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.shared.Registration;

@Tag("d3-org-chart")
@JsModule("./src/component/d3orgchart/orgchart.js")
@NpmPackage(value = "d3-org-chart", version = "3.1.1")
public class D3OrgChart extends Component implements HasSize {

    public void setData(String json) {
        getElement().callJsFunction("setData", json);
    }

    /**
     * Fired when a chart node is clicked. {@code nodeId} is the {@code id} of the
     * corresponding node in the JSON passed to {@link #setData(String)}.
     */
    @DomEvent("node-click")
    public static class NodeClickEvent extends ComponentEvent<D3OrgChart> {

        private final String nodeId;

        public NodeClickEvent(D3OrgChart source, boolean fromClient,
                              @EventData("event.detail.nodeId") String nodeId) {
            super(source, fromClient);
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }
    }

    public Registration addNodeClickListener(ComponentEventListener<NodeClickEvent> listener) {
        return addListener(NodeClickEvent.class, listener);
    }
}
