package com.smartmealplanner.auth.application;

import org.springframework.security.authentication.BadCredentialsException;

public class InvalidRefreshTokenException
        extends BadCredentialsException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid");
    }
}