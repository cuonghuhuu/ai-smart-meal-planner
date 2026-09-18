package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.smartmealplanner.nutrition.persistence.MeasurementUnit;
import com.smartmealplanner.nutrition.persistence.MeasurementUnitRepository;
import com.smartmealplanner.nutrition.persistence.Nutrient;
import com.smartmealplanner.nutrition.persistence.NutrientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional application boundary for an explicit, offline catalog import.
 * It owns neither workbook parsing nor category/nutrient guessing.
 */
@Service
public class FoodCatalogImportService {
    private final FoodRepository foods;
    private final FoodCategoryRepository categories;
    private final IngredientRepository ingredients;
    private final IngredientAliasRepository aliases;
    private final NutrientRepository nutrients;
    private final MeasurementUnitRepository units;

    FoodCatalogImportService(
            FoodRepository foods,
            FoodCategoryRepository categories,
            IngredientRepository ingredients,
            IngredientAliasRepository aliases,
            NutrientRepository nutrients,
            MeasurementUnitRepository units) {
        this.foods = foods;
        this.categories = categories;
        this.ingredients = ingredients;
        this.aliases = aliases;
        this.nutrients = nutrients;
        this.units = units;
    }

    /**
     * Performs repository-backed validation without changing catalog state.
     * Unsupported nutrients are warnings because V001 deliberately has no row
     * for them; all other listed errors block the import.
     */
    @Transactional(readOnly = true)
    public CatalogImportValidationReport validate(CatalogImportDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document is required");
        }

        Map<String, FoodCategory> categoryByCode = loadCategories();
        Map<String, Nutrient> nutrientByCode = loadNutrients();
        Map<String, MeasurementUnit> unitByCode = loadUnits();
        Map<String, Food> existingFoods = new HashMap<>();
        Map<String, Ingredient> existingIngredients = new HashMap<>();
        Set<String> foodCodes = new HashSet<>();
        Set<String> sourceIdentifiers = new HashSet<>();
        Map<String, String> primaryFoodByIngredient = new HashMap<>();
        Map<String, String> aliasOwnerByKey = new HashMap<>();
        List<CatalogImportIssue> errors = new ArrayList<>();
        List<CatalogImportIssue> warnings = new ArrayList<>();

        for (CatalogImportFood input : document.foods()) {
            validateFoodIdentity(
                    input,
                    foodCodes,
                    sourceIdentifiers,
                    existingFoods,
                    errors);

            if (!categoryByCode.containsKey(input.categoryCode())) {
                errors.add(issue(
                        CatalogImportIssueType.UNKNOWN_CATEGORY,
                        input,
                        "categoryCode",
                        "No existing food category has code " + input.categoryCode()));
            }

            validateNutrients(input, nutrientByCode, warnings, errors);
            validateIngredientMapping(
                    input,
                    categoryByCode,
                    unitByCode,
                    existingFoods,
                    existingIngredients,
                    primaryFoodByIngredient,
                    aliasOwnerByKey,
                    errors);
        }

        return new CatalogImportValidationReport(errors, warnings);
    }

    /** Imports one normalized document as one all-or-nothing transaction. */
    @Transactional(rollbackFor = CatalogImportValidationException.class)
    public CatalogImportReport importCatalog(CatalogImportDocument document) {
        return importDocumentWithinTransaction(document);
    }

    /**
     * Public document-oriented import boundary used by the command-line runner.
     * Keep this transaction on the method called through the Spring proxy.
     */
    @Transactional(rollbackFor = CatalogImportValidationException.class)
    public CatalogImportReport importDocument(CatalogImportDocument document) {
        return importDocumentWithinTransaction(document);
    }

    private CatalogImportReport importDocumentWithinTransaction(
            CatalogImportDocument document) {
        CatalogImportValidationReport validation = validate(document);
        if (!validation.isValid()) {
            throw new CatalogImportValidationException(validation.errors());
        }

        Map<String, FoodCategory> categoryByCode = loadCategories();
        Map<String, Nutrient> nutrientByCode = loadNutrients();
        Map<String, MeasurementUnit> unitByCode = loadUnits();
        int foodsCreated = 0;
        int foodsUpdated = 0;
        int ingredientsCreated = 0;
        int ingredientsUpdated = 0;

        for (CatalogImportFood input : document.foods()) {
            FoodCategory category = categoryByCode.get(input.categoryCode());
            Food existing = foods.findByCode(input.catalogCode()).orElse(null);
            Food persisted;
            if (existing == null) {
                persisted = createFood(input, category, nutrientByCode);
                foodsCreated++;
            } else {
                if (existing.source() != FoodSource.IMPORTED) {
                    throw new CatalogImportValidationException(List.of(issue(
                            CatalogImportIssueType.FOOD_CODE_CONFLICT,
                            input,
                            "catalogCode",
                            "The existing food is not an imported catalog row")));
                }
                persisted = updateFood(existing, input, category, nutrientByCode);
                foodsUpdated++;
            }

            if (input.ingredientMapping() != null) {
                if (upsertIngredient(
                        input,
                        persisted,
                        categoryByCode,
                        unitByCode)) {
                    ingredientsCreated++;
                } else {
                    ingredientsUpdated++;
                }
            }
        }

        return new CatalogImportReport(
                document.foods().size(),
                foodsCreated,
                foodsUpdated,
                ingredientsCreated,
                ingredientsUpdated,
                validation.warnings());
    }

    private Food createFood(
            CatalogImportFood input,
            FoodCategory category,
            Map<String, Nutrient> nutrientByCode) {

        Food food = new Food(
                input.catalogCode(),
                input.displayName(),
                null,
                category,
                input.description(),
                input.nutritionBasis(),
                input.densityGPerMl(),
                FoodSource.IMPORTED,
                input.sourceReference());
        addSupportedNutrients(food, input, nutrientByCode);
        return foods.saveAndFlush(food);
    }

    private Food updateFood(
            Food food,
            CatalogImportFood input,
            FoodCategory category,
            Map<String, Nutrient> nutrientByCode) {

        boolean nutritionChanged = food.applyImportedMetadata(
                input.displayName(),
                category,
                input.description(),
                input.nutritionBasis(),
                input.densityGPerMl(),
                input.sourceReference());
        boolean nutrientFactsChanged = replaceNutrients(food, input, nutrientByCode);
        if (nutritionChanged || nutrientFactsChanged) {
            food.recordNutritionCorrection();
        }
        return foods.saveAndFlush(food);
    }

    private void addSupportedNutrients(
            Food food,
            CatalogImportFood input,
            Map<String, Nutrient> nutrientByCode) {

        for (CatalogImportNutrientFact fact : input.nutrientFacts()) {
            Nutrient nutrient = nutrientByCode.get(fact.canonicalCode());
            if (nutrient != null && fact.amount() != null) {
                food.addNutrient(nutrient, fact.amount(), fact.dataQuality());
            }
        }
    }

    private boolean replaceNutrients(
            Food food,
            CatalogImportFood input,
            Map<String, Nutrient> nutrientByCode) {

        Map<String, CatalogImportNutrientFact> incoming = new LinkedHashMap<>();
        for (CatalogImportNutrientFact fact : input.nutrientFacts()) {
            if (fact.amount() != null && nutrientByCode.containsKey(fact.canonicalCode())) {
                incoming.put(fact.canonicalCode(), fact);
            }
        }

        boolean changed = food.removeNutrientsNotIn(incoming.keySet());
        Set<String> persistedCodes = new HashSet<>();
        for (FoodNutrient existing : food.nutrientFacts()) {
            String code = existing.nutrient().code();
            persistedCodes.add(code);
            CatalogImportNutrientFact replacement = incoming.get(code);
            if (replacement != null
                    && (!sameAmount(existing.amount(), replacement.amount())
                    || existing.dataQuality() != replacement.dataQuality())) {
                existing.correct(
                        replacement.amount(),
                        replacement.dataQuality());
                changed = true;
            }
        }

        for (Map.Entry<String, CatalogImportNutrientFact> entry : incoming.entrySet()) {
            if (!persistedCodes.contains(entry.getKey())) {
                CatalogImportNutrientFact fact = entry.getValue();
                food.addNutrient(
                        nutrientByCode.get(entry.getKey()),
                        fact.amount(),
                        fact.dataQuality());
                changed = true;
            }
        }
        return changed;
    }

    private boolean upsertIngredient(
            CatalogImportFood input,
            Food food,
            Map<String, FoodCategory> categoryByCode,
            Map<String, MeasurementUnit> unitByCode) {

        CatalogImportIngredientMapping mapping = input.ingredientMapping();
        Ingredient ingredient = ingredients.findByCode(mapping.ingredientCode()).orElse(null);
        FoodCategory category = mapping.categoryCode() == null
                ? null
                : categoryByCode.get(mapping.categoryCode());
        MeasurementUnit unit = mapping.defaultUnitCode() == null
                ? null
                : unitByCode.get(mapping.defaultUnitCode());

        if (ingredient == null) {
            ingredient = new Ingredient(
                    mapping.ingredientCode(),
                    mapping.displayName(),
                    category,
                    mapping.primary() ? food : null,
                    unit,
                    null,
                    null,
                    false);
            ingredients.saveAndFlush(ingredient);
            ingredient.addFoodMapping(
                    food,
                    mapping.preparationState(),
                    mapping.yieldFactor(),
                    mapping.primary());
            addAliases(ingredient, mapping.aliases());
            ingredients.saveAndFlush(ingredient);
            return true;
        }

        ingredient.applyImportedMetadata(mapping.displayName(), category, unit);
        IngredientFood existingMapping = findFoodMapping(ingredient, food);
        if (existingMapping == null) {
            ingredient.addFoodMapping(
                    food,
                    mapping.preparationState(),
                    mapping.yieldFactor(),
                    mapping.primary());
        } else if (!existingMapping.matches(
                mapping.preparationState(),
                mapping.yieldFactor(),
                mapping.primary())) {
            throw new CatalogImportValidationException(List.of(issue(
                    CatalogImportIssueType.INGREDIENT_MAPPING_CONFLICT,
                    input,
                    "ingredientMapping",
                    "An existing ingredient-to-food mapping has different facts")));
        }
        addAliases(ingredient, mapping.aliases());
        ingredients.saveAndFlush(ingredient);
        return false;
    }

    private void addAliases(Ingredient ingredient, List<CatalogImportAlias> importedAliases) {
        Set<String> existing = new HashSet<>();
        for (IngredientAlias alias : ingredient.aliases()) {
            existing.add(aliasKey(alias.alias()));
        }
        for (CatalogImportAlias alias : importedAliases) {
            if (existing.add(aliasKey(alias.alias()))) {
                ingredient.addAlias(alias.alias(), alias.locale());
            }
        }
    }

    private void validateFoodIdentity(
            CatalogImportFood input,
            Set<String> foodCodes,
            Set<String> sourceIdentifiers,
            Map<String, Food> existingFoods,
            List<CatalogImportIssue> errors) {

        if (input.source() != FoodSource.IMPORTED) {
            errors.add(issue(
                    CatalogImportIssueType.INVALID_SOURCE,
                    input,
                    "source",
                    "Catalog imports must use source IMPORTED"));
        }
        if (!foodCodes.add(input.catalogCode())) {
            errors.add(issue(
                    CatalogImportIssueType.DUPLICATE_FOOD,
                    input,
                    "catalogCode",
                    "The catalog code occurs more than once in the document"));
        }
        if (!sourceIdentifiers.add(input.sourceIdentifier())) {
            errors.add(issue(
                    CatalogImportIssueType.DUPLICATE_FOOD,
                    input,
                    "sourceIdentifier",
                    "The source identifier occurs more than once in the document"));
        }

        Food existing = existingFoods.get(input.catalogCode());
        if (!existingFoods.containsKey(input.catalogCode())) {
            existing = foods.findByCode(input.catalogCode()).orElse(null);
            existingFoods.put(input.catalogCode(), existing);
        }
        if (existing != null && existing.source() != FoodSource.IMPORTED) {
            errors.add(issue(
                    CatalogImportIssueType.FOOD_CODE_CONFLICT,
                    input,
                    "catalogCode",
                    "The existing food code belongs to a non-imported row"));
        }
    }

    private void validateNutrients(
            CatalogImportFood input,
            Map<String, Nutrient> nutrientByCode,
            List<CatalogImportIssue> warnings,
            List<CatalogImportIssue> errors) {

        Set<String> seenCanonicalCodes = new HashSet<>();
        for (CatalogImportNutrientFact fact : input.nutrientFacts()) {
            if (fact.canonicalCode() == null) {
                warnings.add(issue(
                        CatalogImportIssueType.UNSUPPORTED_NUTRIENT,
                        input,
                        "nutrientFacts",
                        "No V001 nutrient mapping for source nutrient " + fact.sourceCode()));
                continue;
            }
            if (!seenCanonicalCodes.add(fact.canonicalCode())) {
                errors.add(issue(
                        CatalogImportIssueType.DUPLICATE_NUTRIENT,
                        input,
                        "nutrientFacts",
                        "Nutrient code occurs more than once: " + fact.canonicalCode()));
                continue;
            }

            Nutrient nutrient = nutrientByCode.get(fact.canonicalCode());
            if (nutrient == null) {
                warnings.add(issue(
                        CatalogImportIssueType.UNSUPPORTED_NUTRIENT,
                        input,
                        "nutrientFacts",
                        "V001 has no canonical nutrient code " + fact.canonicalCode()));
                continue;
            }
            if (fact.amount() == null) {
                continue;
            }
            if (!nutrient.unit().code().equals(fact.unitCode())) {
                errors.add(issue(
                        CatalogImportIssueType.UNIT_MISMATCH,
                        input,
                        "nutrientFacts",
                        "Expected unit " + nutrient.unit().code()
                                + " for " + fact.canonicalCode()));
            }
            if (fact.dataQuality() == null) {
                errors.add(issue(
                        CatalogImportIssueType.INVALID_NUTRIENT_QUALITY,
                        input,
                        "nutrientFacts",
                        "A present nutrient fact requires dataQuality"));
            }
        }
    }

    private void validateIngredientMapping(
            CatalogImportFood input,
            Map<String, FoodCategory> categoryByCode,
            Map<String, MeasurementUnit> unitByCode,
            Map<String, Food> existingFoods,
            Map<String, Ingredient> existingIngredients,
            Map<String, String> primaryFoodByIngredient,
            Map<String, String> aliasOwnerByKey,
            List<CatalogImportIssue> errors) {

        CatalogImportIngredientMapping mapping = input.ingredientMapping();
        if (mapping == null) {
            return;
        }
        if (mapping.categoryCode() != null
                && !categoryByCode.containsKey(mapping.categoryCode())) {
            errors.add(issue(
                    CatalogImportIssueType.UNKNOWN_CATEGORY,
                    input,
                    "ingredientMapping.categoryCode",
                    "No existing food category has code " + mapping.categoryCode()));
        }
        if (mapping.defaultUnitCode() != null
                && !unitByCode.containsKey(mapping.defaultUnitCode())) {
            errors.add(issue(
                    CatalogImportIssueType.UNKNOWN_UNIT,
                    input,
                    "ingredientMapping.defaultUnitCode",
                    "No existing measurement unit has code " + mapping.defaultUnitCode()));
        }

        if (mapping.primary()) {
            String previousFood = primaryFoodByIngredient.putIfAbsent(
                    mapping.ingredientCode(),
                    input.catalogCode());
            if (previousFood != null && !previousFood.equals(input.catalogCode())) {
                errors.add(issue(
                        CatalogImportIssueType.INGREDIENT_MAPPING_CONFLICT,
                        input,
                        "ingredientMapping.primary",
                        "The ingredient already has a primary food in this document"));
            }
        }

        Set<String> localAliases = new HashSet<>();
        for (CatalogImportAlias alias : mapping.aliases()) {
            String key = aliasKey(alias.alias());
            if (!localAliases.add(key)) {
                errors.add(issue(
                        CatalogImportIssueType.DUPLICATE_ALIAS,
                        input,
                        "ingredientMapping.aliases",
                        "Alias occurs more than once in this mapping"));
            }
            String owner = aliasOwnerByKey.putIfAbsent(key, mapping.ingredientCode());
            if (owner != null && !owner.equals(mapping.ingredientCode())) {
                errors.add(issue(
                        CatalogImportIssueType.ALIAS_CONFLICT,
                        input,
                        "ingredientMapping.aliases",
                        "Alias is assigned to more than one ingredient"));
            }
            IngredientAlias existingAlias = aliases.findByAlias(alias.alias()).orElse(null);
            if (existingAlias != null
                    && !existingAlias.ingredient().code().equals(mapping.ingredientCode())) {
                errors.add(issue(
                        CatalogImportIssueType.ALIAS_CONFLICT,
                        input,
                        "ingredientMapping.aliases",
                        "Alias already belongs to another ingredient"));
            }
        }

        Ingredient existingIngredient = existingIngredients.get(mapping.ingredientCode());
        if (!existingIngredients.containsKey(mapping.ingredientCode())) {
            existingIngredient = ingredients.findByCode(mapping.ingredientCode()).orElse(null);
            existingIngredients.put(mapping.ingredientCode(), existingIngredient);
        }
        Food existingFood = existingFoods.get(input.catalogCode());
        if (existingIngredient != null && existingFood != null) {
            IngredientFood existingMapping = findFoodMapping(existingIngredient, existingFood);
            if (existingMapping != null
                    && !existingMapping.matches(
                    mapping.preparationState(),
                    mapping.yieldFactor(),
                    mapping.primary())) {
                errors.add(issue(
                        CatalogImportIssueType.INGREDIENT_MAPPING_CONFLICT,
                        input,
                        "ingredientMapping",
                        "An existing ingredient-to-food mapping has different facts"));
            }
            if (mapping.primary()
                    && existingIngredient.defaultFood() != null
                    && !existingIngredient.defaultFood().internalId().equals(existingFood.internalId())) {
                errors.add(issue(
                        CatalogImportIssueType.INGREDIENT_MAPPING_CONFLICT,
                        input,
                        "ingredientMapping.primary",
                        "Primary mapping conflicts with the ingredient default food"));
            }
        }
    }

    private Map<String, FoodCategory> loadCategories() {
        Map<String, FoodCategory> result = new HashMap<>();
        for (FoodCategory category : categories.findAll()) {
            result.put(category.code(), category);
        }
        return result;
    }

    private Map<String, Nutrient> loadNutrients() {
        Map<String, Nutrient> result = new HashMap<>();
        for (Nutrient nutrient : nutrients.findAllWithUnitOrderByDisplayOrderAscCodeAsc()) {
            result.put(nutrient.code(), nutrient);
        }
        return result;
    }

    private Map<String, MeasurementUnit> loadUnits() {
        Map<String, MeasurementUnit> result = new HashMap<>();
        for (MeasurementUnit unit : units.findAll()) {
            result.put(unit.code().toLowerCase(Locale.ROOT), unit);
        }
        return result;
    }

    private static IngredientFood findFoodMapping(Ingredient ingredient, Food food) {
        return ingredient.foodMappings().stream()
                .filter(mapping -> mapping.food().internalId().equals(food.internalId()))
                .findFirst()
                .orElse(null);
    }

    private static CatalogImportIssue issue(
            CatalogImportIssueType type,
            CatalogImportFood input,
            String field,
            String detail) {
        return new CatalogImportIssue(type, input.catalogCode(), field, detail);
    }

    private static String aliasKey(String alias) {
        return alias.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean sameAmount(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.compareTo(second) == 0;
    }
}
