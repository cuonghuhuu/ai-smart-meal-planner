package com.smartmealplanner.nutrition.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

/** One user-defined nutrient amount or range supplied through the HTTP API. */
public record UserDefinedNutritionValueRequest(
        @NotBlank
        String nutrientCode,
        BigDecimal targetAmount,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean hardLimit) {
}
