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
@Table(name = "user_security_tokens")
public class UserSecurityToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_kind", nullable = false, length = 30)
    private SecurityTokenKind tokenKind;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64,
            columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(
            name = "issued_at",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime consumedAt;

    protected UserSecurityToken() {
    }

    public UserSecurityToken(
            UserAccount user,
            SecurityTokenKind tokenKind,
            String tokenHash,
            LocalDateTime expiresAt) {

        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }

        if (tokenKind == null) {
            throw new IllegalArgumentException("tokenKind is required");
        }

        if (tokenHash == null || tokenHash.length() != 64) {
            throw new IllegalArgumentException(
                    "tokenHash must be a 64-character SHA-256 hex value");
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }

        this.user = user;
        this.tokenKind = tokenKind;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long internalId() {
        return id;
    }

    public UserAccount user() {
        return user;
    }

    public SecurityTokenKind tokenKind() {
        return tokenKind;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public LocalDateTime issuedAt() {
        return issuedAt;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public LocalDateTime consumedAt() {
        return consumedAt;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }

        return !expiresAt.isAfter(now);
    }

    public void consume(LocalDateTime consumedAt) {
        if (consumedAt == null) {
            throw new IllegalArgumentException("consumedAt is required");
        }

        if (this.consumedAt != null) {
            throw new IllegalStateException("Security token is already consumed");
        }

        this.consumedAt = consumedAt;
    }
}