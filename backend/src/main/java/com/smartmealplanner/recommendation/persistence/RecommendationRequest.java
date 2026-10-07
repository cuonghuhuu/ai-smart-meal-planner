package com.smartmealplanner.recommendation.persistence;

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
public class RecommendationRequest {
    public enum Kind { RECIPE_SUGGESTION, MEAL_PLAN, PANTRY_USE_UP, SUBSTITUTION }
    public enum Status { PENDING, SUCCEEDED, DEGRADED, INFEASIBLE, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private byte[] publicId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING) @Column(name = "request_kind", nullable = false, length = 30)
    private Kind requestKind;
    @Column(name = "target_meal_slot_type_id")
    private Long targetMealSlotTypeId;
    @Column(name = "target_date")
    private LocalDate targetDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "correlation_id", length = 36, columnDefinition = "CHAR(36)")
    private String correlationId;
    @Column(name = "constraints_hash", length = 64, columnDefinition = "CHAR(64)")
    private String constraintsHash;
    @Column(name = "algorithm_version", length = 60)
    private String algorithmVersion;
    @Column(name = "model_identifier", length = 120)
    private String modelIdentifier;
    @Column(name = "requested_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime requestedAt;
    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime completedAt;
    @Column(name = "duration_ms")
    private Integer durationMs;
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected RecommendationRequest() { }

    public RecommendationRequest(Long userId, Kind requestKind) {
        if (userId == null || requestKind == null) {
            throw new IllegalArgumentException("userId and requestKind are required");
        }
        this.publicId = RecommendationPublicIds.bytes(UUID.randomUUID());
        this.userId = userId;
        this.requestKind = requestKind;
        this.status = Status.PENDING;
    }

    public static RecommendationRequest begin(Long userId, Kind requestKind, UUID publicId,
            String algorithmVersion, String constraintsHash) {
        if (requestKind == null || publicId == null
                || algorithmVersion == null || algorithmVersion.isBlank()
                || algorithmVersion.length() > 60
                || constraintsHash == null || !constraintsHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Invalid request metadata");
        }
        RecommendationRequest request = new RecommendationRequest(userId, requestKind);
        request.publicId = RecommendationPublicIds.bytes(publicId);
        request.correlationId = publicId.toString();
        request.algorithmVersion = algorithmVersion;
        request.constraintsHash = constraintsHash;
        return request;
    }

    public Long id() { return id; }
    public UUID publicId() { return RecommendationPublicIds.uuid(publicId); }
    public Long userId() { return userId; }
    public Kind requestKind() { return requestKind; }
    public Long targetMealSlotTypeId() { return targetMealSlotTypeId; }
    public LocalDate targetDate() { return targetDate; }
    public Status status() { return status; }
    public String correlationId() { return correlationId; }
    public String constraintsHash() { return constraintsHash; }
    public String algorithmVersion() { return algorithmVersion; }
    public String modelIdentifier() { return modelIdentifier; }
    public LocalDateTime requestedAt() { return requestedAt; }
    public LocalDateTime completedAt() { return completedAt; }
    public Integer durationMs() { return durationMs; }
    public String failureReason() { return failureReason; }

    public void finish(Status status, LocalDateTime completedAt, String failureReason) {
        finish(status, completedAt, null, failureReason);
    }

    public void finish(Status status, LocalDateTime completedAt, Integer durationMs,
            String failureReason) {
        if (status == null || status == Status.PENDING || completedAt == null
                || this.status != Status.PENDING
                || durationMs != null && durationMs < 0
                || (status == Status.FAILED) != (failureReason != null)
                || failureReason != null && (failureReason.isBlank()
                || failureReason.length() > 255)) {
            throw new IllegalArgumentException("Invalid terminal request state");
        }
        this.status = status;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
        this.failureReason = failureReason;
    }
}
