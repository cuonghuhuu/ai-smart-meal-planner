package com.smartmealplanner.profile.web;

import java.math.BigDecimal;

public record ActivityLevelItemResponse(
        String code,
        String displayName,
        String description,
        BigDecimal energyFactor,
        int displayOrder) {
}
