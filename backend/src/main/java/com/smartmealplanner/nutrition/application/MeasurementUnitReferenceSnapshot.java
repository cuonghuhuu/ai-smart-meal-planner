package com.smartmealplanner.nutrition.application;

/** Immutable measurement-unit metadata for read-only cross-module use. */
public record MeasurementUnitReferenceSnapshot(
        Long internalId,
        String code,
        String displayName) {
}
