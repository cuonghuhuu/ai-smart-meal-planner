package com.smartmealplanner.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(

        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{64}$")
        String token) {
}