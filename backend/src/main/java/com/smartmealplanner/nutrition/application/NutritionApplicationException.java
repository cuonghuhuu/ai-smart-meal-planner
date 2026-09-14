package com.smartmealplanner.nutrition.application;

/**
 * Structured application failure that does not require parsing message text.
 */
public final class NutritionApplicationException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final NutritionApplicationFailure failure;

    public NutritionApplicationException(
            NutritionApplicationFailure failure,
            String message) {

        super(message);

        if (failure == null) {
            throw new IllegalArgumentException(
                    "failure is required");
        }

        this.failure = failure;
    }

    public NutritionApplicationException(
            NutritionApplicationFailure failure,
            String message,
            Throwable cause) {

        super(message, cause);

        if (failure == null) {
            throw new IllegalArgumentException(
                    "failure is required");
        }

        this.failure = failure;
    }

    public NutritionApplicationFailure failure() {
        return failure;
    }

    public static NutritionApplicationException invalidRequest(
            String message) {

        return new NutritionApplicationException(
                NutritionApplicationFailure.INVALID_REQUEST,
                message);
    }
}
