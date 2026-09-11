package com.smartmealplanner.auth.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAuthSessionRepository
        extends JpaRepository<UserAuthSession, Long> {

    Optional<UserAuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from UserAuthSession session
            where session.refreshTokenHash = :refreshTokenHash
            """)
    Optional<UserAuthSession> findByRefreshTokenHashForUpdate(
            @Param("refreshTokenHash") String refreshTokenHash);
}