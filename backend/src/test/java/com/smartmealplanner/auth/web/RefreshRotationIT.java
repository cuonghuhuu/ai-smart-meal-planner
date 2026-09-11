package com.smartmealplanner.auth.web;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
class RefreshRotationIT {

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
    void androidRefreshRotatesTokenAndLinksSuccessor()
            throws Exception {

        UserAccount account =
                createActiveUser();

        String refreshA =
                androidLoginAndGetRefreshToken(
                        account);

        String hashA =
                refreshTokens.hashToken(
                        refreshA);

        MvcResult result =
                mvc.perform(
                                post(
                                        "/api/v1/auth/refresh")
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                refreshBody(
                                                        refreshA)))
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

        String refreshB =
                body.get("refreshToken")
                        .asText();

        String accessToken =
                body.get("accessToken")
                        .asText();

        assertThat(refreshB)
                .isNotBlank()
                .isNotEqualTo(
                        refreshA);

        Jwt jwt =
                jwtDecoder.decode(
                        accessToken);

        assertThat(jwt.getSubject())
                .isEqualTo(
                        account.publicId()
                                .toString());

        assertThat(
                jwt.getClaimAsStringList(
                        "roles"))
                .containsExactly(
                        USER_ROLE);

        String hashB =
                refreshTokens.hashToken(
                        refreshB);

        SessionState oldSession =
                sessionState(
                        hashA);

        SessionState newSession =
                sessionState(
                        hashB);

        assertThat(oldSession.revocationReason())
                .isEqualTo("ROTATED");

        assertThat(oldSession.revokedAt())
                .isNotNull();

        assertThat(oldSession.lastUsedAt())
                .isNotNull();

        assertThat(oldSession.replacedBySessionId())
                .isEqualTo(
                        newSession.id());

        assertThat(newSession.revokedAt())
                .isNull();

        assertThat(newSession.revocationReason())
                .isNull();

        assertThat(newSession.clientKind())
                .isEqualTo("ANDROID");
    }

    @Test
    void reusedRotatedTokenRevokesActiveSuccessor()
            throws Exception {

        UserAccount account =
                createActiveUser();

        String refreshA =
                androidLoginAndGetRefreshToken(
                        account);

        String refreshB =
                rotateAndroidAndGetRefreshToken(
                        refreshA);

        String hashA =
                refreshTokens.hashToken(
                        refreshA);

        String hashB =
                refreshTokens.hashToken(
                        refreshB);

        mvc.perform(
                        post(
                                "/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        refreshBody(
                                                refreshA)))
                .andExpect(
                        status().isUnauthorized())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED"));

        SessionState oldSession =
                sessionState(
                        hashA);

        SessionState successor =
                sessionState(
                        hashB);

        assertThat(oldSession.revocationReason())
                .isEqualTo("ROTATED");

        assertThat(successor.revocationReason())
                .isEqualTo(
                        "SUSPECTED_REUSE");

        assertThat(successor.revokedAt())
                .isNotNull();

        mvc.perform(
                        post(
                                "/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        refreshBody(
                                                refreshB)))
                .andExpect(
                        status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenReturns401()
            throws Exception {

        UserAccount account =
                createActiveUser();

        String refreshToken =
                androidLoginAndGetRefreshToken(
                        account);

        String tokenHash =
                refreshTokens.hashToken(
                        refreshToken);

        int updatedRows =
                jdbc.update(
                        """
                        update user_auth_sessions
                        set issued_at =
                                date_sub(
                                    utc_timestamp(6),
                                    interval 2 hour),
                            expires_at =
                                date_sub(
                                    utc_timestamp(6),
                                    interval 1 hour)
                        where refresh_token_hash = ?
                        """,
                        tokenHash);

        assertThat(updatedRows)
                .isEqualTo(1);

        mvc.perform(
                        post(
                                "/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        refreshBody(
                                                refreshToken)))
                .andExpect(
                        status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED"));
    }

    @Test
    void webRefreshRequiresCsrfAndRotatesCookie()
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

        String refreshA =
                extractCookieValue(
                        loginCookieHeader,
                        REFRESH_COOKIE_NAME);

        Cookie refreshCookie =
                new Cookie(
                        REFRESH_COOKIE_NAME,
                        refreshA);

        mvc.perform(
                        post(
                                "/api/v1/auth/refresh")
                                .cookie(
                                        refreshCookie)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(
                        status().isForbidden());

        MvcResult refreshResult =
                mvc.perform(
                                post(
                                        "/api/v1/auth/refresh")
                                        .cookie(
                                                refreshCookie)
                                        .with(
                                                csrf())
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andExpect(
                                status().isOk())
                        .andExpect(
                                jsonPath("$.accessToken")
                                        .isString())
                        .andReturn();

        JsonNode body =
                objectMapper.readTree(
                        refreshResult
                                .getResponse()
                                .getContentAsString());

        assertThat(
                body.has(
                        "refreshToken"))
                .isFalse();

        String rotatedCookieHeader =
                refreshResult.getResponse()
                        .getHeader(
                                HttpHeaders.SET_COOKIE);

        assertThat(rotatedCookieHeader)
                .isNotNull()
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");

        String refreshB =
                extractCookieValue(
                        rotatedCookieHeader,
                        REFRESH_COOKIE_NAME);

        assertThat(refreshB)
                .isNotBlank()
                .isNotEqualTo(
                        refreshA);

        assertThat(
                refreshResult
                        .getResponse()
                        .getContentAsString())
                .doesNotContain(
                        refreshB);
    }

    @Test
    void concurrentUseOfSameRefreshTokenAllowsAtMostOneSuccess()
            throws Exception {

        UserAccount account =
                createActiveUser();

        String refreshToken =
                androidLoginAndGetRefreshToken(
                        account);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        try {
            Future<Integer> first =
                    executor.submit(
                            () ->
                                    refreshStatus(
                                            refreshToken,
                                            ready,
                                            start));

            Future<Integer> second =
                    executor.submit(
                            () ->
                                    refreshStatus(
                                            refreshToken,
                                            ready,
                                            start));

            ready.await();
            start.countDown();

            List<Integer> statuses =
                    List.of(
                            first.get(),
                            second.get());

            assertThat(statuses)
                    .containsExactlyInAnyOrder(
                            200,
                            401);

        } finally {
            executor.shutdownNow();
        }
    }

    private int refreshStatus(
            String refreshToken,
            CountDownLatch ready,
            CountDownLatch start)
            throws Exception {

        ready.countDown();
        start.await();

        return mvc.perform(
                        post(
                                "/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        refreshBody(
                                                refreshToken)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String androidLoginAndGetRefreshToken(
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

        return body.get(
                        "refreshToken")
                .asText();
    }

    private String rotateAndroidAndGetRefreshToken(
            String refreshToken)
            throws Exception {

        MvcResult result =
                mvc.perform(
                                post(
                                        "/api/v1/auth/refresh")
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                refreshBody(
                                                        refreshToken)))
                        .andExpect(
                                status().isOk())
                        .andReturn();

        JsonNode body =
                objectMapper.readTree(
                        result.getResponse()
                                .getContentAsString());

        return body.get(
                        "refreshToken")
                .asText();
    }

    private UserAccount createActiveUser() {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        UserAccount account =
                accounts.saveAndFlush(
                        new UserAccount(
                                "refresh-"
                                        + suffix
                                        + "@example.com",
                                passwordEncoder.encode(
                                        RAW_PASSWORD),
                                "Refresh Test"));

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
            String tokenHash) {

        return jdbc.queryForObject(
                """
                select
                    id,
                    last_used_at,
                    revoked_at,
                    revocation_reason,
                    replaced_by_session_id,
                    client_kind
                from user_auth_sessions
                where refresh_token_hash = ?
                """,
                (resultSet, rowNumber) ->
                        new SessionState(
                                resultSet.getLong(
                                        "id"),
                                resultSet.getObject(
                                        "last_used_at",
                                        LocalDateTime.class),
                                resultSet.getObject(
                                        "revoked_at",
                                        LocalDateTime.class),
                                resultSet.getString(
                                        "revocation_reason"),
                                nullableLong(
                                        resultSet,
                                        "replaced_by_session_id"),
                                resultSet.getString(
                                        "client_kind")),
                tokenHash);
    }

    private static Long nullableLong(
            java.sql.ResultSet resultSet,
            String column)
            throws java.sql.SQLException {

        long value =
                resultSet.getLong(
                        column);

        return resultSet.wasNull()
                ? null
                : value;
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

    private record SessionState(
            Long id,
            LocalDateTime lastUsedAt,
            LocalDateTime revokedAt,
            String revocationReason,
            Long replacedBySessionId,
            String clientKind) {
    }
}
