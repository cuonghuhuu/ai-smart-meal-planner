package com.smartmealplanner.mealplanning.persistence;

/** Bounded lifecycle failure without snapshots or remote payloads in its message. */
public final class MealPlanPersistenceException extends RuntimeException {
    public enum Reason { INVALID_COMMAND, REQUEST_NOT_FOUND, INVALID_TRANSITION,
        DUPLICATE_PLAN, MISSING_REFERENCE, EXISTING_GRAPH }

    private final Reason reason;

    public MealPlanPersistenceException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
