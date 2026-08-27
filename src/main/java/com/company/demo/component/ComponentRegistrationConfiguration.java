package com.company.demo.component;

import com.company.demo.component.d3orgchart.D3OrgChart;
import com.company.demo.component.d3orgchart.D3OrgChartLoader;
import com.company.demo.component.slider.Slider;
import com.company.demo.component.slider.SliderLoader;
import io.jmix.flowui.sys.registration.ComponentRegistration;
import io.jmix.flowui.sys.registration.ComponentRegistrationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComponentRegistrationConfiguration {

    @Bean
    public ComponentRegistration slider() {
        return ComponentRegistrationBuilder.create(Slider.class)
                .withComponentLoader("slider", SliderLoader.class)
                .build();
    }

    @Bean
    public ComponentRegistration d3OrgChart() {
        return ComponentRegistrationBuilder.create(D3OrgChart.class)
                .withComponentLoader("d3-org-chart", D3OrgChartLoader.class)
                .build();
    }

}
