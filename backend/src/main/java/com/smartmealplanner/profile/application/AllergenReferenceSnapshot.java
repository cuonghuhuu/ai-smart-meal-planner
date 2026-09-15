package com.smartmealplanner.profile.application;

/** Stable, persistence-neutral allergen reference projection for other modules. */
public record AllergenReferenceSnapshot(
        String code,
        String displayName,
        Short displayOrder) {
}
