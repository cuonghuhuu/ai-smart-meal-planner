package com.smartmealplanner.auth.application;

import org.springframework.security.authentication.BadCredentialsException;

public class AuthenticationFailedException
        extends BadCredentialsException {

    public AuthenticationFailedException() {
        super("Authentication failed");
    }
}