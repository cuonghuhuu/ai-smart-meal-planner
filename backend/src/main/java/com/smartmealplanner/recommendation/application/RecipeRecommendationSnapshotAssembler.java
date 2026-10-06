package com.smartmealplanner.recommendation.application;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.food.DislikedIngredientStrength;
import com.smartmealplanner.food.DislikedIngredientView;
import com.smartmealplanner.food.IngredientSafetyQueryService;
import com.smartmealplanner.food.IngredientSafetySnapshot;
import com.smartmealplanner.food.UserDislikedIngredientService;
import com.smartmealplanner.nutrition.application.NutritionApplicationException;
import com.smartmealplanner.nutrition.application.NutritionApplicationFailure;
import com.smartmealplanner.nutrition.application.NutritionTargetQueryService;
import com.smartmealplanner.nutrition.application.NutritionTargetValueView;
import com.smartmealplanner.nutrition.application.NutritionTargetView;
import com.smartmealplanner.nutrition.application.PlanningUnitQueryService;
import com.smartmealplanner.nutrition.application.PlanningUnitSnapshot;
import com.smartmealplanner.pantry.PantryAvailabilityQueryService;
import com.smartmealplanner.pantry.PantryAvailabilitySnapshot;
import com.smartmealplanner.profile.application.MealPlanningPreferencesQueryService;
import com.smartmealplanner.profile.application.MealPlanningPreferencesSnapshot;
import com.smartmealplanner.recipe.RecipeRecommendationQueryService;
import com.smartmealplanner.recipe.RecipeRecommendationSnapshot;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractJson;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractLimits;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractValidationException;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Assembles one bounded immutable recommendation snapshot without a long transaction. */
@Service
public class RecipeRecommendationSnapshotAssembler {
    private final CurrentUserService users;
    private final PantryAvailabilityQueryService pantry;
    private final RecipeRecommendationQueryService recipes;
    private final MealPlanningPreferencesQueryService preferences;
    private final UserDislikedIngredientService dislikedIngredients;
    private final NutritionTargetQueryService nutrition;
    private final IngredientSafetyQueryService ingredientFacts;
    private final PlanningUnitQueryService units;
    private final RecommendationHistoryQueryService history;
    private final RecipeRankingContractJson contract;
    private final Clock clock;

    @Autowired
    public RecipeRecommendationSnapshotAssembler(
            CurrentUserService users,
            PantryAvailabilityQueryService pantry,
            RecipeRecommendationQueryService recipes,
            MealPlanningPreferencesQueryService preferences,
            UserDislikedIngredientService dislikedIngredients,
            NutritionTargetQueryService nutrition,
            IngredientSafetyQueryService ingredientFacts,
            PlanningUnitQueryService units,
            RecommendationHistoryQueryService history,
            RecipeRankingContractJson contract) {
        this(users, pantry, recipes, preferences, dislikedIngredients, nutrition,
                ingredientFacts, units, history, contract, Clock.systemUTC());
    }

    RecipeRecommendationSnapshotAssembler(
            CurrentUserService users,
            PantryAvailabilityQueryService pantry,
            RecipeRecommendationQueryService recipes,
            MealPlanningPreferencesQueryService preferences,
            UserDislikedIngredientService dislikedIngredients,
            NutritionTargetQueryService nutrition,
            IngredientSafetyQueryService ingredientFacts,
            PlanningUnitQueryService units,
            RecommendationHistoryQueryService history,
            RecipeRankingContractJson contract,
            Clock clock) {
        this.users = users;
        this.pantry = pantry;
        this.recipes = recipes;
        this.preferences = preferences;
        this.dislikedIngredients = dislikedIngredients;
        this.nutrition = nutrition;
        this.ingredientFacts = ingredientFacts;
        this.units = units;
        this.history = history;
        this.contract = contract;
        this.clock = clock;
    }

    public RecipeRankingRequest assemble(
            UUID authenticatedUserPublicId,
            UUID requestId,
            RecipeRecommendationCommand command) {
        if (authenticatedUserPublicId == null || requestId == null || command == null) {
            throw failure(RecipeRecommendationFailure.INVALID_INPUT);
        }

        CurrentUserIdentity identity = users.getIdentity(authenticatedUserPublicId);
        LocalDate targetDate = localDate(identity);
        List<PantryAvailabilitySnapshot> lots = pantry.availableFor(authenticatedUserPublicId);
        limit(lots.size(), RecipeRankingContractLimits.MAX_PANTRY_LOTS);

        List<RecipeRecommendationSnapshot> candidates = recipes.candidates(
                List.of(command.mealSlotCode().name()),
                RecipeRankingContractLimits.MAX_RECIPE_CANDIDATES);
        limit(candidates.size(), RecipeRankingContractLimits.MAX_RECIPE_CANDIDATES);
        for (RecipeRecommendationSnapshot candidate : candidates) {
            limit(candidate.ingredients().size(),
                    RecipeRankingContractLimits.MAX_INGREDIENTS_PER_RECIPE);
            limit(candidate.tagCodes().size(),
                    RecipeRankingContractLimits.MAX_RECIPE_TAG_CODES);
            if (candidate.nutrition() != null) {
                limit(candidate.nutrition().values().size(),
                        RecipeRankingContractLimits.MAX_NUTRITION_VALUES_PER_RECIPE);
            }
        }

        MealPlanningPreferencesSnapshot profile =
                preferences.forUser(authenticatedUserPublicId);
        List<DislikedIngredientView> dislikes =
                dislikedIngredients.getPreferences(authenticatedUserPublicId);
        List<NutritionTargetValueView> targets =
                optionalTargets(authenticatedUserPublicId, targetDate);
        limit(targets.size(), RecipeRankingContractLimits.MAX_NUTRITION_TARGETS);

        Set<UUID> candidateIngredientIds = new HashSet<>();
        Set<UUID> candidateRecipeIds = new HashSet<>();
        for (RecipeRecommendationSnapshot candidate : candidates) {
            candidateRecipeIds.add(candidate.recipePublicId());
            candidate.ingredients().forEach(item ->
                    candidateIngredientIds.add(item.ingredientPublicId()));
        }
        limit(candidateIngredientIds.size(), RecipeRankingContractLimits.MAX_INGREDIENT_FACTS);

        List<IngredientSafetySnapshot> facts =
                ingredientFacts.forIngredients(candidateIngredientIds);
        if (facts.size() != candidateIngredientIds.size()) {
            throw failure(RecipeRecommendationFailure.SNAPSHOT_INCONSISTENT);
        }

        Set<String> unitCodes = new HashSet<>();
        targets.forEach(target -> unitCodes.add(target.unitCode()));
        lots.forEach(lot -> unitCodes.add(lot.unitCode()));
        for (RecipeRecommendationSnapshot candidate : candidates) {
            candidate.ingredients().stream()
                    .map(RecipeRecommendationSnapshot.IngredientRequirement::unitCode)
                    .filter(java.util.Objects::nonNull).forEach(unitCodes::add);
            if (candidate.nutrition() != null) {
                candidate.nutrition().values().forEach(value ->
                        unitCodes.add(value.unitCode()));
            }
        }
        limit(unitCodes.size(), RecipeRankingContractLimits.MAX_UNIT_DEFINITIONS);
        List<PlanningUnitSnapshot> definitions = units.definitionsFor(unitCodes);
        limit(definitions.size(), RecipeRankingContractLimits.MAX_UNIT_DEFINITIONS);

        List<RecommendationHistoryQueryService.RecentRecipeCount> recent =
                history.recentCounts(identity.internalId(), candidateRecipeIds,
                        targetDate.minusDays(30).atStartOfDay());
        limit(recent.size(), RecipeRankingContractLimits.MAX_RECENT_RECIPE_COUNTS);

        RecipeRankingRequest result = new RecipeRankingRequest(
                RecipeRankingContractLimits.CONTRACT_VERSION,
                requestId,
                RecipeRankingContractCodes.AlgorithmVersion.HEURISTIC_RECIPE_RANK_V1,
                new RecipeRankingRequest.Context(
                        targetDate, command.mealSlotCode(), command.servings(),
                        command.maxMinutes(), command.resultLimit()),
                new RecipeRankingRequest.HardConstraints(
                        profile.allergenCodes().stream().sorted().toList(),
                        profile.exclusionaryDietaryCodes().stream().sorted().toList(),
                        preferenceIds(dislikes, DislikedIngredientStrength.AVOID)),
                new RecipeRankingRequest.SoftPreferences(
                        preferenceIds(dislikes, DislikedIngredientStrength.DISLIKE),
                        profile.softPreferenceCodes().stream().sorted().toList()),
                targets.stream().map(RecipeRecommendationSnapshotAssembler::target)
                        .sorted(Comparator.comparing(RecipeRankingRequest.NutritionTarget::nutrientCode))
                        .toList(),
                recent.stream().map(value -> new RecipeRankingRequest.RecentRecipeCount(
                        value.recipePublicId(), value.count())).toList(),
                definitions.stream().map(RecipeRecommendationSnapshotAssembler::unit).toList(),
                lots.stream().map(RecipeRecommendationSnapshotAssembler::lot).toList(),
                facts.stream().map(RecipeRecommendationSnapshotAssembler::fact).toList(),
                candidates.stream().map(RecipeRecommendationSnapshotAssembler::recipe).toList());

        try {
            contract.writeRequest(result);
        } catch (RecipeRankingContractValidationException exception) {
            throw new RecipeRecommendationException(
                    RecipeRecommendationFailure.SNAPSHOT_INCONSISTENT, exception);
        }
        return result;
    }

    private List<NutritionTargetValueView> optionalTargets(
            UUID authenticatedUserPublicId, LocalDate targetDate) {
        try {
            NutritionTargetView value = nutrition.getCurrentTarget(authenticatedUserPublicId);
            boolean starts = !targetDate.isBefore(value.effectiveFrom());
            boolean ends = value.effectiveTo() == null
                    || !targetDate.isAfter(value.effectiveTo());
            return starts && ends ? value.nutrientValues() : List.of();
        } catch (NutritionApplicationException exception) {
            if (exception.failure() == NutritionApplicationFailure.NO_CURRENT_TARGET) {
                return List.of();
            }
            throw exception;
        }
    }

    private LocalDate localDate(CurrentUserIdentity identity) {
        try {
            return LocalDate.ofInstant(clock.instant(), ZoneId.of(identity.timeZone()));
        } catch (DateTimeException exception) {
            throw new RecipeRecommendationException(
                    RecipeRecommendationFailure.INVALID_TIME_ZONE, exception);
        }
    }

    private static List<UUID> preferenceIds(
            List<DislikedIngredientView> values, DislikedIngredientStrength strength) {
        return values.stream().filter(value -> value.strength() == strength)
                .map(DislikedIngredientView::ingredientPublicId)
                .sorted(Comparator.comparing(UUID::toString)).toList();
    }

    private static RecipeRankingRequest.NutritionTarget target(
            NutritionTargetValueView value) {
        return new RecipeRankingRequest.NutritionTarget(
                value.nutrientCode(), value.targetAmount(), value.minAmount(),
                value.maxAmount(), value.hardLimit(), value.unitCode());
    }

    private static RecipeRankingRequest.UnitDefinition unit(PlanningUnitSnapshot value) {
        return new RecipeRankingRequest.UnitDefinition(
                value.unitCode(),
                RecipeRankingContractCodes.UnitDimension.valueOf(value.dimension()),
                value.baseUnitCode(), value.toBaseFactor());
    }

    private static RecipeRankingRequest.PantryLot lot(PantryAvailabilitySnapshot value) {
        return new RecipeRankingRequest.PantryLot(
                value.pantryItemPublicId(), value.ingredientPublicId(),
                value.foodPublicId(), value.quantityRemaining(), value.unitCode(),
                value.expiryDate(),
                RecipeRankingContractCodes.ExpiryKind.valueOf(value.expiryKind().name()),
                RecipeRankingContractCodes.StorageLocation.valueOf(
                        value.storageLocation().name()));
    }

    private static RecipeRankingRequest.IngredientFact fact(
            IngredientSafetySnapshot value) {
        return new RecipeRankingRequest.IngredientFact(
                value.ingredientPublicId(),
                value.allergenFacts().stream()
                        .map(item -> new RecipeRankingRequest.AllergenFact(
                                item.allergenCode(),
                                RecipeRankingContractCodes.AllergenEvidenceStatus.valueOf(
                                        item.presence().name())))
                        .sorted(Comparator.comparing(
                                RecipeRankingRequest.AllergenFact::allergenCode))
                        .toList());
    }

    private static RecipeRankingRequest.RecipeCandidate recipe(
            RecipeRecommendationSnapshot value) {
        RecipeRankingRequest.NutritionData nutrition = value.nutrition() == null
                ? null : new RecipeRankingRequest.NutritionData(
                        value.nutrition().completenessRatio(),
                        value.nutrition().values().stream()
                                .map(item -> new RecipeRankingRequest.NutritionValue(
                                        item.nutrientCode(), item.amountPerServing(),
                                        item.unitCode()))
                                .toList());
        return new RecipeRankingRequest.RecipeCandidate(
                value.recipePublicId(), value.servings(), value.totalMinutes(),
                value.mealSlotCodes().stream()
                        .map(RecipeRankingContractCodes.MealSlotCode::valueOf)
                        .toList(),
                value.dietaryCodes(), value.tagCodes(),
                value.ingredients().stream()
                        .map(item -> new RecipeRankingRequest.IngredientRequirement(
                                item.ingredientPublicId(), item.quantity(), item.unitCode(),
                                item.optional(), item.allowSubstitution()))
                        .toList(),
                nutrition);
    }

    private static void limit(int count, int maximum) {
        if (count > maximum) {
            throw failure(RecipeRecommendationFailure.SNAPSHOT_LIMIT_EXCEEDED);
        }
    }

    private static RecipeRecommendationException failure(
            RecipeRecommendationFailure failure) {
        return new RecipeRecommendationException(failure);
    }
}
