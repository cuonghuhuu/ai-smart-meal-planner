package com.smartmealplanner.recipe;

/** Safe application failure for the read-only Recipe catalog boundary. */
public class RecipeException extends RuntimeException {

    private final RecipeFailure failure;

    public RecipeException(RecipeFailure failure) {
        super();
        this.failure = failure;
    }

    public RecipeFailure failure() {
        return failure;
    }
}
