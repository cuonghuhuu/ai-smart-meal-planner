package com.smartmealplanner.food;

import java.util.UUID;

/**
 * Immutable, read-only Food reference used by other modules.
 *
 * <p>The internal id is an application-boundary handle for resolving an
 * existing foreign key. It is never part of a web response.</p>
 */
public record FoodReferenceSnapshot(
        Long internalId,
        UUID publicId,
        String code,
        String displayName) {
}
