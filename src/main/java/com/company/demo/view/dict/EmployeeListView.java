package com.company.demo.view.dict;

import com.company.demo.entity.dict.Employee;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "employees", layout = MainView.class)
@ViewController(id = "demo_Employee.list")
@ViewDescriptor(path = "employee-list-view.xml")
@LookupComponent("employeesDataGrid")
@DialogMode(width = "64em")
public class EmployeeListView extends StandardListView<Employee> {
}