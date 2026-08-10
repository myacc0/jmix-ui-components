package com.company.demo.component;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.function.ValueProvider;
import io.jmix.core.Messages;
import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Renders a DQ enum value as a badge, for grid columns that show a status, a severity or a
 * dimension. The colours live in {@code themes/demo/dq-badge.css}, which keys on the shape class
 * plus a value class built from the enum id — so a new enum constant needs no change here.
 */
@Component
public class DqBadges {

    /** Class prefixes, one per enum, mirroring the stylesheet. */
    public static final String SEVERITY = "dq-severity-";
    public static final String DIMENSION = "dq-dimension-";
    public static final String ISSUE_STATUS = "dq-issue-status-";
    public static final String RUN_STATUS = "dq-run-status-";
    public static final String RESULT_STATUS = "dq-result-status-";

    private static final String BADGE = "dq-badge";

    @Autowired
    private Messages messages;

    /**
     * A column renderer showing the enum the value provider reads as a badge.
     *
     * @param classPrefix one of the prefix constants of this class
     * @param value       reads the enum value off the row
     */
    public <S, E extends Enum<E> & EnumClass<String>> Renderer<S> renderer(String classPrefix,
                                                                          ValueProvider<S, E> value) {
        return new ComponentRenderer<>(item -> badge(classPrefix, value.apply(item)));
    }

    /** The localized value as a badge, or nothing at all when there is no value to show. */
    @Nullable
    public <E extends Enum<E> & EnumClass<String>> Span badge(String classPrefix, @Nullable E value) {
        if (value == null) {
            return null;
        }

        Span badge = new Span(messages.getMessage(value));
        badge.addClassNames(BADGE, classPrefix + value.getId());
        return badge;
    }
}
