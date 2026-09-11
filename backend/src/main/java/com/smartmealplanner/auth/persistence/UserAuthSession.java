package com.smartmealplanner.auth.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_auth_sessions")
public class UserAuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(
            name = "refresh_token_hash",
            nullable = false,
            unique = true,
            length = 64,
            columnDefinition = "CHAR(64)")
    private String refreshTokenHash;

    @Column(
            name = "issued_at",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 30)
    private SessionRevocationReason revocationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_session_id")
    private UserAuthSession replacedBySession;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_kind", nullable = false, length = 20)
    private ClientKind clientKind;

    @Column(name = "ip_address", columnDefinition = "VARBINARY(16)")
    private byte[] ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    protected UserAuthSession() {
    }

    public UserAuthSession(
            UserAccount user,
            String refreshTokenHash,
            LocalDateTime expiresAt,
            ClientKind clientKind,
            byte[] ipAddress,
            String userAgent) {

        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }

        if (refreshTokenHash == null || refreshTokenHash.length() != 64) {
            throw new IllegalArgumentException("refreshTokenHash must be a 64-character SHA-256 hex value");
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }

        this.user = user;
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
        this.clientKind = clientKind == null ? ClientKind.UNKNOWN : clientKind;
        this.ipAddress = ipAddress == null ? null : ipAddress.clone();
        this.userAgent = normalizeUserAgent(userAgent);
    }

    public Long internalId() {
        return id;
    }

    public UserAccount user() {
        return user;
    }

    public String refreshTokenHash() {
        return refreshTokenHash;
    }

    public LocalDateTime issuedAt() {
        return issuedAt;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public LocalDateTime lastUsedAt() {
        return lastUsedAt;
    }

    public LocalDateTime revokedAt() {
        return revokedAt;
    }

    public SessionRevocationReason revocationReason() {
        return revocationReason;
    }

    public UserAuthSession replacedBySession() {
        return replacedBySession;
    }

    public ClientKind clientKind() {
        return clientKind;
    }

    public byte[] ipAddress() {
        return ipAddress == null ? null : ipAddress.clone();
    }

    public String userAgent() {
        return userAgent;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void markUsed(LocalDateTime usedAt) {
        if (usedAt == null) {
            throw new IllegalArgumentException("usedAt is required");
        }
        this.lastUsedAt = usedAt;
    }

    public void revoke(
            LocalDateTime revokedAt,
            SessionRevocationReason reason,
            UserAuthSession replacement) {

        if (revokedAt == null) {
            throw new IllegalArgumentException("revokedAt is required");
        }

        if (reason == null) {
            throw new IllegalArgumentException("revocation reason is required");
        }

        this.revokedAt = revokedAt;
        this.revocationReason = reason;
        this.replacedBySession = replacement;
    }

    private static String normalizeUserAgent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.length() <= 255
                ? trimmed
                : trimmed.substring(0, 255);
    }
}