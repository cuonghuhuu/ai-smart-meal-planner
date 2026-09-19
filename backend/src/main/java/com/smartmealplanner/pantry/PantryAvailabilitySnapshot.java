package com.smartmealplanner.pantry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Immutable Pantry-owned read boundary for future recommendation code. */
public record PantryAvailabilitySnapshot(
        UUID pantryItemPublicId,
        UUID ingredientPublicId,
        UUID foodPublicId,
        BigDecimal quantityRemaining,
        String unitCode,
        LocalDate expiryDate,
        PantryExpiryKind expiryKind,
        PantryStorageLocation storageLocation) {
}
