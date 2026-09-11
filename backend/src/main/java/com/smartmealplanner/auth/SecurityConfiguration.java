package com.smartmealplanner.auth;

import java.util.Arrays;
import java.util.List;

import com.smartmealplanner.shared.web.ApiProblems;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final String REGISTER_PATH =
            "/api/v1/auth/register";

    private static final String VERIFY_EMAIL_PATH =
            "/api/v1/auth/verify-email";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiProblems problems)
            throws Exception {

        return http
                .cors(Customizer.withDefaults())

                /*
                 * Registration and email verification do not rely on
                 * authenticated browser cookies/sessions.
                 *
                 * CSRF remains enabled for the rest of the application.
                 */
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                REGISTER_PATH,
                                VERIFY_EMAIL_PATH))

                .authorizeHttpRequests(routes -> routes

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                REGISTER_PATH,
                                VERIFY_EMAIL_PATH)
                        .permitAll()

                        .requestMatchers(
                                "/api/v1/admin/**")
                        .hasRole("ADMIN")

                        .anyRequest()
                        .authenticated())

                .exceptionHandling(errors -> errors

                        .authenticationEntryPoint(
                                (request, response, exception) ->
                                        problems.write(
                                                HttpStatus.UNAUTHORIZED,
                                                request,
                                                response))

                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        problems.write(
                                                HttpStatus.FORBIDDEN,
                                                request,
                                                response)))

                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}")
            String origins) {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                Arrays.stream(origins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList());

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"));

        configuration.setAllowedHeaders(
                List.of(
                        "Content-Type",
                        "Authorization",
                        "X-CSRF-TOKEN",
                        "X-Request-ID"));

        configuration.setExposedHeaders(
                List.of("X-Request-ID"));

        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/api/**",
                configuration);

        return source;
    }
}