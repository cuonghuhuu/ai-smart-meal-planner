package com.smartmealplanner.profile.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.smartmealplanner.profile.persistence.Sex;

public record UpdateProfileRequest(
        LocalDate birthDate,
        Sex sex,
        BigDecimal heightCm,
        String activityLevel,
        String nutritionGoal,
        BigDecimal targetWeightKg,
        BigDecimal weeklyChangeKg,
        Integer householdSize,
        Integer maxCookMinutes,
        String notes,
        Long version) {
}
