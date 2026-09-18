package com.smartmealplanner.food;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Food-module boundary for read-only Food identity resolution.
 * Recipe must not depend on the Food entity or repository directly.
 */
@Service
public class FoodReferenceQueryService {

    private final FoodRepository foods;

    public FoodReferenceQueryService(FoodRepository foods) {
        this.foods = foods;
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

                throw new IllegalStateException(
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
}
