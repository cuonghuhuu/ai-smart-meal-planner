package com.smartmealplanner.mealplanning.application;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationResponse;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.AlgorithmVersion;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.AllergenEvidenceStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractJson;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MealPlanningResponseValidatorTest {
    private final MealPlanningContractJson contract = MealPlanningContractJson.createDefault();
    private final MealPlanningResponseValidator validator = new MealPlanningResponseValidator(contract);

    @Test
    void acceptsAllThreeOutcomesWhenTheyExactlyCoverRequestedSlots() throws Exception {
        MealPlanGenerationRequest request = fixtureRequest();
        MealPlanGenerationResponse succeeded = fixtureResponse("valid_succeeded_response.json");
        MealPlanGenerationResponse.Entry originalDinner = succeeded.entries().get(1);
        // Golden responses test transport validity; the Java safety check also needs
        // a candidate whose cooking time is known to fit this particular request.
        MealPlanGenerationResponse.Entry safeDinner = entry(originalDinner, MealSlotCode.DINNER,
                request.recipeCandidates().get(0).recipePublicId(), originalDinner.servings(),
                originalDinner.totalScore(), originalDinner.scoreComponents());
        assertThat(validator).satisfies(check -> check.validate(request,
                response(succeeded, List.of(succeeded.entries().get(0), safeDinner), List.of())));
        validator.validate(request, fixtureResponse("valid_degraded_response.json"));
        validator.validate(request, fixtureResponse("valid_infeasible_response.json"));
    }

    @Test
    void rejectsEnvelopeAndSlotCoverageViolations() throws Exception {
        MealPlanGenerationRequest request = fixtureRequest();
        MealPlanGenerationResponse base = fixtureResponse("valid_degraded_response.json");
        MealPlanGenerationResponse.Entry first = base.entries().get(0);
        rejects(request, new MealPlanGenerationResponse(base.contractVersion(), UUID.randomUUID(),
                base.algorithmVersion(), base.status(), base.entries(), base.unfilledSlots()));
        rejects(request, new MealPlanGenerationResponse("2", base.requestId(),
                base.algorithmVersion(), base.status(), base.entries(), base.unfilledSlots()));
        // An unknown algorithm version cannot be represented by the V1 enum; the
        // strict JSON codec rejects it before this request-relative validator.
        assertThatThrownBy(() -> contract.readResponse(contract.writeResponse(base)
                .replace("HEURISTIC_MEAL_PLAN_V1", "UNKNOWN_ALGORITHM"))).isNotNull();
        rejects(request, response(base, List.of(entry(first, first.mealSlotCode(),
                UUID.randomUUID(), first.servings(), first.totalScore(),
                first.scoreComponents())), base.unfilledSlots()));
        rejects(request, response(base, List.of(new MealPlanGenerationResponse.Entry(
                LocalDate.of(2026, 10, 2), first.mealSlotCode(), first.recipePublicId(),
                first.servings(), first.totalScore(), first.scoreComponents(),
                first.explanation())), base.unfilledSlots()));
        rejects(request, response(base, List.of(entry(first, MealSlotCode.LUNCH,
                first.recipePublicId(), first.servings(), first.totalScore(),
                first.scoreComponents())), base.unfilledSlots()));
        rejects(request, response(base, List.of(first, first), base.unfilledSlots()));
        rejects(request, response(base, base.entries(), List.of()));
        rejects(request, response(base, base.entries(), List.of(new MealPlanGenerationResponse.UnfilledSlot(
                first.planDate(), first.mealSlotCode(), base.unfilledSlots().get(0).reasonCode(), null))));
        rejects(request, response(base, base.entries(), List.of(
                base.unfilledSlots().get(0), base.unfilledSlots().get(0))));
        MealPlanGenerationResponse infeasible = fixtureResponse("valid_infeasible_response.json");
        rejects(request, response(infeasible, List.of(),
                List.of(infeasible.unfilledSlots().get(0))));
    }

    @Test
    void rejectsInvalidServingsScoresComponentsAndWeights() throws Exception {
        MealPlanGenerationRequest request = fixtureRequest();
        MealPlanGenerationResponse base = fixtureResponse("valid_degraded_response.json");
        MealPlanGenerationResponse.Entry first = base.entries().get(0);
        rejects(request, withEntry(base, entry(first, first.mealSlotCode(),
                first.recipePublicId(), new BigDecimal("51"), first.totalScore(),
                first.scoreComponents())));
        rejects(request, withEntry(base, entry(first, first.mealSlotCode(),
                first.recipePublicId(), first.servings(), new BigDecimal("1.01"),
                first.scoreComponents())));
        List<MealPlanGenerationResponse.ScoreComponent> missing = new ArrayList<>(first.scoreComponents());
        missing.removeLast();
        rejects(request, withEntry(base, entry(first, first.mealSlotCode(),
                first.recipePublicId(), first.servings(), first.totalScore(), missing)));
        List<MealPlanGenerationResponse.ScoreComponent> duplicate = new ArrayList<>(first.scoreComponents());
        duplicate.set(1, duplicate.get(0));
        rejects(request, withEntry(base, entry(first, first.mealSlotCode(),
                first.recipePublicId(), first.servings(), first.totalScore(), duplicate)));
        List<MealPlanGenerationResponse.ScoreComponent> wrongWeight = new ArrayList<>(first.scoreComponents());
        MealPlanGenerationResponse.ScoreComponent component = wrongWeight.get(6);
        wrongWeight.set(6, new MealPlanGenerationResponse.ScoreComponent(
                component.componentCode(), component.value(), new BigDecimal("0.10")));
        rejects(request, withEntry(base, entry(first, first.mealSlotCode(),
                first.recipePublicId(), first.servings(), first.totalScore(), wrongWeight)));
    }

    @Test
    void independentlyRejectsAllergenAvoidDietCookingAndMissingHardNutrition() throws Exception {
        MealPlanGenerationRequest base = fixtureRequest();
        MealPlanGenerationResponse degraded = fixtureResponse("valid_degraded_response.json");
        List<MealPlanGenerationRequest.IngredientFact> missingEvidence = base.ingredientFacts().stream()
                .filter(fact -> !fact.ingredientPublicId().equals(
                        base.recipeCandidates().get(0).ingredients().get(0).ingredientPublicId()))
                .toList();
        rejects(request(base, base.hardConstraints(), missingEvidence,
                base.recipeCandidates(), base.nutritionTargets()), degraded);
        for (AllergenEvidenceStatus unsafe : List.of(AllergenEvidenceStatus.CONTAINS,
                AllergenEvidenceStatus.MAY_CONTAIN, AllergenEvidenceStatus.UNKNOWN)) {
            UUID selectedIngredient = base.recipeCandidates().get(0).ingredients().get(0)
                    .ingredientPublicId();
            List<MealPlanGenerationRequest.IngredientFact> unsafeEvidence = base.ingredientFacts()
                    .stream().map(fact -> fact.ingredientPublicId().equals(selectedIngredient)
                            ? new MealPlanGenerationRequest.IngredientFact(selectedIngredient,
                                    List.of(new MealPlanGenerationRequest.AllergenFact(
                                            "PEANUT", unsafe))) : fact).toList();
            rejects(request(base, base.hardConstraints(), unsafeEvidence,
                    base.recipeCandidates(), base.nutritionTargets()), degraded);
        }
        MealPlanGenerationRequest.HardConstraints avoid = new MealPlanGenerationRequest.HardConstraints(
                base.hardConstraints().allergenCodes(), base.hardConstraints().exclusionaryDietaryCodes(),
                List.of(base.recipeCandidates().get(0).ingredients().get(0).ingredientPublicId()));
        rejects(request(base, avoid, base.ingredientFacts(), base.recipeCandidates(),
                base.nutritionTargets()), degraded);
        MealPlanGenerationRequest.HardConstraints diet = new MealPlanGenerationRequest.HardConstraints(
                base.hardConstraints().allergenCodes(), List.of("VEGAN"), List.of());
        rejects(request(base, diet, base.ingredientFacts(), base.recipeCandidates(),
                base.nutritionTargets()), degraded);
        MealPlanGenerationRequest.RecipeCandidate candidate = base.recipeCandidates().get(0);
        List<MealPlanGenerationRequest.RecipeCandidate> tooSlow = List.of(new MealPlanGenerationRequest.RecipeCandidate(
                candidate.recipePublicId(), candidate.servings(), 60, candidate.mealSlotCodes(),
                candidate.dietaryCodes(), candidate.tagCodes(), candidate.ingredients(), candidate.nutrition()),
                base.recipeCandidates().get(1));
        rejects(request(base, base.hardConstraints(), base.ingredientFacts(), tooSlow,
                base.nutritionTargets()), degraded);
        MealPlanGenerationRequest.NutritionTarget hard = new MealPlanGenerationRequest.NutritionTarget(
                "FAT", null, null, new BigDecimal("50"), true, "g");
        rejects(request(base, base.hardConstraints(), base.ingredientFacts(),
                base.recipeCandidates(), List.of(hard)), degraded);
    }

    @Test
    void checksKnownHardDailyNutritionWithoutTurningUnknownIntoZero() throws Exception {
        MealPlanGenerationRequest base = fixtureRequest();
        MealPlanGenerationResponse degraded = fixtureResponse("valid_degraded_response.json");
        MealPlanGenerationRequest.NutritionTarget safeMaximum = new MealPlanGenerationRequest.NutritionTarget(
                "ENERGY", null, null, new BigDecimal("900"), true, "kcal");
        validator.validate(request(base, base.hardConstraints(), base.ingredientFacts(),
                base.recipeCandidates(), List.of(safeMaximum)), degraded);
        MealPlanGenerationRequest.NutritionTarget unsafeMaximum = new MealPlanGenerationRequest.NutritionTarget(
                "ENERGY", null, null, new BigDecimal("800"), true, "kcal");
        rejects(request(base, base.hardConstraints(), base.ingredientFacts(),
                base.recipeCandidates(), List.of(unsafeMaximum)), degraded);
        MealPlanGenerationRequest.NutritionTarget safeMinimum = new MealPlanGenerationRequest.NutritionTarget(
                "ENERGY", null, new BigDecimal("800"), null, true, "kcal");
        validator.validate(request(base, base.hardConstraints(), base.ingredientFacts(),
                base.recipeCandidates(), List.of(safeMinimum)), degraded);
        MealPlanGenerationRequest.NutritionTarget unsafeMinimum = new MealPlanGenerationRequest.NutritionTarget(
                "ENERGY", null, new BigDecimal("900"), null, true, "kcal");
        rejects(request(base, base.hardConstraints(), base.ingredientFacts(),
                base.recipeCandidates(), List.of(unsafeMinimum)), degraded);
    }

    @Test
    void twoMealsBelowHardMinimumMayTogetherSatisfyDailyMinimum() throws Exception {
        MealPlanGenerationRequest base = fixtureRequest();
        MealPlanGenerationResponse successful = fixtureResponse("valid_succeeded_response.json");
        MealPlanGenerationResponse.Entry dinner = successful.entries().get(1);
        MealPlanGenerationResponse twoMeals = response(successful,
                List.of(successful.entries().get(0), entry(dinner, MealSlotCode.DINNER,
                        base.recipeCandidates().get(0).recipePublicId(), dinner.servings(),
                        dinner.totalScore(), dinner.scoreComponents())), List.of());
        // Each meal supplies 840 kcal; neither alone reaches 1500, but the
        // selected day's aggregate is 1680 kcal.
        MealPlanGenerationRequest.NutritionTarget dailyMinimum = new MealPlanGenerationRequest.NutritionTarget(
                "ENERGY", null, new BigDecimal("1500"), null, true, "kcal");
        validator.validate(request(base, base.hardConstraints(), base.ingredientFacts(),
                base.recipeCandidates(), List.of(dailyMinimum)), twoMeals);
        MealPlanGenerationRequest.NutritionTarget dailyMaximum = new MealPlanGenerationRequest.NutritionTarget(
                "ENERGY", null, null, new BigDecimal("1600"), true, "kcal");
        rejects(request(base, base.hardConstraints(), base.ingredientFacts(),
                base.recipeCandidates(), List.of(dailyMaximum)), twoMeals);
        MealPlanGenerationRequest.NutritionTarget unmetDailyMinimum = new MealPlanGenerationRequest.NutritionTarget(
                "ENERGY", null, new BigDecimal("1700"), null, true, "kcal");
        rejects(request(base, base.hardConstraints(), base.ingredientFacts(),
                base.recipeCandidates(), List.of(unmetDailyMinimum)), twoMeals);

        MealPlanGenerationRequest twoDays = new MealPlanGenerationRequest(
                base.contractVersion(), base.requestId(), base.algorithmVersion(),
                new MealPlanGenerationRequest.Planning(base.planning().startDate(), 2,
                        base.planning().requestedMealSlots(), base.planning().defaultServings(),
                        base.planning().maxMinutesPerMeal()), base.hardConstraints(),
                base.softPreferences(), List.of(dailyMinimum), base.unitDefinitions(),
                base.pantryLots(), base.ingredientFacts(), base.recipeCandidates());
        MealPlanGenerationResponse.UnfilledSlot gap = fixtureResponse(
                "valid_degraded_response.json").unfilledSlots().get(0);
        MealPlanGenerationResponse emptySecondDay = new MealPlanGenerationResponse(
                successful.contractVersion(), successful.requestId(),
                successful.algorithmVersion(), GenerationStatus.DEGRADED,
                twoMeals.entries(), List.of(
                        new MealPlanGenerationResponse.UnfilledSlot(gap.planDate().plusDays(1),
                                MealSlotCode.BREAKFAST, gap.reasonCode(), null),
                        new MealPlanGenerationResponse.UnfilledSlot(gap.planDate().plusDays(1),
                                MealSlotCode.DINNER, gap.reasonCode(), null)));
        rejects(twoDays, emptySecondDay);
    }

    @Test
    void optionalIngredientsStillRequireExplicitAllergenSafetyAndAvoidIsNotDislike() throws Exception {
        MealPlanGenerationRequest base = fixtureRequest();
        MealPlanGenerationResponse degraded = fixtureResponse("valid_degraded_response.json");
        MealPlanGenerationRequest.RecipeCandidate original = base.recipeCandidates().get(0);
        MealPlanGenerationRequest.IngredientRequirement second = original.ingredients().get(1);
        MealPlanGenerationRequest.IngredientRequirement optional =
                new MealPlanGenerationRequest.IngredientRequirement(
                        second.ingredientPublicId(), second.quantity(), second.unitCode(),
                        true, second.allowSubstitution());
        MealPlanGenerationRequest.RecipeCandidate changed = new MealPlanGenerationRequest.RecipeCandidate(
                original.recipePublicId(), original.servings(), original.totalMinutes(),
                original.mealSlotCodes(), original.dietaryCodes(), original.tagCodes(),
                List.of(original.ingredients().get(0), optional), original.nutrition());
        List<MealPlanGenerationRequest.RecipeCandidate> candidates = List.of(
                changed, base.recipeCandidates().get(1));
        // The optional ingredient is DISLIKE in the fixture, which is not a hard exclusion.
        validator.validate(request(base, base.hardConstraints(), base.ingredientFacts(),
                candidates, base.nutritionTargets()), degraded);
        MealPlanGenerationRequest.HardConstraints avoidOptional = new MealPlanGenerationRequest.HardConstraints(
                base.hardConstraints().allergenCodes(),
                base.hardConstraints().exclusionaryDietaryCodes(),
                List.of(second.ingredientPublicId()));
        rejects(request(base, avoidOptional, base.ingredientFacts(), candidates,
                base.nutritionTargets()), degraded);
        List<MealPlanGenerationRequest.IngredientFact> missingIngredient = base.ingredientFacts()
                .stream().filter(fact -> !fact.ingredientPublicId().equals(
                        second.ingredientPublicId())).toList();
        rejects(request(base, base.hardConstraints(), missingIngredient, candidates,
                base.nutritionTargets()), degraded);
        List<MealPlanGenerationRequest.IngredientFact> missingAllergen = base.ingredientFacts()
                .stream().map(fact -> fact.ingredientPublicId().equals(second.ingredientPublicId())
                        ? new MealPlanGenerationRequest.IngredientFact(second.ingredientPublicId(),
                                List.of()) : fact).toList();
        rejects(request(base, base.hardConstraints(), missingAllergen, candidates,
                base.nutritionTargets()), degraded);
        for (AllergenEvidenceStatus unsafe : List.of(AllergenEvidenceStatus.CONTAINS,
                AllergenEvidenceStatus.MAY_CONTAIN, AllergenEvidenceStatus.UNKNOWN)) {
            List<MealPlanGenerationRequest.IngredientFact> facts = base.ingredientFacts().stream()
                    .map(fact -> fact.ingredientPublicId().equals(second.ingredientPublicId())
                            ? new MealPlanGenerationRequest.IngredientFact(second.ingredientPublicId(),
                                    List.of(new MealPlanGenerationRequest.AllergenFact(
                                            "PEANUT", unsafe))) : fact).toList();
            rejects(request(base, base.hardConstraints(), facts, candidates,
                    base.nutritionTargets()), degraded);
        }
    }

    private void rejects(MealPlanGenerationRequest request, MealPlanGenerationResponse response) {
        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOfSatisfying(MealPlanningIntegrationException.class,
                        error -> assertThat(error.failure()).isEqualTo(
                                MealPlanningIntegrationFailure.AI_BAD_RESPONSE));
    }

    private static MealPlanGenerationResponse.Entry entry(MealPlanGenerationResponse.Entry base,
            MealSlotCode slot, UUID recipe, BigDecimal servings, BigDecimal score,
            List<MealPlanGenerationResponse.ScoreComponent> components) {
        return new MealPlanGenerationResponse.Entry(base.planDate(), slot, recipe, servings,
                score, components, base.explanation());
    }

    private static MealPlanGenerationResponse withEntry(MealPlanGenerationResponse base,
            MealPlanGenerationResponse.Entry entry) {
        return response(base, List.of(entry), base.unfilledSlots());
    }

    private static MealPlanGenerationResponse response(MealPlanGenerationResponse base,
            List<MealPlanGenerationResponse.Entry> entries,
            List<MealPlanGenerationResponse.UnfilledSlot> gaps) {
        return new MealPlanGenerationResponse(base.contractVersion(), base.requestId(),
                base.algorithmVersion(), base.status(), entries, gaps);
    }

    private static MealPlanGenerationRequest request(MealPlanGenerationRequest base,
            MealPlanGenerationRequest.HardConstraints constraints,
            List<MealPlanGenerationRequest.IngredientFact> facts,
            List<MealPlanGenerationRequest.RecipeCandidate> candidates,
            List<MealPlanGenerationRequest.NutritionTarget> targets) {
        return new MealPlanGenerationRequest(base.contractVersion(), base.requestId(),
                AlgorithmVersion.HEURISTIC_MEAL_PLAN_V1, base.planning(), constraints,
                base.softPreferences(), targets, base.unitDefinitions(), base.pantryLots(),
                facts, candidates);
    }

    private MealPlanGenerationRequest fixtureRequest() throws Exception {
        return contract.readRequest(fixture("valid_request.json"));
    }

    private MealPlanGenerationResponse fixtureResponse(String name) throws Exception {
        return contract.readResponse(fixture(name));
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("..", "contract_fixtures", "meal_planning", "v1", name));
    }
}
