package com.smartmealplanner.mealplanning;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import com.smartmealplanner.shared.web.InvalidRequestException;

/** Contextual validation using dates loaded from the parent, never client copies. */
@Service
public class MealPlanDateValidator {
    /** Call inside the entry write transaction after loading/locking the parent plan. */
    public void validate(LocalDate startDate, LocalDate endDate, LocalDate planDate) {
        if (startDate == null || endDate == null || planDate == null
                || endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) > 30
                || planDate.isBefore(startDate) || planDate.isAfter(endDate)) {
            throw new InvalidRequestException();
        }
    }
}
