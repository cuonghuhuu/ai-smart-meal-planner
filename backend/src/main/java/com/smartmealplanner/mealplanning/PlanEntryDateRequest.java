package com.smartmealplanner.mealplanning;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;

/** Structural contract example; the parent window must be obtained by the service. */
public record PlanEntryDateRequest(@NotNull LocalDate planDate) {}
