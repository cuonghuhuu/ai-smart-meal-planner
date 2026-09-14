package com.smartmealplanner.nutrition.lifecycle;

/**
 * Domain exception for a rejected nutrition-target lifecycle operation.
 */
public final class NutritionTargetLifecycleException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final NutritionTargetLifecycleFailure failure;

    public NutritionTargetLifecycleException(
            NutritionTargetLifecycleFailure failure,
            String message) {

        super(message);

        if (failure == null) {
            throw new IllegalArgumentException(
                    "failure is required");
        }

        this.failure = failure;
    }

    public NutritionTargetLifecycleFailure failure() {
        return failure;
    }

    public static NutritionTargetLifecycleException invalidCommand(
            String message) {

        return new NutritionTargetLifecycleException(
                NutritionTargetLifecycleFailure.INVALID_COMMAND,
                message);
    }
}
