package com.smartmealplanner.food;

import java.util.List;
import java.util.UUID;

import com.smartmealplanner.profile.application.AllergenReferenceQueryService;
import com.smartmealplanner.profile.application.AllergenReferenceSnapshot;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only orchestration for canonical ingredients and their catalog facts. */
@Service
public class IngredientCatalogService {
    private final IngredientRepository ingredients;
    private final IngredientAliasRepository aliases;
    private final IngredientFoodRepository foodMappings;
    private final IngredientAllergenRepository allergens;
    private final IngredientUnitConversionRepository conversions;
    private final AllergenReferenceQueryService allergenReferences;
    private final FoodCategoryRepository categories;

    IngredientCatalogService(IngredientRepository ingredients, IngredientAliasRepository aliases,
            IngredientFoodRepository foodMappings, IngredientAllergenRepository allergens,
            IngredientUnitConversionRepository conversions, AllergenReferenceQueryService allergenReferences,
            FoodCategoryRepository categories) {
        this.ingredients = ingredients; this.aliases = aliases; this.foodMappings = foodMappings;
        this.allergens = allergens; this.conversions = conversions;
        this.allergenReferences = allergenReferences;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public IngredientCatalogPage getIngredients(String query, String categoryCode, int page, int size) {
        FoodCatalogService.validatePage(page, size);
        String normalizedQuery = FoodCatalogService.normalizeOptional(query);
        String normalizedCategory = FoodCatalogService.normalizeOptional(categoryCode);
        requireKnownCategory(normalizedCategory);
        Page<Ingredient> result;
        if (normalizedQuery == null) {
            result = ingredients.findActiveByCategory(normalizedCategory,
                    PageRequest.of(page, size, Sort.by("displayName").ascending().and(Sort.by("publicId").ascending())));
        } else {
            var exactAlias = aliases.findActiveByAlias(normalizedQuery);
            if (exactAlias.isPresent() && (normalizedCategory == null
                    || (exactAlias.get().ingredient().category() != null
                    && normalizedCategory.equals(exactAlias.get().ingredient().category().code())))) {
                Ingredient ingredient = exactAlias.get().ingredient();
                return new IngredientCatalogPage(page, size, 1, 1,
                        page == 0 ? List.of(summary(ingredient)) : List.of());
            }
            result = ingredients.searchActive(normalizedQuery, normalizedCategory, PageRequest.of(page, size));
        }
        return new IngredientCatalogPage(result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.getContent().stream().map(IngredientCatalogService::summary).toList());
    }

    @Transactional(readOnly = true)
    public IngredientDetailView getIngredient(UUID publicId) {
        if (publicId == null) throw new FoodCatalogException(FoodCatalogFailure.INVALID_REQUEST);
        Ingredient ingredient = ingredients.findActiveSummaryByPublicId(CatalogIds.uuidToBytes(publicId))
                .orElseThrow(() -> new FoodCatalogException(FoodCatalogFailure.INGREDIENT_NOT_FOUND));
        List<IngredientAllergen> allergenFacts = allergens.findByIngredientId(ingredient.internalId());
        List<AllergenReferenceSnapshot> allergenSnapshots = allergenReferences.resolveByInternalIdsInOrder(
                allergenFacts.stream().map(IngredientAllergen::allergenId).toList());
        if (allergenSnapshots.size() != allergenFacts.size()) {
            throw new FoodCatalogException(FoodCatalogFailure.CORRUPTED_CATALOG_DATA);
        }
        return new IngredientDetailView(ingredient.publicId(), ingredient.code(), ingredient.displayName(),
                FoodCatalogService.category(ingredient.category()),
                ingredient.defaultFood() == null ? null : ingredient.defaultFood().publicId(),
                ingredient.defaultFood() == null ? null : ingredient.defaultFood().code(),
                ingredient.defaultFood() == null ? null : ingredient.defaultFood().displayName(),
                ingredient.defaultUnit() == null ? null : ingredient.defaultUnit().code(),
                ingredient.defaultUnit() == null ? null : ingredient.defaultUnit().displayName(),
                ingredient.pieceGramWeight(), ingredient.typicalShelfLifeDays(), ingredient.isStaple(),
                aliases.findByIngredientIdOrderByAlias(ingredient.internalId()).stream().map(IngredientAlias::alias).toList(),
                foodMappings.findByIngredientIdWithFood(ingredient.internalId()).stream().map(IngredientCatalogService::mapping).toList(),
                allergenViews(allergenFacts, allergenSnapshots),
                conversions.findByIngredientIdWithUnits(ingredient.internalId()).stream().map(IngredientCatalogService::conversion).toList());
    }

    static IngredientSummaryView summary(Ingredient ingredient) {
        return new IngredientSummaryView(ingredient.publicId(), ingredient.code(), ingredient.displayName(),
                FoodCatalogService.category(ingredient.category()), ingredient.isStaple());
    }

    private static IngredientFoodMappingView mapping(IngredientFood mapping) {
        Food food = mapping.food();
        return new IngredientFoodMappingView(food.publicId(), food.code(), food.displayName(), mapping.preparationState(),
                mapping.yieldFactor(), mapping.isPrimary());
    }
    private static List<IngredientAllergenView> allergenViews(
            List<IngredientAllergen> facts,
            List<AllergenReferenceSnapshot> references) {

        return java.util.stream.IntStream.range(0, facts.size())
                .mapToObj(index -> allergen(facts.get(index), references.get(index)))
                .sorted(java.util.Comparator.comparing(IngredientAllergenView::allergenCode))
                .toList();
    }

    private static IngredientAllergenView allergen(IngredientAllergen fact, AllergenReferenceSnapshot reference) {
        return new IngredientAllergenView(reference.code(), reference.displayName(), fact.presence(), fact.note());
    }

    private void requireKnownCategory(String categoryCode) {
        if (categoryCode != null && !categories.existsByCode(categoryCode)) {
            throw new FoodCatalogException(FoodCatalogFailure.INVALID_REQUEST);
        }
    }
    private static IngredientUnitConversionView conversion(IngredientUnitConversion value) {
        return new IngredientUnitConversionView(value.fromUnit().code(), value.fromUnit().displayName(), value.fromQuantity(),
                value.toUnit().code(), value.toUnit().displayName(), value.toQuantity(), value.confidence(), value.sourceNote());
    }
}
