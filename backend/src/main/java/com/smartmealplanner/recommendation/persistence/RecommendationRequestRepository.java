package com.smartmealplanner.recommendation.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRequestRepository extends JpaRepository<RecommendationRequest, Long> {
    Optional<RecommendationRequest> findByPublicId(byte[] publicId);

    default Optional<RecommendationRequest> findByPublicId(UUID publicId) {
        return findByPublicId(RecommendationPublicIds.bytes(publicId));
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from RecommendationRequest request where request.id = :requestId")
    Optional<RecommendationRequest> findByIdForUpdate(@Param("requestId") Long requestId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE recommendation_requests
            SET status = :status, completed_at = CURRENT_TIMESTAMP(6),
                duration_ms = :durationMs, failure_reason = :failureReason
            WHERE id = :requestId AND request_kind = :requestKind AND status = 'PENDING'
            """, nativeQuery = true)
    int completePending(@Param("requestId") Long requestId,
            @Param("requestKind") String requestKind,
            @Param("status") String status,
            @Param("durationMs") Integer durationMs,
            @Param("failureReason") String failureReason);
}
