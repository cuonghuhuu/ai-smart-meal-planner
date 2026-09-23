package com.smartmealplanner.mealplanning.contract.v1;

import java.math.BigDecimal;

/** Closed vocabularies owned by meal-planning contract version 1. */
public final class MealPlanningContractCodes {

    public enum AlgorithmVersion {
        HEURISTIC_MEAL_PLAN_V1
    }

    public enum MealSlotCode {
        BREAKFAST,
        MORNING_SNACK,
        LUNCH,
        AFTERNOON_SNACK,
        DINNER,
        EVENING_SNACK
    }

    public enum UnitDimension {
        MASS,
        VOLUME,
        COUNT,
        ENERGY
    }

    public enum ExpiryKind {
        USE_BY,
        BEST_BEFORE,
        UNKNOWN
    }

    public enum StorageLocation {
        PANTRY,
        FRIDGE,
        FREEZER,
        OTHER
    }

    public enum AllergenEvidenceStatus {
        CONTAINS,
        MAY_CONTAIN,
        FREE_FROM,
        UNKNOWN
    }

    public enum GenerationStatus {
        SUCCEEDED,
        DEGRADED,
        INFEASIBLE
    }

    public enum ScoreComponentCode {
        PANTRY_COVERAGE("0.40"),
        NUTRITION_FIT("0.25"),
        EXPIRY_URGENCY("0.10"),
        PREFERENCE_MATCH("0.10"),
        VARIETY("0.10"),
        EFFORT_FIT("0.05"),
        DISLIKE_PENALTY("0.20");

        private final BigDecimal expectedPositiveWeight;

        ScoreComponentCode(String expectedPositiveWeight) {
            this.expectedPositiveWeight = new BigDecimal(expectedPositiveWeight);
        }

        public BigDecimal expectedPositiveWeight() {
            return expectedPositiveWeight;
        }
    }

    public enum UnfilledSlotReasonCode {
        NO_ELIGIBLE_RECIPE,
        HARD_CONSTRAINT_CONFLICT,
        UNSUPPORTED_HARD_CONSTRAINT,
        PANTRY_INFEASIBLE,
        NUTRITION_INFEASIBLE,
        SEARCH_LIMIT_REACHED
    }

    private MealPlanningContractCodes() {
    }
}
