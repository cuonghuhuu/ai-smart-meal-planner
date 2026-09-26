package com.smartmealplanner.food;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Food-module boundary for read-only Food identity resolution.
 * Recipe and Pantry must not depend on the Food entity or repository directly.
 */
@Service
public class FoodReferenceQueryService {

    private final FoodRepository foods;
    private final IngredientFoodRepository ingredientFoods;

    public FoodReferenceQueryService(
            FoodRepository foods,
            IngredientFoodRepository ingredientFoods) {
        this.foods = foods;
        this.ingredientFoods = ingredientFoods;
    }

    @Transactional(readOnly = true)
    public Map<Long, FoodReferenceSnapshot> resolveByInternalIds(
            Collection<Long> internalIds) {

        if (internalIds == null || internalIds.isEmpty()) {
            return Map.of();
        }

        List<Food> values = foods.findAllById(internalIds);
        Map<Long, FoodReferenceSnapshot> snapshots = new LinkedHashMap<>();
        for (Food food : values) {
            if (food == null
                    || food.internalId() == null
                    || food.publicId() == null
                    || food.displayName() == null) {

                throw new ReferenceDataIntegrityException(
                        "Food reference data is inconsistent");
            }

            snapshots.put(
                    food.internalId(),
                    new FoodReferenceSnapshot(
                            food.internalId(),
                            food.publicId(),
                            food.code(),
                            food.displayName()));
        }
        return Map.copyOf(snapshots);
    }

    /** Resolves a Food selected by public UUID, including retired historical rows. */
    @Transactional(readOnly = true)
    public Optional<FoodReferenceSnapshot> resolveByPublicId(UUID publicId) {

        if (publicId == null) {
            return Optional.empty();
        }

        return foods.findByPublicId(CatalogIds.uuidToBytes(publicId))
                .map(FoodReferenceQueryService::snapshot);
    }

    /** Validates the existing Ingredient-to-Food catalog relationship. */
    @Transactional(readOnly = true)
    public boolean isMappedToIngredient(Long ingredientId, Long foodId) {
        return ingredientId != null
                && foodId != null
                && ingredientFoods.existsByIngredientIdAndFoodId(
                        ingredientId,
                        foodId);
    }

    private static FoodReferenceSnapshot snapshot(Food food) {
        if (food == null
                || food.internalId() == null
                || food.publicId() == null
                || food.displayName() == null) {

            throw new ReferenceDataIntegrityException(
                    "Food reference data is inconsistent");
        }

        return new FoodReferenceSnapshot(
                food.internalId(),
                food.publicId(),
                food.code(),
                food.displayName());
    }
}
