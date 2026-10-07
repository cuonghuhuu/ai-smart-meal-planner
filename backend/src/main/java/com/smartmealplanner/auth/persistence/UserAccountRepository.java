package com.smartmealplanner.auth.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
            select account.*
            from users account
            where (:query is null
                   or lower(account.email) like concat('%', lower(:query), '%')
                   or lower(account.display_name) like concat('%', lower(:query), '%'))
              and (:status is null or account.account_status = :status)
            order by account.created_at asc, account.public_id asc
            """, countQuery = """
            select count(*)
            from users account
            where (:query is null
                   or lower(account.email) like concat('%', lower(:query), '%')
                   or lower(account.display_name) like concat('%', lower(:query), '%'))
              and (:status is null or account.account_status = :status)
            """, nativeQuery = true)
    Page<UserAccount> findForAdmin(
            @Param("query") String query,
            @Param("status") String status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from UserAccount account
            where account.publicId = :publicId
            """)
    Optional<UserAccount> findByPublicIdForUpdate(
            @Param("publicId") byte[] publicId);

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
