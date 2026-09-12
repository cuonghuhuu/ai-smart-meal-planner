package com.smartmealplanner.profile.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.smartmealplanner.profile.persistence.MeasurementSource;

public record RecordMeasurementRequest(
        LocalDate measuredOn,
        BigDecimal weightKg,
        BigDecimal bodyFatPercent,
        BigDecimal waistCm,
        MeasurementSource source,
        String note) {
}
