package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceSnapshot;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recipe-owned, offline import boundary for the project-curated catalog.
 * Preflight resolves the whole document before the write transaction proceeds.
 */
@Service
public class RecipeImportService {

    private static final Comparator<RecipeImportIngredient> INGREDIENT_ORDER =
            Comparator.comparing(RecipeImportIngredient::lineNumber);
    private static final Comparator<RecipeImportStep> STEP_ORDER =
            Comparator.comparing(RecipeImportStep::stepNumber);

    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final RecipeStepRepository steps;
    private final RecipeTagRepository tags;
    private final RecipeTagAssignmentRepository tagAssignments;
    private final MealSlotTypeRepository mealSlots;
    private final RecipeMealSlotTypeRepository mealSlotAssignments;
    private final IngredientReferenceQueryService ingredientReferences;
    private final MeasurementUnitReferenceQueryService unitReferences;
    private final RecipeNutritionSnapshotRepository nutritionSnapshots;
    private final RecipeNutritionComputationService nutrition;
    private final Clock clock;

    @Autowired
    public RecipeImportService(
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            RecipeStepRepository steps,
            RecipeTagRepository tags,
            RecipeTagAssignmentRepository tagAssignments,
            MealSlotTypeRepository mealSlots,
            RecipeMealSlotTypeRepository mealSlotAssignments,
            IngredientReferenceQueryService ingredientReferences,
            MeasurementUnitReferenceQueryService unitReferences,
            RecipeNutritionSnapshotRepository nutritionSnapshots,
            RecipeNutritionComputationService nutrition) {
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
                nutritionSnapshots,
                nutrition,
                Clock.systemUTC());
    }

    RecipeImportService(
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            RecipeStepRepository steps,
            RecipeTagRepository tags,
            RecipeTagAssignmentRepository tagAssignments,
            MealSlotTypeRepository mealSlots,
            RecipeMealSlotTypeRepository mealSlotAssignments,
            IngredientReferenceQueryService ingredientReferences,
            MeasurementUnitReferenceQueryService unitReferences,
            RecipeNutritionSnapshotRepository nutritionSnapshots,
            RecipeNutritionComputationService nutrition,
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
        this.nutritionSnapshots = nutritionSnapshots;
        this.nutrition = nutrition;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecipeImportValidationReport validateDocument(
            RecipeImportDocument document) {
        return preflight(document).report();
    }

    @Transactional(rollbackFor = RecipeImportValidationException.class)
    public RecipeImportReport importDocument(RecipeImportDocument document) {
        Preflight preflight = preflight(document);
        if (!preflight.report().isValid()) {
            throw new RecipeImportValidationException(preflight.report().errors());
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int ingredientLinesWritten = 0;
        int stepsWritten = 0;
        int snapshotsComputed = 0;
        List<RecipeImportIssue> warnings = new ArrayList<>(preflight.report().warnings());

        for (RecipeImportRecipe input : document.recipes()) {
            String sourceReference = requiredText(input.sourceReference());
            Recipe existing = preflight.recipesBySourceReference()
                    .get(sourceReference);
            boolean isNew = existing == null;
            Integer previousServings = existing == null
                    ? null
                    : RecipeSmallInt.toInteger(existing.servings());

            Recipe recipe;
            boolean metadataChanged;
            if (isNew) {
                LocalDateTime publishedAt = LocalDateTime.now(clock);
                recipe = new Recipe(
                        UUID.randomUUID(),
                        requiredText(input.title()),
                        requiredText(input.slug()),
                        input.servings(),
                        input.prepMinutes(),
                        input.cookMinutes(),
                        input.difficulty(),
                        optionalText(input.instructionsNote()),
                        optionalText(input.imageUrl()),
                        null,
                        input.source(),
                        sourceReference,
                        RecipeStatus.PUBLISHED,
                        publishedAt,
                        null);
                recipe.setSummary(optionalText(input.summary()));
                recipe = recipes.saveAndFlush(recipe);
                metadataChanged = true;
                created++;
            } else {
                LocalDateTime publishedAt = existing.publishedAt() == null
                        ? LocalDateTime.now(clock)
                        : existing.publishedAt();
                metadataChanged = existing.applyImportedDefinition(
                        requiredText(input.title()),
                        requiredText(input.slug()),
                        optionalText(input.summary()),
                        input.servings(),
                        input.prepMinutes(),
                        input.cookMinutes(),
                        input.difficulty(),
                        optionalText(input.instructionsNote()),
                        optionalText(input.imageUrl()),
                        input.source(),
                        sourceReference,
                        publishedAt);
                recipe = existing;
                if (metadataChanged) {
                    recipe = recipes.saveAndFlush(recipe);
                }
            }

            if (recipe.internalId() == null) {
                throw new IllegalStateException("Persisted Recipe has no internal id");
            }

            Long recipeId = recipe.internalId();
            List<RecipeIngredient> oldIngredients = isNew
                    ? List.of()
                    : ingredients.findByRecipeIdOrderByLineNumberAsc(recipeId);
            List<RecipeStep> oldSteps = isNew
                    ? List.of()
                    : steps.findByRecipeIdOrderByStepNumberAsc(recipeId);
            List<RecipeTagAssignment> oldTags = isNew
                    ? List.of()
                    : tagAssignments.findByRecipeId(recipeId);
            List<RecipeMealSlotType> oldMealSlots = isNew
                    ? List.of()
                    : mealSlotAssignments.findByRecipeId(recipeId);

            boolean ingredientsChanged = isNew
                    || !sameIngredients(
                    input.ingredients(),
                    oldIngredients,
                    preflight.ingredientsByCode(),
                    preflight.unitsByCode());
            boolean stepsChanged = isNew
                    || !sameSteps(input.steps(), oldSteps);
            boolean tagsChanged = isNew
                    || !sameTags(input.tags(), oldTags, preflight.tagsByCode());
            boolean mealSlotsChanged = isNew
                    || !sameMealSlots(
                    input.mealSlots(),
                    oldMealSlots,
                    preflight.mealSlotsByCode());
            boolean childrenChanged = ingredientsChanged
                    || stepsChanged
                    || tagsChanged
                    || mealSlotsChanged;

            if (childrenChanged) {
                replaceChildren(
                        recipeId,
                        input,
                        preflight.ingredientsByCode(),
                        preflight.unitsByCode(),
                        preflight.tagsByCode(),
                        preflight.mealSlotsByCode(),
                        !isNew);
                ingredientLinesWritten += input.ingredients().size();
                stepsWritten += input.steps().size();
            }

            if (!isNew && !metadataChanged && !childrenChanged) {
                unchanged++;
            } else if (!isNew) {
                updated++;
            }

            boolean servingsChanged = !isNew
                    && !Objects.equals(previousServings, input.servings());
            boolean needsNutrition = isNew
                    || ingredientsChanged
                    || servingsChanged
                    || nutritionSnapshots.findCurrentByRecipeId(recipeId).isEmpty();
            if (needsNutrition) {
                RecipeNutritionComputationResult result = nutrition.recompute(
                        recipe.publicId());
                snapshotsComputed++;
                if (result.completenessRatio().compareTo(BigDecimal.ONE) < 0) {
                    warnings.add(new RecipeImportIssue(
                            RecipeImportIssueType.INCOMPLETE_NUTRITION,
                            input.sourceIdentifier(),
                            "nutrition",
                            "Resolved " + result.resolvedLineCount()
                                    + "/" + result.totalLineCount()
                                    + " ingredient lines; unresolved lines were excluded"));
                }
            }
        }

        return new RecipeImportReport(
                document.recipes().size(),
                created,
                updated,
                unchanged,
                ingredientLinesWritten,
                stepsWritten,
                snapshotsComputed,
                warnings);
    }

    private Preflight preflight(RecipeImportDocument document) {
        List<RecipeImportIssue> errors = new ArrayList<>();
        List<RecipeImportIssue> warnings = new ArrayList<>();
        if (document == null) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_DOCUMENT,
                    null,
                    null,
                    "Recipe import document is required"));
            return new Preflight(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    new RecipeImportValidationReport(errors, warnings));
        }

        if (blank(document.dataset())) {
            errors.add(issue(
                    RecipeImportIssueType.BLANK_DATASET,
                    null,
                    "dataset",
                    "dataset is required"));
        }
        if (blank(document.datasetVersion())) {
            errors.add(issue(
                    RecipeImportIssueType.BLANK_DATASET_VERSION,
                    null,
                    "datasetVersion",
                    "datasetVersion is required"));
        }
        List<RecipeImportRecipe> importedRecipes = document.recipes() == null
                ? List.of()
                : document.recipes();
        if (importedRecipes.isEmpty()) {
            errors.add(issue(
                    RecipeImportIssueType.NO_RECIPES,
                    null,
                    "recipes",
                    "At least one Recipe is required"));
        }

        Set<String> ingredientCodes = new LinkedHashSet<>();
        Set<String> unitCodes = new LinkedHashSet<>();
        Set<String> tagCodes = new LinkedHashSet<>();
        Set<String> mealSlotCodes = new LinkedHashSet<>();
        Set<String> sourceReferences = new LinkedHashSet<>();
        Set<String> slugs = new LinkedHashSet<>();
        for (RecipeImportRecipe input : importedRecipes) {
            if (input == null) {
                continue;
            }
            for (RecipeImportIngredient ingredient : input.ingredients()) {
                if (ingredient == null) {
                    continue;
                }
                String ingredientCode = normalize(ingredient.ingredientCode());
                if (ingredientCode != null) {
                    ingredientCodes.add(ingredientCode);
                }
                String unitCode = normalizeUnit(ingredient.unitCode());
                if (unitCode != null) {
                    unitCodes.add(unitCode);
                }
            }
            for (String tag : input.tags()) {
                String code = normalizeCode(tag);
                if (code != null) {
                    tagCodes.add(code);
                }
            }
            for (String mealSlot : input.mealSlots()) {
                String code = normalizeCode(mealSlot);
                if (code != null) {
                    mealSlotCodes.add(code);
                }
            }
            String sourceReference = normalize(input.sourceReference());
            if (sourceReference != null) {
                sourceReferences.add(sourceReference);
            }
            String slug = normalize(input.slug());
            if (slug != null) {
                slugs.add(slug);
            }
        }

        Map<String, IngredientReferenceSnapshot> ingredientsByCode =
                ingredientCodes.isEmpty()
                        ? Map.of()
                        : ingredientReferences.resolveByCodes(ingredientCodes);
        Map<String, MeasurementUnitReferenceSnapshot> unitsByCode =
                unitCodes.isEmpty()
                        ? Map.of()
                        : unitReferences.resolveByCodes(unitCodes);
        Map<String, RecipeTag> tagsByCode = indexTags(tagCodes);
        Map<String, MealSlotType> mealSlotsByCode = indexMealSlots(mealSlotCodes);
        Map<String, List<Recipe>> existingByReference = groupBySourceReference(
                sourceReferences);
        Map<String, List<Recipe>> existingBySlug = groupBySlug(slugs);
        Map<String, Recipe> recipesBySourceReference = new HashMap<>();

        Set<String> seenSourceIdentifiers = new HashSet<>();
        Set<String> seenSourceRefs = new HashSet<>();
        Set<String> seenSlugs = new HashSet<>();
        for (RecipeImportRecipe input : importedRecipes) {
            validateRecipe(
                    input,
                    ingredientsByCode,
                    unitsByCode,
                    tagsByCode,
                    mealSlotsByCode,
                    existingByReference,
                    existingBySlug,
                    seenSourceIdentifiers,
                    seenSourceRefs,
                    seenSlugs,
                    errors);

            if (input == null) {
                continue;
            }
            String sourceReference = normalize(input.sourceReference());
            List<Recipe> matches = sourceReference == null
                    ? List.of()
                    : existingByReference.getOrDefault(sourceReference, List.of());
            if (matches.size() == 1) {
                Recipe existing = matches.getFirst();
                if (existing.source() == RecipeSource.CURATED
                        && existing.createdByUserId() == null) {
                    recipesBySourceReference.put(sourceReference, existing);
                }
            }
        }

        return new Preflight(
                ingredientsByCode,
                unitsByCode,
                tagsByCode,
                mealSlotsByCode,
                recipesBySourceReference,
                new RecipeImportValidationReport(errors, warnings));
    }

    private void validateRecipe(
            RecipeImportRecipe input,
            Map<String, IngredientReferenceSnapshot> ingredientsByCode,
            Map<String, MeasurementUnitReferenceSnapshot> unitsByCode,
            Map<String, RecipeTag> tagsByCode,
            Map<String, MealSlotType> mealSlotsByCode,
            Map<String, List<Recipe>> existingByReference,
            Map<String, List<Recipe>> existingBySlug,
            Set<String> seenSourceIdentifiers,
            Set<String> seenSourceRefs,
            Set<String> seenSlugs,
            List<RecipeImportIssue> errors) {

        if (input == null) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_DOCUMENT,
                    null,
                    "recipes",
                    "Recipe entry is required"));
            return;
        }

        String sourceIdentifier = normalize(input.sourceIdentifier());
        if (sourceIdentifier == null) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_DOCUMENT,
                    null,
                    "sourceIdentifier",
                    "sourceIdentifier is required"));
        } else if (!seenSourceIdentifiers.add(sourceIdentifier)) {
            errors.add(issue(
                    RecipeImportIssueType.DUPLICATE_SOURCE_IDENTIFIER,
                    sourceIdentifier,
                    "sourceIdentifier",
                    "sourceIdentifier occurs more than once"));
        }

        String sourceReference = normalize(input.sourceReference());
        if (sourceReference == null || sourceReference.length() > 255) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_SOURCE_REFERENCE,
                    sourceIdentifier,
                    "sourceReference",
                    "sourceReference is required and must be at most 255 characters"));
        } else if (!seenSourceRefs.add(sourceReference)) {
            errors.add(issue(
                    RecipeImportIssueType.DUPLICATE_SOURCE_REFERENCE,
                    sourceIdentifier,
                    "sourceReference",
                    "sourceReference occurs more than once"));
        }

        String slug = normalize(input.slug());
        if (slug == null || slug.length() > 220) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_DOCUMENT,
                    sourceIdentifier,
                    "slug",
                    "slug is required and must be at most 220 characters"));
        } else if (!seenSlugs.add(slug)) {
            errors.add(issue(
                    RecipeImportIssueType.DUPLICATE_SLUG,
                    sourceIdentifier,
                    "slug",
                    "slug occurs more than once"));
        }

        if (blank(input.title()) || input.title().length() > 200) {
            errors.add(issue(
                    RecipeImportIssueType.BLANK_TITLE,
                    sourceIdentifier,
                    "title",
                    "title is required and must be at most 200 characters"));
        }
        if (input.summary() != null && input.summary().length() > 500) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_SUMMARY,
                    sourceIdentifier,
                    "summary",
                    "summary must be at most 500 characters"));
        }
        if (input.instructionsNote() != null
                && input.instructionsNote().length() > 1000) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_DOCUMENT,
                    sourceIdentifier,
                    "instructionsNote",
                    "instructionsNote must be at most 1000 characters"));
        }
        if (input.imageUrl() != null && input.imageUrl().length() > 500) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_DOCUMENT,
                    sourceIdentifier,
                    "imageUrl",
                    "imageUrl must be at most 500 characters"));
        }
        if (input.servings() == null
                || input.servings() < 1
                || input.servings() > 100) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_SERVINGS,
                    sourceIdentifier,
                    "servings",
                    "servings must be between 1 and 100"));
        }
        validateMinutes(input.prepMinutes(), "prepMinutes", sourceIdentifier, errors);
        validateMinutes(input.cookMinutes(), "cookMinutes", sourceIdentifier, errors);
        if (input.difficulty() == null) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_DIFFICULTY,
                    sourceIdentifier,
                    "difficulty",
                    "difficulty is required"));
        }
        if (input.source() != RecipeSource.CURATED) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_SOURCE,
                    sourceIdentifier,
                    "source",
                    "Recipe imports only accept source CURATED"));
        }

        if (sourceReference != null) {
            List<Recipe> sameReference = existingByReference.getOrDefault(
                    sourceReference,
                    List.of());
            if (sameReference.size() > 1) {
                errors.add(issue(
                        RecipeImportIssueType.SOURCE_REFERENCE_CONFLICT,
                        sourceIdentifier,
                        "sourceReference",
                        "More than one persisted Recipe uses this sourceReference"));
            } else if (sameReference.size() == 1) {
                Recipe existing = sameReference.getFirst();
                if (existing.source() != RecipeSource.CURATED) {
                    errors.add(issue(
                            RecipeImportIssueType.SOURCE_REFERENCE_CONFLICT,
                            sourceIdentifier,
                            "sourceReference",
                            "The sourceReference belongs to another Recipe source"));
                } else if (existing.createdByUserId() != null) {
                    errors.add(issue(
                            RecipeImportIssueType.USER_CREATED_CONFLICT,
                            sourceIdentifier,
                            "sourceReference",
                            "A user-owned Recipe must not be overwritten"));
                }
            }
        }
        if (slug != null) {
            List<Recipe> sameSlug = existingBySlug.getOrDefault(slug, List.of());
            if (sameSlug.size() > 1) {
                errors.add(issue(
                        RecipeImportIssueType.SLUG_CONFLICT,
                        sourceIdentifier,
                        "slug",
                        "More than one persisted Recipe uses this slug"));
            } else if (sameSlug.size() == 1
                    && !Objects.equals(
                    normalize(sameSlug.getFirst().sourceReference()),
                    sourceReference)) {
                errors.add(issue(
                        RecipeImportIssueType.SLUG_CONFLICT,
                        sourceIdentifier,
                        "slug",
                        "The slug belongs to a different source identity"));
            }
        }

        validateIngredients(
                input,
                sourceIdentifier,
                ingredientsByCode,
                unitsByCode,
                errors);
        validateSteps(input, sourceIdentifier, errors);
        validateTags(input, sourceIdentifier, tagsByCode, errors);
        validateMealSlots(input, sourceIdentifier, mealSlotsByCode, errors);
    }

    private static void validateIngredients(
            RecipeImportRecipe input,
            String sourceIdentifier,
            Map<String, IngredientReferenceSnapshot> ingredientsByCode,
            Map<String, MeasurementUnitReferenceSnapshot> unitsByCode,
            List<RecipeImportIssue> errors) {

        Set<Integer> lineNumbers = new HashSet<>();
        Set<String> ingredientCodes = new HashSet<>();
        for (int index = 0; index < input.ingredients().size(); index++) {
            RecipeImportIngredient line = input.ingredients().get(index);
            String field = "ingredients[" + index + "]";
            if (line == null) {
                errors.add(issue(
                        RecipeImportIssueType.INVALID_DOCUMENT,
                        sourceIdentifier,
                        field,
                        "ingredient line is required"));
                continue;
            }
            if (line.lineNumber() == null || line.lineNumber() < 1
                    || !lineNumbers.add(line.lineNumber())) {
                errors.add(issue(
                        RecipeImportIssueType.DUPLICATE_INGREDIENT_LINE,
                        sourceIdentifier,
                        field + ".lineNumber",
                        "ingredient line numbers must be unique and positive"));
            }

            String ingredientCode = normalize(line.ingredientCode());
            if (ingredientCode == null || !ingredientCodes.add(ingredientCode)) {
                errors.add(issue(
                        RecipeImportIssueType.DUPLICATE_INGREDIENT,
                        sourceIdentifier,
                        field + ".ingredientCode",
                        "ingredient codes must be present and unique within a Recipe"));
            } else if (!ingredientsByCode.containsKey(ingredientCode)) {
                errors.add(issue(
                        RecipeImportIssueType.UNKNOWN_INGREDIENT,
                        sourceIdentifier,
                        field + ".ingredientCode",
                        "No existing canonical Ingredient has code " + ingredientCode));
            }

            String unitCode = normalizeUnit(line.unitCode());
            boolean hasQuantity = line.quantity() != null;
            boolean hasUnit = unitCode != null;
            if (hasQuantity != hasUnit) {
                errors.add(issue(
                        RecipeImportIssueType.QUANTITY_UNIT_MISMATCH,
                        sourceIdentifier,
                        field,
                        "quantity and unitCode must both be present or both be null"));
            }
            if (line.quantity() != null
                    && (line.quantity().signum() <= 0
                    || line.quantity().scale() > 4
                    || line.quantity().precision() > 12)) {
                errors.add(issue(
                        RecipeImportIssueType.INVALID_QUANTITY,
                        sourceIdentifier,
                        field + ".quantity",
                        "quantity must be positive and fit DECIMAL(12,4)"));
            }
            if (hasUnit && !unitsByCode.containsKey(unitCode)) {
                errors.add(issue(
                        RecipeImportIssueType.UNKNOWN_UNIT,
                        sourceIdentifier,
                        field + ".unitCode",
                        "No existing measurement unit has code " + unitCode));
            }
            if (line.preparationNote() != null
                    && line.preparationNote().length() > 200) {
                errors.add(issue(
                        RecipeImportIssueType.INVALID_PREPARATION_NOTE,
                        sourceIdentifier,
                        field + ".preparationNote",
                        "preparationNote must be at most 200 characters"));
            }
            if (line.sectionLabel() != null
                    && line.sectionLabel().length() > 80) {
                errors.add(issue(
                        RecipeImportIssueType.INVALID_SECTION_LABEL,
                        sourceIdentifier,
                        field + ".sectionLabel",
                        "sectionLabel must be at most 80 characters"));
            }
        }
        if (!isContiguous(lineNumbers, input.ingredients().size())) {
            errors.add(issue(
                    RecipeImportIssueType.NON_CONTIGUOUS_INGREDIENT_LINES,
                    sourceIdentifier,
                    "ingredients.lineNumber",
                    "ingredient line numbers must be contiguous from 1"));
        }
    }

    private static void validateSteps(
            RecipeImportRecipe input,
            String sourceIdentifier,
            List<RecipeImportIssue> errors) {

        Set<Integer> stepNumbers = new HashSet<>();
        for (int index = 0; index < input.steps().size(); index++) {
            RecipeImportStep step = input.steps().get(index);
            String field = "steps[" + index + "]";
            if (step == null) {
                errors.add(issue(
                        RecipeImportIssueType.INVALID_DOCUMENT,
                        sourceIdentifier,
                        field,
                        "step is required"));
                continue;
            }
            if (step.stepNumber() == null || step.stepNumber() < 1
                    || !stepNumbers.add(step.stepNumber())) {
                errors.add(issue(
                        RecipeImportIssueType.DUPLICATE_STEP,
                        sourceIdentifier,
                        field + ".stepNumber",
                        "step numbers must be unique and positive"));
            }
            if (blank(step.instruction()) || step.instruction().length() > 2000) {
                errors.add(issue(
                        RecipeImportIssueType.BLANK_STEP_INSTRUCTION,
                        sourceIdentifier,
                        field + ".instruction",
                        "instruction is required and must be at most 2000 characters"));
            }
            if (step.durationMinutes() != null
                    && (step.durationMinutes() < 0
                    || step.durationMinutes() > 10080)) {
                errors.add(issue(
                        RecipeImportIssueType.INVALID_STEP_DURATION,
                        sourceIdentifier,
                        field + ".durationMinutes",
                        "durationMinutes must be between 0 and 10080"));
            }
        }
        if (!isContiguous(stepNumbers, input.steps().size())) {
            errors.add(issue(
                    RecipeImportIssueType.NON_CONTIGUOUS_STEPS,
                    sourceIdentifier,
                    "steps.stepNumber",
                    "step numbers must be contiguous from 1"));
        }
    }

    private static void validateTags(
            RecipeImportRecipe input,
            String sourceIdentifier,
            Map<String, RecipeTag> tagsByCode,
            List<RecipeImportIssue> errors) {

        Set<String> seen = new HashSet<>();
        for (String rawCode : input.tags()) {
            String code = normalizeCode(rawCode);
            if (code == null || !seen.add(code)) {
                errors.add(issue(
                        RecipeImportIssueType.DUPLICATE_TAG,
                        sourceIdentifier,
                        "tags",
                        "tag codes must be present and unique"));
                continue;
            }
            RecipeTag tag = tagsByCode.get(code);
            if (tag == null) {
                errors.add(issue(
                        RecipeImportIssueType.UNKNOWN_TAG,
                        sourceIdentifier,
                        "tags",
                        "No existing Recipe tag has code " + code));
            } else if (tag.internalId() == null
                    || tag.displayName() == null
                    || tag.tagKind() == null) {
                errors.add(issue(
                        RecipeImportIssueType.INVALID_TAG_REFERENCE,
                        sourceIdentifier,
                        "tags",
                        "Recipe tag reference data is incomplete for " + code));
            }
        }
    }

    private static void validateMealSlots(
            RecipeImportRecipe input,
            String sourceIdentifier,
            Map<String, MealSlotType> mealSlotsByCode,
            List<RecipeImportIssue> errors) {

        Set<String> seen = new HashSet<>();
        for (String rawCode : input.mealSlots()) {
            String code = normalizeCode(rawCode);
            if (code == null || !seen.add(code)) {
                errors.add(issue(
                        RecipeImportIssueType.DUPLICATE_MEAL_SLOT,
                        sourceIdentifier,
                        "mealSlots",
                        "meal-slot codes must be present and unique"));
                continue;
            }
            MealSlotType slot = mealSlotsByCode.get(code);
            if (slot == null) {
                errors.add(issue(
                        RecipeImportIssueType.UNKNOWN_MEAL_SLOT,
                        sourceIdentifier,
                        "mealSlots",
                        "No existing meal slot has code " + code));
            } else if (slot.internalId() == null
                    || slot.code() == null
                    || slot.displayName() == null
                    || slot.displayOrder() == null) {
                errors.add(issue(
                        RecipeImportIssueType.INVALID_DOCUMENT,
                        sourceIdentifier,
                        "mealSlots",
                        "Meal-slot reference data is incomplete for " + code));
            }
        }
    }

    private void replaceChildren(
            Long recipeId,
            RecipeImportRecipe input,
            Map<String, IngredientReferenceSnapshot> ingredientsByCode,
            Map<String, MeasurementUnitReferenceSnapshot> unitsByCode,
            Map<String, RecipeTag> tagsByCode,
            Map<String, MealSlotType> mealSlotsByCode,
            boolean deleteExisting) {

        if (deleteExisting) {
            ingredients.deleteByRecipeId(recipeId);
            steps.deleteByRecipeId(recipeId);
            tagAssignments.deleteByRecipeId(recipeId);
            mealSlotAssignments.deleteByRecipeId(recipeId);
        }

        List<RecipeIngredient> ingredientRows = input.ingredients().stream()
                .sorted(INGREDIENT_ORDER)
                .map(line -> {
                    IngredientReferenceSnapshot ingredient = ingredientsByCode.get(
                            normalize(line.ingredientCode()));
                    MeasurementUnitReferenceSnapshot unit = line.unitCode() == null
                            ? null
                            : unitsByCode.get(normalizeUnit(line.unitCode()));
                    return new RecipeIngredient(
                            recipeId,
                            line.lineNumber(),
                            ingredient.internalId(),
                            null,
                            line.quantity(),
                            unit == null ? null : unit.internalId(),
                            optionalText(line.preparationNote()),
                            line.optional(),
                            line.allowSubstitution(),
                            optionalText(line.sectionLabel()));
                })
                .toList();
        List<RecipeStep> stepRows = input.steps().stream()
                .sorted(STEP_ORDER)
                .map(step -> new RecipeStep(
                        recipeId,
                        step.stepNumber(),
                        requiredText(step.instruction()),
                        step.durationMinutes()))
                .toList();
        List<RecipeTagAssignment> tagRows = input.tags().stream()
                .map(RecipeImportService::normalizeCode)
                .sorted()
                .map(code -> new RecipeTagAssignment(
                        recipeId,
                        tagsByCode.get(code).internalId()))
                .toList();
        List<RecipeMealSlotType> mealSlotRows = input.mealSlots().stream()
                .map(RecipeImportService::normalizeCode)
                .sorted()
                .map(code -> new RecipeMealSlotType(
                        recipeId,
                        mealSlotsByCode.get(code).internalId()))
                .toList();

        ingredients.saveAll(ingredientRows);
        steps.saveAll(stepRows);
        tagAssignments.saveAll(tagRows);
        mealSlotAssignments.saveAll(mealSlotRows);
        ingredients.flush();
    }

    private static boolean sameIngredients(
            List<RecipeImportIngredient> imported,
            List<RecipeIngredient> persisted,
            Map<String, IngredientReferenceSnapshot> ingredientsByCode,
            Map<String, MeasurementUnitReferenceSnapshot> unitsByCode) {

        if (imported.size() != persisted.size()) {
            return false;
        }
        List<RecipeImportIngredient> ordered = imported.stream()
                .sorted(INGREDIENT_ORDER)
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            RecipeImportIngredient input = ordered.get(index);
            RecipeIngredient existing = persisted.get(index);
            IngredientReferenceSnapshot ingredient = ingredientsByCode.get(
                    normalize(input.ingredientCode()));
            MeasurementUnitReferenceSnapshot unit = input.unitCode() == null
                    ? null
                    : unitsByCode.get(normalizeUnit(input.unitCode()));
            if (ingredient == null
                    || existing.lineNumber() == null
                    || !Objects.equals(
                    existing.lineNumber().intValue(), input.lineNumber())
                    || !Objects.equals(existing.ingredientId(), ingredient.internalId())
                    || !sameAmount(existing.quantity(), input.quantity())
                    || !Objects.equals(
                    existing.unitId(),
                    unit == null ? null : unit.internalId())
                    || !Objects.equals(
                    existing.preparationNote(),
                    optionalText(input.preparationNote()))
                    || existing.isOptional() != input.optional()
                    || existing.allowSubstitution() != input.allowSubstitution()
                    || !Objects.equals(
                    existing.sectionLabel(),
                    optionalText(input.sectionLabel()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameSteps(
            List<RecipeImportStep> imported,
            List<RecipeStep> persisted) {

        if (imported.size() != persisted.size()) {
            return false;
        }
        List<RecipeImportStep> ordered = imported.stream()
                .sorted(STEP_ORDER)
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            RecipeImportStep input = ordered.get(index);
            RecipeStep existing = persisted.get(index);
            if (existing.stepNumber() == null
                    || !Objects.equals(
                    existing.stepNumber().intValue(), input.stepNumber())
                    || !Objects.equals(
                    existing.instruction(),
                    requiredText(input.instruction()))
                    || !Objects.equals(
                    RecipeSmallInt.toInteger(existing.durationMinutes()),
                    input.durationMinutes())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameTags(
            List<String> imported,
            List<RecipeTagAssignment> persisted,
            Map<String, RecipeTag> tagsByCode) {

        Set<Long> expected = imported.stream()
                .map(RecipeImportService::normalizeCode)
                .map(code -> tagsByCode.get(code).internalId())
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> actual = persisted.stream()
                .map(RecipeTagAssignment::tagId)
                .collect(java.util.stream.Collectors.toSet());
        return expected.equals(actual);
    }

    private static boolean sameMealSlots(
            List<String> imported,
            List<RecipeMealSlotType> persisted,
            Map<String, MealSlotType> mealSlotsByCode) {

        Set<Long> expected = imported.stream()
                .map(RecipeImportService::normalizeCode)
                .map(code -> mealSlotsByCode.get(code).internalId())
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> actual = persisted.stream()
                .map(RecipeMealSlotType::mealSlotTypeId)
                .collect(java.util.stream.Collectors.toSet());
        return expected.equals(actual);
    }

    private Map<String, RecipeTag> indexTags(Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, RecipeTag> result = new LinkedHashMap<>();
        for (RecipeTag tag : tags.findAllByCodeIn(codes)) {
            result.put(tag.code(), tag);
        }
        return Map.copyOf(result);
    }

    private Map<String, MealSlotType> indexMealSlots(Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, MealSlotType> result = new LinkedHashMap<>();
        for (MealSlotType slot : mealSlots.findAllByCodeIn(codes)) {
            result.put(slot.code(), slot);
        }
        return Map.copyOf(result);
    }

    private Map<String, List<Recipe>> groupBySourceReference(
            Collection<String> sourceReferences) {
        if (sourceReferences.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Recipe>> result = new LinkedHashMap<>();
        for (Recipe recipe : recipes.findAllBySourceReferenceIn(sourceReferences)) {
            result.computeIfAbsent(recipe.sourceReference(), key -> new ArrayList<>())
                    .add(recipe);
        }
        return result;
    }

    private Map<String, List<Recipe>> groupBySlug(Collection<String> slugs) {
        if (slugs.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Recipe>> result = new LinkedHashMap<>();
        for (Recipe recipe : recipes.findAllBySlugIn(slugs)) {
            result.computeIfAbsent(recipe.slug(), key -> new ArrayList<>())
                    .add(recipe);
        }
        return result;
    }

    private static void validateMinutes(
            Integer value,
            String field,
            String sourceIdentifier,
            List<RecipeImportIssue> errors) {
        if (value != null && (value < 0 || value > 10080)) {
            errors.add(issue(
                    RecipeImportIssueType.INVALID_MINUTES,
                    sourceIdentifier,
                    field,
                    field + " must be between 0 and 10080"));
        }
    }

    private static boolean isContiguous(Set<Integer> values, int count) {
        if (values.size() != count) {
            return false;
        }
        for (int expected = 1; expected <= count; expected++) {
            if (!values.contains(expected)) {
                return false;
            }
        }
        return true;
    }

    private static RecipeImportIssue issue(
            RecipeImportIssueType type,
            String sourceIdentifier,
            String field,
            String detail) {
        return new RecipeImportIssue(type, sourceIdentifier, field, detail);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeCode(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeUnit(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String requiredText(String value) {
        return Objects.requireNonNull(value, "required text").trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean sameAmount(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.compareTo(second) == 0;
    }

    private record Preflight(
            Map<String, IngredientReferenceSnapshot> ingredientsByCode,
            Map<String, MeasurementUnitReferenceSnapshot> unitsByCode,
            Map<String, RecipeTag> tagsByCode,
            Map<String, MealSlotType> mealSlotsByCode,
            Map<String, Recipe> recipesBySourceReference,
            RecipeImportValidationReport report) {
    }
}
