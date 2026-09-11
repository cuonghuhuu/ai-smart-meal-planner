package com.smartmealplanner.auth.web;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartmealplanner.auth.application.PasswordResetService;
import com.smartmealplanner.auth.application.RefreshTokenService;
import com.smartmealplanner.auth.application.VerificationResendService;
import com.smartmealplanner.auth.persistence.Role;
import com.smartmealplanner.auth.persistence.RoleRepository;
import com.smartmealplanner.auth.persistence.SessionRevocationReason;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserAuthSession;
import com.smartmealplanner.auth.persistence.UserAuthSessionRepository;
import com.smartmealplanner.auth.persistence.UserRole;
import com.smartmealplanner.auth.persistence.UserRoleRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class AccountRecoveryIT {

    private static final String ROLE_USER =
            "ROLE_USER";

    private static final String OLD_PASSWORD =
            "old-password-for-tests-123";

    private static final String NEW_PASSWORD =
            "new-password-for-tests-456";

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
    UserAuthSessionRepository sessions;

    @Autowired
    RefreshTokenService refreshTokens;

    @Autowired
    PasswordResetService passwordResetService;

    @Autowired
    VerificationResendService verificationResendService;

    @Test
    void resendVerificationIsPublicAndReturnsGenericNoContent()
            throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auth/resend-verification")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        emailBody(
                                                "missing-"
                                                        + UUID.randomUUID()
                                                        + "@example.com")))
                .andExpect(
                        status().isNoContent())
                .andExpect(
                        content().string(""));
    }

    @Test
    void latestVerificationTokenReplacesEarlierToken()
            throws Exception {

        UserAccount account =
                createPendingUser();

        String firstToken =
                verificationResendService.resend(
                                account.email())
                        .orElseThrow();

        String secondToken =
                verificationResendService.resend(
                                account.email())
                        .orElseThrow();

        mvc.perform(
                        post(
                                "/api/v1/auth/verify-email")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        tokenBody(
                                                firstToken)))
                .andExpect(
                        status().isBadRequest());

        mvc.perform(
                        post(
                                "/api/v1/auth/verify-email")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        tokenBody(
                                                secondToken)))
                .andExpect(
                        status().isNoContent());
    }

    @Test
    void forgotPasswordDoesNotRevealWhetherAccountExists()
            throws Exception {

        UserAccount account =
                createActiveUser();

        mvc.perform(
                        post(
                                "/api/v1/auth/forgot-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        emailBody(
                                                account.email())))
                .andExpect(
                        status().isNoContent())
                .andExpect(
                        content().string(""));

        mvc.perform(
                        post(
                                "/api/v1/auth/forgot-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        emailBody(
                                                "missing-"
                                                        + UUID.randomUUID()
                                                        + "@example.com")))
                .andExpect(
                        status().isNoContent())
                .andExpect(
                        content().string(""));
    }

    @Test
    void invalidResetTokenIsRejected()
            throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        resetBody(
                                                "0".repeat(64),
                                                NEW_PASSWORD)))
                .andExpect(
                        status().isBadRequest());
    }

    @Test
    void resetTokenIsSingleUse()
            throws Exception {

        UserAccount account =
                createActiveUser();

        String resetToken =
                passwordResetService.requestReset(
                                account.email())
                        .orElseThrow();

        mvc.perform(
                        post(
                                "/api/v1/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        resetBody(
                                                resetToken,
                                                NEW_PASSWORD)))
                .andExpect(
                        status().isNoContent());

        mvc.perform(
                        post(
                                "/api/v1/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        resetBody(
                                                resetToken,
                                                "another-password-for-tests-789")))
                .andExpect(
                        status().isBadRequest());
    }

    @Test
    void successfulResetChangesPasswordAndRevokesExistingRefreshSessions()
            throws Exception {

        UserAccount account =
                createActiveUser();

        LoginTokens firstLogin =
                login(
                        account.email(),
                        OLD_PASSWORD);

        LoginTokens secondLogin =
                login(
                        account.email(),
                        OLD_PASSWORD);

        String resetToken =
                passwordResetService.requestReset(
                                account.email())
                        .orElseThrow();

        mvc.perform(
                        post(
                                "/api/v1/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        resetBody(
                                                resetToken,
                                                NEW_PASSWORD)))
                .andExpect(
                        status().isNoContent());

        UserAccount reloaded =
                accounts.findByEmailNormalized(
                                account.email())
                        .orElseThrow();

        assertNotNull(
                reloaded.passwordUpdatedAt());

        assertTrue(
                passwordEncoder.matches(
                        NEW_PASSWORD,
                        reloaded.passwordHash()));

        assertRevokedForPasswordChange(
                firstLogin.refreshToken());

        assertRevokedForPasswordChange(
                secondLogin.refreshToken());

        mvc.perform(
                        post(
                                "/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        refreshBody(
                                                firstLogin.refreshToken())))
                .andExpect(
                        status().isUnauthorized());

        mvc.perform(
                        post(
                                "/api/v1/auth/login/android")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        loginBody(
                                                account.email(),
                                                OLD_PASSWORD)))
                .andExpect(
                        status().isUnauthorized());

        mvc.perform(
                        post(
                                "/api/v1/auth/login/android")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        loginBody(
                                                account.email(),
                                                NEW_PASSWORD)))
                .andExpect(
                        status().isOk());
    }

    private UserAccount createPendingUser() {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        UserAccount account =
                accounts.saveAndFlush(
                        new UserAccount(
                                "pending-"
                                        + suffix
                                        + "@example.com",
                                passwordEncoder.encode(
                                        OLD_PASSWORD),
                                "Pending Recovery Test"));

        assignUserRole(
                account);

        return account;
    }

    private UserAccount createActiveUser() {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        UserAccount account =
                accounts.saveAndFlush(
                        new UserAccount(
                                "recovery-"
                                        + suffix
                                        + "@example.com",
                                passwordEncoder.encode(
                                        OLD_PASSWORD),
                                "Recovery Test"));

        account.verifyEmail(
                LocalDateTime.now(
                        ZoneOffset.UTC));

        account =
                accounts.saveAndFlush(
                        account);

        assignUserRole(
                account);

        return account;
    }

    private void assignUserRole(
            UserAccount account) {

        Role role =
                roles.findByCode(
                                ROLE_USER)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ROLE_USER reference data is missing"));

        userRoles.saveAndFlush(
                new UserRole(
                        account,
                        role,
                        null));
    }

    private LoginTokens login(
            String email,
            String password)
            throws Exception {

        MvcResult result =
                mvc.perform(
                                post(
                                        "/api/v1/auth/login/android")
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                loginBody(
                                                        email,
                                                        password)))
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

    private void assertRevokedForPasswordChange(
            String plaintextRefreshToken) {

        String hash =
                refreshTokens.hashToken(
                        plaintextRefreshToken);

        UserAuthSession session =
                sessions.findByRefreshTokenHash(
                                hash)
                        .orElseThrow();

        assertNotNull(
                session.revokedAt());

        assertEquals(
                SessionRevocationReason.PASSWORD_CHANGE,
                session.revocationReason());
    }

    private static String emailBody(
            String email) {

        return """
                {
                  "email": "%s"
                }
                """.formatted(
                email);
    }

    private static String tokenBody(
            String token) {

        return """
                {
                  "token": "%s"
                }
                """.formatted(
                token);
    }

    private static String resetBody(
            String token,
            String password) {

        return """
                {
                  "token": "%s",
                  "password": "%s"
                }
                """.formatted(
                token,
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

    private record LoginTokens(
            String accessToken,
            String refreshToken) {
    }
}
