package com.smartmealplanner.auth;

import java.util.Arrays;
import java.util.List;

import com.smartmealplanner.shared.web.ApiProblems;

import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
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

    private static final String WEB_LOGIN_PATH =
            "/api/v1/auth/login/web";

    private static final String ANDROID_LOGIN_PATH =
            "/api/v1/auth/login/android";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiProblems problems,
            ObjectProvider<JwtDecoder> jwtDecoderProvider)
            throws Exception {

        http
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                REGISTER_PATH,
                                VERIFY_EMAIL_PATH,
                                WEB_LOGIN_PATH,
                                ANDROID_LOGIN_PATH))

                .authorizeHttpRequests(routes -> routes

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                REGISTER_PATH,
                                VERIFY_EMAIL_PATH,
                                WEB_LOGIN_PATH,
                                ANDROID_LOGIN_PATH)
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
                                                response)));

        /*
         * Full application contexts contain JwtDecoder through
         * JwtConfiguration.
         *
         * WebMvc slice tests intentionally do not load that configuration,
         * so Bearer support is enabled whenever JwtDecoder is available.
         *
         * Production startup still fails in JwtConfiguration if signing
         * keys are not configured.
         */
        JwtDecoder jwtDecoder =
                jwtDecoderProvider.getIfAvailable();

        if (jwtDecoder != null) {

            http.oauth2ResourceServer(
                    resourceServer ->
                            resourceServer

                                    .jwt(jwt ->
                                            jwt.decoder(
                                                            jwtDecoder)
                                                    .jwtAuthenticationConverter(
                                                            jwtAuthenticationConverter()))

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
                                                            response)));
        }

        return http.build();
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
                Arrays.stream(
                                origins.split(","))
                        .map(String::trim)
                        .filter(
                                origin ->
                                        !origin.isEmpty())
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
                List.of(
                        "X-Request-ID"));

        configuration.setAllowCredentials(
                true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/api/**",
                configuration);

        return source;
    }

    private static JwtAuthenticationConverter
    jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authorities =
                new JwtGrantedAuthoritiesConverter();

        /*
         * AccessTokenService stores authorities as:
         *
         * roles = ["ROLE_USER", "ROLE_ADMIN"]
         *
         * Do not add another prefix here.
         */
        authorities.setAuthoritiesClaimName(
                "roles");

        authorities.setAuthorityPrefix(
                "");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                authorities);

        return converter;
    }
}