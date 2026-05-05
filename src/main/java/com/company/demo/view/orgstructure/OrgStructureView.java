package com.company.demo.view.orgstructure;

import com.company.demo.entity.Department;
import com.company.demo.entity.Employee;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.SupportsTypedValue;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

@Route(value = "orgstructure", layout = MainView.class)
@ViewController(id = "demo_OrgStructure")
@ViewDescriptor(path = "orgstructure-view.xml")
public class OrgStructureView extends StandardView {

    @ViewComponent
    private TypedTextField<String> departmentSearchField;

    @ViewComponent
    private TypedTextField<String> employeeSearchField;

    @ViewComponent
    private CollectionLoader<Department> departmentsDl;

    @ViewComponent
    private CollectionLoader<Employee> employeesDl;

    @Subscribe
    public void onInit(InitEvent event) {
        departmentSearchField.setValueChangeMode(ValueChangeMode.LAZY);
        employeeSearchField.setValueChangeMode(ValueChangeMode.LAZY);
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        
    }

    @Subscribe("departmentSearchField")
    public void onDepartmentSearchFieldTypedValueChange(final SupportsTypedValue.TypedValueChangeEvent<TypedTextField<String>, String> e) {
        String searchTerm = e.getValue();
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            departmentsDl.setQuery("select e from demo_Department e");
        } else {
            departmentsDl.setQuery("select e from demo_Department e where lower(e.name) like :search");
            departmentsDl.setParameter("search", "%" + searchTerm.toLowerCase() + "%");
        }
        departmentsDl.load();
    }

    @Subscribe("employeeSearchField")
    public void onEmployeeSearchFieldTypedValueChange(final SupportsTypedValue.TypedValueChangeEvent<TypedTextField<String>, String> e) {
        String searchTerm = e.getValue();
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            employeesDl.setQuery("select e from demo_Employee e");
        } else {
            employeesDl.setQuery("select e from demo_Employee e where lower(e.firstName) like :search or lower(e.lastName) like :search");
            employeesDl.setParameter("search", "%" + searchTerm.toLowerCase() + "%");
        }
        employeesDl.load();
    }

    
}
