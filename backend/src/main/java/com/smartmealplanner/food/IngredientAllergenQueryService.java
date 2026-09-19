package com.smartmealplanner.food;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Food-owned, read-only batch boundary for ingredient allergen facts.
 */
@Service
public class IngredientAllergenQueryService {

    private final IngredientAllergenRepository allergens;

    public IngredientAllergenQueryService(IngredientAllergenRepository allergens) {
        this.allergens = allergens;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<IngredientAllergenSnapshot>> resolveByIngredientIds(
            Collection<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<IngredientAllergenSnapshot>> result = new LinkedHashMap<>();
        for (IngredientAllergen fact : allergens.findByIngredientIdIn(ingredientIds)) {
            if (fact == null || fact.ingredientId() == null
                    || fact.allergenId() == null || fact.presence() == null) {
                throw new IllegalStateException("Ingredient allergen data is inconsistent");
            }
            result.computeIfAbsent(fact.ingredientId(), ignored -> new java.util.ArrayList<>())
                    .add(new IngredientAllergenSnapshot(
                            fact.ingredientId(), fact.allergenId(), fact.presence()));
        }
        return result;
    }
}
