package uz.kapitalbank.umida.report;

import io.jmix.flowui.view.ViewRegistry;
import io.jmix.reports.entity.ReportInputParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.view.report.parameter.ReportParameterCustomDetailView;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The editor of report parameters of our own replaces the one of the add-on under the same id, as the
 * application registers its views after the ones of the add-ons. Not a UI test: the UI test extension sets the
 * view configurations of the registry without that order.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportParameterEditorRegistrationTest {

    @Autowired
    ViewRegistry viewRegistry;

    @Test
    void ourEditorReplacesTheOneOfTheAddOn() {
        assertEquals(ReportParameterCustomDetailView.class,
                viewRegistry.getViewInfo("report_ReportInputParameter.detail").getControllerClass());
        assertEquals(ReportParameterCustomDetailView.class,
                viewRegistry.getDetailViewInfo(ReportInputParameter.class).getControllerClass());
    }
}
