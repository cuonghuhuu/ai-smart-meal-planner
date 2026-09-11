package com.smartmealplanner.auth.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailNormalized(
            String emailNormalized);

    boolean existsByEmailNormalized(
            String emailNormalized);

    Optional<UserAccount> findByPublicId(
            byte[] publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from UserAccount account
            where account.emailNormalized = :emailNormalized
            """)
    Optional<UserAccount> findByEmailNormalizedForUpdate(
            @Param("emailNormalized")
            String emailNormalized);
}
