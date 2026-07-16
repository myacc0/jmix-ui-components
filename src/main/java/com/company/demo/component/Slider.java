package com.company.demo.component;

import com.vaadin.flow.component.AbstractSinglePropertyField;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.shared.Registration;
import io.jmix.flowui.component.HasRequired;
import io.jmix.flowui.component.SupportsValidation;
import io.jmix.flowui.component.delegate.FieldDelegate;
import io.jmix.flowui.component.validation.Validator;
import io.jmix.flowui.data.SupportsValueSource;
import io.jmix.flowui.data.ValueSource;
import io.jmix.flowui.exception.ValidationException;
import jakarta.annotation.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Integer slider field. An alternative to {@code numberField} for bounded numeric
 * input in forms. Supports {@code min}, {@code max}, {@code step} and {@code value}
 * parameters and the standard Jmix data binding ({@code dataContainer}/{@code property}).
 * <p>
 * Must be created through {@code UiComponents} (or the XML {@code <slider>} tag),
 * not with {@code new}.
 */
@Tag("demo-slider")
@JsModule("./src/component/demo-slider.js")
public class Slider extends AbstractSinglePropertyField<Slider, Integer>
        implements SupportsValueSource<Integer>, SupportsValidation<Integer>, HasRequired,
        HasLabel, HasSize, ApplicationContextAware, InitializingBean {

    protected static final String PROPERTY_MIN = "min";
    protected static final String PROPERTY_MAX = "max";
    protected static final String PROPERTY_STEP = "step";

    protected ApplicationContext applicationContext;
    protected FieldDelegate<Slider, Integer, Integer> fieldDelegate;

    public Slider() {
        super("value", null, String.class,
                presentation -> (presentation == null || presentation.isEmpty())
                        ? null
                        : Integer.valueOf(presentation),
                model -> model == null ? "" : String.valueOf(model));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        fieldDelegate = createFieldDelegate();
        addValueChangeListener(event -> fieldDelegate.updateInvalidState());
    }

    @SuppressWarnings("unchecked")
    protected FieldDelegate<Slider, Integer, Integer> createFieldDelegate() {
        return applicationContext.getBean(FieldDelegate.class, this);
    }

    public int getMin() {
        return getElement().getProperty(PROPERTY_MIN, 0);
    }

    public void setMin(int min) {
        getElement().setProperty(PROPERTY_MIN, min);
    }

    public int getMax() {
        return getElement().getProperty(PROPERTY_MAX, 100);
    }

    public void setMax(int max) {
        getElement().setProperty(PROPERTY_MAX, max);
    }

    public int getStep() {
        return getElement().getProperty(PROPERTY_STEP, 1);
    }

    public void setStep(int step) {
        if (step <= 0) {
            throw new IllegalArgumentException("Slider step must be a positive integer");
        }
        getElement().setProperty(PROPERTY_STEP, step);
    }

    @Nullable
    @Override
    public ValueSource<Integer> getValueSource() {
        return fieldDelegate.getValueSource();
    }

    @Override
    public void setValueSource(@Nullable ValueSource<Integer> valueSource) {
        fieldDelegate.setValueSource(valueSource);
    }

    @Override
    public Registration addValidator(Validator<? super Integer> validator) {
        return fieldDelegate.addValidator(validator);
    }

    @Override
    public void executeValidators() throws ValidationException {
        fieldDelegate.executeValidators();
    }

    @Override
    public boolean isInvalid() {
        return fieldDelegate.isInvalid();
    }

    @Override
    public void setInvalid(boolean invalid) {
        fieldDelegate.setInvalid(invalid);
    }

    @Nullable
    @Override
    public String getRequiredMessage() {
        return fieldDelegate.getRequiredMessage();
    }

    @Override
    public void setRequiredMessage(@Nullable String requiredMessage) {
        fieldDelegate.setRequiredMessage(requiredMessage);
    }

    @Override
    public void setRequired(boolean required) {
        HasRequired.super.setRequired(required);

        if (fieldDelegate != null) {
            fieldDelegate.updateRequiredState();
        }
    }

    @Override
    public void setRequiredIndicatorVisible(boolean requiredIndicatorVisible) {
        super.setRequiredIndicatorVisible(requiredIndicatorVisible);

        if (fieldDelegate != null) {
            fieldDelegate.updateRequiredState();
        }
    }
}
