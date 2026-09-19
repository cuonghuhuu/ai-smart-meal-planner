package com.smartmealplanner.pantry.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.smartmealplanner.pantry.PantryExpiryConfidence;
import com.smartmealplanner.pantry.PantryExpiryKind;
import com.smartmealplanner.pantry.PantryStorageLocation;

public record CreatePantryItemRequest(
        UUID ingredientPublicId,
        UUID foodPublicId,
        BigDecimal quantity,
        String unitCode,
        PantryStorageLocation storageLocation,
        LocalDate acquiredOn,
        LocalDate expiryDate,
        PantryExpiryKind expiryKind,
        PantryExpiryConfidence expiryConfidence,
        String note) {
}
