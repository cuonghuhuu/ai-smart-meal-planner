package com.smartmealplanner.auth.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supplies the Spring Security CSRF value used by browser cookie operations.
 * This endpoint is public so a Web application can restore a session on
 * startup. It never reveals the HttpOnly refresh credential.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(
            CsrfToken csrfToken) {

        return ResponseEntity.ok()
                .cacheControl(
                        CacheControl.noStore())
                .body(
                        new CsrfTokenResponse(
                                csrfToken.getHeaderName(),
                                csrfToken.getToken()));
    }
}
