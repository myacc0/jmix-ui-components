package uz.kapitalbank.umida.view.report.parameter;

import com.vaadin.flow.component.AbstractField;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.reports.entity.ParameterType;
import io.jmix.reportsflowui.view.run.ParameterComponentGenerationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.entity.report.ReportInputParameterCustom;
import uz.kapitalbank.umida.service.report.ReportSqlEnumerationService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

/**
 * The fields of the report parameters with the parameter types of our own, in the dialog that asks for the
 * parameters before a run and as the default value in the parameter editor. The add-on takes the strategy
 * by its class, so this one is {@link Primary}.
 *
 * <p>A {@code SQL_ENUMERATION} parameter is a {@code TEXT} one for the add-on, so its field comes from the
 * creator of the text fields, which is replaced here: a combo box with the values of the query of the
 * parameter. A query that fails leaves the list empty, with the reason under the field.
 *
 * <p>The add-on formats the required message with the class of the strategy, so this package has the
 * {@code error.paramIsRequiredButEmpty} key of its own.
 */
@Primary
@Component("umida_ReportCustomParameterComponentGenerationStrategy")
public class ReportCustomParameterComponentGenerationStrategy extends ParameterComponentGenerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(ReportCustomParameterComponentGenerationStrategy.class);

    private final ReportSqlEnumerationService sqlEnumerationService;

    public ReportCustomParameterComponentGenerationStrategy(ReportSqlEnumerationService sqlEnumerationService) {
        this.sqlEnumerationService = sqlEnumerationService;

        FieldCreator<?> textFieldCreator = fieldCreationMapping.get(ParameterType.TEXT);
        FieldCreator<AbstractField> textOrSqlEnumerationCreator = parameter ->
                sqlEnumerationService.isSqlEnumeration(parameter)
                        ? createSqlEnumerationField((ReportInputParameterCustom) parameter)
                        : textFieldCreator.createField(parameter);

        Map<ParameterType, FieldCreator<?>> mapping = new EnumMap<>(ParameterType.class);
        mapping.putAll(fieldCreationMapping);
        mapping.put(ParameterType.TEXT, textOrSqlEnumerationCreator);
        fieldCreationMapping = mapping;
    }

    @SuppressWarnings("unchecked")
    protected JmixComboBox<String> createSqlEnumerationField(ReportInputParameterCustom parameter) {
        JmixComboBox<String> comboBox = uiComponents.create(JmixComboBox.class);

        Map<String, String> options = Map.of();
        if (sqlEnumerationService.isConfigured(parameter)) {
            try {
                options = sqlEnumerationService.loadOptions(parameter);
            } catch (ReportSqlEnumerationService.SqlEnumerationException e) {
                log.warn("No values for the report parameter '{}': {}", parameter.getAlias(), e.getMessage());
                comboBox.setErrorMessage(e.getMessage());
                comboBox.setInvalid(true);
            }
        }

        Map<String, String> captions = options;
        comboBox.setItems(new ArrayList<>(options.keySet()));
        comboBox.setItemLabelGenerator(value -> captions.getOrDefault(value, value));
        return comboBox;
    }
}
