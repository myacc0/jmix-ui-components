package com.company.demo.component;

import com.company.demo.component.slider.Slider;
import com.company.demo.entity.User;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

/**
 * Test-only host view for {@link Slider} — exercises the XML loader
 * (min/max/step/value attributes) and dataContainer/property binding.
 */
@Route(value = "slider-test-view", layout = MainView.class)
@ViewController(id = "SliderTestView")
@ViewDescriptor(path = "slider-test-view.xml")
public class SliderTestView extends StandardView {

    @ViewComponent
    public InstanceContainer<User> userDc;
}
