package com.smartmealplanner.auth.web;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartmealplanner.auth.persistence.Role;
import com.smartmealplanner.auth.persistence.RoleRepository;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserRole;
import com.smartmealplanner.auth.persistence.UserRoleRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
@Import(BearerAuthorizationIT.ProtectedProbeController.class)
class BearerAuthorizationIT {

    private static final String RAW_PASSWORD =
            "correct-horse-battery-staple";

    private static final String ROLE_USER =
            "ROLE_USER";

    private static final String ROLE_ADMIN =
            "ROLE_ADMIN";

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("smart_meal_planner")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(
            DynamicPropertyRegistry registry) {

        registry.add(
                "spring.datasource.url",
                MYSQL::getJdbcUrl);

        registry.add(
                "spring.datasource.username",
                MYSQL::getUsername);

        registry.add(
                "spring.datasource.password",
                MYSQL::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserAccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Autowired
    UserRoleRepository userRoles;

    @Test
    void protectedRouteWithoutBearerTokenReturns401()
            throws Exception {

        mvc.perform(
                        get(
                                "/api/v1/auth-test/protected"))
                .andExpect(
                        status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED"));
    }

    @Test
    void validUserAccessTokenAuthenticatesProtectedRoute()
            throws Exception {

        UserAccount account =
                createActiveUserWithRole(
                        ROLE_USER);

        String accessToken =
                loginAndGetAccessToken(
                        account);

        mvc.perform(
                        get(
                                "/api/v1/auth-test/protected")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                accessToken)))
                .andExpect(
                        status().isOk());
    }

    @Test
    void tamperedAccessTokenReturns401()
            throws Exception {

        UserAccount account =
                createActiveUserWithRole(
                        ROLE_USER);

        String accessToken =
                loginAndGetAccessToken(
                        account);

        String tamperedToken =
                tamperSignature(
                        accessToken);

        mvc.perform(
                        get(
                                "/api/v1/auth-test/protected")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                tamperedToken)))
                .andExpect(
                        status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED"));
    }

    @Test
    void userRoleCannotAccessAdminRoute()
            throws Exception {

        UserAccount account =
                createActiveUserWithRole(
                        ROLE_USER);

        String accessToken =
                loginAndGetAccessToken(
                        account);

        mvc.perform(
                        get(
                                "/api/v1/admin/auth-test")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                accessToken)))
                .andExpect(
                        status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("FORBIDDEN"));
    }

    @Test
    void adminRoleCanAccessAdminRoute()
            throws Exception {

        UserAccount account =
                createActiveUserWithRole(
                        ROLE_ADMIN);

        String accessToken =
                loginAndGetAccessToken(
                        account);

        mvc.perform(
                        get(
                                "/api/v1/admin/auth-test")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                accessToken)))
                .andExpect(
                        status().isOk());
    }

    private UserAccount createActiveUserWithRole(
            String roleCode) {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        UserAccount account =
                accounts.saveAndFlush(
                        new UserAccount(
                                "bearer-"
                                        + suffix
                                        + "@example.com",
                                passwordEncoder.encode(
                                        RAW_PASSWORD),
                                "Bearer Test"));

        account.verifyEmail(
                LocalDateTime.now(
                        ZoneOffset.UTC));

        account =
                accounts.saveAndFlush(
                        account);

        Role role =
                roles.findByCode(
                                roleCode)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                roleCode
                                                        + " reference data is missing"));

        userRoles.saveAndFlush(
                new UserRole(
                        account,
                        role,
                        null));

        return account;
    }

    private String loginAndGetAccessToken(
            UserAccount account)
            throws Exception {

        MvcResult loginResult =
                mvc.perform(
                                post(
                                        "/api/v1/auth/login/android")
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                loginBody(
                                                        account.email(),
                                                        RAW_PASSWORD)))
                        .andExpect(
                                status().isOk())
                        .andReturn();

        JsonNode body =
                objectMapper.readTree(
                        loginResult
                                .getResponse()
                                .getContentAsString());

        return body.get(
                        "accessToken")
                .asText();
    }

    private static String bearer(
            String accessToken) {

        return "Bearer "
                + accessToken;
    }

    private static String tamperSignature(
            String accessToken) {

        String[] parts =
                accessToken.split(
                        "\\.");

        if (parts.length != 3
                || parts[2].isEmpty()) {

            throw new IllegalArgumentException(
                    "JWT format is invalid");
        }

        char first =
                parts[2].charAt(0);

        char replacement =
                first == 'A'
                        ? 'B'
                        : 'A';

        parts[2] =
                replacement
                        + parts[2].substring(1);

        return String.join(
                ".",
                parts);
    }

    private static String loginBody(
            String email,
            String password) {

        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(
                email,
                password);
    }

    @RestController
    static class ProtectedProbeController {

        @GetMapping(
                "/api/v1/auth-test/protected")
        String protectedRoute() {
            return "ok";
        }

        @GetMapping(
                "/api/v1/admin/auth-test")
        String adminRoute() {
            return "admin";
        }
    }
}