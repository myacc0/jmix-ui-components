package uz.kapitalbank.umida.component.d3orgchart;

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

    /**
     * Layout of a chart node, see {@code NODE_TEMPLATES} in orgchart.js. Set it before the
     * first {@link #setData(String)} call — the template is read when the chart is built.
     */
    public enum NodeTemplate {
        /** OrgStructureSubdivision title on top, then the employee photo, name and job title. */
        POSITION("position"),
        /**
         * Head photo on the left; business owner, domain / product name and head name on the
         * right — see {@code DataAssetChartNode}.
         */
        DATA_ASSET("data-asset");

        private final String id;

        NodeTemplate(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private NodeTemplate nodeTemplate = NodeTemplate.POSITION;

    public void setData(String json) {
        getElement().callJsFunction("setData", json);
    }

    public NodeTemplate getNodeTemplate() {
        return nodeTemplate;
    }

    public void setNodeTemplate(NodeTemplate nodeTemplate) {
        this.nodeTemplate = nodeTemplate != null ? nodeTemplate : NodeTemplate.POSITION;
        getElement().setAttribute("node-template", this.nodeTemplate.getId());
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
