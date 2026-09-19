package com.smartmealplanner.pantry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Append-only quantity history for a PantryItem. */
@Entity
@Table(name = "pantry_item_events")
class PantryItemEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pantry_item_id", nullable = false)
    private Long pantryItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private PantryItemEventType eventType;

    @Column(name = "quantity_delta", nullable = false,
            precision = 12, scale = 4)
    private BigDecimal quantityDelta;

    @Column(name = "quantity_after", nullable = false,
            precision = 12, scale = 4)
    private BigDecimal quantityAfter;

    @Column(name = "meal_plan_entry_id")
    private Long mealPlanEntryId;

    @Column(length = 255)
    private String note;

    @Column(name = "occurred_at", nullable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime occurredAt;

    protected PantryItemEvent() {
    }

    PantryItemEvent(
            Long pantryItemId,
            PantryItemEventType eventType,
            BigDecimal quantityDelta,
            BigDecimal quantityAfter,
            String note,
            LocalDateTime occurredAt) {

        if (pantryItemId == null || eventType == null || occurredAt == null) {
            throw new IllegalArgumentException("Pantry event identity is required");
        }
        if (!PantryItem.fitsDecimal(quantityDelta)
                || quantityAfter == null
                || quantityAfter.signum() < 0
                || !PantryItem.fitsDecimal(quantityAfter)) {
            throw new IllegalArgumentException("Invalid pantry event quantity");
        }
        this.pantryItemId = pantryItemId;
        this.eventType = eventType;
        this.quantityDelta = quantityDelta;
        this.quantityAfter = quantityAfter;
        this.mealPlanEntryId = null;
        this.note = note == null || note.isBlank() ? null : note.trim();
        if (this.note != null && this.note.length() > 255) {
            throw new IllegalArgumentException("note must not exceed 255 characters");
        }
        this.occurredAt = occurredAt;
    }

    Long internalId() {
        return id;
    }

    Long pantryItemId() {
        return pantryItemId;
    }

    PantryItemEventType eventType() {
        return eventType;
    }

    BigDecimal quantityDelta() {
        return quantityDelta;
    }

    BigDecimal quantityAfter() {
        return quantityAfter;
    }

    Long mealPlanEntryId() {
        return mealPlanEntryId;
    }

    String note() {
        return note;
    }

    LocalDateTime occurredAt() {
        return occurredAt;
    }
}
