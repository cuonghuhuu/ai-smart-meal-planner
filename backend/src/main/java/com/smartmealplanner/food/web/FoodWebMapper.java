package com.smartmealplanner.food.web;

import java.util.List;

import com.smartmealplanner.food.DislikedIngredientDraft;
import com.smartmealplanner.food.DislikedIngredientView;
import com.smartmealplanner.food.FoodCatalogPage;
import com.smartmealplanner.food.FoodCategoryView;
import com.smartmealplanner.food.FoodDetailView;
import com.smartmealplanner.food.FoodNutrientView;
import com.smartmealplanner.food.FoodServingView;
import com.smartmealplanner.food.FoodSummaryView;
import com.smartmealplanner.food.IngredientAllergenView;
import com.smartmealplanner.food.IngredientCatalogPage;
import com.smartmealplanner.food.IngredientDetailView;
import com.smartmealplanner.food.IngredientFoodMappingView;
import com.smartmealplanner.food.IngredientSummaryView;
import com.smartmealplanner.food.IngredientUnitConversionView;

final class FoodWebMapper {
    private FoodWebMapper() { }
    static FoodResponse.Page page(FoodCatalogPage value) {
        return new FoodResponse.Page(value.page(), value.size(), value.totalElements(), value.totalPages(),
                value.content().stream().map(FoodWebMapper::food).toList());
    }
    static FoodResponse.Detail food(FoodDetailView value) {
        return new FoodResponse.Detail(value.publicId(), value.code(), value.displayName(), value.brand(), category(value.category()),
                value.description(), value.nutritionBasis(), value.densityGPerMl(), value.source(), value.sourceReference(), value.revision(),
                value.nutrients().stream().map(FoodWebMapper::nutrient).toList(), value.servings().stream().map(FoodWebMapper::serving).toList());
    }
    static FoodResponse.Item food(FoodSummaryView value) { return new FoodResponse.Item(value.publicId(), value.code(), value.displayName(), value.brand(), category(value.category())); }
    static FoodResponse.Category category(FoodCategoryView value) { return value == null ? null : new FoodResponse.Category(value.code(), value.displayName(), value.parentCategoryCode(), value.description()); }
    static FoodResponse.Nutrient nutrient(FoodNutrientView value) { return new FoodResponse.Nutrient(value.nutrientCode(), value.nutrientDisplayName(), value.amount(), value.unitCode(), value.unitDisplayName(), value.dataQuality()); }
    static FoodResponse.Serving serving(FoodServingView value) { return new FoodResponse.Serving(value.displayName(), value.quantity(), value.unitCode(), value.unitDisplayName(), value.gramWeight(), value.milliliters(), value.defaultServing()); }
    static IngredientResponse.Page page(IngredientCatalogPage value) {
        return new IngredientResponse.Page(value.page(), value.size(), value.totalElements(), value.totalPages(), value.content().stream().map(FoodWebMapper::ingredient).toList());
    }
    static IngredientResponse.Item ingredient(IngredientSummaryView value) { return new IngredientResponse.Item(value.publicId(), value.code(), value.displayName(), category(value.category()), value.staple()); }
    static IngredientResponse.Detail ingredient(IngredientDetailView value) {
        return new IngredientResponse.Detail(value.publicId(), value.code(), value.displayName(), category(value.category()),
                value.defaultFoodPublicId(), value.defaultFoodCode(), value.defaultFoodDisplayName(), value.defaultUnitCode(), value.defaultUnitDisplayName(),
                value.pieceGramWeight(), value.typicalShelfLifeDays(), value.staple(), value.aliases(),
                value.foodMappings().stream().map(FoodWebMapper::mapping).toList(), value.allergens().stream().map(FoodWebMapper::allergen).toList(),
                value.unitConversions().stream().map(FoodWebMapper::conversion).toList());
    }
    static IngredientResponse.FoodMapping mapping(IngredientFoodMappingView value) { return new IngredientResponse.FoodMapping(value.foodPublicId(), value.foodCode(), value.foodDisplayName(), value.preparationState(), value.yieldFactor(), value.primary()); }
    static IngredientResponse.Allergen allergen(IngredientAllergenView value) { return new IngredientResponse.Allergen(value.allergenCode(), value.allergenDisplayName(), value.presence(), value.note()); }
    static IngredientResponse.UnitConversion conversion(IngredientUnitConversionView value) { return new IngredientResponse.UnitConversion(value.fromUnitCode(), value.fromUnitDisplayName(), value.fromQuantity(), value.toUnitCode(), value.toUnitDisplayName(), value.toQuantity(), value.confidence(), value.sourceNote()); }
    static List<IngredientResponse.Disliked> dislikes(List<DislikedIngredientView> values) { return values.stream().map(value -> new IngredientResponse.Disliked(value.ingredientPublicId(), value.ingredientCode(), value.ingredientDisplayName(), category(value.category()), value.strength(), value.note())).toList(); }
    static List<DislikedIngredientDraft> drafts(ReplaceDislikedIngredientsRequest request) { return request.ingredients().stream().map(value -> new DislikedIngredientDraft(value.ingredientPublicId(), value.strength(), value.note())).toList(); }
}
