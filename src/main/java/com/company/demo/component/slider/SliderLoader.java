package com.company.demo.component.slider;

import io.jmix.flowui.xml.layout.loader.AbstractComponentLoader;
import io.jmix.flowui.xml.layout.support.DataLoaderSupport;

public class SliderLoader extends AbstractComponentLoader<Slider> {

    protected DataLoaderSupport dataLoaderSupport;

    @Override
    protected Slider createComponent() {
        return factory.create(Slider.class);
    }

    @Override
    public void loadComponent() {
        getDataLoaderSupport().loadData(resultComponent, element);

        loadInteger(element, "min", resultComponent::setMin);
        loadInteger(element, "max", resultComponent::setMax);
        loadInteger(element, "step", resultComponent::setStep);
        loadInteger(element, "value", resultComponent::setValue);

        componentLoader().loadLabel(resultComponent, element);
        componentLoader().loadEnabled(resultComponent, element);
        componentLoader().loadClassNames(resultComponent, element);
        componentLoader().loadSizeAttributes(resultComponent, element);
        componentLoader().loadRequired(resultComponent, element, context);
        componentLoader().loadValueAndElementAttributes(resultComponent, element);
    }

    protected DataLoaderSupport getDataLoaderSupport() {
        if (dataLoaderSupport == null) {
            dataLoaderSupport = applicationContext.getBean(DataLoaderSupport.class, context);
        }
        return dataLoaderSupport;
    }
}
