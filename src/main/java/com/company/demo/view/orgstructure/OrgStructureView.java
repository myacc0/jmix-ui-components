package com.company.demo.view.orgstructure;

import com.company.demo.entity.Department;
import com.company.demo.entity.Employee;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.SupportsTypedValue;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "orgstructure", layout = MainView.class)
@ViewController(id = "demo_OrgStructure")
@ViewDescriptor(path = "orgstructure-view.xml")
public class OrgStructureView extends StandardView {

    @ViewComponent
    private TypedTextField<String> departmentSearchField;

    @ViewComponent
    private CollectionLoader<Department> departmentsDl;
    @ViewComponent
    private CollectionContainer<Department> departmentsDc;
    @ViewComponent
    private CollectionLoader<Employee> employeesDl;
    @ViewComponent
    private TreeDataGrid<Department> departmentsTreeDataGrid;

    private List<Department> allDepartments;

    @Subscribe
    public void onInit(InitEvent event) {
        departmentSearchField.setValueChangeMode(ValueChangeMode.LAZY);
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
            employeesDl.setQuery("select e from demo_Employee e where e.department = :department");
            employeesDl.setParameter("department", d);
            employeesDl.load();
        }
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
