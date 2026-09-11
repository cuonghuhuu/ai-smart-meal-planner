package com.smartmealplanner.auth.persistence;

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
}