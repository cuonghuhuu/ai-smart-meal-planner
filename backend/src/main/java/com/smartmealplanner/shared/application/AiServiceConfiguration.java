package com.smartmealplanner.shared.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Shared registration for typed Java-to-Python transport settings. */
@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
public class AiServiceConfiguration {
}
