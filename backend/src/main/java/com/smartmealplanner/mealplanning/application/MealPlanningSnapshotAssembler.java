package com.smartmealplanner.mealplanning.application;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.food.DislikedIngredientStrength;
import com.smartmealplanner.food.DislikedIngredientView;
import com.smartmealplanner.food.IngredientSafetyQueryService;
import com.smartmealplanner.food.IngredientSafetySnapshot;
import com.smartmealplanner.food.UserDislikedIngredientService;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractJson;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractValidationException;
import com.smartmealplanner.nutrition.application.NutritionTargetQueryService;
import com.smartmealplanner.nutrition.application.NutritionTargetValueView;
import com.smartmealplanner.nutrition.application.PlanningUnitQueryService;
import com.smartmealplanner.nutrition.application.PlanningUnitSnapshot;
import com.smartmealplanner.pantry.PantryAvailabilityQueryService;
import com.smartmealplanner.pantry.PantryAvailabilitySnapshot;
import com.smartmealplanner.profile.application.MealPlanningPreferencesQueryService;
import com.smartmealplanner.profile.application.MealPlanningPreferencesSnapshot;
import com.smartmealplanner.recipe.RecipeRecommendationQueryService;
import com.smartmealplanner.recipe.RecipeRecommendationSnapshot;

import org.springframework.stereotype.Service;

/** Assembles one bounded, immutable V1 snapshot through module-owned reads. */
@Service
public class MealPlanningSnapshotAssembler {
    private final CurrentUserService users;
    private final PantryAvailabilityQueryService pantry;
    private final RecipeRecommendationQueryService recipes;
    private final MealPlanningPreferencesQueryService preferences;
    private final UserDislikedIngredientService dislikedIngredients;
    private final NutritionTargetQueryService nutrition;
    private final IngredientSafetyQueryService ingredientFacts;
    private final PlanningUnitQueryService units;
    private final MealPlanningContractJson contract;

    public MealPlanningSnapshotAssembler(CurrentUserService users,
            PantryAvailabilityQueryService pantry,
            RecipeRecommendationQueryService recipes,
            MealPlanningPreferencesQueryService preferences,
            UserDislikedIngredientService dislikedIngredients,
            NutritionTargetQueryService nutrition,
            IngredientSafetyQueryService ingredientFacts,
            PlanningUnitQueryService units, MealPlanningContractJson contract) {
        this.users = users;
        this.pantry = pantry;
        this.recipes = recipes;
        this.preferences = preferences;
        this.dislikedIngredients = dislikedIngredients;
        this.nutrition = nutrition;
        this.ingredientFacts = ingredientFacts;
        this.units = units;
        this.contract = contract;
    }

    /** No transaction surrounds these module-owned short read transactions. */
    public MealPlanGenerationRequest assemble(UUID authenticatedUserPublicId,
            UUID requestId, MealPlanGenerationCommand command) {
        validateInput(authenticatedUserPublicId, requestId, command);
        users.getIdentity(authenticatedUserPublicId);
        List<PantryAvailabilitySnapshot> lots = pantry.availableFor(authenticatedUserPublicId);
        limit(lots.size(), MealPlanningContractLimits.MAX_PANTRY_LOTS);
        List<String> requestedSlots = command.requestedMealSlots().stream()
                .map(Enum::name).toList();
        List<RecipeRecommendationSnapshot> candidates = recipes.candidates(
                requestedSlots, MealPlanningContractLimits.MAX_RECIPE_CANDIDATES);
        limit(candidates.size(), MealPlanningContractLimits.MAX_RECIPE_CANDIDATES);
        for (RecipeRecommendationSnapshot candidate : candidates) {
            limit(candidate.ingredients().size(), MealPlanningContractLimits.MAX_INGREDIENTS_PER_RECIPE);
            if (candidate.nutrition() != null) {
                limit(candidate.nutrition().values().size(),
                        MealPlanningContractLimits.MAX_NUTRITION_VALUES_PER_RECIPE);
            }
        }
        MealPlanningPreferencesSnapshot profile = preferences.forUser(authenticatedUserPublicId);
        List<DislikedIngredientView> dislikes =
                dislikedIngredients.getPreferences(authenticatedUserPublicId);
        List<NutritionTargetValueView> targets = nutrition.getCurrentTarget(
                authenticatedUserPublicId).nutrientValues();
        limit(targets.size(), MealPlanningContractLimits.MAX_NUTRITION_TARGETS);
        Set<UUID> candidateIngredientIds = new HashSet<>();
        for (RecipeRecommendationSnapshot candidate : candidates) {
            candidate.ingredients().forEach(item -> candidateIngredientIds.add(
                    item.ingredientPublicId()));
        }
        limit(candidateIngredientIds.size(), MealPlanningContractLimits.MAX_INGREDIENT_FACTS);
        List<IngredientSafetySnapshot> facts = ingredientFacts.forIngredients(candidateIngredientIds);
        if (facts.size() != candidateIngredientIds.size()) { throw inconsistent(); }
        Set<String> unitCodes = new HashSet<>();
        targets.forEach(target -> unitCodes.add(target.unitCode()));
        lots.forEach(lot -> unitCodes.add(lot.unitCode()));
        for (RecipeRecommendationSnapshot candidate : candidates) {
            candidate.ingredients().stream().map(RecipeRecommendationSnapshot.IngredientRequirement::unitCode)
                    .filter(java.util.Objects::nonNull).forEach(unitCodes::add);
            if (candidate.nutrition() != null) {
                candidate.nutrition().values().forEach(value -> unitCodes.add(value.unitCode()));
            }
        }
        limit(unitCodes.size(), MealPlanningContractLimits.MAX_UNIT_DEFINITIONS);
        List<PlanningUnitSnapshot> definitions = units.definitionsFor(unitCodes);
        limit(definitions.size(), MealPlanningContractLimits.MAX_UNIT_DEFINITIONS);

        MealPlanGenerationRequest result = new MealPlanGenerationRequest(
                MealPlanningContractLimits.CONTRACT_VERSION, requestId,
                MealPlanningContractCodes.AlgorithmVersion.HEURISTIC_MEAL_PLAN_V1,
                new MealPlanGenerationRequest.Planning(command.startDate(), command.days(),
                        command.requestedMealSlots(), command.defaultServings(),
                        command.maxMinutesPerMeal()),
                new MealPlanGenerationRequest.HardConstraints(
                        profile.allergenCodes().stream().sorted().toList(),
                        profile.exclusionaryDietaryCodes().stream().sorted().toList(),
                        preferenceIds(dislikes, DislikedIngredientStrength.AVOID)),
                new MealPlanGenerationRequest.SoftPreferences(
                        preferenceIds(dislikes, DislikedIngredientStrength.DISLIKE),
                        profile.softPreferenceCodes().stream().sorted().toList()),
                targets.stream().map(MealPlanningSnapshotAssembler::target)
                        .sorted(Comparator.comparing(MealPlanGenerationRequest.NutritionTarget::nutrientCode))
                        .toList(),
                definitions.stream().map(MealPlanningSnapshotAssembler::unit).toList(),
                lots.stream().map(MealPlanningSnapshotAssembler::lot).toList(),
                facts.stream().map(MealPlanningSnapshotAssembler::fact).toList(),
                candidates.stream().map(MealPlanningSnapshotAssembler::recipe).toList());
        try { contract.writeRequest(result); }
        catch (MealPlanningContractValidationException exception) {
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.SNAPSHOT_INCONSISTENT, exception);
        }
        return result;
    }

    private static void validateInput(UUID user, UUID requestId,
            MealPlanGenerationCommand command) {
        if (user == null || requestId == null || command == null
                || command.startDate() == null || command.days() < 1
                || command.days() > MealPlanningContractLimits.MAX_PLAN_DAYS
                || command.requestedMealSlots() == null
                || command.requestedMealSlots().isEmpty()
                || command.requestedMealSlots().contains(null)
                || command.requestedMealSlots().size()
                        > MealPlanningContractLimits.MAX_REQUESTED_MEAL_SLOTS
                || new HashSet<>(command.requestedMealSlots()).size()
                        != command.requestedMealSlots().size()
                || command.defaultServings() == null
                || command.defaultServings().signum() <= 0
                || command.defaultServings().compareTo(new BigDecimal("50")) > 0
                || command.defaultServings().scale() > 2
                || (command.maxMinutesPerMeal() != null
                && (command.maxMinutesPerMeal() < 1
                || command.maxMinutesPerMeal() > MealPlanningContractLimits.MAX_MINUTES_PER_MEAL))) {
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.INVALID_GENERATION_INPUT);
        }
        try { command.startDate().plusDays(command.days() - 1L); }
        catch (DateTimeException exception) {
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.INVALID_GENERATION_INPUT, exception);
        }
    }

    private static List<UUID> preferenceIds(List<DislikedIngredientView> values,
            DislikedIngredientStrength strength) {
        return values.stream().filter(value -> value.strength() == strength)
                .map(DislikedIngredientView::ingredientPublicId)
                .sorted(Comparator.comparing(UUID::toString)).toList();
    }

    private static MealPlanGenerationRequest.NutritionTarget target(NutritionTargetValueView value) {
        return new MealPlanGenerationRequest.NutritionTarget(value.nutrientCode(),
                value.targetAmount(), value.minAmount(), value.maxAmount(),
                value.hardLimit(), value.unitCode());
    }

    private static MealPlanGenerationRequest.UnitDefinition unit(PlanningUnitSnapshot value) {
        return new MealPlanGenerationRequest.UnitDefinition(value.unitCode(),
                MealPlanningContractCodes.UnitDimension.valueOf(value.dimension()),
                value.baseUnitCode(), value.toBaseFactor());
    }

    private static MealPlanGenerationRequest.PantryLot lot(PantryAvailabilitySnapshot value) {
        return new MealPlanGenerationRequest.PantryLot(value.pantryItemPublicId(),
                value.ingredientPublicId(), value.foodPublicId(), value.quantityRemaining(),
                value.unitCode(), value.expiryDate(),
                MealPlanningContractCodes.ExpiryKind.valueOf(value.expiryKind().name()),
                MealPlanningContractCodes.StorageLocation.valueOf(value.storageLocation().name()));
    }

    private static MealPlanGenerationRequest.IngredientFact fact(IngredientSafetySnapshot value) {
        return new MealPlanGenerationRequest.IngredientFact(value.ingredientPublicId(),
                value.allergenFacts().stream().map(item -> new MealPlanGenerationRequest.AllergenFact(
                        item.allergenCode(), MealPlanningContractCodes.AllergenEvidenceStatus
                                .valueOf(item.presence().name())))
                        .sorted(Comparator.comparing(MealPlanGenerationRequest.AllergenFact::allergenCode))
                        .toList());
    }

    private static MealPlanGenerationRequest.RecipeCandidate recipe(RecipeRecommendationSnapshot value) {
        MealPlanGenerationRequest.NutritionData nutrition = value.nutrition() == null ? null
                : new MealPlanGenerationRequest.NutritionData(
                        value.nutrition().completenessRatio(),
                        value.nutrition().values().stream()
                                .map(item -> new MealPlanGenerationRequest.NutritionValue(
                                        item.nutrientCode(), item.amountPerServing(), item.unitCode()))
                                .toList());
        return new MealPlanGenerationRequest.RecipeCandidate(value.recipePublicId(),
                value.servings(), value.totalMinutes(),
                value.mealSlotCodes().stream().map(
                        MealPlanningContractCodes.MealSlotCode::valueOf).toList(),
                value.dietaryCodes(), value.tagCodes(),
                value.ingredients().stream().map(item ->
                        new MealPlanGenerationRequest.IngredientRequirement(
                                item.ingredientPublicId(), item.quantity(), item.unitCode(),
                                item.optional(), item.allowSubstitution())).toList(), nutrition);
    }

    private static void limit(int count, int maximum) {
        if (count > maximum) {
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.SNAPSHOT_LIMIT_EXCEEDED);
        }
    }

    private static MealPlanningIntegrationException inconsistent() {
        return new MealPlanningIntegrationException(
                MealPlanningIntegrationFailure.SNAPSHOT_INCONSISTENT);
    }
}
