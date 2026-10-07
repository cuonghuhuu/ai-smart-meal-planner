package com.smartmealplanner.recommendation.contract.v1;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.AlgorithmVersion;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.AllergenEvidenceStatus;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.ExpiryKind;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.MealSlotCode;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.StorageLocation;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.UnitDimension;

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

import static com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractLimits.*;

/** Strict public-ID-only snapshot sent from Java to the Python ranker. */
public record RecipeRankingRequest(
        @NotNull @Pattern(regexp = "^1$") String contractVersion,
        @NotNull UUID requestId,
        @NotNull AlgorithmVersion algorithmVersion,
        @NotNull @Valid Context context,
        @NotNull @Valid HardConstraints hardConstraints,
        @NotNull @Valid SoftPreferences softPreferences,
        @NotNull @Size(max = MAX_NUTRITION_TARGETS)
        List<@NotNull @Valid NutritionTarget> nutritionTargets,
        @NotNull @Size(max = MAX_RECENT_RECIPE_COUNTS)
        List<@NotNull @Valid RecentRecipeCount> recentRecipeCounts,
        @NotNull @Size(max = MAX_UNIT_DEFINITIONS)
        List<@NotNull @Valid UnitDefinition> unitDefinitions,
        @NotNull @Size(max = MAX_PANTRY_LOTS)
        List<@NotNull @Valid PantryLot> pantryLots,
        @NotNull @Size(max = MAX_INGREDIENT_FACTS)
        List<@NotNull @Valid IngredientFact> ingredientFacts,
        @NotNull @Size(max = MAX_RECIPE_CANDIDATES)
        List<@NotNull @Valid RecipeCandidate> recipeCandidates) {

    public RecipeRankingRequest {
        nutritionTargets = copy(nutritionTargets);
        recentRecipeCounts = copy(recentRecipeCounts);
        unitDefinitions = copy(unitDefinitions);
        pantryLots = copy(pantryLots);
        ingredientFacts = copy(ingredientFacts);
        recipeCandidates = copy(recipeCandidates);
    }

    @JsonIgnore
    @AssertTrue(message = "request collections must use unique public IDs or codes")
    public boolean isIdentityUnique() {
        return uniqueBy(nutritionTargets, NutritionTarget::nutrientCode)
                && uniqueBy(recentRecipeCounts, RecentRecipeCount::recipePublicId)
                && uniqueBy(unitDefinitions, UnitDefinition::unitCode)
                && uniqueBy(pantryLots, PantryLot::pantryItemPublicId)
                && uniqueBy(ingredientFacts, IngredientFact::ingredientPublicId)
                && uniqueBy(recipeCandidates, RecipeCandidate::recipePublicId);
    }

    @JsonIgnore
    @AssertTrue(message = "recentRecipeCounts must refer only to transported candidates")
    public boolean isRecentHistoryCandidateBounded() {
        if (recentRecipeCounts == null || recipeCandidates == null) {
            return true;
        }
        Set<UUID> candidates = recipeCandidates.stream()
                .filter(java.util.Objects::nonNull)
                .map(RecipeCandidate::recipePublicId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return recentRecipeCounts.stream().filter(java.util.Objects::nonNull)
                .allMatch(value -> candidates.contains(value.recipePublicId()));
    }

    @JsonIgnore
    @AssertTrue(message = "unit definitions must cover every referenced unit safely")
    public boolean isUnitDefinitionGraphValid() {
        if (unitDefinitions == null) {
            return true;
        }
        Map<String, UnitDefinition> definitions = unitDefinitions.stream()
                .filter(value -> value != null && value.unitCode() != null)
                .collect(Collectors.toMap(UnitDefinition::unitCode, Function.identity(),
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
        Set<String> referenced = new HashSet<>();
        if (nutritionTargets != null) {
            nutritionTargets.stream().filter(java.util.Objects::nonNull)
                    .map(NutritionTarget::unitCode).filter(java.util.Objects::nonNull)
                    .forEach(referenced::add);
        }
        if (pantryLots != null) {
            pantryLots.stream().filter(java.util.Objects::nonNull)
                    .map(PantryLot::unitCode).filter(java.util.Objects::nonNull)
                    .forEach(referenced::add);
        }
        if (recipeCandidates != null) {
            for (RecipeCandidate recipe : recipeCandidates) {
                if (recipe == null) {
                    continue;
                }
                if (recipe.ingredients() != null) {
                    recipe.ingredients().stream().filter(java.util.Objects::nonNull)
                            .map(IngredientRequirement::unitCode)
                            .filter(java.util.Objects::nonNull).forEach(referenced::add);
                }
                if (recipe.nutrition() != null && recipe.nutrition().values() != null) {
                    recipe.nutrition().values().stream().filter(java.util.Objects::nonNull)
                            .map(NutritionValue::unitCode).filter(java.util.Objects::nonNull)
                            .forEach(referenced::add);
                }
            }
        }
        return definitions.keySet().containsAll(referenced);
    }

    public record Context(
            @NotNull LocalDate targetDate,
            @NotNull MealSlotCode mealSlotCode,
            @NotNull @DecimalMin(value = "0", inclusive = false)
            @DecimalMax("50.00") @Digits(integer = 2, fraction = 2) BigDecimal servings,
            @Min(1) @Max(MAX_MINUTES) Integer maxMinutes,
            @Min(1) @Max(MAX_RESULT_LIMIT) int resultLimit) {
    }

    public record HardConstraints(
            @NotNull @Size(max = MAX_ALLERGEN_CODES)
            List<@NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String> allergenCodes,
            @NotNull @Size(max = MAX_DIETARY_CODES)
            List<@NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String> exclusionaryDietaryCodes,
            @NotNull @Size(max = MAX_INGREDIENT_PREFERENCE_IDS)
            List<@NotNull UUID> avoidIngredientPublicIds) {
        public HardConstraints {
            allergenCodes = copy(allergenCodes);
            exclusionaryDietaryCodes = copy(exclusionaryDietaryCodes);
            avoidIngredientPublicIds = copy(avoidIngredientPublicIds);
        }

        @JsonIgnore @AssertTrue(message = "hard-constraint arrays must be unique")
        public boolean isUnique() {
            return unique(allergenCodes) && unique(exclusionaryDietaryCodes)
                    && unique(avoidIngredientPublicIds);
        }
    }

    public record SoftPreferences(
            @NotNull @Size(max = MAX_INGREDIENT_PREFERENCE_IDS)
            List<@NotNull UUID> dislikeIngredientPublicIds,
            @NotNull @Size(max = MAX_PREFERENCE_CODES)
            List<@NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String> preferenceCodes) {
        public SoftPreferences {
            dislikeIngredientPublicIds = copy(dislikeIngredientPublicIds);
            preferenceCodes = copy(preferenceCodes);
        }

        @JsonIgnore @AssertTrue(message = "soft-preference arrays must be unique")
        public boolean isUnique() {
            return unique(dislikeIngredientPublicIds) && unique(preferenceCodes);
        }
    }

    public record NutritionTarget(
            @NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String nutrientCode,
            @DecimalMin("0") @Digits(integer = 10, fraction = 4) BigDecimal targetValue,
            @DecimalMin("0") @Digits(integer = 10, fraction = 4) BigDecimal minValue,
            @DecimalMin("0") @Digits(integer = 10, fraction = 4) BigDecimal maxValue,
            boolean hardLimit,
            @NotNull @Pattern(regexp = UNIT_CODE_PATTERN) String unitCode) {
        @JsonIgnore @AssertTrue(message = "nutrition target values must be present and ordered")
        public boolean isRangeValid() {
            if (targetValue == null && minValue == null && maxValue == null) {
                return false;
            }
            if (minValue != null && maxValue != null && minValue.compareTo(maxValue) > 0) {
                return false;
            }
            if (targetValue != null && minValue != null && targetValue.compareTo(minValue) < 0) {
                return false;
            }
            return targetValue == null || maxValue == null
                    || targetValue.compareTo(maxValue) <= 0;
        }
    }

    public record RecentRecipeCount(
            @NotNull UUID recipePublicId,
            @Min(1) @Max(365) int count) {
    }

    public record UnitDefinition(
            @NotNull @Pattern(regexp = UNIT_CODE_PATTERN) String unitCode,
            @NotNull UnitDimension dimension,
            @NotNull @Pattern(regexp = UNIT_CODE_PATTERN) String baseUnitCode,
            @NotNull @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 10, fraction = 12) BigDecimal toBaseFactor) {
    }

    public record PantryLot(
            @NotNull UUID pantryItemPublicId,
            @NotNull UUID ingredientPublicId,
            UUID foodPublicId,
            @NotNull @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 10, fraction = 4) BigDecimal quantityRemaining,
            @NotNull @Pattern(regexp = UNIT_CODE_PATTERN) String unitCode,
            LocalDate expiryDate,
            @NotNull ExpiryKind expiryKind,
            @NotNull StorageLocation storageLocation) {
        @JsonIgnore @AssertTrue(message = "missing expiryDate requires UNKNOWN expiryKind")
        public boolean isExpiryValid() {
            return expiryDate != null || expiryKind == null || expiryKind == ExpiryKind.UNKNOWN;
        }
    }

    public record AllergenFact(
            @NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String allergenCode,
            @NotNull AllergenEvidenceStatus evidenceStatus) {
    }

    public record IngredientFact(
            @NotNull UUID ingredientPublicId,
            @NotNull @Size(max = MAX_ALLERGEN_FACTS_PER_INGREDIENT)
            List<@NotNull @Valid AllergenFact> allergenFacts) {
        public IngredientFact {
            allergenFacts = copy(allergenFacts);
        }

        @JsonIgnore @AssertTrue(message = "allergen facts must use unique codes")
        public boolean isUnique() {
            return allergenFacts == null || unique(allergenFacts.stream()
                    .filter(java.util.Objects::nonNull).map(AllergenFact::allergenCode).toList());
        }
    }

    public record IngredientRequirement(
            @NotNull UUID ingredientPublicId,
            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 10, fraction = 4) BigDecimal quantity,
            @Pattern(regexp = UNIT_CODE_PATTERN) String unitCode,
            boolean optional,
            boolean allowSubstitution) {
        @JsonIgnore @AssertTrue(message = "quantity and unitCode must both be present or both null")
        public boolean isPairValid() {
            return (quantity == null) == (unitCode == null);
        }
    }

    public record NutritionValue(
            @NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String nutrientCode,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 4)
            BigDecimal amountPerServing,
            @NotNull @Pattern(regexp = UNIT_CODE_PATTERN) String unitCode) {
    }

    public record NutritionData(
            @NotNull @DecimalMin("0") @DecimalMax("1")
            @Digits(integer = 1, fraction = 6) BigDecimal completenessRatio,
            @NotNull @Size(max = MAX_NUTRITION_VALUES_PER_RECIPE)
            List<@NotNull @Valid NutritionValue> values) {
        public NutritionData {
            values = copy(values);
        }

        @JsonIgnore @AssertTrue(message = "nutrition values must use unique nutrient codes")
        public boolean isUnique() {
            return values == null || unique(values.stream()
                    .filter(java.util.Objects::nonNull).map(NutritionValue::nutrientCode).toList());
        }
    }

    public record RecipeCandidate(
            @NotNull UUID recipePublicId,
            @NotNull @DecimalMin(value = "0", inclusive = false)
            @DecimalMax("50.00") @Digits(integer = 2, fraction = 2) BigDecimal servings,
            @Min(0) @Max(MAX_MINUTES) Integer totalMinutes,
            @NotNull @NotEmpty @Size(max = 6) List<@NotNull MealSlotCode> mealSlotCodes,
            @NotNull @Size(max = MAX_DIETARY_CODES)
            List<@NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String> dietaryCodes,
            @NotNull @Size(max = MAX_RECIPE_TAG_CODES)
            List<@NotNull @Pattern(regexp = REFERENCE_CODE_PATTERN) String> tagCodes,
            @NotNull @NotEmpty @Size(max = MAX_INGREDIENTS_PER_RECIPE)
            List<@NotNull @Valid IngredientRequirement> ingredients,
            @Valid NutritionData nutrition) {
        public RecipeCandidate {
            mealSlotCodes = copy(mealSlotCodes);
            dietaryCodes = copy(dietaryCodes);
            tagCodes = copy(tagCodes);
            ingredients = copy(ingredients);
        }

        @JsonIgnore @AssertTrue(message = "recipe arrays must be unique")
        public boolean isUnique() {
            return unique(mealSlotCodes) && unique(dietaryCodes) && unique(tagCodes)
                    && (ingredients == null || unique(ingredients.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(IngredientRequirement::ingredientPublicId).toList()));
        }
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    private static boolean unique(List<?> values) {
        return values == null || new HashSet<>(values).size() == values.size();
    }

    private static <T, K> boolean uniqueBy(List<T> values, Function<T, K> key) {
        if (values == null) {
            return true;
        }
        Set<K> seen = new HashSet<>();
        for (T value : values) {
            if (value != null && !seen.add(key.apply(value))) {
                return false;
            }
        }
        return true;
    }
}
