package com.company.demo.view.demopvttbl;


import com.company.demo.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import io.jmix.pivottableflowui.component.PivotTable;
import io.jmix.pivottableflowui.export.PivotTableExcelExporter;
import io.jmix.pivottableflowui.export.PivotTableExporter;

@Route(value = "demo-pvttbl", layout = MainView.class)
@ViewController(id = "demo_DemoPvttbl")
@ViewDescriptor(path = "demo-pvttbl.xml")
public class DemoPvttbl extends StandardView {

    @ViewComponent
    private PivotTable<Object> pivotTable;

    private PivotTableExporter pivotTableExport;

    @Subscribe
    public void onInit(final InitEvent event) {
        PivotTableExcelExporter pivotTableExcelExporter = getApplicationContext()
                .getBean(PivotTableExcelExporter.class);
        pivotTableExport = getApplicationContext()
                .getBean(PivotTableExporter.class, pivotTable, pivotTableExcelExporter);
    }

    @Subscribe(id = "exportButton", subject = "clickListener")
    public void onExportButtonClick(final ClickEvent<JmixButton> event) {
        pivotTableExport.exportTableToXls();
    }

}