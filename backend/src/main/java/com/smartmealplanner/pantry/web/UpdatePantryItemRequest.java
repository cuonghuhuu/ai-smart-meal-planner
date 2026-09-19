package com.smartmealplanner.pantry.web;

import java.time.LocalDate;

import com.smartmealplanner.pantry.PantryExpiryConfidence;
import com.smartmealplanner.pantry.PantryExpiryKind;
import com.smartmealplanner.pantry.PantryStorageLocation;

public record UpdatePantryItemRequest(
        PantryStorageLocation storageLocation,
        LocalDate acquiredOn,
        LocalDate expiryDate,
        PantryExpiryKind expiryKind,
        PantryExpiryConfidence expiryConfidence,
        String note) {
}
