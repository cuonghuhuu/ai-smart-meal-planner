package com.smartmealplanner.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.shared.web.ApiProblems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationContextTest {

    @ParameterizedTest
    @ValueSource(strings = {"catalog-import", "recipe-import"})
    void nonWebImportContextDoesNotLoadServletSecurity(String profile) {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profile)
                .withUserConfiguration(SecurityConfiguration.class,
                        PasswordEncodingConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SecurityConfiguration.class);
                    assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
                    assertThat(context).hasSingleBean(PasswordEncoder.class);
                });
    }

    @Test
    void servletContextRetainsSecurityFilterChain() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
                .withUserConfiguration(SecurityConfiguration.class,
                        PasswordEncodingConfiguration.class)
                .withBean(ApiProblems.class,
                        () -> new ApiProblems(new ObjectMapper()))
                .withBean("mvcHandlerMappingIntrospector",
                        HandlerMappingIntrospector.class,
                        HandlerMappingIntrospector::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityConfiguration.class);
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context).hasSingleBean(PasswordEncoder.class);
                });
    }
}
