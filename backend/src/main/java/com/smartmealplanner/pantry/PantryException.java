package com.smartmealplanner.pantry;

/** Safe application failure for the authenticated Pantry boundary. */
public class PantryException extends RuntimeException {

    private final PantryFailure failure;

    public PantryException(PantryFailure failure) {
        super();
        this.failure = failure;
    }

    public PantryFailure failure() {
        return failure;
    }
}
