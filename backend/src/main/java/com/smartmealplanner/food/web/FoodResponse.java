package com.smartmealplanner.food.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.food.FoodNutrientDataQuality;
import com.smartmealplanner.food.FoodSource;
import com.smartmealplanner.food.NutritionBasis;

/** Public, surrogate-ID-free HTTP representations for Food catalog reads. */
public final class FoodResponse {
    private FoodResponse() { }
    public record Category(String code, String displayName, String parentCategoryCode, String description) { }
    public record Item(UUID publicId, String code, String displayName, String brand, Category category) { }
    public record Nutrient(String nutrientCode, String nutrientDisplayName, BigDecimal amount,
                           String unitCode, String unitDisplayName, FoodNutrientDataQuality dataQuality) { }
    public record Serving(String displayName, BigDecimal quantity, String unitCode, String unitDisplayName,
                          BigDecimal gramWeight, BigDecimal milliliters, boolean defaultServing) { }
    public record Detail(UUID publicId, String code, String displayName, String brand, Category category,
                         String description, NutritionBasis nutritionBasis, BigDecimal densityGPerMl,
                         FoodSource source, String sourceReference, Integer revision,
                         List<Nutrient> nutrients, List<Serving> servings) { }
    public record Page(int page, int size, long totalElements, int totalPages, List<Item> content) { }
}
