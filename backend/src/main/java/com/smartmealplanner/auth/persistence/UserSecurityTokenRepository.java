package com.smartmealplanner.auth.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSecurityTokenRepository
        extends JpaRepository<UserSecurityToken, Long> {

    Optional<UserSecurityToken> findByTokenHash(
            String tokenHash);

    Optional<UserSecurityToken> findByTokenHashAndTokenKind(
            String tokenHash,
            SecurityTokenKind tokenKind);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from UserSecurityToken token
            join fetch token.user
            where token.tokenHash = :tokenHash
              and token.tokenKind = :tokenKind
              and token.consumedAt is null
              and token.expiresAt > CURRENT_TIMESTAMP
            """)
    Optional<UserSecurityToken> findUsableForUpdate(
            @Param("tokenHash")
            String tokenHash,

            @Param("tokenKind")
            SecurityTokenKind tokenKind);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from UserSecurityToken token
            where token.user.id = :userId
              and token.tokenKind = :tokenKind
              and token.consumedAt is null
            order by token.id
            """)
    List<UserSecurityToken> findOutstandingByUserIdAndKindForUpdate(
            @Param("userId")
            Long userId,

            @Param("tokenKind")
            SecurityTokenKind tokenKind);

    /*
     * user_security_tokens.issued_at is populated by MySQL with
     * CURRENT_TIMESTAMP(6). Expiration must therefore be calculated from
     * the database clock as well, otherwise a JVM/database timezone
     * difference can make a short-lived token appear to expire before it
     * was issued and violate ck_user_security_tokens_expiry.
     */
    @Query(
            value = "select CURRENT_TIMESTAMP(6)",
            nativeQuery = true)
    LocalDateTime currentDatabaseTime();
}
