package com.smartmealplanner.auth.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class RegistrationIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("p4_registration")
                    .withUsername("p4_registration_test")
                    .withPassword(UUID.randomUUID().toString())
                    .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
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
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void registerCreatesPendingUserRoleAndVerificationToken()
            throws Exception {

        String suffix = randomSuffix();

        String email =
                "P4.Register." + suffix + "@Example.COM";

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        String rawPassword =
                "StrongPassword123!";

        String body = registrationBody(
                email,
                rawPassword,
                "P4 Registration Probe");

        MvcResult response = mvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))

                .andExpect(status().isCreated())

                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON))

                .andExpect(
                        jsonPath("$.publicId").isString())

                .andExpect(
                        jsonPath("$.accountStatus")
                                .value("PENDING_VERIFICATION"))

                .andExpect(
                        jsonPath("$.password").doesNotExist())

                .andExpect(
                        jsonPath("$.passwordHash").doesNotExist())

                .andExpect(
                        jsonPath("$.verificationToken").doesNotExist())

                .andExpect(
                        header().exists("X-Request-ID"))

                .andReturn();

        JsonNode responseJson =
                objectMapper.readTree(
                        response.getResponse().getContentAsString());

        String publicId =
                responseJson.get("publicId").asText();

        Map<String, Object> user =
                jdbc.queryForMap(
                        """
                        SELECT
                            id,
                            BIN_TO_UUID(public_id) AS public_uuid,
                            email,
                            email_normalized,
                            password_hash,
                            account_status,
                            failed_login_count
                        FROM users
                        WHERE email_normalized = ?
                        """,
                        normalizedEmail);

        Number userId =
                (Number) user.get("id");

        String passwordHash =
                user.get("password_hash").toString();

        assertThat(
                user.get("public_uuid").toString())
                .isEqualTo(publicId);

        assertThat(
                user.get("email").toString())
                .isEqualTo(normalizedEmail);

        assertThat(
                user.get("email_normalized").toString())
                .isEqualTo(normalizedEmail);

        assertThat(
                user.get("account_status").toString())
                .isEqualTo("PENDING_VERIFICATION");

        assertThat(
                ((Number) user.get("failed_login_count"))
                        .intValue())
                .isZero();

        assertThat(passwordHash)
                .startsWith("{bcrypt}")
                .isNotEqualTo(rawPassword);

        assertThat(
                passwordEncoder.matches(
                        rawPassword,
                        passwordHash))
                .isTrue();

        List<String> roleCodes =
                jdbc.queryForList(
                        """
                        SELECT r.code
                        FROM user_roles ur
                        JOIN roles r
                          ON r.id = ur.role_id
                        WHERE ur.user_id = ?
                        ORDER BY r.code
                        """,
                        String.class,
                        userId.longValue());

        assertThat(roleCodes)
                .containsExactly("ROLE_USER");

        List<Map<String, Object>> securityTokens =
                jdbc.queryForList(
                        """
                        SELECT
                            token_kind,
                            token_hash,
                            issued_at,
                            expires_at,
                            consumed_at
                        FROM user_security_tokens
                        WHERE user_id = ?
                        """,
                        userId.longValue());

        assertThat(securityTokens)
                .hasSize(1);

        Map<String, Object> token =
                securityTokens.getFirst();

        assertThat(
                token.get("token_kind").toString())
                .isEqualTo("EMAIL_VERIFICATION");

        assertThat(
                token.get("token_hash").toString())
                .matches("[0-9a-f]{64}");

        assertThat(
                token.get("consumed_at"))
                .isNull();

        Integer validExpiry =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM user_security_tokens
                        WHERE user_id = ?
                          AND expires_at > issued_at
                          AND consumed_at IS NULL
                        """,
                        Integer.class,
                        userId.longValue());

        assertThat(validExpiry)
                .isEqualTo(1);
    }

    @Test
    void duplicateEmailReturns409AndDoesNotCreateDuplicateData()
            throws Exception {

        String suffix = randomSuffix();

        String email =
                "duplicate." + suffix + "@example.com";

        String normalizedEmail =
                email.toLowerCase(Locale.ROOT);

        String firstRequest =
                registrationBody(
                        email,
                        "StrongPassword123!",
                        "First Registration");

        mvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(firstRequest))

                .andExpect(status().isCreated());

        String duplicateRequest =
                registrationBody(
                        email.toUpperCase(Locale.ROOT),
                        "AnotherPassword123!",
                        "Duplicate Registration");

        mvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(duplicateRequest))

                .andExpect(status().isConflict())

                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))

                .andExpect(
                        jsonPath("$.code")
                                .value("CONFLICT"))

                .andExpect(
                        header().exists("X-Request-ID"));

        Integer users =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM users
                        WHERE email_normalized = ?
                        """,
                        Integer.class,
                        normalizedEmail);

        Integer roles =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM user_roles ur
                        JOIN users u
                          ON u.id = ur.user_id
                        WHERE u.email_normalized = ?
                        """,
                        Integer.class,
                        normalizedEmail);

        Integer tokens =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM user_security_tokens ust
                        JOIN users u
                          ON u.id = ust.user_id
                        WHERE u.email_normalized = ?
                        """,
                        Integer.class,
                        normalizedEmail);

        assertThat(users)
                .isEqualTo(1);

        assertThat(roles)
                .isEqualTo(1);

        assertThat(tokens)
                .isEqualTo(1);
    }

    @Test
    void invalidRegistrationReturns400AndDoesNotCreateUser()
            throws Exception {

        String suffix = randomSuffix();

        String email =
                "invalid." + suffix + "@example.com";

        String body =
                registrationBody(
                        email,
                        "short",
                        "Invalid Registration");

        mvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))

                .andExpect(status().isBadRequest())

                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))

                .andExpect(
                        jsonPath("$.code")
                                .value("BAD_REQUEST"))

                .andExpect(
                        header().exists("X-Request-ID"));

        Integer users =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM users
                        WHERE email_normalized = ?
                        """,
                        Integer.class,
                        email.toLowerCase(Locale.ROOT));

        assertThat(users)
                .isZero();
    }

    private String registrationBody(
            String email,
            String password,
            String displayName)
            throws Exception {

        return objectMapper.writeValueAsString(
                Map.of(
                        "email", email,
                        "password", password,
                        "displayName", displayName));
    }

    private static String randomSuffix() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
    }
}