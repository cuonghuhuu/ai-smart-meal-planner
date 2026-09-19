package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.admin.AdminException;
import com.smartmealplanner.admin.AdminFailure;
import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceSnapshot;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recipe-owned write orchestration for the minimal administrator workflow. */
@Service
public class RecipeAdminService {

    private static final Comparator<RecipeAdminDraft.Step> STEP_ORDER =
            Comparator.comparing(RecipeAdminDraft.Step::stepNumber);

    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final RecipeStepRepository steps;
    private final RecipeTagRepository tags;
    private final RecipeTagAssignmentRepository tagAssignments;
    private final MealSlotTypeRepository mealSlots;
    private final RecipeMealSlotTypeRepository mealSlotAssignments;
    private final IngredientReferenceQueryService ingredientReferences;
    private final MeasurementUnitReferenceQueryService unitReferences;
    private final RecipeNutritionComputationService nutrition;
    private final RecipeCatalogService catalog;
    private final CurrentUserService currentUsers;
    private final Clock clock;

    @Autowired
    public RecipeAdminService(
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            RecipeStepRepository steps,
            RecipeTagRepository tags,
            RecipeTagAssignmentRepository tagAssignments,
            MealSlotTypeRepository mealSlots,
            RecipeMealSlotTypeRepository mealSlotAssignments,
            IngredientReferenceQueryService ingredientReferences,
            MeasurementUnitReferenceQueryService unitReferences,
            RecipeNutritionComputationService nutrition,
            RecipeCatalogService catalog,
            CurrentUserService currentUsers) {
        this(
                recipes,
                ingredients,
                steps,
                tags,
                tagAssignments,
                mealSlots,
                mealSlotAssignments,
                ingredientReferences,
                unitReferences,
                nutrition,
                catalog,
                currentUsers,
                Clock.systemUTC());
    }

    RecipeAdminService(
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            RecipeStepRepository steps,
            RecipeTagRepository tags,
            RecipeTagAssignmentRepository tagAssignments,
            MealSlotTypeRepository mealSlots,
            RecipeMealSlotTypeRepository mealSlotAssignments,
            IngredientReferenceQueryService ingredientReferences,
            MeasurementUnitReferenceQueryService unitReferences,
            RecipeNutritionComputationService nutrition,
            RecipeCatalogService catalog,
            CurrentUserService currentUsers,
            Clock clock) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.steps = steps;
        this.tags = tags;
        this.tagAssignments = tagAssignments;
        this.mealSlots = mealSlots;
        this.mealSlotAssignments = mealSlotAssignments;
        this.ingredientReferences = ingredientReferences;
        this.unitReferences = unitReferences;
        this.nutrition = nutrition;
        this.catalog = catalog;
        this.currentUsers = currentUsers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecipeAdminPage list(
            String query,
            String status,
            int page,
            int size) {
        return catalog.getAdminRecipes(query, parseStatus(status), page, size);
    }

    @Transactional(readOnly = true)
    public RecipeDetailView get(UUID publicId) {
        return catalog.getAdminRecipe(publicId);
    }

    @Transactional
    public RecipeDetailView create(
            UUID adminPublicId,
            RecipeAdminDraft draft) {

        CurrentUserIdentity admin = currentUsers.getIdentity(adminPublicId);
        ResolvedDraft resolved = resolveAndValidate(draft);

        Recipe recipe;
        try {
            recipe = new Recipe(
                    UUID.randomUUID(),
                    draft.title(),
                    draft.slug(),
                    draft.servings(),
                    draft.prepMinutes(),
                    draft.cookMinutes(),
                    draft.difficulty(),
                    draft.instructionsNote(),
                    draft.imageUrl(),
                    admin.internalId(),
                    RecipeSource.CURATED,
                    null,
                    RecipeStatus.DRAFT,
                    null,
                    null);
            recipe.setSummary(draft.summary());
            recipe = recipes.saveAndFlush(recipe);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }

        if (recipe.internalId() == null) {
            throw corrupted();
        }
        replaceChildren(recipe.internalId(), resolved);
        return catalog.getAdminRecipe(recipe.publicId());
    }

    @Transactional
    public RecipeDetailView update(
            UUID publicId,
            RecipeAdminDraft draft) {

        if (publicId == null) {
            throw invalidRequest();
        }
        ResolvedDraft resolved = resolveAndValidate(draft);
        Recipe recipe = recipes.findByPublicIdForUpdate(RecipeIds.uuidToBytes(publicId))
                .orElseThrow(() -> notFound());
        ensureEditableDraft(recipe);
        ensureSlugAvailable(draft.slug(), recipe);

        try {
            recipe.applyAdminDraftUpdate(
                    draft.title(),
                    draft.slug(),
                    draft.summary(),
                    draft.servings(),
                    draft.prepMinutes(),
                    draft.cookMinutes(),
                    draft.difficulty(),
                    draft.instructionsNote(),
                    draft.imageUrl());
            recipes.saveAndFlush(recipe);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        } catch (IllegalStateException exception) {
            throw invalidLifecycle();
        }

        replaceChildren(recipe.internalId(), resolved);
        return catalog.getAdminRecipe(recipe.publicId());
    }

    @Transactional
    public RecipeDetailView publish(UUID publicId) {
        if (publicId == null) {
            throw invalidRequest();
        }
        Recipe recipe = recipes.findByPublicIdForUpdate(RecipeIds.uuidToBytes(publicId))
                .orElseThrow(() -> notFound());
        ensureEditableDraft(recipe);

        List<RecipeIngredient> recipeIngredients = ingredients
                .findByRecipeIdOrderByLineNumberAsc(recipe.internalId());
        List<RecipeStep> recipeSteps = steps
                .findByRecipeIdOrderByStepNumberAsc(recipe.internalId());
        validatePublishable(recipeIngredients, recipeSteps);

        // P9B is the only nutrition calculation authority. Any failure is
        // deliberately allowed to escape this transaction.
        nutrition.recompute(recipe.publicId());
        try {
            recipe.publish(databaseTimestamp());
            recipes.saveAndFlush(recipe);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        } catch (IllegalStateException exception) {
            throw invalidLifecycle();
        }

        return catalog.getAdminRecipe(recipe.publicId());
    }

    @Transactional
    public RecipeDetailView archive(UUID publicId) {
        if (publicId == null) {
            throw invalidRequest();
        }
        Recipe recipe = recipes.findByPublicIdForUpdate(RecipeIds.uuidToBytes(publicId))
                .orElseThrow(() -> notFound());
        try {
            recipe.archive(databaseTimestamp());
            recipes.saveAndFlush(recipe);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        } catch (IllegalStateException exception) {
            throw invalidLifecycle();
        }
        return catalog.getAdminRecipe(recipe.publicId());
    }

    private ResolvedDraft resolveAndValidate(RecipeAdminDraft draft) {
        if (draft == null || draft.difficulty() == null) {
            throw invalidRequest();
        }
        validateMetadata(draft);

        List<RecipeAdminDraft.Ingredient> draftIngredients = safe(
                draft.ingredients());
        Set<UUID> ingredientIds = new HashSet<>();
        for (RecipeAdminDraft.Ingredient ingredient : draftIngredients) {
            if (ingredient == null || ingredient.ingredientPublicId() == null
                    || !ingredientIds.add(ingredient.ingredientPublicId())) {
                throw invalidRequest();
            }
            validateIngredient(ingredient);
        }
        Map<UUID, IngredientReferenceSnapshot> ingredientByPublicId;
        try {
            ingredientByPublicId = ingredientReferences.resolveActiveByPublicIds(
                    ingredientIds);
        } catch (RuntimeException exception) {
            throw corrupted();
        }
        if (ingredientByPublicId.size() != ingredientIds.size()) {
            throw invalidRequest();
        }

        Map<String, MeasurementUnitReferenceSnapshot> unitsByCode = resolveUnits(
                draftIngredients);
        Map<String, RecipeTag> tagsByCode = resolveTags(safe(draft.tagCodes()));
        Map<String, MealSlotType> mealSlotsByCode = resolveMealSlots(
                safe(draft.mealSlotCodes()));

        validateSteps(safe(draft.steps()));
        return new ResolvedDraft(
                draftIngredients,
                safe(draft.steps()),
                ingredientByPublicId,
                unitsByCode,
                tagsByCode,
                mealSlotsByCode);
    }

    private Map<String, MeasurementUnitReferenceSnapshot> resolveUnits(
            List<RecipeAdminDraft.Ingredient> draftIngredients) {
        Set<String> codes = new HashSet<>();
        for (RecipeAdminDraft.Ingredient ingredient : draftIngredients) {
            String code = normalizeUnit(ingredient.unitCode());
            if (code != null) {
                codes.add(code);
            }
        }
        Map<String, MeasurementUnitReferenceSnapshot> units;
        try {
            units = unitReferences.resolveByCodes(codes);
        } catch (RuntimeException exception) {
            throw corrupted();
        }
        if (units.size() != codes.size()) {
            throw invalidRequest();
        }
        return units;
    }

    private Map<String, RecipeTag> resolveTags(List<String> rawCodes) {
        Set<String> codes = normalizedUniqueCodes(rawCodes);
        List<RecipeTag> values = tags.findAllByCodeIn(codes);
        Map<String, RecipeTag> result = new LinkedHashMap<>();
        for (RecipeTag tag : values) {
            if (tag == null || tag.internalId() == null || tag.code() == null
                    || tag.displayName() == null || tag.tagKind() == null) {
                throw corrupted();
            }
            result.put(tag.code(), tag);
        }
        if (result.size() != codes.size()) {
            throw invalidRequest();
        }
        return result;
    }

    private Map<String, MealSlotType> resolveMealSlots(List<String> rawCodes) {
        Set<String> codes = normalizedUniqueCodes(rawCodes);
        List<MealSlotType> values = mealSlots.findAllByCodeIn(codes);
        Map<String, MealSlotType> result = new LinkedHashMap<>();
        for (MealSlotType slot : values) {
            if (slot == null || slot.internalId() == null || slot.code() == null
                    || slot.displayName() == null || slot.displayOrder() == null) {
                throw corrupted();
            }
            result.put(slot.code(), slot);
        }
        if (result.size() != codes.size()) {
            throw invalidRequest();
        }
        return result;
    }

    private void replaceChildren(Long recipeId, ResolvedDraft resolved) {
        tagAssignments.deleteByRecipeId(recipeId);
        mealSlotAssignments.deleteByRecipeId(recipeId);
        steps.deleteByRecipeId(recipeId);
        ingredients.deleteByRecipeId(recipeId);
        ingredients.flush();

        List<RecipeIngredient> ingredientRows = new ArrayList<>();
        for (int index = 0; index < resolved.ingredients().size(); index++) {
            RecipeAdminDraft.Ingredient input = resolved.ingredients().get(index);
            IngredientReferenceSnapshot ingredient = resolved.ingredientsByPublicId()
                    .get(input.ingredientPublicId());
            MeasurementUnitReferenceSnapshot unit = input.unitCode() == null
                    ? null
                    : resolved.unitsByCode().get(normalizeUnit(input.unitCode()));
            ingredientRows.add(new RecipeIngredient(
                    recipeId,
                    index + 1,
                    ingredient.internalId(),
                    null,
                    input.quantity(),
                    unit == null ? null : unit.internalId(),
                    input.preparationNote(),
                    input.optional(),
                    input.allowSubstitution(),
                    input.sectionLabel()));
        }

        List<RecipeStep> stepRows = resolved.steps().stream()
                .sorted(STEP_ORDER)
                .map(step -> new RecipeStep(
                        recipeId,
                        step.stepNumber(),
                        step.instruction(),
                        step.durationMinutes()))
                .toList();
        List<RecipeTagAssignment> tagRows = resolved.tagsByCode().values().stream()
                .sorted(Comparator.comparing(RecipeTag::code))
                .map(tag -> new RecipeTagAssignment(recipeId, tag.internalId()))
                .toList();
        List<RecipeMealSlotType> mealSlotRows = resolved.mealSlotsByCode().values()
                .stream()
                .sorted(Comparator.comparing(MealSlotType::code))
                .map(slot -> new RecipeMealSlotType(recipeId, slot.internalId()))
                .toList();

        ingredients.saveAll(ingredientRows);
        steps.saveAll(stepRows);
        tagAssignments.saveAll(tagRows);
        mealSlotAssignments.saveAll(mealSlotRows);
        ingredients.flush();
    }

    private static void validateIngredient(RecipeAdminDraft.Ingredient ingredient) {
        String unit = normalizeUnit(ingredient.unitCode());
        if ((ingredient.quantity() == null) != (unit == null)
                || (ingredient.quantity() != null
                && ingredient.quantity().signum() <= 0)
                || (ingredient.quantity() != null
                && (ingredient.quantity().scale() > 4
                || ingredient.quantity().precision() > 12))
                || tooLong(ingredient.preparationNote(), 200)
                || tooLong(ingredient.sectionLabel(), 80)) {
            throw invalidRequest();
        }
    }

    private static void validateSteps(List<RecipeAdminDraft.Step> values) {
        Set<Integer> numbers = new HashSet<>();
        for (RecipeAdminDraft.Step step : values) {
            if (step == null || step.stepNumber() == null
                    || step.stepNumber() < 1
                    || !numbers.add(step.stepNumber())
                    || step.instruction() == null
                    || step.instruction().isBlank()
                    || step.instruction().length() > 2000
                    || (step.durationMinutes() != null
                    && (step.durationMinutes() < 0
                    || step.durationMinutes() > 10080))) {
                throw invalidRequest();
            }
        }
        for (int expected = 1; expected <= values.size(); expected++) {
            if (!numbers.contains(expected)) {
                throw invalidRequest();
            }
        }
    }

    private static void validatePublishable(
            List<RecipeIngredient> ingredients,
            List<RecipeStep> steps) {
        if (ingredients.isEmpty() || steps.isEmpty()) {
            throw invalidRequest();
        }
        try {
            Recipe.validateIngredientLines(ingredients);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
        Set<Integer> stepNumbers = new HashSet<>();
        for (RecipeStep step : steps) {
            if (step == null || step.stepNumber() == null
                    || !stepNumbers.add(step.stepNumber().intValue())
                    || step.instruction() == null || step.instruction().isBlank()) {
                throw invalidRequest();
            }
        }
        for (int expected = 1; expected <= steps.size(); expected++) {
            if (!stepNumbers.contains(expected)) {
                throw invalidRequest();
            }
        }
    }

    private void ensureEditableDraft(Recipe recipe) {
        if (recipe == null || recipe.internalId() == null) {
            throw corrupted();
        }
        if (recipe.status() != RecipeStatus.DRAFT) {
            throw invalidLifecycle();
        }
    }

    private static void validateMetadata(RecipeAdminDraft draft) {
        if (draft.title() == null || draft.title().isBlank()
                || draft.title().length() > 200
                || draft.slug() == null || draft.slug().isBlank()
                || draft.slug().length() > 220
                || tooLong(draft.summary(), 500)
                || draft.servings() == null || draft.servings() < 1
                || draft.servings() > 100
                || invalidMinutes(draft.prepMinutes())
                || invalidMinutes(draft.cookMinutes())
                || tooLong(draft.instructionsNote(), 1000)
                || tooLong(draft.imageUrl(), 500)) {
            throw invalidRequest();
        }
    }

    private static boolean invalidMinutes(Integer value) {
        return value != null && (value < 0 || value > 10080);
    }

    private void ensureSlugAvailable(String slug, Recipe current) {
        Recipe existing = recipes.findBySlug(slug).orElse(null);
        if (existing != null && !existing.internalId().equals(current.internalId())) {
            throw invalidRequest();
        }
    }

    private static RecipeStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RecipeStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    private static Set<String> normalizedUniqueCodes(List<String> rawCodes) {
        Set<String> codes = new HashSet<>();
        for (String raw : rawCodes) {
            String code = normalizeCode(raw);
            if (code == null || !codes.add(code)) {
                throw invalidRequest();
            }
        }
        return codes;
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeUnit(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean tooLong(String value, int max) {
        return value != null && value.length() > max;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private LocalDateTime databaseTimestamp() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static AdminException invalidRequest() {
        return new AdminException(AdminFailure.INVALID_REQUEST);
    }

    private static AdminException invalidLifecycle() {
        return new AdminException(AdminFailure.INVALID_LIFECYCLE);
    }

    private static AdminException notFound() {
        return new AdminException(AdminFailure.RESOURCE_NOT_FOUND);
    }

    private static AdminException corrupted() {
        return new AdminException(AdminFailure.CORRUPTED_RECIPE_DATA);
    }

    private record ResolvedDraft(
            List<RecipeAdminDraft.Ingredient> ingredients,
            List<RecipeAdminDraft.Step> steps,
            Map<UUID, IngredientReferenceSnapshot> ingredientsByPublicId,
            Map<String, MeasurementUnitReferenceSnapshot> unitsByCode,
            Map<String, RecipeTag> tagsByCode,
            Map<String, MealSlotType> mealSlotsByCode) {
    }
}
