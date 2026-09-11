package com.smartmealplanner.auth.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSecurityTokenRepository
        extends JpaRepository<UserSecurityToken, Long> {

    Optional<UserSecurityToken> findByTokenHash(String tokenHash);

    Optional<UserSecurityToken> findByTokenHashAndTokenKind(
            String tokenHash,
            SecurityTokenKind tokenKind);
}