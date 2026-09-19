package com.smartmealplanner.mealplanning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.food.DislikedIngredientStrength;
import com.smartmealplanner.food.DislikedIngredientView;
import com.smartmealplanner.food.IngredientAllergenPresence;
import com.smartmealplanner.food.IngredientAllergenQueryService;
import com.smartmealplanner.food.IngredientAllergenSnapshot;
import com.smartmealplanner.food.UserDislikedIngredientService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;
import com.smartmealplanner.nutrition.application.NutritionTargetQueryService;
import com.smartmealplanner.nutrition.application.NutritionTargetValueView;
import com.smartmealplanner.nutrition.application.NutritionTargetView;
import com.smartmealplanner.pantry.PantryAvailabilityQueryService;
import com.smartmealplanner.pantry.PantryAvailabilitySnapshot;
import com.smartmealplanner.profile.application.AllergenReferenceQueryService;
import com.smartmealplanner.profile.application.UserAllergenService;
import com.smartmealplanner.profile.application.UserDietaryPreferenceService;
import com.smartmealplanner.profile.web.UserAllergenResponse;
import com.smartmealplanner.profile.web.UserDietaryPreferenceResponse;
import com.smartmealplanner.recipe.RecipeRecommendationCandidate;
import com.smartmealplanner.recipe.RecipeRecommendationQueryService;
import com.smartmealplanner.mealplanning.web.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.web.MealPlanResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-scoped deterministic RULE_BASED_V1 meal-plan generation and reads.
 */
@Service
public class MealPlanService {

    public static final String ALGORITHM_VERSION = "RULE_BASED_V1";

    private static final List<String> DEFAULT_SLOT_CODES = List.of(
            "BREAKFAST", "LUNCH", "DINNER");
    private static final Set<String> EXACT_DIETARY_TAGS = Set.of(
            "VEGETARIAN", "VEGAN", "GLUTEN_FREE", "DAIRY_FREE");
    private static final Set<String> UNSUPPORTED_DIETARY_CODES = Set.of(
            "PESCATARIAN", "HALAL", "KOSHER");
    private static final Set<String> CORE_NUTRIENTS = Set.of(
            "ENERGY", "PROTEIN", "CARBOHYDRATE", "FAT_TOTAL");
    private static final List<String> SCORE_CODES = List.of(
            "PANTRY_COVERAGE", "EXPIRY_URGENCY", "NUTRITION_FIT",
            "PREFERENCE_MATCH", "VARIETY", "EFFORT_FIT", "DISLIKE_PENALTY");

    private final CurrentUserService currentUserService;
    private final RecipeRecommendationQueryService recipeReferences;
    private final PantryAvailabilityQueryService pantryAvailability;
    private final MeasurementUnitReferenceQueryService unitReferences;
    private final IngredientAllergenQueryService ingredientAllergens;
    private final AllergenReferenceQueryService allergenReferences;
    private final UserDislikedIngredientService dislikedIngredients;
    private final UserDietaryPreferenceService dietaryPreferences;
    private final UserAllergenService userAllergens;
    private final NutritionTargetQueryService nutritionTargets;
    private final RecommendationRequestRepository recommendationRequests;
    private final RecommendationResultRepository recommendationResults;
    private final RecommendationResultScoreRepository recommendationScores;
    private final AiScoreComponentRepository scoreComponents;
    private final MealPlanRepository mealPlans;
    private final MealPlanEntryRepository mealPlanEntries;
    private final Clock clock;

    @Autowired
    public MealPlanService(
            CurrentUserService currentUserService,
            RecipeRecommendationQueryService recipeReferences,
            PantryAvailabilityQueryService pantryAvailability,
            MeasurementUnitReferenceQueryService unitReferences,
            IngredientAllergenQueryService ingredientAllergens,
            AllergenReferenceQueryService allergenReferences,
            UserDislikedIngredientService dislikedIngredients,
            UserDietaryPreferenceService dietaryPreferences,
            UserAllergenService userAllergens,
            NutritionTargetQueryService nutritionTargets,
            RecommendationRequestRepository recommendationRequests,
            RecommendationResultRepository recommendationResults,
            RecommendationResultScoreRepository recommendationScores,
            AiScoreComponentRepository scoreComponents,
            MealPlanRepository mealPlans,
            MealPlanEntryRepository mealPlanEntries) {
        this(currentUserService, recipeReferences, pantryAvailability, unitReferences,
                ingredientAllergens, allergenReferences, dislikedIngredients,
                dietaryPreferences, userAllergens, nutritionTargets,
                recommendationRequests, recommendationResults, recommendationScores,
                scoreComponents, mealPlans, mealPlanEntries, Clock.systemUTC());
    }

    MealPlanService(
            CurrentUserService currentUserService,
            RecipeRecommendationQueryService recipeReferences,
            PantryAvailabilityQueryService pantryAvailability,
            MeasurementUnitReferenceQueryService unitReferences,
            IngredientAllergenQueryService ingredientAllergens,
            AllergenReferenceQueryService allergenReferences,
            UserDislikedIngredientService dislikedIngredients,
            UserDietaryPreferenceService dietaryPreferences,
            UserAllergenService userAllergens,
            NutritionTargetQueryService nutritionTargets,
            RecommendationRequestRepository recommendationRequests,
            RecommendationResultRepository recommendationResults,
            RecommendationResultScoreRepository recommendationScores,
            AiScoreComponentRepository scoreComponents,
            MealPlanRepository mealPlans,
            MealPlanEntryRepository mealPlanEntries,
            Clock clock) {
        this.currentUserService = currentUserService;
        this.recipeReferences = recipeReferences;
        this.pantryAvailability = pantryAvailability;
        this.unitReferences = unitReferences;
        this.ingredientAllergens = ingredientAllergens;
        this.allergenReferences = allergenReferences;
        this.dislikedIngredients = dislikedIngredients;
        this.dietaryPreferences = dietaryPreferences;
        this.userAllergens = userAllergens;
        this.nutritionTargets = nutritionTargets;
        this.recommendationRequests = recommendationRequests;
        this.recommendationResults = recommendationResults;
        this.recommendationScores = recommendationScores;
        this.scoreComponents = scoreComponents;
        this.mealPlans = mealPlans;
        this.mealPlanEntries = mealPlanEntries;
        this.clock = clock;
    }

    @Transactional
    public MealPlanResponse.Generation generate(
            UUID authenticatedPublicId,
            MealPlanGenerationRequest request) {
        GenerationInputs inputs = validateRequest(request);
        CurrentUserIdentity identity = currentUserService.getIdentity(authenticatedPublicId);
        LocalDateTime startedAt = databaseTimestamp(clock);

        List<UserDietaryPreferenceResponse> dietary = dietaryPreferences
                .getPreferences(authenticatedPublicId);
        rejectUnsupportedDietaryConstraints(dietary);
        Set<String> dietaryCodes = dietary.stream()
                .map(UserDietaryPreferenceResponse::code)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        List<UserAllergenResponse> declaredAllergens = userAllergens
                .getAllergens(authenticatedPublicId);
        Set<String> allergenCodes = declaredAllergens.stream()
                .map(UserAllergenResponse::allergen)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Map<UUID, DislikedIngredientStrength> dislikes = new HashMap<>();
        for (DislikedIngredientView dislike : dislikedIngredients
                .getPreferences(authenticatedPublicId)) {
            if (dislike.ingredientPublicId() != null && dislike.strength() != null) {
                dislikes.put(dislike.ingredientPublicId(), dislike.strength());
            }
        }

        List<RecipeRecommendationCandidate> candidates = recipeReferences
                .findPublishedCandidates();
        if (candidates.isEmpty()) {
            throw new MealPlanException(MealPlanFailure.NO_ELIGIBLE_RECIPE);
        }

        List<PantryAvailabilitySnapshot> pantry = pantryAvailability
                .availableFor(authenticatedPublicId);
        Set<String> unitCodes = new LinkedHashSet<>();
        pantry.forEach(value -> unitCodes.add(value.unitCode()));
        candidates.stream()
                .flatMap(candidate -> candidate.ingredients().stream())
                .map(RecipeRecommendationCandidate.Ingredient::unitCode)
                .filter(java.util.Objects::nonNull)
                .forEach(unitCodes::add);
        Map<String, MeasurementUnitReferenceSnapshot> units = unitReferences
                .resolveByCodes(unitCodes);
        VirtualPantry virtualPantry = VirtualPantry.from(pantry, units);

        Set<Long> candidateIngredientIds = candidates.stream()
                .flatMap(candidate -> candidate.ingredients().stream())
                .map(RecipeRecommendationCandidate.Ingredient::internalId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<IngredientAllergenSnapshot>> allergenFacts = ingredientAllergens
                .resolveByIngredientIds(candidateIngredientIds);
        Set<Long> allergenIds = allergenFacts.values().stream()
                .flatMap(List::stream)
                .map(IngredientAllergenSnapshot::allergenId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> allergenCodeById = allergenReferences
                .resolveCodesByInternalIds(allergenIds);

        Map<String, RecipeRecommendationCandidate.MealSlot> slotsByCode = recipeReferences
                .resolveMealSlots(inputs.slotCodes());
        if (slotsByCode.size() != inputs.slotCodes().size()) {
            throw new MealPlanException(MealPlanFailure.INVALID_REQUEST);
        }
        List<RecipeRecommendationCandidate.MealSlot> orderedSlots = inputs.slotCodes().stream()
                .map(slotsByCode::get)
                .sorted(Comparator.comparing(RecipeRecommendationCandidate.MealSlot::displayOrder)
                        .thenComparing(RecipeRecommendationCandidate.MealSlot::code))
                .toList();

        Map<String, BigDecimal> mealTargets = mealNutritionTargets(
                authenticatedPublicId, inputs.slotCodes().size());
        Set<UUID> usedRecipes = new HashSet<>();
        List<Selection> selections = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (int day = 0; day < inputs.days(); day++) {
            LocalDate planDate = inputs.startDate().plusDays(day);
            for (RecipeRecommendationCandidate.MealSlot slot : orderedSlots) {
                List<ScoredCandidate> eligible = new ArrayList<>();
                for (RecipeRecommendationCandidate candidate : candidates) {
                    ScoredCandidate scored = scoreCandidate(
                            candidate,
                            slot.code(),
                            planDate,
                            inputs,
                            virtualPantry,
                            units,
                            dietaryCodes,
                            allergenCodes,
                            allergenFacts,
                            allergenCodeById,
                            dislikes,
                            usedRecipes,
                            mealTargets);
                    if (scored != null) {
                        eligible.add(scored);
                    }
                }
                if (eligible.isEmpty()) {
                    warnings.add(planDate + ":" + slot.code());
                    continue;
                }
                ScoredCandidate selected = eligible.stream()
                        .sorted(Comparator
                                .comparing((ScoredCandidate value) -> value.score().totalScore())
                                .reversed()
                                .thenComparing(value -> value.score().pantryCoverage(), Comparator.reverseOrder())
                                .thenComparing(value -> value.score().nutritionFit(), Comparator.reverseOrder())
                                .thenComparing(value -> value.candidate().title())
                                .thenComparing(value -> value.candidate().publicId().toString()))
                        .findFirst()
                        .orElseThrow();
                selections.add(new Selection(planDate, slot, selected.candidate(), selected.score()));
                usedRecipes.add(selected.candidate().publicId());
                virtualPantry.consume(selected.candidate(), inputs.defaultServings(), planDate, units);
            }
        }

        if (selections.isEmpty()) {
            throw new MealPlanException(MealPlanFailure.NO_ELIGIBLE_RECIPE);
        }

        LocalDate endDate = inputs.startDate().plusDays(inputs.days() - 1L);
        String sourceHash = constraintsHash(inputs);
        RecommendationRequest requestEntity = new RecommendationRequest(
                identity.internalId(), RecommendationRequestKind.MEAL_PLAN,
                UUID.randomUUID().toString(), sourceHash, ALGORITHM_VERSION, null, startedAt);
        recommendationRequests.saveAndFlush(requestEntity);

        List<RecommendationResult> resultEntities = new ArrayList<>();
        for (int index = 0; index < selections.size(); index++) {
            Selection selection = selections.get(index);
            resultEntities.add(new RecommendationResult(
                    requestEntity.internalId(), index + 1,
                    selection.candidate().internalId(),
                    selection.score().totalScore(),
                    explanation(selection.score())));
        }
        recommendationResults.saveAllAndFlush(resultEntities);

        Map<String, AiScoreComponent> components = resolveScoreComponents();
        List<RecommendationResultScore> scoreEntities = new ArrayList<>();
        for (int index = 0; index < resultEntities.size(); index++) {
            RecommendationResult result = resultEntities.get(index);
            RecommendationScore score = selections.get(index).score();
            scoreEntities.add(scoreEntity(result, components.get("PANTRY_COVERAGE"),
                    score.pantryCoverage(), MealPlanScoring.PANTRY_WEIGHT));
            scoreEntities.add(scoreEntity(result, components.get("NUTRITION_FIT"),
                    score.nutritionFit(), MealPlanScoring.NUTRITION_WEIGHT));
            scoreEntities.add(scoreEntity(result, components.get("EXPIRY_URGENCY"),
                    score.expiryUrgency(), MealPlanScoring.EXPIRY_WEIGHT));
            scoreEntities.add(scoreEntity(result, components.get("PREFERENCE_MATCH"),
                    score.preferenceMatch(), MealPlanScoring.PREFERENCE_WEIGHT));
            scoreEntities.add(scoreEntity(result, components.get("VARIETY"),
                    score.variety(), MealPlanScoring.VARIETY_WEIGHT));
            scoreEntities.add(scoreEntity(result, components.get("EFFORT_FIT"),
                    score.effortFit(), MealPlanScoring.EFFORT_WEIGHT));
            scoreEntities.add(scoreEntity(result, components.get("DISLIKE_PENALTY"),
                    score.dislikePenalty(), MealPlanScoring.DISLIKE_WEIGHT));
        }
        recommendationScores.saveAll(scoreEntities);

        MealPlan plan = new MealPlan(
                identity.internalId(),
                "K\u1ebf ho\u1ea1ch \u0103n " + inputs.startDate() + " - " + endDate,
                inputs.startDate(), endDate, inputs.slotCodes().size(),
                inputs.defaultServings(), requestEntity.internalId());
        mealPlans.saveAndFlush(plan);
        List<MealPlanEntry> entries = new ArrayList<>();
        for (int index = 0; index < selections.size(); index++) {
            Selection selection = selections.get(index);
            entries.add(new MealPlanEntry(
                    plan.internalId(), selection.planDate(), selection.slot().internalId(),
                    1, selection.candidate().internalId(),
                    BigDecimal.valueOf(inputs.defaultServings()),
                    resultEntities.get(index).internalId()));
        }
        mealPlanEntries.saveAllAndFlush(entries);

        RecommendationStatus finalStatus = warnings.isEmpty()
                ? RecommendationStatus.SUCCEEDED : RecommendationStatus.DEGRADED;
        LocalDateTime completedAt = databaseTimestamp(clock);
        requestEntity.complete(
                finalStatus,
                completedAt,
                durationMillis(startedAt, completedAt),
                null);
        recommendationRequests.saveAndFlush(requestEntity);

        return new MealPlanResponse.Generation(
                detail(plan), List.copyOf(warnings));
    }

    @Transactional(readOnly = true)
    public List<MealPlanResponse.Item> list(UUID authenticatedPublicId) {
        CurrentUserIdentity identity = currentUserService.getIdentity(authenticatedPublicId);
        return mealPlans.findByUserIdOrderByCreatedAtDescIdDesc(identity.internalId())
                .stream().map(MealPlanService::item).toList();
    }

    @Transactional(readOnly = true)
    public MealPlanResponse.Detail get(UUID authenticatedPublicId, UUID publicId) {
        if (publicId == null) {
            throw new MealPlanException(MealPlanFailure.INVALID_REQUEST);
        }
        CurrentUserIdentity identity = currentUserService.getIdentity(authenticatedPublicId);
        MealPlan plan = mealPlans.findByPublicIdAndUserId(
                        MealPlanningIds.uuidToBytes(publicId), identity.internalId())
                .orElseThrow(() -> new MealPlanException(MealPlanFailure.MEAL_PLAN_NOT_FOUND));
        return detail(plan);
    }

    @Transactional
    public MealPlanResponse.Detail accept(UUID authenticatedPublicId, UUID publicId) {
        if (publicId == null) {
            throw new MealPlanException(MealPlanFailure.INVALID_REQUEST);
        }
        CurrentUserIdentity identity = currentUserService.getIdentity(authenticatedPublicId);
        MealPlan plan = mealPlans.findByPublicIdAndUserId(
                        MealPlanningIds.uuidToBytes(publicId), identity.internalId())
                .orElseThrow(() -> new MealPlanException(MealPlanFailure.MEAL_PLAN_NOT_FOUND));
        try {
            plan.accept(databaseTimestamp(clock));
        } catch (IllegalStateException exception) {
            throw new MealPlanException(MealPlanFailure.INVALID_PLAN_STATE);
        }
        mealPlans.saveAndFlush(plan);
        return detail(plan);
    }

    private ScoredCandidate scoreCandidate(
            RecipeRecommendationCandidate candidate,
            String slotCode,
            LocalDate planDate,
            GenerationInputs inputs,
            VirtualPantry pantry,
            Map<String, MeasurementUnitReferenceSnapshot> units,
            Set<String> dietaryCodes,
            Set<String> allergenCodes,
            Map<Long, List<IngredientAllergenSnapshot>> allergenFacts,
            Map<Long, String> allergenCodeById,
            Map<UUID, DislikedIngredientStrength> dislikes,
            Set<UUID> usedRecipes,
            Map<String, BigDecimal> mealTargets) {

        if (!candidate.mealSlots().isEmpty()
                && candidate.mealSlots().stream().noneMatch(slot -> slot.code().equals(slotCode))) {
            return null;
        }
        if (candidate.totalMinutes() != null
                && inputs.maxMinutesPerMeal() != null
                && candidate.totalMinutes() > inputs.maxMinutesPerMeal()) {
            return null;
        }
        if (violatesDietaryConstraint(candidate, dietaryCodes)
                || violatesAllergens(candidate, allergenCodes, allergenFacts, allergenCodeById)) {
            return null;
        }
        long avoidCount = 0;
        long dislikeCount = 0;
        long requiredCount = 0;
        for (RecipeRecommendationCandidate.Ingredient line : candidate.ingredients()) {
            if (!line.optional()) {
                requiredCount++;
            }
            DislikedIngredientStrength strength = dislikes.get(line.publicId());
            if (strength == DislikedIngredientStrength.AVOID) {
                avoidCount++;
            } else if (strength == DislikedIngredientStrength.DISLIKE && !line.optional()) {
                dislikeCount++;
            }
        }
        if (avoidCount > 0) {
            return null;
        }

        BigDecimal pantryCoverage = pantry.coverage(candidate, inputs.defaultServings(), planDate, units);
        BigDecimal expiryUrgency = pantry.expiryUrgency(candidate, inputs.defaultServings(), planDate, units);
        BigDecimal nutritionFit = nutritionFit(candidate.nutritionPerServing(), mealTargets);
        BigDecimal preferenceMatch = preferenceMatch(candidate.tagCodes(), dietaryCodes);
        BigDecimal variety = usedRecipes.contains(candidate.publicId())
                ? BigDecimal.ZERO : BigDecimal.ONE;
        BigDecimal effortFit = candidate.totalMinutes() == null
                ? BigDecimal.ONE : BigDecimal.ONE;
        BigDecimal dislikePenalty = requiredCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(dislikeCount)
                        .divide(BigDecimal.valueOf(requiredCount), 12, RoundingMode.HALF_UP);
        return new ScoredCandidate(candidate, MealPlanScoring.score(
                pantryCoverage, nutritionFit, expiryUrgency, preferenceMatch,
                variety, effortFit, dislikePenalty));
    }

    private static boolean violatesDietaryConstraint(
            RecipeRecommendationCandidate candidate, Set<String> dietaryCodes) {
        for (String code : EXACT_DIETARY_TAGS) {
            if (dietaryCodes.contains(code) && !candidate.tagCodes().contains(code)) {
                return true;
            }
        }
        return false;
    }

    private static boolean violatesAllergens(
            RecipeRecommendationCandidate candidate,
            Set<String> allergenCodes,
            Map<Long, List<IngredientAllergenSnapshot>> facts,
            Map<Long, String> codeById) {
        if (allergenCodes.isEmpty()) {
            return false;
        }
        for (RecipeRecommendationCandidate.Ingredient line : candidate.ingredients()) {
            for (IngredientAllergenSnapshot fact : facts.getOrDefault(line.internalId(), List.of())) {
                String code = codeById.get(fact.allergenId());
                if (code != null && allergenCodes.contains(code)
                        && (fact.presence() == IngredientAllergenPresence.CONTAINS
                        || fact.presence() == IngredientAllergenPresence.MAY_CONTAIN)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BigDecimal preferenceMatch(List<String> tags, Set<String> preferences) {
        List<String> soft = preferences.stream()
                .filter(code -> code.equals("HIGH_PROTEIN") || code.equals("MEDITERRANEAN"))
                .toList();
        if (soft.isEmpty()) {
            return new BigDecimal("0.5000");
        }
        long matches = soft.stream().filter(tags::contains).count();
        return BigDecimal.valueOf(matches)
                .divide(BigDecimal.valueOf(soft.size()), 12, RoundingMode.HALF_UP);
    }

    private static BigDecimal nutritionFit(
            Map<String, BigDecimal> actual, Map<String, BigDecimal> targets) {
        if (targets.isEmpty()) {
            return new BigDecimal("0.5000");
        }
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (String code : CORE_NUTRIENTS) {
            BigDecimal target = targets.get(code);
            BigDecimal value = actual.get(code);
            if (target == null || value == null || target.signum() <= 0) {
                continue;
            }
            BigDecimal difference = value.subtract(target).abs()
                    .divide(target, 12, RoundingMode.HALF_UP);
            total = total.add(BigDecimal.ONE.subtract(difference).max(BigDecimal.ZERO));
            count++;
        }
        return count == 0
                ? new BigDecimal("0.5000")
                : total.divide(BigDecimal.valueOf(count), 12, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private Map<String, BigDecimal> mealNutritionTargets(UUID publicId, int mealsPerDay) {
        Optional<NutritionTargetView> target = nutritionTargets.findCurrentTarget(publicId);
        if (target.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (NutritionTargetValueView value : target.get().nutrientValues()) {
            if (CORE_NUTRIENTS.contains(value.nutrientCode()) && value.targetAmount() != null) {
                result.put(value.nutrientCode(), value.targetAmount()
                        .divide(BigDecimal.valueOf(mealsPerDay), 12, RoundingMode.HALF_UP));
            }
        }
        return result;
    }

    private Map<String, AiScoreComponent> resolveScoreComponents() {
        Map<String, AiScoreComponent> result = new LinkedHashMap<>();
        for (AiScoreComponent component : scoreComponents.findAllByCodeIn(SCORE_CODES)) {
            if (component == null || component.id() == null || component.code() == null
                    || component.scaleMin() == null || component.scaleMax() == null
                    || component.scaleMin().compareTo(BigDecimal.ZERO) > 0
                    || component.scaleMax().compareTo(BigDecimal.ONE) < 0) {
                throw new MealPlanException(MealPlanFailure.CORRUPTED_MEAL_PLAN_DATA);
            }
            result.put(component.code(), component);
        }
        if (result.size() != SCORE_CODES.size()) {
            throw new MealPlanException(MealPlanFailure.CORRUPTED_MEAL_PLAN_DATA);
        }
        return result;
    }

    private static RecommendationResultScore scoreEntity(
            RecommendationResult result, AiScoreComponent component,
            BigDecimal value, BigDecimal weight) {
        if (component == null || result.internalId() == null) {
            throw new MealPlanException(MealPlanFailure.CORRUPTED_MEAL_PLAN_DATA);
        }
        return new RecommendationResultScore(
                result.internalId(), component.id(), value.setScale(4, RoundingMode.HALF_UP), weight);
    }

    private MealPlanResponse.Detail detail(MealPlan plan) {
        List<MealPlanEntry> entries = mealPlanEntries.findByMealPlanId(plan.internalId());
        Set<Long> slotIds = new LinkedHashSet<>();
        Set<Long> recipeIds = new LinkedHashSet<>();
        Set<Long> resultIds = new LinkedHashSet<>();
        for (MealPlanEntry entry : entries) {
            slotIds.add(entry.mealSlotTypeId());
            recipeIds.add(entry.recipeId());
            if (entry.sourceResultId() != null) resultIds.add(entry.sourceResultId());
        }
        Map<Long, RecipeRecommendationCandidate.MealSlot> slots = recipeReferences
                .resolveMealSlotsByInternalIds(slotIds);
        Map<Long, RecipeRecommendationCandidate.RecipeReference> recipes = recipeReferences
                .resolveRecipeReferences(recipeIds);
        Map<Long, RecommendationResult> results = recommendationResults.findAllByIdIn(resultIds)
                .stream().collect(java.util.stream.Collectors.toMap(
                        RecommendationResult::internalId, value -> value));
        if (slots.size() != slotIds.size() || recipes.size() != recipeIds.size()
                || results.size() != resultIds.size()) {
            throw new MealPlanException(MealPlanFailure.CORRUPTED_MEAL_PLAN_DATA);
        }
        for (RecommendationResult result : results.values()) {
            if (!Objects.equals(result.requestId(), plan.sourceRequestId())) {
                throw new MealPlanException(MealPlanFailure.CORRUPTED_MEAL_PLAN_DATA);
            }
        }
        List<MealPlanEntry> ordered = entries.stream()
                .sorted(Comparator.comparing(MealPlanEntry::planDate)
                        .thenComparing(entry -> slots.get(entry.mealSlotTypeId()).displayOrder())
                        .thenComparing(MealPlanEntry::positionInSlot))
                .toList();
        List<MealPlanResponse.Entry> entryResponses = ordered.stream().map(entry -> {
            RecipeRecommendationCandidate.MealSlot slot = slots.get(entry.mealSlotTypeId());
            RecipeRecommendationCandidate.RecipeReference recipe = recipes.get(entry.recipeId());
            RecommendationResult result = results.get(entry.sourceResultId());
            return new MealPlanResponse.Entry(
                    entry.planDate(), slot.code(), slot.displayName(),
                    Integer.valueOf(entry.positionInSlot()), recipe.publicId(), recipe.title(),
                    entry.servings(), entry.provenance(), entry.consumptionStatus(),
                    result == null ? null : result.totalScore(),
                    result == null ? null : result.explanation());
        }).toList();
        return new MealPlanResponse.Detail(
                plan.publicId(), plan.title(), plan.startDate(), plan.endDate(),
                dayCount(plan), integer(plan.mealsPerDayTarget()), integer(plan.defaultServings()),
                plan.status(), plan.acceptedAt(), plan.createdAt(), entryResponses);
    }

    private static MealPlanResponse.Item item(MealPlan plan) {
        return new MealPlanResponse.Item(
                plan.publicId(), plan.title(), plan.startDate(), plan.endDate(),
                dayCount(plan), integer(plan.mealsPerDayTarget()), integer(plan.defaultServings()),
                plan.status(), plan.createdAt());
    }

    private static int dayCount(MealPlan plan) {
        return plan.dayCount() == null
                ? (int) (java.time.temporal.ChronoUnit.DAYS.between(plan.startDate(), plan.endDate()) + 1)
                : plan.dayCount();
    }

    private static Integer integer(Byte value) { return value == null ? null : value.intValue(); }

    private static String explanation(RecommendationScore score) {
        if (score.pantryCoverage().compareTo(new BigDecimal("0.7500")) >= 0) {
            return "\u01afu ti\u00ean v\u00ec s\u1eed d\u1ee5ng nhi\u1ec1u nguy\u00ean li\u1ec7u \u0111ang c\u00f3 trong t\u1ee7.";
        }
        if (score.nutritionFit().compareTo(new BigDecimal("0.7500")) >= 0) {
            return "\u01afu ti\u00ean v\u00ec ph\u00f9 h\u1ee3p v\u1edbi m\u1ee5c ti\u00eau dinh d\u01b0\u1ee1ng hi\u1ec7n c\u00f3.";
        }
        if (score.expiryUrgency().compareTo(new BigDecimal("0.7500")) >= 0) {
            return "\u01afu ti\u00ean v\u00ec c\u00f3 nguy\u00ean li\u1ec7u s\u1eafp h\u1ebft h\u1ea1n c\u1ea7n d\u00f9ng tr\u01b0\u1edbc.";
        }
        return "\u0110\u01b0\u1ee3c ch\u1ecdn theo \u0111i\u1ec3m RULE_BASED_V1 v\u00e0 th\u1ee9 t\u1ef1 \u01b0u ti\u00ean x\u00e1c \u0111\u1ecbnh.";
    }

    private static int durationMillis(LocalDateTime start, LocalDateTime end) {
        long millis = Duration.between(start, end).toMillis();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, millis));
    }

    private static LocalDateTime databaseTimestamp(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static String constraintsHash(GenerationInputs inputs) {
        String normalized = inputs.startDate() + "|" + inputs.days() + "|"
                + String.join(",", inputs.slotCodes()) + "|" + inputs.defaultServings()
                + "|" + (inputs.maxMinutesPerMeal() == null ? "" : inputs.maxMinutesPerMeal());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void rejectUnsupportedDietaryConstraints(
            List<UserDietaryPreferenceResponse> preferences) {
        for (UserDietaryPreferenceResponse preference : preferences) {
            if (preference.isExclusionary()
                    && UNSUPPORTED_DIETARY_CODES.contains(preference.code())) {
                throw new MealPlanException(MealPlanFailure.UNSUPPORTED_DIETARY_CONSTRAINT);
            }
        }
    }

    private static GenerationInputs validateRequest(MealPlanGenerationRequest request) {
        if (request == null || request.startDate() == null
                || request.days() == null || request.days() < 1 || request.days() > 7) {
            throw new MealPlanException(MealPlanFailure.INVALID_REQUEST);
        }
        int servings = request.defaultServings() == null ? 1 : request.defaultServings();
        if (servings < 1 || servings > 50
                || (request.maxMinutesPerMeal() != null
                && (request.maxMinutesPerMeal() <= 0
                || request.maxMinutesPerMeal() > 10080))) {
            throw new MealPlanException(MealPlanFailure.INVALID_REQUEST);
        }
        List<String> slots = request.mealSlotCodes() == null
                ? DEFAULT_SLOT_CODES
                : request.mealSlotCodes().stream()
                        .map(value -> value == null ? null : value.trim())
                        .toList();
        if (slots.isEmpty() || slots.stream().anyMatch(value -> value == null || value.isBlank())
                || new HashSet<>(slots).size() != slots.size()) {
            throw new MealPlanException(MealPlanFailure.INVALID_REQUEST);
        }
        return new GenerationInputs(request.startDate(), request.days(), List.copyOf(slots),
                servings, request.maxMinutesPerMeal());
    }

    private record GenerationInputs(
            LocalDate startDate,
            int days,
            List<String> slotCodes,
            int defaultServings,
            Integer maxMinutesPerMeal) {
    }

    private record Selection(
            LocalDate planDate,
            RecipeRecommendationCandidate.MealSlot slot,
            RecipeRecommendationCandidate candidate,
            RecommendationScore score) {
    }

    private record ScoredCandidate(
            RecipeRecommendationCandidate candidate,
            RecommendationScore score) {
    }
}
