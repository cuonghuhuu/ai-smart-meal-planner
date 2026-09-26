package com.smartmealplanner.pantry.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.smartmealplanner.pantry.PantryExpiryConfidence;
import com.smartmealplanner.pantry.PantryExpiryKind;
import com.smartmealplanner.pantry.PantryItemStatus;
import com.smartmealplanner.pantry.PantryStorageLocation;

public final class PantryResponse {

    private PantryResponse() {
    }

    public record Item(
            UUID publicId,
            UUID ingredientPublicId,
            String ingredientCode,
            String ingredientName,
            UUID foodPublicId,
            String foodCode,
            String foodName,
            BigDecimal quantityInitial,
            BigDecimal quantityRemaining,
            String unitCode,
            String unitDisplayName,
            PantryStorageLocation storageLocation,
            LocalDate acquiredOn,
            LocalDate expiryDate,
            PantryExpiryKind expiryKind,
            PantryExpiryConfidence expiryConfidence,
            PantryItemStatus status,
            LocalDateTime closedAt,
            String note,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
