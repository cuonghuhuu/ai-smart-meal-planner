package com.smartmealplanner.profile.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.smartmealplanner.profile.persistence.Sex;

public record ProfileResponse(
        LocalDate birthDate,
        Sex sex,
        BigDecimal heightCm,
        String activityLevel,
        String nutritionGoal,
        BigDecimal targetWeightKg,
        BigDecimal weeklyChangeKg,
        int householdSize,
        Integer maxCookMinutes,
        String notes,
        long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
