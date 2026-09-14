package com.smartmealplanner.food;

/** Structured, safe application failure for the Food catalog HTTP adapter. */
public class FoodCatalogException extends RuntimeException {
    private final FoodCatalogFailure failure;

    FoodCatalogException(FoodCatalogFailure failure) {
        this.failure = failure;
    }

    public FoodCatalogFailure failure() {
        return failure;
    }
}
