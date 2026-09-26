package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;

import com.smartmealplanner.nutrition.persistence.MeasurementUnitType;

/** Immutable measurement-unit metadata for read-only cross-module use. */
public record MeasurementUnitReferenceSnapshot(
        Long internalId,
        String code,
        String displayName,
        MeasurementUnitType unitType,
        Long baseUnitId,
        String baseUnitCode,
        BigDecimal factorToBaseUnit) {

    /** Backward-compatible identity-only construction for existing readers. */
    public MeasurementUnitReferenceSnapshot(
            Long internalId,
            String code,
            String displayName) {
        this(internalId, code, displayName, null, null, null, null);
    }
}
