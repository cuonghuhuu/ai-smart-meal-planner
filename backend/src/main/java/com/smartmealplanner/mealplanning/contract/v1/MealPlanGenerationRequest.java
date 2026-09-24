package com.smartmealplanner.mealplanning.contract.v1;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.AlgorithmVersion;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.AllergenEvidenceStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.ExpiryKind;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.StorageLocation;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.UnitDimension;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_ALLERGEN_CODES;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_ALLERGEN_FACTS_PER_INGREDIENT;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_DIETARY_CODES;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_INGREDIENTS_PER_RECIPE;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_INGREDIENT_FACTS;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_INGREDIENT_PREFERENCE_IDS;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_MINUTES_PER_MEAL;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_NUTRITION_TARGETS;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_NUTRITION_VALUES_PER_RECIPE;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_PANTRY_LOTS;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_PLAN_DAYS;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_PREFERENCE_CODES;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_RECIPE_CANDIDATES;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_RECIPE_TAG_CODES;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_REQUESTED_MEAL_SLOTS;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.MAX_UNIT_DEFINITIONS;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.REFERENCE_CODE_PATTERN;
import static com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits.UNIT_CODE_PATTERN;

/** Strict, surrogate-ID-free request for internal meal-plan computation. */
public record MealPlanGenerationRequest(
        @NotNull
        @Pattern(regexp = "^1$")
        String contractVersion,

        @NotNull
        UUID requestId,

        @NotNull
        AlgorithmVersion algorithmVersion,

        @NotNull
        @Valid
        Planning planning,

        @NotNull
        @Valid
        HardConstraints hardConstraints,

        @NotNull
        @Valid
        SoftPreferences softPreferences,

        @NotNull
        @Size(max = MAX_NUTRITION_TARGETS)
        List<@NotNull @Valid NutritionTarget> nutritionTargets,

        @NotNull
        @Size(max = MAX_UNIT_DEFINITIONS)
        List<@NotNull @Valid UnitDefinition> unitDefinitions,

        @NotNull
        @Size(max = MAX_PANTRY_LOTS)
        List<@NotNull @Valid PantryLot> pantryLots,

        @NotNull
        @Size(max = MAX_INGREDIENT_FACTS)
        List<@NotNull @Valid IngredientFact> ingredientFacts,

        @NotNull
        @Size(max = MAX_RECIPE_CANDIDATES)
        List<@NotNull @Valid RecipeCandidate> recipeCandidates) {

    public MealPlanGenerationRequest {
        nutritionTargets = immutable(nutritionTargets);
        unitDefinitions = immutable(unitDefinitions);
        pantryLots = immutable(pantryLots);
        ingredientFacts = immutable(ingredientFacts);
        recipeCandidates = immutable(recipeCandidates);
    }

    /**
     * Resolves transported evidence without treating absent facts as safe.
     * Eligibility filtering belongs to the later planning algorithm.
     */
    @JsonIgnore
    public AllergenEvidenceStatus allergenEvidenceFor(
            UUID ingredientPublicId, String allergenCode) {
        if (ingredientFacts == null || ingredientPublicId == null || allergenCode == null) {
            return AllergenEvidenceStatus.UNKNOWN;
        }
        for (IngredientFact ingredient : ingredientFacts) {
            if (ingredient == null || !ingredientPublicId.equals(ingredient.ingredientPublicId())
                    || ingredient.allergenFacts() == null) {
                continue;
            }
            for (AllergenFact fact : ingredient.allergenFacts()) {
                if (fact != null && allergenCode.equals(fact.allergenCode())) {
                    return fact.evidenceStatus() == null
                            ? AllergenEvidenceStatus.UNKNOWN : fact.evidenceStatus();
                }
            }
        }
        return AllergenEvidenceStatus.UNKNOWN;
    }

    @JsonIgnore
    @AssertTrue(message = "request collections must use unique public IDs or codes")
    public boolean isIdentityUnique() {
        return uniqueBy(nutritionTargets, NutritionTarget::nutrientCode)
                && uniqueBy(unitDefinitions, UnitDefinition::unitCode)
                && uniqueBy(pantryLots, PantryLot::pantryItemPublicId)
                && uniqueBy(ingredientFacts, IngredientFact::ingredientPublicId)
                && uniqueBy(recipeCandidates, RecipeCandidate::recipePublicId);
    }

    @JsonIgnore
    @AssertTrue(message = "unit definitions must be complete and dimension-safe")
    public boolean isUnitDefinitionGraphValid() {
        if (unitDefinitions == null) {
            return true;
        }
        Map<String, UnitDefinition> definitions = unitDefinitions.stream()
                .filter(value -> value != null && value.unitCode() != null)
                .collect(Collectors.toMap(
                        UnitDefinition::unitCode,
                        Function.identity(),
                        (first, ignored) -> first));
        for (UnitDefinition definition : unitDefinitions) {
            if (definition == null || definition.baseUnitCode() == null
                    || definition.dimension() == null) {
                continue;
            }
            UnitDefinition base = definitions.get(definition.baseUnitCode());
            if (base == null || base.dimension() != definition.dimension()
                    || !base.unitCode().equals(base.baseUnitCode())
                    || base.toBaseFactor() == null
                    || base.toBaseFactor().compareTo(BigDecimal.ONE) != 0) {
                return false;
            }
        }
        Predicate<String> isDefined = code -> code == null || definitions.containsKey(code);
        return allMatch(nutritionTargets, target -> isDefined.test(target.unitCode()))
                && allMatch(pantryLots, lot -> isDefined.test(lot.unitCode()))
                && allMatch(recipeCandidates, recipe -> allMatch(
                        recipe.ingredients(), item -> isDefined.test(item.unitCode())))
                && allMatch(recipeCandidates, recipe -> recipe.nutrition() == null
                        || allMatch(recipe.nutrition().values(),
                                value -> isDefined.test(value.unitCode())));
    }

    public record Planning(
            @NotNull
            LocalDate startDate,

            @Min(1)
            @Max(MAX_PLAN_DAYS)
            int days,

            @NotEmpty
            @Size(max = MAX_REQUESTED_MEAL_SLOTS)
            List<@NotNull MealSlotCode> requestedMealSlots,

            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @DecimalMax("50.00")
            @Digits(integer = 2, fraction = 2)
            BigDecimal defaultServings,

            @Min(1)
            @Max(MAX_MINUTES_PER_MEAL)
            Integer maxMinutesPerMeal) {

        public Planning {
            requestedMealSlots = immutable(requestedMealSlots);
        }

        @JsonIgnore
        @AssertTrue(message = "requestedMealSlots must not contain duplicates")
        public boolean isRequestedMealSlotsUnique() {
            return unique(requestedMealSlots);
        }
    }

    public record HardConstraints(
            @NotNull
            @Size(max = MAX_ALLERGEN_CODES)
            List<@NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String> allergenCodes,

            @NotNull
            @Size(max = MAX_DIETARY_CODES)
            List<@NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String>
                    exclusionaryDietaryCodes,

            @NotNull
            @Size(max = MAX_INGREDIENT_PREFERENCE_IDS)
            List<@NotNull UUID> avoidIngredientPublicIds) {

        public HardConstraints {
            allergenCodes = immutable(allergenCodes);
            exclusionaryDietaryCodes = immutable(exclusionaryDietaryCodes);
            avoidIngredientPublicIds = immutable(avoidIngredientPublicIds);
        }

        @JsonIgnore
        @AssertTrue(message = "hard-constraint arrays must not contain duplicates")
        public boolean isUnique() {
            return unique(allergenCodes)
                    && unique(exclusionaryDietaryCodes)
                    && unique(avoidIngredientPublicIds);
        }
    }

    public record SoftPreferences(
            @NotNull
            @Size(max = MAX_INGREDIENT_PREFERENCE_IDS)
            List<@NotNull UUID> dislikeIngredientPublicIds,

            @NotNull
            @Size(max = MAX_PREFERENCE_CODES)
            List<@NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String> preferenceCodes) {

        public SoftPreferences {
            dislikeIngredientPublicIds = immutable(dislikeIngredientPublicIds);
            preferenceCodes = immutable(preferenceCodes);
        }

        @JsonIgnore
        @AssertTrue(message = "soft-preference arrays must not contain duplicates")
        public boolean isUnique() {
            return unique(dislikeIngredientPublicIds) && unique(preferenceCodes);
        }
    }

    public record NutritionTarget(
            @NotNull
            @Pattern(regexp = REFERENCE_CODE_PATTERN)
            String nutrientCode,

            @DecimalMin("0")
            @Digits(integer = 10, fraction = 4)
            BigDecimal targetValue,

            @DecimalMin("0")
            @Digits(integer = 10, fraction = 4)
            BigDecimal minValue,

            @DecimalMin("0")
            @Digits(integer = 10, fraction = 4)
            BigDecimal maxValue,

            boolean hardLimit,

            @NotNull
            @Pattern(regexp = UNIT_CODE_PATTERN)
            String unitCode) {

        @JsonIgnore
        @AssertTrue(message = "a nutrition target requires targetValue, minValue or maxValue")
        public boolean isAnyValuePresent() {
            return targetValue != null || minValue != null || maxValue != null;
        }

        @JsonIgnore
        @AssertTrue(message = "nutrition target values must be ordered")
        public boolean isValueOrderValid() {
            if (minValue != null && maxValue != null && minValue.compareTo(maxValue) > 0) {
                return false;
            }
            if (targetValue != null && minValue != null && targetValue.compareTo(minValue) < 0) {
                return false;
            }
            return targetValue == null || maxValue == null || targetValue.compareTo(maxValue) <= 0;
        }
    }

    public record UnitDefinition(
            @NotNull
            @Pattern(regexp = UNIT_CODE_PATTERN)
            String unitCode,

            @NotNull
            UnitDimension dimension,

            @NotNull
            @Pattern(regexp = UNIT_CODE_PATTERN)
            String baseUnitCode,

            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 10, fraction = 12)
            BigDecimal toBaseFactor) {
    }

    public record PantryLot(
            @NotNull
            UUID pantryItemPublicId,

            @NotNull
            UUID ingredientPublicId,

            UUID foodPublicId,

            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 10, fraction = 4)
            BigDecimal quantityRemaining,

            @NotNull
            @Pattern(regexp = UNIT_CODE_PATTERN)
            String unitCode,

            LocalDate expiryDate,

            @NotNull
            ExpiryKind expiryKind,

            @NotNull
            StorageLocation storageLocation) {

        @JsonIgnore
        @AssertTrue(message = "a missing expiryDate requires expiryKind UNKNOWN")
        public boolean isExpirySemanticsValid() {
            return expiryDate != null || expiryKind == null || expiryKind == ExpiryKind.UNKNOWN;
        }
    }

    public record IngredientFact(
            @NotNull
            UUID ingredientPublicId,

            @NotNull
            @Size(max = MAX_ALLERGEN_FACTS_PER_INGREDIENT)
            List<@NotNull @Valid AllergenFact> allergenFacts) {

        public IngredientFact {
            allergenFacts = immutable(allergenFacts);
        }

        @JsonIgnore
        @AssertTrue(message = "allergenFacts must have unique allergen codes")
        public boolean isAllergenFactUnique() {
            return uniqueBy(allergenFacts, AllergenFact::allergenCode);
        }
    }

    public record AllergenFact(
            @NotNull
            @Pattern(regexp = REFERENCE_CODE_PATTERN)
            String allergenCode,

            @NotNull
            AllergenEvidenceStatus evidenceStatus) {
    }

    public record RecipeCandidate(
            @NotNull
            UUID recipePublicId,

            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @DecimalMax("50.00")
            @Digits(integer = 2, fraction = 2)
            BigDecimal servings,

            @Min(0)
            @Max(MAX_MINUTES_PER_MEAL)
            Integer totalMinutes,

            @NotEmpty
            @Size(max = MAX_REQUESTED_MEAL_SLOTS)
            List<@NotNull MealSlotCode> mealSlotCodes,

            @NotNull
            @Size(max = MAX_DIETARY_CODES)
            List<@NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String> dietaryCodes,

            @NotNull
            @Size(max = MAX_RECIPE_TAG_CODES)
            List<@NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String> tagCodes,

            @NotEmpty
            @Size(max = MAX_INGREDIENTS_PER_RECIPE)
            List<@NotNull @Valid IngredientRequirement> ingredients,

            @Valid
            NutritionData nutrition) {

        public RecipeCandidate {
            mealSlotCodes = immutable(mealSlotCodes);
            dietaryCodes = immutable(dietaryCodes);
            tagCodes = immutable(tagCodes);
            ingredients = immutable(ingredients);
        }

        @JsonIgnore
        @AssertTrue(message = "recipe code and ingredient arrays must not contain duplicates")
        public boolean isRecipeDataUnique() {
            return unique(mealSlotCodes)
                    && unique(dietaryCodes)
                    && unique(tagCodes)
                    && uniqueBy(ingredients, IngredientRequirement::ingredientPublicId);
        }
    }

    public record IngredientRequirement(
            @NotNull
            UUID ingredientPublicId,

            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 10, fraction = 4)
            BigDecimal quantity,

            @Pattern(regexp = UNIT_CODE_PATTERN)
            String unitCode,

            boolean optional,

            boolean allowSubstitution) {

        @JsonIgnore
        @AssertTrue(message = "quantity and unitCode must either both be present or both be null")
        public boolean isQuantityUnitPairValid() {
            return (quantity == null) == (unitCode == null);
        }
    }

    public record NutritionData(
            @NotNull
            @DecimalMin("0")
            @DecimalMax("1")
            @Digits(integer = 1, fraction = 6)
            BigDecimal completenessRatio,

            @NotNull
            @Size(max = MAX_NUTRITION_VALUES_PER_RECIPE)
            List<@NotNull @Valid NutritionValue> values) {

        public NutritionData {
            values = immutable(values);
        }

        @JsonIgnore
        @AssertTrue(message = "nutrition values must have unique nutrient codes")
        public boolean isNutrientUnique() {
            return uniqueBy(values, NutritionValue::nutrientCode);
        }
    }

    public record NutritionValue(
            @NotNull
            @Pattern(regexp = REFERENCE_CODE_PATTERN)
            String nutrientCode,

            @NotNull
            @DecimalMin("0")
            @Digits(integer = 10, fraction = 4)
            BigDecimal amountPerServing,

            @NotNull
            @Pattern(regexp = UNIT_CODE_PATTERN)
            String unitCode) {
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    private static boolean unique(List<?> values) {
        return values == null || new HashSet<>(values).size() == values.size();
    }

    private static <T, K> boolean uniqueBy(List<T> values, Function<T, K> key) {
        if (values == null) {
            return true;
        }
        Set<K> keys = new HashSet<>();
        for (T value : values) {
            if (value == null) {
                continue;
            }
            if (!keys.add(key.apply(value))) {
                return false;
            }
        }
        return true;
    }

    private static <T> boolean allMatch(List<T> values, Predicate<T> predicate) {
        return values == null || values.stream()
                .filter(value -> value != null)
                .allMatch(predicate);
    }
}
