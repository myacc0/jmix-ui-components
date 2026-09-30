package com.company.demo.view.orgstructure;

import com.company.demo.component.LoaderComponent;
import com.company.demo.component.d3orgchart.D3OrgChart;
import com.company.demo.dto.orgstructure.DataAssetChartNode;
import com.company.demo.entity.dict.DictDataDomain;
import com.company.demo.service.orgstructure.DataStructureService;
import com.company.demo.view.main.MainView;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hierarchy of the data domains: the tree on the left, and the d3-org-chart of the subtree
 * of the selected domain on the right. A click on a chart node opens {@link DataAssetCardView}.
 */
@Route(value = "data-domain-structure", layout = MainView.class)
@ViewController(id = "demo_DataDomainStructure")
@ViewDescriptor(path = "data-domain-structure-view.xml")
public class DataDomainStructureView extends StandardView {

    private static final int SEARCH_RESULT_SIZE = 20;

    @ViewComponent
    private TypedTextField<String> domainSearchField;
    @ViewComponent
    private D3OrgChart d3OrgChart;

    @ViewComponent
    private CollectionContainer<DictDataDomain> domainsDc;
    @ViewComponent
    private TreeDataGrid<DictDataDomain> domainsTreeDataGrid;

    @Autowired
    private UiAsyncTasks uiAsyncTasks;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private Messages messages;
    @Autowired
    private DataStructureService dataStructureService;
    @Autowired
    private DialogWindows dialogWindows;

    private Popover searchPopover;
    /** Nodes currently rendered in the chart, by node id — used to fill the card dialog. */
    private Map<String, DataAssetChartNode> chartNodesById = new HashMap<>();

    @Subscribe
    public void onInit(final InitEvent event) {
        d3OrgChart.addNodeClickListener(this::onChartNodeClick);
    }

    @Subscribe(id = "domainsDl", target = Target.DATA_LOADER)
    public void onDomainsDlPostLoad(final CollectionLoader.PostLoadEvent<DictDataDomain> event) {
        domainsDc.getItems().forEach(domainsTreeDataGrid::expand);
    }

    @Subscribe("domainsTreeDataGrid")
    public void onDomainsTreeDataGridSelection(final SelectionEvent<TreeDataGrid<DictDataDomain>, DictDataDomain> event) {
        DictDataDomain domain = event.getFirstSelectedItem().orElse(null);
        if (domain == null) {
            chartNodesById = new HashMap<>();
            d3OrgChart.setData(null);
            return;
        }

        List<DataAssetChartNode> nodes = dataStructureService.getDomainChartNodes(domain.getId());
        chartNodesById = nodes.stream()
                .collect(Collectors.toMap(DataAssetChartNode::getId, node -> node, (first, second) -> first));

        d3OrgChart.setData(dataStructureService.toJson(nodes));
    }

    private void onChartNodeClick(final D3OrgChart.NodeClickEvent event) {
        DataAssetChartNode node = chartNodesById.get(event.getNodeId());
        if (node == null) {
            return;
        }

        dialogWindows.view(this, DataAssetCardView.class)
                .withViewConfigurer(view -> view.setNode(node))
                .open();
    }

    @Subscribe("domainSearchField")
    public void onDomainSearchFieldTypedValueChange(final SupportsTypedValue.TypedValueChangeEvent<TypedTextField<String>, String> event) {
        if (searchPopover != null) {
            searchPopover.removeFromParent();
            searchPopover = null;
        }

        String searchText = event.getValue();
        if (StringUtils.isBlank(searchText) || !event.isFromClient()) {
            return;
        }

        searchPopover = showSearchFieldPopover(searchText);
    }

    private Popover showSearchFieldPopover(String searchText) {
        LoaderComponent loader = new LoaderComponent();
        loader.setSizeFull();
        Popover popover = new Popover(loader);
        popover.setTarget(domainSearchField);
        popover.setHeight("16em");
        popover.setCloseOnEsc(true);
        popover.setCloseOnOutsideClick(true);

        domainSearchField.getElement()
                .executeJs("return this.offsetWidth")
                .then(Integer.class, width -> {
                    popover.setWidth(width + "px");
                    popover.open();
                    loader.startLoading();
                });

        uiAsyncTasks.supplierConfigurer(() -> dataStructureService.searchDomains(searchText, SEARCH_RESULT_SIZE))
                .withResultHandler(domains -> updateSearchPopover(domains, popover))
                .withExceptionHandler(e -> {
                    popover.removeAll();
                    popover.add(new Span(messages.getMessage("something.went.wrong")));
                })
                .supplyAsync();

        return popover;
    }

    private void updateSearchPopover(List<DictDataDomain> domains, Popover popover) {
        JmixVirtualList<DictDataDomain> virtualList = uiComponents.create(JmixVirtualList.class);
        virtualList.setItems(domains);
        virtualList.setRenderer(createSearchResultRenderer(popover));
        virtualList.addClassNames(LumoUtility.Padding.MEDIUM, "popover-inner-list");

        popover.removeAll();
        popover.add(virtualList);
    }

    private ComponentRenderer<Component, DictDataDomain> createSearchResultRenderer(Popover popover) {
        return new ComponentRenderer<>(domain -> {
            Div div = uiComponents.create(Div.class);
            div.setText(domain.getShortName());
            div.addClassNames(LumoUtility.TextOverflow.ELLIPSIS, LumoUtility.Whitespace.NOWRAP, "btn-list-item");

            div.addClickListener(click -> {
                popover.close();
                domainSearchField.clear();
                selectInTree(domain);
            });
            return div;
        });
    }

    /** Selects the tree row of the found domain, which in turn redraws the chart from it. */
    private void selectInTree(DictDataDomain domain) {
        DictDataDomain item = domainsDc.getItemOrNull(domain.getId());
        if (item == null) {
            return;
        }
        domainsTreeDataGrid.select(item);
    }
}
