package com.smartmealplanner.auth.persistence;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "public_id",
            nullable = false,
            updatable = false,
            columnDefinition = "BINARY(16)")
    private byte[] publicId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(
            name = "email_normalized",
            nullable = false,
            insertable = false,
            updatable = false,
            length = 320)
    private String emailNormalized;

    @Column(
            name = "password_hash",
            nullable = false,
            length = 255)
    private String passwordHash;

    @Column(
            name = "password_updated_at",
            columnDefinition = "DATETIME(6)")
    private LocalDateTime passwordUpdatedAt;

    @Column(
            name = "display_name",
            nullable = false,
            length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "account_status",
            nullable = false,
            length = 20)
    private AccountStatus accountStatus;

    @Column(
            name = "email_verified_at",
            columnDefinition = "DATETIME(6)")
    private LocalDateTime emailVerifiedAt;

    @Column(
            name = "failed_login_count",
            nullable = false)
    private Short failedLoginCount;

    @Column(
            name = "locked_until",
            columnDefinition = "DATETIME(6)")
    private LocalDateTime lockedUntil;

    @Column(
            name = "last_login_at",
            columnDefinition = "DATETIME(6)")
    private LocalDateTime lastLoginAt;

    @Column(
            name = "deactivated_at",
            columnDefinition = "DATETIME(6)")
    private LocalDateTime deactivatedAt;

    @Column(
            name = "anonymized_at",
            columnDefinition = "DATETIME(6)")
    private LocalDateTime anonymizedAt;

    @Column(
            name = "time_zone",
            nullable = false,
            length = 64)
    private String timeZone;

    @Column(
            nullable = false,
            length = 20)
    private String locale;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected UserAccount() {
    }

    public UserAccount(
            String email,
            String passwordHash,
            String displayName) {

        this.publicId =
                uuidToBytes(UUID.randomUUID());

        this.email =
                normalizeEmail(email);

        this.passwordHash =
                requireText(
                        passwordHash,
                        255,
                        "passwordHash");

        this.displayName =
                requireText(
                        displayName,
                        100,
                        "displayName")
                        .trim();

        this.accountStatus =
                AccountStatus.PENDING_VERIFICATION;

        this.failedLoginCount = 0;
        this.timeZone = "UTC";
        this.locale = "en";
    }

    public Long internalId() {
        return id;
    }

    public UUID publicId() {
        ByteBuffer bytes =
                ByteBuffer.wrap(publicId);

        return new UUID(
                bytes.getLong(),
                bytes.getLong());
    }

    public String email() {
        return email;
    }

    public String emailNormalized() {
        return emailNormalized;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public AccountStatus accountStatus() {
        return accountStatus;
    }

    public LocalDateTime emailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Short failedLoginCount() {
        return failedLoginCount;
    }

    public String timeZone() {
        return timeZone;
    }

    public String locale() {
        return locale;
    }

    public Long version() {
        return version;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public void verifyEmail(
            LocalDateTime verifiedAt) {

        if (verifiedAt == null) {
            throw new IllegalArgumentException(
                    "verifiedAt is required");
        }

        if (accountStatus
                != AccountStatus.PENDING_VERIFICATION) {

            throw new IllegalStateException(
                    "Account is not pending verification");
        }

        if (emailVerifiedAt != null) {
            throw new IllegalStateException(
                    "Email is already verified");
        }

        this.emailVerifiedAt =
                verifiedAt;

        this.accountStatus =
                AccountStatus.ACTIVE;
    }

    private static String normalizeEmail(
            String value) {

        return requireText(
                value,
                320,
                "email")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String requireText(
            String value,
            int maxLength,
            String field) {

        if (value == null
                || value.isBlank()
                || value.length() > maxLength) {

            throw new IllegalArgumentException(
                    "Invalid " + field);
        }

        return value;
    }

    private static byte[] uuidToBytes(
            UUID uuid) {

        return ByteBuffer.allocate(16)
                .putLong(
                        uuid.getMostSignificantBits())
                .putLong(
                        uuid.getLeastSignificantBits())
                .array();
    }
}