package com.smartmealplanner.auth;

import java.util.Arrays;
import java.util.List;

import com.smartmealplanner.shared.web.ApiProblems;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

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
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final String REFRESH_COOKIE_NAME =
            "__Host-smartmeal_refresh";

    private static final String REGISTER_PATH =
            "/api/v1/auth/register";

    private static final String VERIFY_EMAIL_PATH =
            "/api/v1/auth/verify-email";

    private static final String WEB_LOGIN_PATH =
            "/api/v1/auth/login/web";

    private static final String ANDROID_LOGIN_PATH =
            "/api/v1/auth/login/android";

    private static final String REFRESH_PATH =
            "/api/v1/auth/refresh";

    private static final String LOGOUT_PATH =
            "/api/v1/auth/logout";

    private static final String LOGOUT_ALL_PATH =
            "/api/v1/auth/logout-all";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiProblems problems,
            ObjectProvider<JwtDecoder> jwtDecoderProvider)
            throws Exception {

        RequestMatcher androidRefreshMatcher =
                cookieLessPostMatcher(
                        REFRESH_PATH);

        RequestMatcher androidLogoutMatcher =
                cookieLessPostMatcher(
                        LOGOUT_PATH);

        RequestMatcher bearerLogoutAllMatcher =
                request ->
                        matchesPostPath(
                                request,
                                LOGOUT_ALL_PATH);

        http
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf
                        /*
                         * Register, verification and login do not rely on
                         * an existing browser authentication cookie.
                         */
                        .ignoringRequestMatchers(
                                REGISTER_PATH,
                                VERIFY_EMAIL_PATH,
                                WEB_LOGIN_PATH,
                                ANDROID_LOGIN_PATH)

                        /*
                         * Android refresh/logout sends the opaque refresh
                         * token explicitly in the request body and therefore
                         * does not authenticate via a browser cookie.
                         *
                         * Web refresh/logout carries the refresh cookie, so
                         * these matchers deliberately do NOT exempt those
                         * requests from CSRF protection.
                         */
                        .ignoringRequestMatchers(
                                androidRefreshMatcher,
                                androidLogoutMatcher)

                        /*
                         * logout-all requires a Bearer access JWT and does
                         * not rely on cookie authentication.
                         */
                        .ignoringRequestMatchers(
                                bearerLogoutAllMatcher))

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
                                ANDROID_LOGIN_PATH,
                                REFRESH_PATH,
                                LOGOUT_PATH)
                        .permitAll()

                        /*
                         * logout-all intentionally falls through to
                         * anyRequest().authenticated().
                         */
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

    private static RequestMatcher cookieLessPostMatcher(
            String path) {

        return request ->
                matchesPostPath(
                        request,
                        path)
                        && !hasRefreshCookie(
                        request.getCookies());
    }

    private static boolean matchesPostPath(
            HttpServletRequest request,
            String path) {

        return HttpMethod.POST.matches(
                request.getMethod())
                && path.equals(
                applicationPath(
                        request));
    }

    /*
     * Use requestURI rather than servletPath.
     *
     * In MockMvc and some servlet dispatch configurations, servletPath can
     * be empty even when requestURI contains the API path. That caused
     * cookie-less Android logout and Bearer logout-all to miss their CSRF
     * exemptions and be rejected as 403 before authentication/controller
     * processing.
     */
    private static String applicationPath(
            HttpServletRequest request) {

        String requestUri =
                request.getRequestURI();

        String contextPath =
                request.getContextPath();

        if (contextPath != null
                && !contextPath.isEmpty()
                && requestUri.startsWith(
                contextPath)) {

            return requestUri.substring(
                    contextPath.length());
        }

        return requestUri;
    }

    private static boolean hasRefreshCookie(
            Cookie[] cookies) {

        if (cookies == null) {
            return false;
        }

        for (Cookie cookie : cookies) {

            if (REFRESH_COOKIE_NAME.equals(
                    cookie.getName())) {

                return true;
            }
        }

        return false;
    }
}
