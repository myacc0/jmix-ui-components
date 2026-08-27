package com.company.demo.component;

import com.company.demo.DemoApplication;
import com.company.demo.component.slider.Slider;
import com.company.demo.entity.User;
import io.jmix.core.Metadata;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UI integration tests for the custom {@link Slider} component:
 * XML loader attributes, data binding and programmatic defaults.
 */
@UiTest
@SpringBootTest(classes = {DemoApplication.class, FlowuiTestAssistConfiguration.class})
public class SliderUiTest {

    @Autowired
    ViewNavigators viewNavigators;

    @Autowired
    UiComponents uiComponents;

    @Autowired
    Metadata metadata;

    private SliderTestView navigateToTestView() {
        viewNavigators.view(UiTestUtils.getCurrentView(), SliderTestView.class).navigate();
        return UiTestUtils.getCurrentView();
    }

    @Test
    void slider_loads_min_max_step_value_from_xml() {
        SliderTestView view = navigateToTestView();

        Slider plainSlider = UiTestUtils.getComponent(view, "plainSlider");

        assertEquals(10, plainSlider.getMin());
        assertEquals(200, plainSlider.getMax());
        assertEquals(5, plainSlider.getStep());
        assertEquals(50, plainSlider.getValue());
        assertEquals("Plain slider", plainSlider.getLabel());
    }

    @Test
    void slider_binds_to_data_container_property() {
        SliderTestView view = navigateToTestView();

        Slider boundSlider = UiTestUtils.getComponent(view, "boundSlider");
        assertNull(boundSlider.getValue());

        // entity -> component
        User user = metadata.create(User.class);
        user.setVersion(42);
        view.userDc.setItem(user);
        assertEquals(42, boundSlider.getValue());

        // component -> entity
        boundSlider.setValue(77);
        assertEquals(77, user.getVersion());
    }

    @Test
    void slider_created_programmatically_has_defaults() {
        navigateToTestView();

        Slider slider = uiComponents.create(Slider.class);

        assertEquals(0, slider.getMin());
        assertEquals(100, slider.getMax());
        assertEquals(1, slider.getStep());
        assertNull(slider.getValue());

        slider.setValue(33);
        assertEquals(33, slider.getValue());
    }
}
