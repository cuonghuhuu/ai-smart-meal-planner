package com.smartmealplanner.nutrition.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.smartmealplanner.nutrition.persistence.Nutrient;
import com.smartmealplanner.nutrition.persistence.NutrientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nutrition-owned read boundary for nutrient metadata used by other modules.
 */
@Service
public class NutritionReferenceQueryService {

    private final NutrientRepository nutrients;

    public NutritionReferenceQueryService(NutrientRepository nutrients) {
        this.nutrients = nutrients;
    }

    @Transactional(readOnly = true)
    public Map<Long, NutritionReferenceSnapshot> resolveByInternalIds(
            Collection<Long> internalIds) {

        if (internalIds == null || internalIds.isEmpty()) {
            return Map.of();
        }

        List<Nutrient> values = nutrients.findAllWithUnitByIdIn(internalIds);
        Map<Long, NutritionReferenceSnapshot> snapshots = new LinkedHashMap<>();
        for (Nutrient nutrient : values) {
            if (nutrient == null
                    || nutrient.id() == null
                    || nutrient.code() == null
                    || nutrient.displayName() == null
                    || nutrient.unit() == null
                    || nutrient.unit().code() == null
                    || nutrient.unit().displayName() == null) {

                throw new IllegalStateException(
                        "Nutrient reference data is inconsistent");
            }

            snapshots.put(
                    nutrient.id(),
                    new NutritionReferenceSnapshot(
                            nutrient.id(),
                            nutrient.code(),
                            nutrient.displayName(),
                            nutrient.unit().code(),
                            nutrient.unit().displayName()));
        }
        return Map.copyOf(snapshots);
    }
}
