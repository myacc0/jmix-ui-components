package uz.kapitalbank.umida.service.report;

import io.jmix.reports.converter.GsonConverter;
import io.jmix.reports.entity.Report;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Makes the add-on serialize reports with {@link ReportCustomGsonSerializationSupport}. */
@Primary
@Component("umida_ReportCustomGsonConverter")
public class ReportCustomGsonConverter extends GsonConverter {

    @Override
    public String convertToString(Report report) {
        return new ReportCustomGsonSerializationSupport(beanFactory).convertToString(report);
    }

    @Override
    public Report convertToReport(String json) {
        return new ReportCustomGsonSerializationSupport(beanFactory).convertToReport(json, Report.class);
    }
}
