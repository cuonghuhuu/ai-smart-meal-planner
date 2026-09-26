package com.smartmealplanner.pantry.web;

import java.math.BigDecimal;

public record AdjustPantryItemRequest(
        BigDecimal quantityDelta,
        String note) {
}
