package com.smartmealplanner.auth.web;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartmealplanner.auth.application.RefreshTokenService;
import com.smartmealplanner.auth.persistence.Role;
import com.smartmealplanner.auth.persistence.RoleRepository;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserRole;
import com.smartmealplanner.auth.persistence.UserRoleRepository;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties =
                "app.cors.allowed-origins=https://planner.example")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class LogoutIT {

    private static final String RAW_PASSWORD =
            "correct-horse-battery-staple";

    private static final String USER_ROLE =
            "ROLE_USER";

    private static final String REFRESH_COOKIE_NAME =
            "__Host-smartmeal_refresh";

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

    @Autowired
    RefreshTokenService refreshTokens;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void androidLogoutRevokesCurrentSession()
            throws Exception {

        UserAccount account =
                createActiveUser();

        LoginTokens tokens =
                androidLogin(
                        account);

        String refreshHash =
                refreshTokens.hashToken(
                        tokens.refreshToken());

        mvc.perform(
                        post(
                                "/api/v1/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        refreshBody(
                                                tokens.refreshToken())))
                .andExpect(
                        status().isNoContent());

        SessionState session =
                sessionState(
                        refreshHash);

        assertThat(session.revokedAt())
                .isNotNull();

        assertThat(session.revocationReason())
                .isEqualTo(
                        "USER_LOGOUT");
    }

    @Test
    void loggedOutRefreshTokenCannotBeUsedAgain()
            throws Exception {

        UserAccount account =
                createActiveUser();

        LoginTokens tokens =
                androidLogin(
                        account);

        mvc.perform(
                        post(
                                "/api/v1/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        refreshBody(
                                                tokens.refreshToken())))
                .andExpect(
                        status().isNoContent());

        mvc.perform(
                        post(
                                "/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        refreshBody(
                                                tokens.refreshToken())))
                .andExpect(
                        status().isUnauthorized())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED"));
    }

    @Test
    void webLogoutRequiresCsrfRevokesSessionAndClearsCookie()
            throws Exception {

        UserAccount account =
                createActiveUser();

        MvcResult loginResult =
                mvc.perform(
                                post(
                                        "/api/v1/auth/login/web")
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                loginBody(
                                                        account.email(),
                                                        RAW_PASSWORD)))
                        .andExpect(
                                status().isOk())
                        .andReturn();

        String loginCookieHeader =
                loginResult.getResponse()
                        .getHeader(
                                HttpHeaders.SET_COOKIE);

        String refreshToken =
                extractCookieValue(
                        loginCookieHeader,
                        REFRESH_COOKIE_NAME);

        String refreshHash =
                refreshTokens.hashToken(
                        refreshToken);

        Cookie refreshCookie =
                new Cookie(
                        REFRESH_COOKIE_NAME,
                        refreshToken);

        mvc.perform(
                        post(
                                "/api/v1/auth/logout")
                                .cookie(
                                        refreshCookie)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(
                        status().isForbidden());

        assertThat(
                sessionState(
                        refreshHash)
                        .revokedAt())
                .isNull();

        MvcResult logoutResult =
                mvc.perform(
                                post(
                                        "/api/v1/auth/logout")
                                        .cookie(
                                                refreshCookie)
                                        .with(
                                                csrf())
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andExpect(
                                status().isNoContent())
                        .andReturn();

        SessionState session =
                sessionState(
                        refreshHash);

        assertThat(session.revokedAt())
                .isNotNull();

        assertThat(session.revocationReason())
                .isEqualTo(
                        "USER_LOGOUT");

        String clearedCookie =
                logoutResult.getResponse()
                        .getHeader(
                                HttpHeaders.SET_COOKIE);

        assertThat(clearedCookie)
                .isNotNull()
                .contains(
                        REFRESH_COOKIE_NAME + "=")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Max-Age=0");
    }

    @Test
    void logoutAllRevokesEveryActiveSessionForAuthenticatedUser()
            throws Exception {

        UserAccount account =
                createActiveUser();

        LoginTokens first =
                androidLogin(
                        account);

        LoginTokens second =
                androidLogin(
                        account);

        assertThat(
                activeSessionCount(
                        account))
                .isEqualTo(2);

        mvc.perform(
                        post(
                                "/api/v1/auth/logout-all")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                first.accessToken())))
                .andExpect(
                        status().isNoContent());

        assertThat(
                activeSessionCount(
                        account))
                .isZero();

        assertThat(
                revokedLogoutSessionCount(
                        account))
                .isEqualTo(2);

        mvc.perform(
                        post(
                                "/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        refreshBody(
                                                second.refreshToken())))
                .andExpect(
                        status().isUnauthorized());
    }

    @Test
    void logoutAllWithoutBearerTokenReturns401()
            throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auth/logout-all"))
                .andExpect(
                        status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED"));
    }

    private LoginTokens androidLogin(
            UserAccount account)
            throws Exception {

        MvcResult result =
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
                        result.getResponse()
                                .getContentAsString());

        return new LoginTokens(
                body.get(
                                "accessToken")
                        .asText(),
                body.get(
                                "refreshToken")
                        .asText());
    }

    private UserAccount createActiveUser() {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        UserAccount account =
                accounts.saveAndFlush(
                        new UserAccount(
                                "logout-"
                                        + suffix
                                        + "@example.com",
                                passwordEncoder.encode(
                                        RAW_PASSWORD),
                                "Logout Test"));

        account.verifyEmail(
                LocalDateTime.now(
                        ZoneOffset.UTC));

        account =
                accounts.saveAndFlush(
                        account);

        Role role =
                roles.findByCode(
                                USER_ROLE)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ROLE_USER reference data is missing"));

        userRoles.saveAndFlush(
                new UserRole(
                        account,
                        role,
                        null));

        return account;
    }

    private SessionState sessionState(
            String refreshTokenHash) {

        return jdbc.queryForObject(
                """
                select
                    revoked_at,
                    revocation_reason
                from user_auth_sessions
                where refresh_token_hash = ?
                """,
                (resultSet, rowNumber) ->
                        new SessionState(
                                resultSet.getObject(
                                        "revoked_at",
                                        LocalDateTime.class),
                                resultSet.getString(
                                        "revocation_reason")),
                refreshTokenHash);
    }

    private int activeSessionCount(
            UserAccount account) {

        Integer count =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from user_auth_sessions
                        where user_id = ?
                          and revoked_at is null
                        """,
                        Integer.class,
                        account.internalId());

        return count == null
                ? 0
                : count;
    }

    private int revokedLogoutSessionCount(
            UserAccount account) {

        Integer count =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from user_auth_sessions
                        where user_id = ?
                          and revoked_at is not null
                          and revocation_reason = 'USER_LOGOUT'
                        """,
                        Integer.class,
                        account.internalId());

        return count == null
                ? 0
                : count;
    }

    private static String bearer(
            String accessToken) {

        return "Bearer "
                + accessToken;
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

    private static String refreshBody(
            String refreshToken) {

        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(
                refreshToken);
    }

    private static String extractCookieValue(
            String setCookie,
            String cookieName) {

        if (setCookie == null) {
            throw new IllegalStateException(
                    "Set-Cookie header is missing");
        }

        String prefix =
                cookieName + "=";

        for (String segment :
                setCookie.split(";")) {

            String value =
                    segment.trim();

            if (value.startsWith(
                    prefix)) {

                return value.substring(
                        prefix.length());
            }
        }

        throw new IllegalStateException(
                "Refresh cookie was not found");
    }

    private record LoginTokens(
            String accessToken,
            String refreshToken) {
    }

    private record SessionState(
            LocalDateTime revokedAt,
            String revocationReason) {
    }
}
