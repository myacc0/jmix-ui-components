package com.company.demo.view.orgstructure;

import com.company.demo.component.LoaderComponent;
import com.company.demo.component.d3orgchart.D3OrgChart;
import com.company.demo.entity.orgstructure.Department;
import com.company.demo.entity.orgstructure.Position;
import com.company.demo.service.orgstructure.OrgStructureService;
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
@ViewController(id = "demo_OrgStructure")
@ViewDescriptor(path = "orgstructure-view.xml")
public class OrgStructureView extends StandardView {

    @ViewComponent
    private TypedTextField<String> employeeSearchField;
    @ViewComponent
    private D3OrgChart d3OrgChart;

    @ViewComponent
    private CollectionLoader<Department> departmentsDl;
    @ViewComponent
    private CollectionContainer<Department> departmentsDc;
    @ViewComponent
    private TreeDataGrid<Department> departmentsTreeDataGrid;

    @Autowired
    private UiAsyncTasks uiAsyncTasks;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private Messages messages;
    @Autowired
    private OrgStructureService orgStructureService;

    final Popover[] searchPopover = {null};

    private List<Department> allDepartments;

    @Subscribe
    public void onInit(InitEvent event) {
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        departmentsDl.load();
    }

    @Subscribe(id = "departmentsDl", target = Target.DATA_LOADER)
    public void onDepartmentsDlPostLoad(final CollectionLoader.PostLoadEvent<Department> e) {
        allDepartments = new ArrayList<>(departmentsDc.getItems());
        expandDepartmentTreeAllNodes();
    }

    @Subscribe("departmentSearchField")
    public void onDepartmentSearchFieldTypedValueChange(final SupportsTypedValue.TypedValueChangeEvent<TypedTextField<String>, String> e) {
        List<Department> departments = departmentsDc.getMutableItems();
        departments.clear();

        String searchTerm = e.getValue();
        if (searchTerm == null || searchTerm.isBlank()) {
            departments.addAll(allDepartments);
            expandDepartmentTreeAllNodes();
            return;
        }

        Set<Department> result = allDepartments.stream()
                .filter(d ->
                        d.getName() != null && d.getName().toLowerCase().contains(searchTerm.toLowerCase()))
                .flatMap(d -> collectWithParents(d).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        departments.addAll(result);
        expandDepartmentTreeAllNodes();
    }

    @Subscribe("departmentsTreeDataGrid")
    public void onDepartmentsTreeDataGridSelection(final SelectionEvent<TreeDataGrid<Department>, Department> e) {
        Department d = e.getFirstSelectedItem().orElse(null);
        if (d != null) {
            d3OrgChart.setData(orgStructureService.getNodesJsonFromCsv());
        }
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

    private void updateSearchPopover(List<Position> employees, Popover popover) {
        JmixVirtualList<Position> virtualList = uiComponents.create(JmixVirtualList.class);
        virtualList.setItems(employees);
        virtualList.setRenderer(createClientsListRenderer(popover));
        virtualList.addClassNames(LumoUtility.Padding.MEDIUM, "popover-inner-list");

        popover.removeAll();
        popover.add(virtualList);
    }

    private ComponentRenderer<Component, Position> createClientsListRenderer(Popover popover) {
        return new ComponentRenderer<>(employee -> {
            Div div = uiComponents.create(Div.class);
            div.setText(employee.getEmployee().getFullName());
            div.addClassNames(LumoUtility.TextOverflow.ELLIPSIS, LumoUtility.Whitespace.NOWRAP, "btn-list-item");

            div.addClickListener(click -> {
                popover.close();
                employeeSearchField.clear();
                departmentsTreeDataGrid.select(employee.getDepartment());
            });
            return div;
        });
    }

    private Set<Department> collectWithParents(Department d) {
        Set<Department> result = new LinkedHashSet<>();
        while (d != null) {
            result.add(d);
            d = d.getParentDepartment();
        }
        return result;
    }

    private void expandDepartmentTreeAllNodes() {
        departmentsDc.getItems().forEach(departmentsTreeDataGrid::expand);
    }

}
