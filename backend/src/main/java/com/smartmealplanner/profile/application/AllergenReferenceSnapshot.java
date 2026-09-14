package com.smartmealplanner.profile.application;

/** Stable, persistence-neutral allergen reference projection for other modules. */
public record AllergenReferenceSnapshot(
        Long internalId,
        String code,
        String displayName,
        Short displayOrder) {
}
