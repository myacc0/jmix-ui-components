package com.company.demo.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.di.Instantiator;
import com.vaadin.flow.dom.ThemeList;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.jmix.core.Messages;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public class LoaderComponent extends VerticalLayout {

    private final Span loadingMessage = new Span();

    private String logoSize = "6em";

    public LoaderComponent() {
        initComponent();
    }

    public LoaderComponent(Component forComponent) {
        this();
        forComponent(forComponent);
    }

    private void initComponent() {
        setSizeFull();
        setPadding(false);
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        getStyle().setTransition("opacity 200ms ease");
        addComponents();
    }

    private void forComponent(Component component) {
        if (component instanceof HasSize hasSize) {
            setWidth(hasSize.getWidth());
            setHeight(hasSize.getHeight());

            setMaxWidth(hasSize.getMaxWidth());
            setMaxHeight(hasSize.getMaxHeight());

            setMinWidth(hasSize.getMinWidth());
            setMinHeight(hasSize.getMinHeight());
        }

        if (component instanceof HasComponents hasComponents) {
            hasComponents.add(this);
        }
    }

    public void startLoading() {
        setVisible(true);
    }

    public void stopLoading() {
        setVisible(false);
    }

    public void setLoadingMessage(String message) {
        setLoadingMessage(message, null);
    }

    public void setLoadingMessage(String message, @Nullable String badge) {
        String messageToSet = message == null || message.isBlank()
                ? Instantiator.get(UI.getCurrent()).getOrCreate(Messages.class).getMessage("com.company.demo.component/Loader.loading")
                : message;
        loadingMessage.setText(messageToSet);
        setBadge(loadingMessage, badge);
    }

    public void setLogoSize(String logoSize) {
        this.logoSize = logoSize;
        updateLogoSize();
    }

    private void updateLogoSize() {
        getChildren()
                .filter(SvgIcon.class::isInstance).findFirst()
                .map(SvgIcon.class::cast)
                .ifPresent(icon -> icon.setSize(logoSize));
    }

    private void addComponents() {
        addLogo();
        addLoadingMessage();
    }

    private void addLogo() {
        var logo = new SvgIcon("images/logo.svg");
        logo.addClassName("loader-animation");
        logo.setSize(logoSize);
        add(logo);
    }

    private void addLoadingMessage() {
        setLoadingMessage(loadingMessage.getText(), "default");
        loadingMessage.addClassNames(LumoUtility.FontWeight.THIN, LumoUtility.FontSize.SMALL);
        loadingMessage.addClassName("crm-loader-message");
        add(loadingMessage);
    }

    private void setBadge(Span span, @Nullable String badgeVariant) {
        ThemeList themeList = span.getElement().getThemeList();
        if (StringUtils.isNotBlank(badgeVariant)) {
            themeList.add("badge");
            themeList.add(badgeVariant);
        }
    }

}
