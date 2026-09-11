package com.smartmealplanner.auth.web;

import com.smartmealplanner.auth.application.EmailVerificationService;
import com.smartmealplanner.auth.application.RegistrationResult;
import com.smartmealplanner.auth.application.RegistrationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(
            RegistrationService registrationService,
            EmailVerificationService emailVerificationService) {

        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request) {

        RegistrationResult result =
                registrationService.register(
                        request.email(),
                        request.password(),
                        request.displayName());

        return new RegisterResponse(
                result.publicId(),
                result.accountStatus().name());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        emailVerificationService.verify(
                request.token());
    }
}