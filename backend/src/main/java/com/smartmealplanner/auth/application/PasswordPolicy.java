package com.smartmealplanner.auth.application;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    private static final int MIN_CHARACTERS = 12;
    private static final int BCRYPT_MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(
            String password) {

        if (password == null
                || password.length() < MIN_CHARACTERS) {

            throw new InvalidPasswordException();
        }

        if (password.getBytes(
                StandardCharsets.UTF_8)
                .length > BCRYPT_MAX_BYTES) {

            throw new InvalidPasswordException();
        }
    }
}
