package com.smartmealplanner.recipe;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipe_steps")
@IdClass(RecipeStepId.class)
class RecipeStep {

    @Id
    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Id
    @Column(name = "step_number", nullable = false)
    private Short stepNumber;

    @Column(nullable = false, length = 2000)
    private String instruction;

    @Column(name = "duration_minutes")
    private Short durationMinutes;

    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected RecipeStep() {
    }

    RecipeStep(Long recipeId, Integer stepNumber, String instruction,
            Integer durationMinutes) {
        if (recipeId == null) {
            throw new IllegalArgumentException("recipeId is required");
        }
        if (stepNumber == null || stepNumber < 1) {
            throw new IllegalArgumentException("stepNumber must be at least 1");
        }
        if (instruction == null || instruction.isBlank()
                || instruction.length() > 2000) {
            throw new IllegalArgumentException("Invalid instruction");
        }
        if (durationMinutes != null
                && (durationMinutes < 0 || durationMinutes > 10080)) {
            throw new IllegalArgumentException("durationMinutes is out of range");
        }
        this.recipeId = recipeId;
        this.stepNumber = RecipeSmallInt.toShort(stepNumber, "stepNumber");
        this.instruction = instruction;
        this.durationMinutes = RecipeSmallInt.toShort(
                durationMinutes, "durationMinutes");
    }

    Long recipeId() { return recipeId; }
    Short stepNumber() { return stepNumber; }
    String instruction() { return instruction; }
    Short durationMinutes() { return durationMinutes; }
    LocalDateTime createdAt() { return createdAt; }
    LocalDateTime updatedAt() { return updatedAt; }
}
