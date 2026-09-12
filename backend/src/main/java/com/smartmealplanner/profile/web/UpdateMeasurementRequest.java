package com.smartmealplanner.profile.web;

import java.math.BigDecimal;

import com.smartmealplanner.profile.persistence.MeasurementSource;

public record UpdateMeasurementRequest(
        BigDecimal weightKg,
        BigDecimal bodyFatPercent,
        BigDecimal waistCm,
        MeasurementSource source,
        String note) {
}
