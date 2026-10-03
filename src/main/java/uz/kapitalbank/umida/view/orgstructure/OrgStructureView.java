package uz.kapitalbank.umida.view.orgstructure;

import uz.kapitalbank.umida.component.LoaderComponent;
import uz.kapitalbank.umida.component.d3orgchart.D3OrgChart;
import uz.kapitalbank.umida.dto.orgstructure.OrgChartNode;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.service.orgstructure.OrgStructureService;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.jmix.core.Messages;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.asynctask.UiAsyncTasks;
import io.jmix.flowui.component.SupportsTypedValue;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.virtuallist.JmixVirtualList;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

@Route(value = "orgstructure", layout = MainView.class)
@ViewController(id = "umida_OrgStructure")
@ViewDescriptor(path = "orgstructure-view.xml")
public class OrgStructureView extends StandardView {

    @ViewComponent
    private TypedTextField<String> employeeSearchField;
    @ViewComponent
    private D3OrgChart d3OrgChart;

    @ViewComponent
    private CollectionContainer<OrgStructureSubdivision> subdivisionsDc;
    @ViewComponent
    private TreeDataGrid<OrgStructureSubdivision> subdivisionsTreeDataGrid;

    @Autowired
    private UiAsyncTasks uiAsyncTasks;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private Messages messages;
    @Autowired
    private OrgStructureService orgStructureService;
    @Autowired
    private DialogWindows dialogWindows;

    final Popover[] searchPopover = {null};

    private List<OrgStructureSubdivision> allSubdivisions;
    /** Nodes currently rendered in the chart, by node id — used to fill the card dialog. */
    private Map<String, OrgChartNode> chartNodesById = new HashMap<>();

    @Subscribe
    public void onInit(InitEvent event) {
        d3OrgChart.addNodeClickListener(this::onChartNodeClick);
    }

    @Subscribe(id = "subdivisionsDl", target = Target.DATA_LOADER)
    public void onSubdivisionsDlPostLoad(final CollectionLoader.PostLoadEvent<OrgStructureSubdivision> e) {
        allSubdivisions = new ArrayList<>(subdivisionsDc.getItems());
    }

    @Subscribe("subdivisionSearchField")
    public void onSubdivisionSearchFieldTypedValueChange(final SupportsTypedValue.TypedValueChangeEvent<TypedTextField<String>, String> e) {
        List<OrgStructureSubdivision> subdivisions = subdivisionsDc.getMutableItems();
        subdivisions.clear();

        String searchTerm = e.getValue();
        if (searchTerm == null || searchTerm.isBlank()) {
            subdivisions.addAll(allSubdivisions);
            expandSubdivisionTreeAllNodes();
            return;
        }

        Set<OrgStructureSubdivision> result = allSubdivisions.stream()
                .filter(d ->
                        d.getName() != null && d.getName().toLowerCase().contains(searchTerm.toLowerCase()))
                .flatMap(d -> collectWithParents(d).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        subdivisions.addAll(result);
        expandSubdivisionTreeAllNodes();
    }

    @Subscribe("subdivisionsTreeDataGrid")
    public void onSubdivisionsTreeDataGridSelection(final SelectionEvent<TreeDataGrid<OrgStructureSubdivision>, OrgStructureSubdivision> e) {
        OrgStructureSubdivision subdivision = e.getFirstSelectedItem().orElse(null);
        if (subdivision == null) {
            chartNodesById = new HashMap<>();
            d3OrgChart.setData(null);
            return;
        }

        List<OrgChartNode> nodes = orgStructureService.getOrgChartNodes(subdivision.getId());
        chartNodesById = nodes.stream()
                .collect(Collectors.toMap(OrgChartNode::getId, node -> node, (first, second) -> first));

        d3OrgChart.setData(orgStructureService.toJson(nodes));
    }

    private void onChartNodeClick(final D3OrgChart.NodeClickEvent event) {
        OrgChartNode node = chartNodesById.get(event.getNodeId());
        // a synthetic subdivision node carries no employee, so there is no card to show
        if (node == null || node.isSubdivisionNode()) {
            return;
        }

        DialogWindow<EmployeeCardView> dialogWindow = dialogWindows.view(this, EmployeeCardView.class)
                .withViewConfigurer(view -> view.setNode(node))
                .build();
        // lands on the vaadin-dialog-overlay element, see employee-card-view.css
        dialogWindow.addClassName(EmployeeCardView.DIALOG_CLASS_NAME);
        dialogWindow.open();
    }

    @Subscribe("employeeSearchField")
    public void onEmployeeSearchFieldTypedValueChange(final SupportsTypedValue.TypedValueChangeEvent<TypedTextField<String>, String> e) {
        Optional.ofNullable(searchPopover[0]).ifPresent(Popover::removeFromParent);

        String searchText = e.getValue();
        if (StringUtils.isBlank(searchText) || !e.isFromClient()) {
            return;
        }

        searchPopover[0] = showSearchFieldPopover(searchText);
    }

    private Popover showSearchFieldPopover(String searchText) {
        LoaderComponent loader = new LoaderComponent();
        loader.setSizeFull();
        Popover popover = new Popover(loader);
        popover.setTarget(employeeSearchField);
        popover.setHeight("16em");
        popover.setCloseOnEsc(true);
        popover.setCloseOnOutsideClick(true);

        employeeSearchField.getElement()
                .executeJs("return this.offsetWidth")
                .then(Integer.class, width -> {
                    popover.setWidth(width + "px");
                    popover.open();
                    loader.startLoading();
                });

        var searchResultSize = 20;
        uiAsyncTasks.supplierConfigurer(() -> orgStructureService.getPositions(searchText, searchResultSize))
                .withResultHandler(employees -> updateSearchPopover(employees, popover))
                .withExceptionHandler(e -> {
                    popover.removeAll();
                    popover.add(new Span(messages.getMessage("something.went.wrong")));
                })
                .supplyAsync();

        return popover;
    }

    private void updateSearchPopover(List<OrgStructurePosition> employees, Popover popover) {
        JmixVirtualList<OrgStructurePosition> virtualList = uiComponents.create(JmixVirtualList.class);
        virtualList.setItems(employees);
        virtualList.setRenderer(createClientsListRenderer(popover));
        virtualList.addClassNames(LumoUtility.Padding.MEDIUM, "popover-inner-list");

        popover.removeAll();
        popover.add(virtualList);
    }

    private ComponentRenderer<Component, OrgStructurePosition> createClientsListRenderer(Popover popover) {
        return new ComponentRenderer<>(employee -> {
            Div div = uiComponents.create(Div.class);
            div.setText(employee.getEmployee().getFullName());
            div.addClassNames(LumoUtility.TextOverflow.ELLIPSIS, LumoUtility.Whitespace.NOWRAP, "btn-list-item");

            div.addClickListener(click -> {
                popover.close();
                employeeSearchField.clear();
                subdivisionsTreeDataGrid.select(employee.getSubdivision());
            });
            return div;
        });
    }

    private Set<OrgStructureSubdivision> collectWithParents(OrgStructureSubdivision d) {
        Set<OrgStructureSubdivision> result = new LinkedHashSet<>();
        while (d != null) {
            result.add(d);
            d = d.getParent();
        }
        return result;
    }

    private void expandSubdivisionTreeAllNodes() {
        subdivisionsDc.getItems().forEach(subdivisionsTreeDataGrid::expand);
    }

}
