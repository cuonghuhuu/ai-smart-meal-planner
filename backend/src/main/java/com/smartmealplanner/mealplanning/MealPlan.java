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
import jakarta.persistence.Version;

@Entity
@Table(name = "meal_plans")
class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true,
            updatable = false, columnDefinition = "BINARY(16)")
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
    @Column(name = "default_servings", nullable = false)
    private Byte defaultServings;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealPlanStatus status;
    @Column(name = "source_request_id")
    private Long sourceRequestId;
    @Column(name = "accepted_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime acceptedAt;
    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime completedAt;
    @Column(name = "archived_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime archivedAt;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected MealPlan() {
    }

    MealPlan(Long userId, String title, LocalDate startDate, LocalDate endDate,
            int mealsPerDayTarget, int defaultServings, Long sourceRequestId) {
        if (userId == null || title == null || title.isBlank() || title.length() > 150
                || startDate == null || endDate == null || endDate.isBefore(startDate)
                || mealsPerDayTarget < 1 || mealsPerDayTarget > 12
                || defaultServings < 1 || defaultServings > 50 || sourceRequestId == null) {
            throw new IllegalArgumentException("Invalid meal plan");
        }
        this.publicId = MealPlanningIds.uuidToBytes(UUID.randomUUID());
        this.userId = userId;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.mealsPerDayTarget = (byte) mealsPerDayTarget;
        this.defaultServings = (byte) defaultServings;
        this.status = MealPlanStatus.DRAFT;
        this.sourceRequestId = sourceRequestId;
    }

    Long internalId() { return id; }
    UUID publicId() { return MealPlanningIds.bytesToUuid(publicId); }
    Long userId() { return userId; }
    String title() { return title; }
    LocalDate startDate() { return startDate; }
    LocalDate endDate() { return endDate; }
    Short dayCount() { return dayCount; }
    Byte mealsPerDayTarget() { return mealsPerDayTarget; }
    Byte defaultServings() { return defaultServings; }
    MealPlanStatus status() { return status; }
    Long sourceRequestId() { return sourceRequestId; }
    LocalDateTime acceptedAt() { return acceptedAt; }
    LocalDateTime createdAt() { return createdAt; }
    LocalDateTime updatedAt() { return updatedAt; }

    void accept(LocalDateTime acceptedAt) {
        if (status == MealPlanStatus.ACCEPTED) {
            return;
        }
        if (status != MealPlanStatus.DRAFT || acceptedAt == null) {
            throw new IllegalStateException("Meal plan cannot be accepted");
        }
        this.status = MealPlanStatus.ACCEPTED;
        this.acceptedAt = acceptedAt;
    }
}
