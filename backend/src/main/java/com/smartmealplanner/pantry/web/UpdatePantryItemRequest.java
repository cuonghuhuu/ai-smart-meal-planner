package com.smartmealplanner.pantry.web;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmealplanner.pantry.PantryExpiryConfidence;
import com.smartmealplanner.pantry.PantryExpiryKind;
import com.smartmealplanner.pantry.PantryStorageLocation;

public record UpdatePantryItemRequest(
        PantryStorageLocation storageLocation,
        LocalDate acquiredOn,
        LocalDate expiryDate,
        PantryExpiryKind expiryKind,
        PantryExpiryConfidence expiryConfidence,
        String note,
        /**
         * Write-only JSON-presence sentinel: an explicit JSON null binds as a
         * {@code NullNode}; an absent property remains {@code null}.
         */
        @JsonProperty(value = "quantity", access = JsonProperty.Access.WRITE_ONLY)
        JsonNode quantityProperty) {

    /** Returns whether a metadata PUT explicitly supplied a quantity property. */
    public boolean hasQuantityProperty() {
        return quantityProperty != null;
    }
}
