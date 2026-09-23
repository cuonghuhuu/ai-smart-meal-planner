package com.smartmealplanner.mealplanning.contract.v1;

/**
 * Transport limits for meal-planning contract version 1.
 *
 * <p>The limits are deliberately independent of search configuration. They
 * bound JSON parsing, validation and future search input before any algorithm
 * executes.
 */
public final class MealPlanningContractLimits {

    public static final String CONTRACT_VERSION = "1";

    public static final int MAX_PLAN_DAYS = 7;
    public static final int MAX_REQUESTED_MEAL_SLOTS = 6;
    public static final int MAX_EXPANDED_PLAN_SLOTS =
            MAX_PLAN_DAYS * MAX_REQUESTED_MEAL_SLOTS;

    public static final int MAX_ALLERGEN_CODES = 32;
    public static final int MAX_DIETARY_CODES = 32;
    public static final int MAX_PREFERENCE_CODES = 32;
    public static final int MAX_INGREDIENT_PREFERENCE_IDS = 500;
    public static final int MAX_NUTRITION_TARGETS = 64;
    public static final int MAX_UNIT_DEFINITIONS = 128;
    public static final int MAX_PANTRY_LOTS = 2_000;
    public static final int MAX_INGREDIENT_FACTS = 2_500;
    public static final int MAX_ALLERGEN_FACTS_PER_INGREDIENT = 64;
    public static final int MAX_RECIPE_CANDIDATES = 200;
    public static final int MAX_INGREDIENTS_PER_RECIPE = 50;
    public static final int MAX_RECIPE_TAG_CODES = 64;
    public static final int MAX_NUTRITION_VALUES_PER_RECIPE = 64;
    public static final int MAX_SCORE_COMPONENTS = 7;
    public static final int MAX_EXPLANATION_LENGTH = 500;
    public static final int MAX_REQUEST_BYTES = 5 * 1024 * 1024;

    /** Reserved upper bound for the future Gate C search configuration. */
    public static final int MAX_FUTURE_BEAM_WIDTH = 50;

    public static final int MAX_MINUTES_PER_MEAL = 1_440;
    public static final int MAX_REFERENCE_CODE_LENGTH = 64;
    public static final String REFERENCE_CODE_PATTERN =
            "^[A-Z][A-Z0-9_]{0,63}$";

    private MealPlanningContractLimits() {
    }
}
