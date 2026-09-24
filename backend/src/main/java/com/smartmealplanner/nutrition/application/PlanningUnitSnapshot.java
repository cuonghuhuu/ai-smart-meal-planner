package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;

/** Canonical, dimension-safe unit conversion supplied by Nutrition. */
public record PlanningUnitSnapshot(String unitCode, String dimension,
        String baseUnitCode, BigDecimal toBaseFactor) { }
