package com.smartmealplanner.auth.web;

/** Public, non-authentication CSRF value for browser cookie operations. */
public record CsrfTokenResponse(
        String headerName,
        String token) {
}
