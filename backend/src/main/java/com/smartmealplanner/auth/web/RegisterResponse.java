package com.smartmealplanner.auth.web;

import java.util.UUID;

public record RegisterResponse(
        UUID publicId,
        String accountStatus) {
}