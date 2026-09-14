package com.smartmealplanner.nutrition.application;

import java.util.List;

import com.smartmealplanner.nutrition.persistence.MeasurementUnit;
import com.smartmealplanner.nutrition.persistence.Nutrient;
import com.smartmealplanner.nutrition.persistence.NutrientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nutrition-owned read service for the stable nutrient reference vocabulary.
 */
@Service
public class NutritionReferenceService {

    private final NutrientRepository nutrientRepository;

    public NutritionReferenceService(
            NutrientRepository nutrientRepository) {

        this.nutrientRepository = nutrientRepository;
    }

    @Transactional(readOnly = true)
    public List<NutritionReferenceNutrientView> getNutrients() {

        return nutrientRepository
                .findAllWithUnitOrderByDisplayOrderAscCodeAsc()
                .stream()
                .map(NutritionReferenceService::toView)
                .toList();
    }

    private static NutritionReferenceNutrientView toView(
            Nutrient nutrient) {

        if (nutrient == null
                || nutrient.code() == null
                || nutrient.displayName() == null
                || nutrient.nutrientKind() == null
                || nutrient.unit() == null) {

            throw new NutritionApplicationException(
                    NutritionApplicationFailure.CORRUPTED_TARGET_DATA,
                    "Nutrition reference data is inconsistent");
        }

        MeasurementUnit unit = nutrient.unit();
        if (unit.code() == null
                || unit.displayName() == null) {

            throw new NutritionApplicationException(
                    NutritionApplicationFailure.CORRUPTED_TARGET_DATA,
                    "Nutrition reference unit is inconsistent");
        }

        return new NutritionReferenceNutrientView(
                nutrient.code(),
                nutrient.displayName(),
                nutrient.nutrientKind(),
                nutrient.isCore(),
                nutrient.displayOrder(),
                unit.code(),
                unit.displayName());
    }
}
