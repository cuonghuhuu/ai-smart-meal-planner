package com.smartmealplanner.auth.web;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID publicId,
        String email,
        List<String> roles) {
}
