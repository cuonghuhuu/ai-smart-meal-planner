package com.smartmealplanner.mealplanning.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.AlgorithmVersion;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.ScoreComponentCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.UnfilledSlotReasonCode;

/** Values prepared for persistence; no HTTP payload or JPA entity crosses this boundary. */
public final class MealPlanPersistenceCommands {
    private MealPlanPersistenceCommands() { }

    public record Begin(Long userId, UUID requestPublicId, AlgorithmVersion algorithmVersion,
            String constraintsHash) { }

    public record Started(Long requestId, UUID requestPublicId) { }

    public record GeneratedPlan(Long requestId, GenerationStatus status,
            LocalDate startDate, LocalDate endDate, BigDecimal defaultServings,
            Integer durationMs, List<MealSlotCode> requestedMealSlots,
            List<Entry> entries, List<Gap> gaps) {
        public GeneratedPlan {
            requestedMealSlots = requestedMealSlots == null
                    ? null : List.copyOf(requestedMealSlots);
            entries = entries == null ? null : List.copyOf(entries);
            gaps = gaps == null ? null : List.copyOf(gaps);
        }
    }

    public record Entry(LocalDate planDate, MealSlotCode mealSlotCode,
            short positionInSlot, Long recipeId, BigDecimal servings,
            BigDecimal totalScore, String explanation, List<Score> scores) {
        public Entry { scores = scores == null ? null : List.copyOf(scores); }
    }

    public record Score(ScoreComponentCode componentCode, BigDecimal value,
            BigDecimal weight) { }

    public record Gap(LocalDate planDate, MealSlotCode mealSlotCode,
            UnfilledSlotReasonCode reasonCode, String explanation) { }
}
