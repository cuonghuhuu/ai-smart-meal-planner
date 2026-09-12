package com.smartmealplanner.profile.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.smartmealplanner.profile.persistence.MeasurementSource;

public record MeasurementResponse(
        LocalDate measuredOn,
        BigDecimal weightKg,
        BigDecimal bodyFatPercent,
        BigDecimal waistCm,
        MeasurementSource source,
        String note,
        LocalDateTime createdAt) {
}
