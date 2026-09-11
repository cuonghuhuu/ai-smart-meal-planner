package com.smartmealplanner.auth.persistence;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AuthPersistenceIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("p4_auth_persistence")
                    .withUsername("p4_auth_test")
                    .withPassword(UUID.randomUUID().toString())
                    .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    UserAccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Autowired
    UserRoleRepository userRoles;

    @Autowired
    UserAuthSessionRepository sessions;

    @Autowired
    UserSecurityTokenRepository securityTokens;

    @Autowired
    PlatformTransactionManager transactions;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void authenticationPersistenceMatchesP2Schema() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UserAccount account = accounts.saveAndFlush(
                    new UserAccount(
                            "  Auth.Probe@Example.COM  ",
                            "{noop}not-a-real-password",
                            "Auth Probe"));

            entityManager.refresh(account);

            assertThat(account.internalId()).isPositive();
            assertThat(account.publicId()).isNotNull();
            assertThat(account.email()).isEqualTo("auth.probe@example.com");
            assertThat(account.emailNormalized()).isEqualTo("auth.probe@example.com");
            assertThat(account.accountStatus())
                    .isEqualTo(AccountStatus.PENDING_VERIFICATION);
            assertThat(account.failedLoginCount()).isZero();
            assertThat(account.timeZone()).isEqualTo("UTC");
            assertThat(account.locale()).isEqualTo("en");
            assertThat(account.createdAt()).isNotNull();
            assertThat(account.updatedAt()).isNotNull();

            assertThat(
                    accounts.findByEmailNormalized("auth.probe@example.com"))
                    .contains(account);

            assertThat(
                    accounts.findByPublicId(uuidBytes(account.publicId())))
                    .contains(account);

            Role userRole = roles.findByCode("ROLE_USER").orElseThrow();

            assertThat(userRole.code()).isEqualTo("ROLE_USER");

            UserRole assignment =
                    userRoles.saveAndFlush(
                            new UserRole(account, userRole, null));

            entityManager.refresh(assignment);

            assertThat(assignment.grantedAt()).isNotNull();
            assertThat(assignment.grantedBy()).isNull();

            assertThat(userRoles.findAllByUser_Id(account.internalId()))
                    .extracting(item -> item.role().code())
                    .containsExactly("ROLE_USER");

            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

            UserAuthSession session =
                    sessions.saveAndFlush(
                            new UserAuthSession(
                                    account,
                                    "a".repeat(64),
                                    now.plusDays(30),
                                    ClientKind.WEB,
                                    null,
                                    "P4 auth persistence probe"));

            entityManager.refresh(session);

            assertThat(session.internalId()).isPositive();
            assertThat(session.issuedAt()).isNotNull();
            assertThat(session.clientKind()).isEqualTo(ClientKind.WEB);
            assertThat(session.isRevoked()).isFalse();

            assertThat(
                    sessions.findByRefreshTokenHash("a".repeat(64)))
                    .contains(session);

            assertThat(
                    sessions.findByRefreshTokenHashForUpdate("a".repeat(64)))
                    .contains(session);

            UserSecurityToken securityToken =
                    securityTokens.saveAndFlush(
                            new UserSecurityToken(
                                    account,
                                    SecurityTokenKind.EMAIL_VERIFICATION,
                                    "b".repeat(64),
                                    now.plusDays(1)));

            entityManager.refresh(securityToken);

            assertThat(securityToken.internalId()).isPositive();
            assertThat(securityToken.issuedAt()).isNotNull();
            assertThat(securityToken.isConsumed()).isFalse();

            assertThat(
                    securityTokens.findByTokenHashAndTokenKind(
                            "b".repeat(64),
                            SecurityTokenKind.EMAIL_VERIFICATION))
                    .contains(securityToken);

            status.setRollbackOnly();
        });
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}