package com.smartmealplanner.admin;

/** Safe application failure for the minimal administrator boundary. */
public class AdminException extends RuntimeException {

    private final AdminFailure failure;

    public AdminException(AdminFailure failure) {
        super();
        this.failure = failure;
    }

    public AdminFailure failure() {
        return failure;
    }
}
