package com.smartmealplanner.shared.web;

/** A contextual request constraint was violated. Contains no client data. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException() {
        super();
    }

    public InvalidRequestException(String message) {
        super(message);
    }
}
