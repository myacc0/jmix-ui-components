package uz.kapitalbank.umida.view.report.parameter;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.Stores;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.reports.entity.ParameterType;
import io.jmix.reportsflowui.view.parameter.ReportParameterDetailView;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.report.ReportInputParameterCustom;
import uz.kapitalbank.umida.enums.report.ReportCustomParameterType;
import uz.kapitalbank.umida.service.report.ReportSqlEnumerationService;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The editor of a report parameter of the add-on with the parameter types of our own, see
 * {@link ReportCustomParameterType}. It has the id of the editor of the add-on and replaces it: the parameter
 * tab of the report editor opens it, and the roles of the add-on give access to it.
 *
 * <p>The type is picked in a combo box of our own, with the standard types and ours. The type field of the
 * add-on stays in the layout, hidden, as its controller relies on it: a custom type sets it to its base type,
 * so the add-on shows the controls of that type, and the controls of the custom type are added on top.
 *
 * <p>A {@code SQL_ENUMERATION} parameter gets a data store and a query; the default value is picked from the
 * values of the query once both are set.
 *
 * <p>The descriptor takes the message group of the add-on, which the inherited layout relies on. The
 * controller of the add-on looks a few messages up by the class of the view, so this package has copies of
 * them.
 */
@Route(value = "custom-reports/parameters/:id", layout = MainView.class)
@ViewController(id = "report_ReportInputParameter.detail")
@ViewDescriptor(path = "report-parameter-custom-detail-view.xml")
@EditedEntityContainer("parameterDc")
@DialogMode(width = "40em")
public class ReportParameterCustomDetailView extends ReportParameterDetailView {

    @Autowired
    private ReportSqlEnumerationService sqlEnumerationService;
    @Autowired
    private Notifications notifications;

    @ViewComponent
    private JmixComboBox<Object> typeField;
    @ViewComponent
    private JmixSelect<String> sqlDataStoreField;
    @ViewComponent
    private CodeEditor sqlQueryField;
    @ViewComponent
    private JmixButton sqlCheckBtn;

    @Subscribe
    public void onInitCustomTypes(final InitEvent event) {
        typeField.setItems(parameterTypes());
        typeField.setItemLabelGenerator(type -> messages.getMessage((Enum<?>) type));

        sqlDataStoreField.setItems(sqlEnumerationService.getDataStores());
        sqlDataStoreField.setItemLabelGenerator(this::dataStoreCaption);
    }

    @Subscribe
    public void onBeforeShowCustomTypes(final BeforeShowEvent event) {
        ReportInputParameterCustom parameter = getEditedParameter();
        typeField.setValue(parameter.getCustomType() != null ? parameter.getCustomType() : parameter.getType());
    }

    /**
     * A standard type goes to the type field of the add-on, which then shows the controls of the type. A custom
     * type sets its base type there, and its own controls are shown here when only the custom type changed.
     */
    @Subscribe("typeField")
    public void onTypeFieldComponentValueChange(
            final AbstractField.ComponentValueChangeEvent<JmixComboBox<Object>, Object> event) {
        Object type = event.getValue();
        if (type == null) {
            return;
        }
        ReportInputParameterCustom parameter = getEditedParameter();
        ReportCustomParameterType customType = type instanceof ReportCustomParameterType custom ? custom : null;
        ParameterType standardType = customType != null ? customType.getBaseType() : (ParameterType) type;

        boolean customTypeChanged = customType != parameter.getCustomType();
        if (customTypeChanged) {
            parameter.setCustomType(customType);
            parameter.setDefaultValue(null);
            if (customType == ReportCustomParameterType.SQL_ENUMERATION && parameter.getSqlDataStore() == null) {
                parameter.setSqlDataStore(Stores.MAIN);
            }
        }

        if (standardType != parameterTypeField.getValue()) {
            parameterTypeField.setValue(standardType);
        } else if (customTypeChanged) {
            enableControlsByParamType(standardType);
        }
    }

    @Override
    protected void enableControlsByParamType(ParameterType type) {
        super.enableControlsByParamType(type);

        boolean sqlEnumeration = isSqlEnumeration();
        sqlDataStoreField.setVisible(sqlEnumeration);
        sqlQueryField.setVisible(sqlEnumeration);
        sqlCheckBtn.setVisible(sqlEnumeration);
        if (sqlEnumeration) {
            // the value is picked from the list, there is nothing to put wildcards around
            predefinedTransformationBox.setVisible(false);
            UiComponentUtils.setValue(isPredefinedTransformationField, false);
        }
    }

    /** The default value of a query parameter is picked from the values of the query. */
    @Override
    protected boolean canHaveDefaultValue() {
        if (isSqlEnumeration()) {
            return sqlEnumerationService.isConfigured(getEditedParameter());
        }
        return super.canHaveDefaultValue();
    }

    /** Another data store has other values: the default value is picked anew. */
    @Subscribe("sqlDataStoreField")
    public void onSqlDataStoreFieldComponentValueChange(
            final AbstractField.ComponentValueChangeEvent<JmixSelect<String>, String> event) {
        if (event.isFromClient()) {
            getEditedParameter().setDefaultValue(null);
            initDefaultValueField();
        }
    }

    /** Runs the query, tells how many values it returns, and offers them as the default value. */
    @Subscribe(id = "sqlCheckBtn", subject = "clickListener")
    public void onSqlCheckBtnClick(final ClickEvent<JmixButton> event) {
        ReportInputParameterCustom parameter = getEditedParameter();
        try {
            Map<String, String> options = sqlEnumerationService.loadOptions(parameter);
            notifications.create(messages.formatMessage(ReportParameterCustomDetailView.class,
                            "sqlCheck.success", options.size()))
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } catch (ReportSqlEnumerationService.SqlEnumerationException e) {
            notifications.create(e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
        initDefaultValueField();
    }

    @Subscribe
    public void onValidationCustomTypes(final ValidationEvent event) {
        if (isSqlEnumeration()) {
            ReportInputParameterCustom parameter = getEditedParameter();
            String error = sqlEnumerationService.validate(parameter.getSqlDataStore(), parameter.getSqlQuery());
            if (error != null) {
                event.addErrors(ValidationErrors.of(error));
            }
        }
    }

    /** The settings of a query stay with a query parameter only. */
    @Subscribe
    public void onBeforeSaveCustomTypes(final BeforeSaveEvent event) {
        if (!isSqlEnumeration()) {
            ReportInputParameterCustom parameter = getEditedParameter();
            parameter.setSqlQuery(null);
            parameter.setSqlDataStore(null);
        }
    }

    /** The standard types in the order of the add-on, with the query enumeration after the enumeration. */
    private List<Object> parameterTypes() {
        List<Object> types = new ArrayList<>();
        for (ParameterType type : ParameterType.values()) {
            types.add(type);
            if (type == ParameterType.ENUMERATION) {
                types.add(ReportCustomParameterType.SQL_ENUMERATION);
            }
        }
        return types;
    }

    private String dataStoreCaption(String dataStore) {
        return Stores.isMain(dataStore)
                ? messages.getMessage("io.jmix.reportsflowui.view.report/bandsTab.dataSet.dataStoreMain")
                : dataStore;
    }

    private boolean isSqlEnumeration() {
        return sqlEnumerationService.isSqlEnumeration(getEditedEntity());
    }

    private ReportInputParameterCustom getEditedParameter() {
        return (ReportInputParameterCustom) getEditedEntity();
    }
}
