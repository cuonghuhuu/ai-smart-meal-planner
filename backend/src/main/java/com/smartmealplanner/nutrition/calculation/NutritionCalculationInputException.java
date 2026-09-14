package com.smartmealplanner.nutrition.calculation;

/**
 * Signals that a nutrition calculation input is missing or invalid.
 */
public final class NutritionCalculationInputException
        extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public NutritionCalculationInputException(
            String message) {

        super(message);
    }
}
