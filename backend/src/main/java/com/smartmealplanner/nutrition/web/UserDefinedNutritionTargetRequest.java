package com.smartmealplanner.nutrition.web;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** HTTP request for a user-defined dated Nutrition target. */
public record UserDefinedNutritionTargetRequest(
        @NotNull
        LocalDate effectiveFrom,
        @NotEmpty
        List<@NotNull @Valid UserDefinedNutritionValueRequest> nutrientValues) {
}
