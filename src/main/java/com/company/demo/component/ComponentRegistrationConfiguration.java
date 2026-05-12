package com.company.demo.component;

import io.jmix.flowui.sys.registration.ComponentRegistration;
import io.jmix.flowui.sys.registration.ComponentRegistrationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComponentRegistrationConfiguration {

    @Bean
    public ComponentRegistration typedSearchField() {
        return ComponentRegistrationBuilder.create(TypedSearchField.class)
                .withComponentLoader("searchField", TypedSearchFieldLoader.class)
                .build();
    }
}
