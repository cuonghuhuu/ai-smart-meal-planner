package com.smartmealplanner.mealplanning.contract.v1;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.AlgorithmVersion;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.ScoreComponentCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.UnfilledSlotReasonCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_EXPLANATION_LENGTH;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_EXPANDED_PLAN_SLOTS;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_SCORE_COMPONENTS;

/** Strict internal response envelope for meal-planning contract version 1. */
public record MealPlanGenerationResponse(
        @NotNull
        @Pattern(regexp = "^1$")
        String contractVersion,

        @NotNull
        UUID requestId,

        @NotNull
        AlgorithmVersion algorithmVersion,

        @NotNull
        GenerationStatus status,

        @NotNull
        @Size(max = MAX_EXPANDED_PLAN_SLOTS)
        List<@NotNull @Valid Entry> entries,

        @NotNull
        @Size(max = MAX_EXPANDED_PLAN_SLOTS)
        List<@NotNull @Valid UnfilledSlot> unfilledSlots) {

    public MealPlanGenerationResponse {
        entries = immutable(entries);
        unfilledSlots = immutable(unfilledSlots);
    }

    @JsonIgnore
    @AssertTrue(message = "status must agree with populated and unfilled slots")
    public boolean isStatusShapeValid() {
        if (status == null || entries == null || unfilledSlots == null) {
            return true;
        }
        return switch (status) {
            case SUCCEEDED -> !entries.isEmpty() && unfilledSlots.isEmpty();
            case DEGRADED -> !entries.isEmpty() && !unfilledSlots.isEmpty();
            case INFEASIBLE -> entries.isEmpty() && !unfilledSlots.isEmpty();
        };
    }

    @JsonIgnore
    @AssertTrue(message = "combined entries and unfilled slots exceed the V1 slot limit")
    public boolean isWithinExpandedSlotLimit() {
        return entries == null || unfilledSlots == null
                || entries.size() + unfilledSlots.size() <= MAX_EXPANDED_PLAN_SLOTS;
    }

    @JsonIgnore
    @AssertTrue(message = "slot/date pairs must be unique and cannot be both filled and unfilled")
    public boolean isSlotAssignmentUnique() {
        if (entries == null || unfilledSlots == null) {
            return true;
        }
        Set<SlotKey> filled = new HashSet<>();
        for (Entry entry : entries) {
            if (entry != null && !filled.add(new SlotKey(entry.planDate(), entry.mealSlotCode()))) {
                return false;
            }
        }
        Set<SlotKey> unfilled = new HashSet<>();
        for (UnfilledSlot slot : unfilledSlots) {
            if (slot == null) {
                continue;
            }
            SlotKey key = new SlotKey(slot.planDate(), slot.mealSlotCode());
            if (!unfilled.add(key) || filled.contains(key)) {
                return false;
            }
        }
        return true;
    }

    public record Entry(
            @NotNull
            LocalDate planDate,

            @NotNull
            MealSlotCode mealSlotCode,

            @NotNull
            UUID recipePublicId,

            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @DecimalMax("50.00")
            @Digits(integer = 2, fraction = 2)
            BigDecimal servings,

            @NotNull
            @DecimalMin("-0.20")
            @DecimalMax("1.00")
            @Digits(integer = 1, fraction = 6)
            BigDecimal totalScore,

            @NotNull
            @Size(min = MAX_SCORE_COMPONENTS, max = MAX_SCORE_COMPONENTS)
            List<@NotNull @Valid ScoreComponent> scoreComponents,

            @NotBlank
            @Size(max = MAX_EXPLANATION_LENGTH)
            String explanation) {

        public Entry {
            scoreComponents = immutable(scoreComponents);
        }

        @JsonIgnore
        @AssertTrue(message = "all seven score-component codes are required exactly once")
        public boolean isScoreComponentSetComplete() {
            if (scoreComponents == null || scoreComponents.size() != MAX_SCORE_COMPONENTS) {
                return true;
            }
            EnumSet<ScoreComponentCode> codes = EnumSet.noneOf(ScoreComponentCode.class);
            for (ScoreComponent component : scoreComponents) {
                if (component == null || component.componentCode() == null
                        || !codes.add(component.componentCode())) {
                    return false;
                }
            }
            return codes.equals(EnumSet.allOf(ScoreComponentCode.class));
        }
    }

    public record ScoreComponent(
            @NotNull
            ScoreComponentCode componentCode,

            @NotNull
            @DecimalMin("0")
            @DecimalMax("1")
            @Digits(integer = 1, fraction = 6)
            BigDecimal value,

            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @DecimalMax("1")
            @Digits(integer = 1, fraction = 2)
            BigDecimal weight) {

        @JsonIgnore
        @AssertTrue(message = "component weight must match HEURISTIC_MEAL_PLAN_V1")
        public boolean isExpectedWeight() {
            return componentCode == null || weight == null
                    || weight.compareTo(componentCode.expectedPositiveWeight()) == 0;
        }
    }

    public record UnfilledSlot(
            @NotNull
            LocalDate planDate,

            @NotNull
            MealSlotCode mealSlotCode,

            @NotNull
            UnfilledSlotReasonCode reasonCode,

            @Size(max = MAX_EXPLANATION_LENGTH)
            String explanation) {
    }

    private record SlotKey(LocalDate date, MealSlotCode mealSlotCode) {
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
