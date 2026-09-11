package com.smartmealplanner.auth.web;

import java.time.LocalDateTime;
import java.util.UUID;

import com.smartmealplanner.auth.application.SecurityTokenService;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class EmailVerificationIT {

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
    UserAccountRepository accounts;

    @Autowired
    SecurityTokenService securityTokens;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void validTokenActivatesAccountAndConsumesToken()
            throws Exception {

        UserAccount account =
                createPendingAccount();

        String plaintextToken =
                securityTokens
                        .createEmailVerificationToken(
                                account);

        String tokenHash =
                securityTokens
                        .hashToken(
                                plaintextToken);

        mvc.perform(
                        post(
                                "/api/v1/auth/verify-email")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        verificationBody(
                                                plaintextToken)))
                .andExpect(
                        status().isNoContent())
                .andExpect(
                        content().string(""));

        String accountStatus =
                jdbc.queryForObject(
                        """
                        select account_status
                        from users
                        where id = ?
                        """,
                        String.class,
                        account.internalId());

        LocalDateTime emailVerifiedAt =
                jdbc.queryForObject(
                        """
                        select email_verified_at
                        from users
                        where id = ?
                        """,
                        LocalDateTime.class,
                        account.internalId());

        LocalDateTime consumedAt =
                jdbc.queryForObject(
                        """
                        select consumed_at
                        from user_security_tokens
                        where token_hash = ?
                        """,
                        LocalDateTime.class,
                        tokenHash);

        assertThat(accountStatus)
                .isEqualTo("ACTIVE");

        assertThat(emailVerifiedAt)
                .isNotNull();

        assertThat(consumedAt)
                .isNotNull();
    }

    @Test
    void consumedTokenCannotBeReused()
            throws Exception {

        UserAccount account =
                createPendingAccount();

        String plaintextToken =
                securityTokens
                        .createEmailVerificationToken(
                                account);

        mvc.perform(
                        post(
                                "/api/v1/auth/verify-email")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        verificationBody(
                                                plaintextToken)))
                .andExpect(
                        status().isNoContent());

        mvc.perform(
                        post(
                                "/api/v1/auth/verify-email")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        verificationBody(
                                                plaintextToken)))
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.code")
                                .value("BAD_REQUEST"));
    }

    @Test
    void unknownTokenReturns400()
            throws Exception {

        String unknownToken =
                "a".repeat(64);

        mvc.perform(
                        post(
                                "/api/v1/auth/verify-email")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        verificationBody(
                                                unknownToken)))
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.code")
                                .value("BAD_REQUEST"));
    }

    @Test
    void expiredTokenReturns400AndDoesNotActivateAccount()
            throws Exception {

        UserAccount account =
                createPendingAccount();

        String plaintextToken =
                randomPlaintextToken();

        String tokenHash =
                securityTokens
                        .hashToken(
                                plaintextToken);

        int insertedRows =
                jdbc.update(
                        """
                        insert into user_security_tokens (
                            user_id,
                            token_kind,
                            token_hash,
                            issued_at,
                            expires_at,
                            consumed_at
                        )
                        values (
                            ?,
                            'EMAIL_VERIFICATION',
                            ?,
                            date_sub(
                                utc_timestamp(6),
                                interval 2 hour),
                            date_sub(
                                utc_timestamp(6),
                                interval 1 hour),
                            null
                        )
                        """,
                        account.internalId(),
                        tokenHash);

        assertThat(insertedRows)
                .isEqualTo(1);

        LocalDateTime issuedAt =
                jdbc.queryForObject(
                        """
                        select issued_at
                        from user_security_tokens
                        where token_hash = ?
                        """,
                        LocalDateTime.class,
                        tokenHash);

        LocalDateTime expiresAt =
                jdbc.queryForObject(
                        """
                        select expires_at
                        from user_security_tokens
                        where token_hash = ?
                        """,
                        LocalDateTime.class,
                        tokenHash);

        LocalDateTime databaseNow =
                jdbc.queryForObject(
                        """
                        select utc_timestamp(6)
                        """,
                        LocalDateTime.class);

        assertThat(issuedAt)
                .isBefore(expiresAt);

        assertThat(expiresAt)
                .isBefore(databaseNow);

        mvc.perform(
                        post(
                                "/api/v1/auth/verify-email")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        verificationBody(
                                                plaintextToken)))
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.code")
                                .value("BAD_REQUEST"));

        String accountStatus =
                jdbc.queryForObject(
                        """
                        select account_status
                        from users
                        where id = ?
                        """,
                        String.class,
                        account.internalId());

        LocalDateTime emailVerifiedAt =
                jdbc.queryForObject(
                        """
                        select email_verified_at
                        from users
                        where id = ?
                        """,
                        LocalDateTime.class,
                        account.internalId());

        LocalDateTime consumedAt =
                jdbc.queryForObject(
                        """
                        select consumed_at
                        from user_security_tokens
                        where token_hash = ?
                        """,
                        LocalDateTime.class,
                        tokenHash);

        assertThat(accountStatus)
                .isEqualTo(
                        "PENDING_VERIFICATION");

        assertThat(emailVerifiedAt)
                .isNull();

        assertThat(consumedAt)
                .isNull();
    }

    @Test
    void malformedTokenReturns400()
            throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auth/verify-email")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "token": "not-a-valid-token"
                                        }
                                        """))
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.code")
                                .value("BAD_REQUEST"));
    }

    private UserAccount createPendingAccount() {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        UserAccount account =
                new UserAccount(
                        "verify-"
                                + suffix
                                + "@example.com",
                        "{bcrypt}$2a$10$"
                                + "1234567890123456789012"
                                + "1234567890123456789012345678901",
                        "Verification Test");

        return accounts.saveAndFlush(
                account);
    }

    private static String randomPlaintextToken() {

        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                + UUID.randomUUID()
                .toString()
                .replace("-", "");
    }

    private static String verificationBody(
            String token) {

        return """
                {
                  "token": "%s"
                }
                """.formatted(token);
    }
}