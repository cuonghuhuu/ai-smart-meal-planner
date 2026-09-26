package com.smartmealplanner.recipe;

import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;

@Entity
@Table(name = "meal_slot_types")
class MealSlotType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    @Column(name = "typical_time")
    private LocalTime typicalTime;

    @Column(name = "is_main_meal", nullable = false)
    private boolean mainMeal;

    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected MealSlotType() {
    }

    MealSlotType(String code, String displayName, Short displayOrder,
            LocalTime typicalTime, boolean mainMeal) {
        if (code == null || code.isBlank() || code.length() > 30) {
            throw new IllegalArgumentException("Invalid code");
        }
        if (displayName == null || displayName.isBlank()
                || displayName.length() > 60) {
            throw new IllegalArgumentException("Invalid displayName");
        }
        if (displayOrder == null || displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder is required");
        }
        this.code = code;
        this.displayName = displayName;
        this.displayOrder = displayOrder;
        this.typicalTime = typicalTime;
        this.mainMeal = mainMeal;
    }

    Long internalId() { return id; }
    String code() { return code; }
    String displayName() { return displayName; }
    Short displayOrder() { return displayOrder; }
    LocalTime typicalTime() { return typicalTime; }
    boolean isMainMeal() { return mainMeal; }
    LocalDateTime createdAt() { return createdAt; }
    LocalDateTime updatedAt() { return updatedAt; }
}
