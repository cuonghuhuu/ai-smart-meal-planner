package com.smartmealplanner.nutrition.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.smartmealplanner.nutrition.persistence.MeasurementUnit;
import com.smartmealplanner.nutrition.persistence.Nutrient;
import com.smartmealplanner.nutrition.persistence.UserNutritionTarget;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValue;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValueId;
import com.smartmealplanner.profile.application.NutritionProfileReferenceCodes;

/**
 * Maps loaded Nutrition persistence state to public-safe application views.
 */
final class NutritionTargetViewAssembler {

    private NutritionTargetViewAssembler() {
    }

    static NutritionTargetView toView(
            UserNutritionTarget target,
            List<UserNutritionTargetValue> values,
            NutritionProfileReferenceCodes references) {

        if (target == null
                || target.id() == null
                || target.effectiveFrom() == null
                || target.origin() == null) {

            throw corruptedTargetData();
        }

        if (references == null) {
            throw corruptedTargetData();
        }

        if (values == null || values.isEmpty()) {
            throw corruptedTargetData();
        }

        if (target.effectiveTo() != null
                && target.effectiveTo().isBefore(
                        target.effectiveFrom())) {

            throw new NutritionApplicationException(
                    NutritionApplicationFailure.CORRUPTED_TARGET_TIMELINE,
                    "Nutrition target period is inconsistent");
        }

        List<NutritionTargetValueView> valueViews =
                new ArrayList<>(values.size());

        for (UserNutritionTargetValue value : values) {
            valueViews.add(toValueView(target, value));
        }

        return new NutritionTargetView(
                target.effectiveFrom(),
                target.effectiveTo(),
                target.origin(),
                target.calculationMethod(),
                resolveReferenceCode(
                        target.activityLevelId(),
                        references.activityLevelCodes()),
                resolveReferenceCode(
                        target.nutritionGoalId(),
                        references.nutritionGoalCodes()),
                valueViews);
    }

    private static NutritionTargetValueView toValueView(
            UserNutritionTarget target,
            UserNutritionTargetValue value) {

        if (value == null) {
            throw corruptedTargetData();
        }

        UserNutritionTargetValueId valueId = value.id();
        Nutrient nutrient = value.nutrient();

        if (valueId == null
                || !target.id().equals(valueId.targetId())
                || valueId.nutrientId() == null
                || nutrient == null
                || nutrient.id() == null
                || !nutrient.id().equals(valueId.nutrientId())
                || nutrient.code() == null
                || nutrient.code().isBlank()) {

            throw corruptedTargetData();
        }

        MeasurementUnit unit = nutrient.unit();
        if (unit == null
                || unit.id() == null
                || unit.code() == null
                || unit.code().isBlank()
                || unit.displayName() == null
                || unit.displayName().isBlank()) {

            throw corruptedTargetData();
        }

        return new NutritionTargetValueView(
                nutrient.code(),
                unit.code(),
                unit.displayName(),
                value.targetAmount(),
                value.minAmount(),
                value.maxAmount(),
                value.isHardLimit());
    }

    private static String resolveReferenceCode(
            Long referenceId,
            Map<Long, String> referenceCodes) {

        if (referenceId == null) {
            return null;
        }

        String code = referenceCodes.get(referenceId);
        if (code == null || code.isBlank()) {
            throw corruptedTargetData();
        }

        return code;
    }

    private static NutritionApplicationException corruptedTargetData() {
        return new NutritionApplicationException(
                NutritionApplicationFailure.CORRUPTED_TARGET_DATA,
                "Nutrition target data is inconsistent");
    }
}
