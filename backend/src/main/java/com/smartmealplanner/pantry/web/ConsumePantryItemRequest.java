package com.smartmealplanner.pantry.web;

import java.math.BigDecimal;

public record ConsumePantryItemRequest(
        BigDecimal quantity,
        String note) {
}
