package com.smartmealplanner.nutrition.web;

import com.smartmealplanner.nutrition.application.NutritionCalculationInputSnapshot;
import com.smartmealplanner.nutrition.application.NutritionCalculationPreviewResult;
import com.smartmealplanner.nutrition.application.NutritionReferenceNutrientView;
import com.smartmealplanner.nutrition.application.NutritionTargetApplicationResult;
import com.smartmealplanner.nutrition.application.NutritionTargetHistoryPage;
import com.smartmealplanner.nutrition.application.NutritionTargetValueView;
import com.smartmealplanner.nutrition.application.NutritionTargetView;
import com.smartmealplanner.nutrition.application.UserDefinedNutritionTargetCommand;
import com.smartmealplanner.nutrition.application.UserDefinedNutritionValueCommand;
import com.smartmealplanner.nutrition.calculation.CalculatedNutrientTarget;

/** Explicit mapping between Nutrition application contracts and HTTP DTOs. */
final class NutritionWebMapper {

    private NutritionWebMapper() {
    }

    static UserDefinedNutritionTargetCommand toCommand(
            UserDefinedNutritionTargetRequest request) {

        return new UserDefinedNutritionTargetCommand(
                request.effectiveFrom(),
                request.nutrientValues()
                        .stream()
                        .map(NutritionWebMapper::toCommand)
                        .toList());
    }

    static NutritionCalculationResponse toResponse(
            NutritionCalculationPreviewResult result) {

        return new NutritionCalculationResponse(
                result.effectiveFrom(),
                result.calculationMethod(),
                enumName(result.status()),
                enumName(result.reason()),
                toResponse(result.input()),
                result.age(),
                result.rmrKcal(),
                result.maintenanceEnergyKcal(),
                result.nutrientTargets()
                        .stream()
                        .map(NutritionWebMapper::toResponse)
                        .toList());
    }

    static NutritionTargetWriteResponse toResponse(
            NutritionTargetApplicationResult result) {

        return new NutritionTargetWriteResponse(
                result.effectiveFrom(),
                result.effectiveTo(),
                enumName(result.origin()),
                result.calculationMethod(),
                enumName(result.calculationStatus()),
                enumName(result.calculationReason()));
    }

    static NutritionTargetResponse toResponse(
            NutritionTargetView target) {

        return new NutritionTargetResponse(
                target.effectiveFrom(),
                target.effectiveTo(),
                enumName(target.origin()),
                target.calculationMethod(),
                target.activityLevelCode(),
                target.nutritionGoalCode(),
                target.nutrientValues()
                        .stream()
                        .map(NutritionWebMapper::toResponse)
                        .toList());
    }

    static NutritionTargetHistoryResponse toResponse(
            NutritionTargetHistoryPage page) {

        return new NutritionTargetHistoryResponse(
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.content()
                        .stream()
                        .map(NutritionWebMapper::toResponse)
                        .toList());
    }

    static NutritionReferenceNutrientResponse toResponse(
            NutritionReferenceNutrientView nutrient) {

        return new NutritionReferenceNutrientResponse(
                nutrient.code(),
                nutrient.displayName(),
                enumName(nutrient.nutrientKind()),
                nutrient.core(),
                nutrient.displayOrder(),
                nutrient.unitCode(),
                nutrient.unitDisplayName());
    }

    private static UserDefinedNutritionValueCommand toCommand(
            UserDefinedNutritionValueRequest request) {

        return new UserDefinedNutritionValueCommand(
                request.nutrientCode(),
                request.targetAmount(),
                request.minAmount(),
                request.maxAmount(),
                request.hardLimit());
    }

    private static NutritionCalculationInputResponse toResponse(
            NutritionCalculationInputSnapshot input) {

        return new NutritionCalculationInputResponse(
                input.birthDate(),
                enumName(input.sex()),
                input.heightCm(),
                input.weightKg(),
                input.weightMeasuredOn(),
                input.activityLevelCode(),
                input.activityFactor(),
                input.nutritionGoalCode());
    }

    private static NutritionCalculatedValueResponse toResponse(
            CalculatedNutrientTarget target) {

        return new NutritionCalculatedValueResponse(
                target.nutrientCode(),
                target.targetAmount(),
                target.minAmount(),
                target.maxAmount(),
                target.hardLimit());
    }

    private static NutritionTargetValueResponse toResponse(
            NutritionTargetValueView value) {

        return new NutritionTargetValueResponse(
                value.nutrientCode(),
                value.unitCode(),
                value.unitDisplayName(),
                value.targetAmount(),
                value.minAmount(),
                value.maxAmount(),
                value.hardLimit());
    }

    private static String enumName(
            Enum<?> value) {

        return value == null ? null : value.name();
    }
}
