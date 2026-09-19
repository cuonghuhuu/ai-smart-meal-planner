package com.smartmealplanner.mealplanning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recommendation_requests")
class RecommendationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true,
            updatable = false, columnDefinition = "BINARY(16)")
    private byte[] publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_kind", nullable = false, length = 30)
    private RecommendationRequestKind requestKind;

    @Column(name = "target_meal_slot_type_id")
    private Long targetMealSlotTypeId;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status;

    @Column(name = "correlation_id", length = 36, columnDefinition = "CHAR(36)")
    private String correlationId;

    @Column(name = "constraints_hash", length = 64, columnDefinition = "CHAR(64)")
    private String constraintsHash;

    @Column(name = "algorithm_version", length = 60)
    private String algorithmVersion;

    @Column(name = "model_identifier", length = 120)
    private String modelIdentifier;

    @Column(name = "requested_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime requestedAt;

    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected RecommendationRequest() {
    }

    RecommendationRequest(Long userId, RecommendationRequestKind requestKind,
            String correlationId, String constraintsHash, String algorithmVersion,
            String modelIdentifier, LocalDateTime requestedAt) {
        if (userId == null || requestKind == null || requestedAt == null) {
            throw new IllegalArgumentException("recommendation request identity is required");
        }
        this.publicId = MealPlanningIds.uuidToBytes(UUID.randomUUID());
        this.userId = userId;
        this.requestKind = requestKind;
        this.status = RecommendationStatus.PENDING;
        this.correlationId = bounded(correlationId, 36, "correlationId");
        this.constraintsHash = bounded(constraintsHash, 64, "constraintsHash");
        this.algorithmVersion = bounded(algorithmVersion, 60, "algorithmVersion");
        this.modelIdentifier = bounded(modelIdentifier, 120, "modelIdentifier");
        this.requestedAt = requestedAt;
    }

    Long internalId() { return id; }
    UUID publicId() { return MealPlanningIds.bytesToUuid(publicId); }
    Long userId() { return userId; }
    RecommendationRequestKind requestKind() { return requestKind; }
    RecommendationStatus status() { return status; }
    LocalDateTime requestedAt() { return requestedAt; }

    void complete(RecommendationStatus completedStatus, LocalDateTime completedAt,
            Integer durationMs, String failureReason) {
        if (completedStatus == null || completedStatus == RecommendationStatus.PENDING
                || completedAt == null || completedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("Invalid recommendation completion");
        }
        this.status = completedStatus;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
        this.failureReason = bounded(failureReason, 255, "failureReason");
    }

    private static String bounded(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return value;
    }
}
