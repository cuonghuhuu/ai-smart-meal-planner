package com.smartmealplanner.admin;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class AdminUserIT {

    private static final String EMAIL_PREFIX = "p12-admin-user-it-";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p12_admin_users")
            .withUsername("p12_admin_users_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void cleanSyntheticUsers() {
        jdbc.update("""
                delete from user_roles
                where user_id in (select id from users where email like ?)
                """, EMAIL_PREFIX + "%");
        jdbc.update("delete from users where email like ?", EMAIL_PREFIX + "%");
    }

    @Test
    void userRoleIsForbiddenAndAdminCanListWithoutSensitiveFields()
            throws Exception {
        Account user = insertAccount("user", "ROLE_USER");
        Account admin = insertAccount("admin", "ROLE_ADMIN");

        mvc.perform(get("/api/v1/admin/users")
                        .with(auth(user, "ROLE_USER")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/users")
                        .param("q", "P12")
                        .with(auth(admin, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].publicId")
                        .value(hasItem(user.publicId().toString())))
                .andExpect(jsonPath("$.content[*].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[*].internalId").doesNotExist());
    }

    @Test
    void activeAndSuspendedTransitionsAreExplicitAndSelfSuspendIsBlocked()
            throws Exception {
        Account admin = insertAccount("admin", "ROLE_ADMIN");
        Account target = insertAccount("target", "ROLE_USER");

        mvc.perform(patch("/api/v1/admin/users/{publicId}/status", target.publicId())
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));

        mvc.perform(patch("/api/v1/admin/users/{publicId}/status", target.publicId())
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        mvc.perform(patch("/api/v1/admin/users/{publicId}/status", admin.publicId())
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SELF_SUSPENSION_NOT_ALLOWED"));
    }

    @Test
    void pendingVerificationCannotBeChangedByAdmin()
            throws Exception {
        Account admin = insertAccount("admin", "ROLE_ADMIN");
        Account pending = insertAccount(
                "pending", "ROLE_USER", "PENDING_VERIFICATION");

        mvc.perform(patch("/api/v1/admin/users/{publicId}/status", pending.publicId())
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_LIFECYCLE"));
    }

    private Account insertAccount(String suffix, String role) {
        return insertAccount(suffix, role, "ACTIVE");
    }

    private Account insertAccount(
            String suffix,
            String role,
            String status) {
        UUID publicId = UUID.randomUUID();
        String email = EMAIL_PREFIX + suffix + "-" + publicId + "@example.test";
        jdbc.update("""
                insert into users
                    (public_id, email, password_hash, display_name,
                     account_status, email_verified_at, time_zone, locale, version)
                values (unhex(replace(?, '-', '')), ?, 'test-hash',
                        'P12 Admin Test User', ?,
                        case when ? = 'PENDING_VERIFICATION'
                             then null else current_timestamp(6) end,
                        'UTC', 'vi', 0)
                """, publicId.toString(), email, status, status);
        Long internalId = jdbc.queryForObject(
                "select id from users where email = ?", Long.class, email);
        jdbc.update("""
                insert into user_roles (user_id, role_id)
                select ?, id from roles where code = ?
                """, internalId, role);
        return new Account(publicId, internalId);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auth(
            Account account,
            String role) {
        return jwt()
                .jwt(token -> token.subject(account.publicId().toString()))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private record Account(UUID publicId, Long internalId) {
    }
}
