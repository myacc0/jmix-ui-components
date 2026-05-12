package com.company.demo.component;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.virtuallist.VirtualList;
import io.jmix.flowui.component.textfield.TypedTextField;

import java.util.List;

public class TypedSearchField<V> extends TypedTextField<V> {
    protected final Popover popover;
    protected final VirtualList<V> resultList;

    public TypedSearchField() {
        resultList = new VirtualList<>();

        popover = new Popover();
        popover.setModal(false);
        popover.setOpenOnClick(false);
        popover.setOpenOnFocus(true);
        popover.setOpenOnHover(false);
        popover.add(resultList);

        addValueChangeListener(e -> {
            String value = e.getValue();
            if (value == null || value.isBlank()) {
                popover.close();
                return;
            }

        });

        addKeyDownListener(Key.ESCAPE, e -> popover.close());
    }

    private void showPopover(List<V> results, int size) {
        LoaderComponent loader = new LoaderComponent();
        loader.setSizeFull();
        Popover popover = new Popover(loader);
        popover.setTarget(this);
        popover.setWidth("25em");
        popover.setHeight("10em");
        popover.setCloseOnEsc(true);
        popover.setCloseOnOutsideClick(true);
        popover.open();
        loader.startLoading();
    }



}
