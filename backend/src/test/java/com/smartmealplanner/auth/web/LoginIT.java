
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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

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
class LoginIT {

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
    JwtDecoder jwtDecoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void androidLoginIssuesValidJwtAndPersistsHashedRefreshToken()
            throws Exception {

        UserAccount account =
                createActiveUser();

        MvcResult result =
                mvc.perform(
                                post(
                                        "/api/v1/auth/login/android")
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .header(
                                                HttpHeaders.USER_AGENT,
                                                "SmartMealPlanner-Android-Test")
                                        .content(
                                                loginBody(
                                                        account.email(),
                                                        RAW_PASSWORD)))
                        .andExpect(
                                status().isOk())
                        .andExpect(
                                jsonPath("$.tokenType")
                                        .value("Bearer"))
                        .andExpect(
                                jsonPath("$.accessToken")
                                        .isString())
                        .andExpect(
                                jsonPath("$.refreshToken")
                                        .isString())
                        .andReturn();

        JsonNode body =
                objectMapper.readTree(
                        result.getResponse()
                                .getContentAsString());

        String accessToken =
                body.get("accessToken")
                        .asText();

        String refreshToken =
                body.get("refreshToken")
                        .asText();

        assertThat(accessToken)
                .isNotBlank();

        assertThat(refreshToken)
                .isNotBlank();

        Jwt jwt =
                jwtDecoder.decode(
                        accessToken);

        assertThat(
                jwt.getClaimAsString(
                        "token_type"))
                .isEqualTo("access");

        assertThat(
                jwt.getClaimAsString(
                        "iss"))
                .isEqualTo(
                        "smart-meal-planner");

        assertThat(
                jwt.getSubject())
                .isEqualTo(
                        account.publicId()
                                .toString());

        assertThat(
                jwt.getClaimAsStringList(
                        "roles"))
                .containsExactly(
                        USER_ROLE);

        assertThat(
                jwt.getIssuedAt())
                .isNotNull();

        assertThat(
                jwt.getExpiresAt())
                .isNotNull()
                .isAfter(
                        jwt.getIssuedAt());

        String refreshTokenHash =
                refreshTokens.hashToken(
                        refreshToken);

        Integer sessionCount =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from user_auth_sessions
                        where user_id = ?
                          and refresh_token_hash = ?
                          and client_kind = 'ANDROID'
                        """,
                        Integer.class,
                        account.internalId(),
                        refreshTokenHash);

        assertThat(sessionCount)
                .isEqualTo(1);

        Integer plaintextCount =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from user_auth_sessions
                        where refresh_token_hash = ?
                        """,
                        Integer.class,
                        refreshToken);

        assertThat(plaintextCount)
                .isZero();
    }

    @Test
    void webLoginUsesHttpOnlyCookieAndDoesNotExposeRefreshTokenInJson()
            throws Exception {

        UserAccount account =
                createActiveUser();

        MvcResult result =
                mvc.perform(
                                post(
                                        "/api/v1/auth/login/web")
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .header(
                                                HttpHeaders.USER_AGENT,
                                                "SmartMealPlanner-Web-Test")
                                        .content(
                                                loginBody(
                                                        account.email(),
                                                        RAW_PASSWORD)))
                        .andExpect(
                                status().isOk())
                        .andExpect(
                                jsonPath("$.tokenType")
                                        .value("Bearer"))
                        .andExpect(
                                jsonPath("$.accessToken")
                                        .isString())
                        .andReturn();

        JsonNode body =
                objectMapper.readTree(
                        result.getResponse()
                                .getContentAsString());

        assertThat(
                body.has(
                        "refreshToken"))
                .isFalse();

        String setCookie =
                result.getResponse()
                        .getHeader(
                                HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .isNotNull()
                .contains(
                        REFRESH_COOKIE_NAME + "=")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");

        String refreshToken =
                extractCookieValue(
                        setCookie,
                        REFRESH_COOKIE_NAME);

        assertThat(refreshToken)
                .isNotBlank();

        assertThat(
                result.getResponse()
                        .getContentAsString())
                .doesNotContain(
                        refreshToken);

        String refreshTokenHash =
                refreshTokens.hashToken(
                        refreshToken);

        Integer sessionCount =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from user_auth_sessions
                        where user_id = ?
                          and refresh_token_hash = ?
                          and client_kind = 'WEB'
                        """,
                        Integer.class,
                        account.internalId(),
                        refreshTokenHash);

        assertThat(sessionCount)
                .isEqualTo(1);
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSameSafe401()
            throws Exception {

        UserAccount account =
                createActiveUser();

        mvc.perform(
                        post(
                                "/api/v1/auth/login/android")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        loginBody(
                                                account.email(),
                                                "definitely-wrong-password")))
                .andExpect(
                        status().isUnauthorized())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED"));

        mvc.perform(
                        post(
                                "/api/v1/auth/login/android")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        loginBody(
                                                "missing-"
                                                        + UUID.randomUUID()
                                                        + "@example.com",
                                                RAW_PASSWORD)))
                .andExpect(
                        status().isUnauthorized())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED"));

        assertThat(
                countSessions(
                        account))
                .isZero();
    }

    @Test
    void pendingVerificationAccountCannotLogin()
            throws Exception {

        UserAccount account =
                createPendingUser();

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
                        status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED"));

        assertThat(
                countSessions(
                        account))
                .isZero();
    }

    @Test
    void browserOriginCannotUseAndroidLoginTransport()
            throws Exception {

        UserAccount account =
                createActiveUser();

        mvc.perform(
                        post(
                                "/api/v1/auth/login/android")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://planner.example")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        loginBody(
                                                account.email(),
                                                RAW_PASSWORD)))
                .andExpect(
                        status().isForbidden())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.code")
                                .value("FORBIDDEN"));

        assertThat(
                countSessions(
                        account))
                .isZero();
    }

    private UserAccount createActiveUser() {

        UserAccount account =
                createPendingUser();

        account.verifyEmail(
                LocalDateTime.now(
                        ZoneOffset.UTC));

        return accounts.saveAndFlush(
                account);
    }

    private UserAccount createPendingUser() {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        UserAccount account =
                accounts.saveAndFlush(
                        new UserAccount(
                                "login-"
                                        + suffix
                                        + "@example.com",
                                passwordEncoder.encode(
                                        RAW_PASSWORD),
                                "Login Test"));

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

    private int countSessions(
            UserAccount account) {

        Integer count =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from user_auth_sessions
                        where user_id = ?
                        """,
                        Integer.class,
                        account.internalId());

        return count == null
                ? 0
                : count;
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

    private static String extractCookieValue(
            String setCookie,
            String cookieName) {

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
}
