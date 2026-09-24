package com.smartmealplanner.mealplanning.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationResponse;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.AllergenEvidenceStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractJson;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractValidationException;

import org.springframework.stereotype.Service;

/** Rejects any AI result that is not safe against the original Java snapshot. */
@Service
public class MealPlanningResponseValidator {
    private static final Set<String> SUPPORTED_HARD_DIETS = Set.of(
            "VEGETARIAN", "VEGAN", "PESCATARIAN", "HALAL", "KOSHER",
            "GLUTEN_FREE", "DAIRY_FREE");
    private final MealPlanningContractJson contract;

    public MealPlanningResponseValidator(MealPlanningContractJson contract) {
        this.contract = contract;
    }

    public void validate(MealPlanGenerationRequest request, MealPlanGenerationResponse response) {
        if (request == null || response == null) { throw bad(); }
        try { contract.writeResponse(response); }
        catch (MealPlanningContractValidationException exception) {
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.AI_BAD_RESPONSE, exception);
        }
        if (!request.contractVersion().equals(response.contractVersion())
                || !request.algorithmVersion().equals(response.algorithmVersion())
                || !request.requestId().equals(response.requestId())) {
            throw bad();
        }
        Set<SlotKey> expected = new HashSet<>();
        for (int day = 0; day < request.planning().days(); day++) {
            LocalDate date = request.planning().startDate().plusDays(day);
            for (MealSlotCode slot : request.planning().requestedMealSlots()) {
                expected.add(new SlotKey(date, slot));
            }
        }
        Map<UUID, MealPlanGenerationRequest.RecipeCandidate> candidates = new HashMap<>();
        for (MealPlanGenerationRequest.RecipeCandidate candidate : request.recipeCandidates()) {
            candidates.put(candidate.recipePublicId(), candidate);
        }
        Set<SlotKey> actual = new HashSet<>();
        Map<LocalDate, Map<String, BigDecimal>> dailyNutrition = new HashMap<>();
        Map<String, MealPlanGenerationRequest.UnitDefinition> units = new HashMap<>();
        request.unitDefinitions().forEach(unit -> units.put(unit.unitCode(), unit));
        for (MealPlanGenerationResponse.Entry entry : response.entries()) {
            SlotKey key = new SlotKey(entry.planDate(), entry.mealSlotCode());
            if (!expected.contains(key) || !actual.add(key)) { throw bad(); }
            MealPlanGenerationRequest.RecipeCandidate candidate = candidates.get(
                    entry.recipePublicId());
            validateEntry(request, candidate, entry, units);
            if (candidate.nutrition() != null) {
                Map<String, BigDecimal> daily = dailyNutrition.computeIfAbsent(
                        entry.planDate(), ignored -> new HashMap<>());
                for (MealPlanGenerationRequest.NutritionValue value : candidate.nutrition().values()) {
                    daily.merge(value.nutrientCode(), toBase(
                            value.amountPerServing().multiply(entry.servings()),
                            value.unitCode(), units), BigDecimal::add);
                }
            }
        }
        for (MealPlanGenerationResponse.UnfilledSlot gap : response.unfilledSlots()) {
            SlotKey key = new SlotKey(gap.planDate(), gap.mealSlotCode());
            if (!expected.contains(key) || !actual.add(key)) { throw bad(); }
        }
        if (!actual.equals(expected)) { throw bad(); }
        switch (response.status()) {
            case SUCCEEDED -> {
                if (response.entries().size() != expected.size()
                        || !response.unfilledSlots().isEmpty()) { throw bad(); }
            }
            case DEGRADED -> {
                if (response.entries().isEmpty() || response.unfilledSlots().isEmpty()) { throw bad(); }
            }
            case INFEASIBLE -> {
                if (!response.entries().isEmpty()
                        || response.unfilledSlots().size() != expected.size()) { throw bad(); }
            }
        }
        if (response.status() != GenerationStatus.INFEASIBLE) {
            validateHardNutrition(request, dailyNutrition, units);
        }
    }

    private static void validateEntry(MealPlanGenerationRequest request,
            MealPlanGenerationRequest.RecipeCandidate candidate,
            MealPlanGenerationResponse.Entry entry,
            Map<String, MealPlanGenerationRequest.UnitDefinition> units) {
        if (candidate == null || !candidate.mealSlotCodes().contains(entry.mealSlotCode())
                || entry.servings() == null || entry.servings().signum() <= 0
                || entry.servings().compareTo(new BigDecimal("50")) > 0
                || entry.servings().scale() > 2) { throw bad(); }
        Integer cap = request.planning().maxMinutesPerMeal();
        if (cap != null && (candidate.totalMinutes() == null
                || candidate.totalMinutes() > cap)) { throw bad(); }
        if (!SUPPORTED_HARD_DIETS.containsAll(
                request.hardConstraints().exclusionaryDietaryCodes())
                || !candidate.dietaryCodes().containsAll(
                        request.hardConstraints().exclusionaryDietaryCodes())) {
            throw bad();
        }
        for (MealPlanGenerationRequest.IngredientRequirement ingredient : candidate.ingredients()) {
            if (request.hardConstraints().avoidIngredientPublicIds().contains(
                    ingredient.ingredientPublicId())
                    || (!ingredient.optional()
                    && (ingredient.quantity() == null || ingredient.unitCode() == null))) {
                throw bad();
            }
            for (String allergen : request.hardConstraints().allergenCodes()) {
                if (request.allergenEvidenceFor(
                        ingredient.ingredientPublicId(), allergen)
                        != AllergenEvidenceStatus.FREE_FROM) { throw bad(); }
            }
        }
        for (MealPlanGenerationRequest.NutritionTarget target : request.nutritionTargets()) {
            if (!target.hardLimit()) { continue; }
            if (target.minValue() == null && target.maxValue() == null
                    || candidate.nutrition() == null
                    || candidate.nutrition().completenessRatio().compareTo(BigDecimal.ONE) != 0
                    || candidate.nutrition().values().stream().noneMatch(value ->
                            value.nutrientCode().equals(target.nutrientCode())
                            && compatible(value.unitCode(), target.unitCode(), units))) {
                throw bad();
            }
        }
    }

    private static void validateHardNutrition(MealPlanGenerationRequest request,
            Map<LocalDate, Map<String, BigDecimal>> daily,
            Map<String, MealPlanGenerationRequest.UnitDefinition> units) {
        for (int day = 0; day < request.planning().days(); day++) {
            Map<String, BigDecimal> totals = daily.get(
                    request.planning().startDate().plusDays(day));
            for (MealPlanGenerationRequest.NutritionTarget target : request.nutritionTargets()) {
                if (!target.hardLimit()) { continue; }
                BigDecimal amount = totals == null ? null : totals.get(target.nutrientCode());
                if ((totals != null && amount == null)
                        || (target.maxValue() != null && amount != null && amount.compareTo(
                                toBase(target.maxValue(), target.unitCode(), units)) > 0)
                        || (target.minValue() != null && (amount == null || amount.compareTo(
                                toBase(target.minValue(), target.unitCode(), units)) < 0))) {
                    throw bad();
                }
            }
        }
    }

    private static BigDecimal toBase(BigDecimal amount, String code,
            Map<String, MealPlanGenerationRequest.UnitDefinition> units) {
        MealPlanGenerationRequest.UnitDefinition unit = units.get(code);
        if (unit == null) { throw bad(); }
        return amount.multiply(unit.toBaseFactor());
    }

    private static boolean compatible(String first, String second,
            Map<String, MealPlanGenerationRequest.UnitDefinition> units) {
        MealPlanGenerationRequest.UnitDefinition left = units.get(first);
        MealPlanGenerationRequest.UnitDefinition right = units.get(second);
        return left != null && right != null
                && left.dimension() == right.dimension()
                && left.baseUnitCode().equals(right.baseUnitCode());
    }

    private record SlotKey(LocalDate date, MealSlotCode slot) { }

    private static MealPlanningIntegrationException bad() {
        return new MealPlanningIntegrationException(MealPlanningIntegrationFailure.AI_BAD_RESPONSE);
    }
}
