package com.smartmealplanner.food;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only orchestration for Food facts. Nutrition stays normalized on Food. */
@Service
public class FoodCatalogService {
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final FoodRepository foods;
    private final FoodCategoryRepository categories;
    private final FoodNutrientRepository nutrients;
    private final FoodServingRepository servings;

    FoodCatalogService(FoodRepository foods, FoodCategoryRepository categories,
            FoodNutrientRepository nutrients, FoodServingRepository servings) {
        this.foods = foods;
        this.categories = categories;
        this.nutrients = nutrients;
        this.servings = servings;
    }

    @Transactional(readOnly = true)
    public FoodCatalogPage getFoods(String query, String categoryCode, int page, int size) {
        validatePage(page, size);
        String normalizedQuery = normalizeOptional(query);
        String normalizedCategory = normalizeOptional(categoryCode);
        Page<Food> result = normalizedQuery == null
                ? foods.findActiveByCategory(normalizedCategory,
                        PageRequest.of(page, size, Sort.by("displayName").ascending().and(Sort.by("publicId").ascending())))
                : foods.searchActive(normalizedQuery, normalizedCategory,
                        PageRequest.of(page, size));
        return new FoodCatalogPage(result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.getContent().stream().map(FoodCatalogService::summary).toList());
    }

    @Transactional(readOnly = true)
    public FoodDetailView getFood(UUID publicId) {
        if (publicId == null) throw new FoodCatalogException(FoodCatalogFailure.INVALID_REQUEST);
        Food food = foods.findActiveSummaryByPublicId(CatalogIds.uuidToBytes(publicId))
                .orElseThrow(() -> new FoodCatalogException(FoodCatalogFailure.FOOD_NOT_FOUND));
        return new FoodDetailView(food.publicId(), food.code(), food.displayName(), food.brand(), category(food.category()),
                food.description(), food.nutritionBasis(), food.densityGPerMl(), food.source(), food.sourceReference(),
                food.revision(), nutrients.findByFoodIdWithNutrientAndUnit(food.internalId()).stream()
                        .map(FoodCatalogService::nutrient).toList(),
                servings.findByFoodIdWithUnit(food.internalId()).stream().map(FoodCatalogService::serving).toList());
    }

    @Transactional(readOnly = true)
    public List<FoodCategoryView> getFoodCategories() {
        return categories.findAllWithParentOrderByDisplayNameAscCodeAsc().stream()
                .map(FoodCatalogService::category).toList();
    }

    static FoodSummaryView summary(Food food) {
        return new FoodSummaryView(food.publicId(), food.code(), food.displayName(), food.brand(), category(food.category()));
    }

    static FoodCategoryView category(FoodCategory category) {
        return category == null ? null : new FoodCategoryView(category.code(), category.displayName(),
                category.parentCategory() == null ? null : category.parentCategory().code(), category.description());
    }

    private static FoodNutrientView nutrient(FoodNutrient fact) {
        return new FoodNutrientView(fact.nutrient().code(), fact.nutrient().displayName(), fact.amount(),
                fact.nutrient().unit().code(), fact.nutrient().unit().displayName(), fact.dataQuality());
    }

    private static FoodServingView serving(FoodServing serving) {
        return new FoodServingView(serving.displayName(), serving.quantity(), serving.unit().code(),
                serving.unit().displayName(), serving.gramWeight(), serving.milliliters(), serving.isDefaultServing());
    }

    static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new FoodCatalogException(FoodCatalogFailure.INVALID_REQUEST);
        }
    }

    static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > 200) throw new FoodCatalogException(FoodCatalogFailure.INVALID_REQUEST);
        return value.trim();
    }
}
