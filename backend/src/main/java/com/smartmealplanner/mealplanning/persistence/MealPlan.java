package com.smartmealplanner.mealplanning.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.smartmealplanner.recommendation.persistence.RecommendationRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "meal_plans")
public class MealPlan {
    public enum Status { DRAFT, ACCEPTED, ACTIVE, COMPLETED, ABANDONED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private byte[] publicId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(length = 150)
    private String title;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(name = "day_count", insertable = false, updatable = false)
    private Short dayCount;
    @Column(name = "meals_per_day_target")
    private Byte mealsPerDayTarget;
    @Column(name = "default_servings", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultServings;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_request_id", unique = true)
    private RecommendationRequest sourceRequest;
    @Column(name = "accepted_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime acceptedAt;
    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime completedAt;
    @Column(name = "archived_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime archivedAt;
    @Version @Column(nullable = false)
    private Long version;
    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected MealPlan() { }

    public MealPlan(Long userId, LocalDate startDate, LocalDate endDate,
            BigDecimal defaultServings, RecommendationRequest sourceRequest) {
        if (userId == null || startDate == null || endDate == null
                || ChronoUnit.DAYS.between(startDate, endDate) < 0
                || ChronoUnit.DAYS.between(startDate, endDate) > 30
                || defaultServings == null || defaultServings.signum() <= 0
                || defaultServings.compareTo(new BigDecimal("50")) > 0
                || (sourceRequest != null && !userId.equals(sourceRequest.userId()))) {
            throw new IllegalArgumentException("Invalid meal plan");
        }
        this.publicId = PublicIds.bytes(UUID.randomUUID());
        this.userId = userId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.defaultServings = defaultServings;
        this.sourceRequest = sourceRequest;
        this.status = Status.DRAFT;
    }

    public Long id() { return id; }
    public UUID publicId() { return PublicIds.uuid(publicId); }
    public Long userId() { return userId; }
    public String title() { return title; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public Short dayCount() { return dayCount; }
    public Byte mealsPerDayTarget() { return mealsPerDayTarget; }
    public BigDecimal defaultServings() { return defaultServings; }
    public Status status() { return status; }
    public RecommendationRequest sourceRequest() { return sourceRequest; }
    public LocalDateTime acceptedAt() { return acceptedAt; }
    public LocalDateTime completedAt() { return completedAt; }
    public LocalDateTime archivedAt() { return archivedAt; }
    public Long version() { return version; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}