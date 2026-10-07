package com.smartmealplanner.foodrecognition;

public class IngredientRecognitionException extends RuntimeException {
    private final IngredientRecognitionFailure failure;

    public IngredientRecognitionException(IngredientRecognitionFailure failure) {
        super(failure.name());
        this.failure = failure;
    }

    public IngredientRecognitionException(
            IngredientRecognitionFailure failure,
            Throwable cause) {
        super(failure.name(), cause);
        this.failure = failure;
    }

    public IngredientRecognitionFailure failure() {
        return failure;
    }
}
