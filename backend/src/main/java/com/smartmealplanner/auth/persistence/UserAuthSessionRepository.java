package com.smartmealplanner.auth.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAuthSessionRepository
        extends JpaRepository<UserAuthSession, Long> {

    Optional<UserAuthSession> findByRefreshTokenHash(
            String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from UserAuthSession session
            join fetch session.user
            where session.refreshTokenHash = :refreshTokenHash
            """)
    Optional<UserAuthSession> findByRefreshTokenHashForUpdate(
            @Param("refreshTokenHash")
            String refreshTokenHash);

    @Query("""
            select case
                when count(session) > 0 then true
                else false
            end
            from UserAuthSession session
            where session.id = :sessionId
              and session.expiresAt <= CURRENT_TIMESTAMP
            """)
    boolean isExpired(
            @Param("sessionId")
            Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from UserAuthSession session
            where session.user.id = :userId
              and session.revokedAt is null
            order by session.id
            """)
    List<UserAuthSession> findActiveByUserIdForUpdate(
            @Param("userId")
            Long userId);
}