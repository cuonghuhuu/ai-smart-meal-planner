package com.smartmealplanner.mealplanning.application;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed, environment-backed internal AI transport settings. */
@ConfigurationProperties(prefix = "app.ai")
public record AiServiceProperties(URI baseUrl, String internalServiceToken,
        Duration connectTimeout, Duration responseTimeout, int maxResponseBytes) {
    public AiServiceProperties {
        if (baseUrl == null || baseUrl.getHost() == null
                || !("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getRawUserInfo() != null || baseUrl.getRawQuery() != null
                || baseUrl.getRawFragment() != null
                || (baseUrl.getRawPath() != null && !baseUrl.getRawPath().isEmpty()
                && !"/".equals(baseUrl.getRawPath()))
                || connectTimeout == null || connectTimeout.isNegative()
                || connectTimeout.isZero() || responseTimeout == null
                || responseTimeout.isNegative() || responseTimeout.isZero()
                || maxResponseBytes < 1024 || maxResponseBytes > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid internal AI service configuration");
        }
    }
}
