package com.smartmealplanner.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank
        @Size(max = 512)
        String token,

        @NotBlank
        @Size(max = 1024)
        String password) {
}
