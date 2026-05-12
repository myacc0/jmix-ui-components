package com.company.demo.component;

import io.jmix.flowui.xml.layout.loader.component.TextFieldLoader;

public class TypedSearchFieldLoader extends TextFieldLoader {

    @Override
    protected TypedSearchField<?> createComponent() {
        return factory.create(TypedSearchField.class);
    }

    @Override
    public void loadComponent() {
        super.loadComponent();
    }
}
