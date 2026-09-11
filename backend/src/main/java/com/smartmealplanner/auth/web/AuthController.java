package com.smartmealplanner.auth.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import com.smartmealplanner.auth.application.EmailVerificationService;
import com.smartmealplanner.auth.application.LoginResult;
import com.smartmealplanner.auth.application.LoginService;
import com.smartmealplanner.auth.application.RegistrationResult;
import com.smartmealplanner.auth.application.RegistrationService;
import com.smartmealplanner.auth.persistence.ClientKind;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME =
            "__Host-smartmeal_refresh";

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final LoginService loginService;
    private final Duration refreshTokenTtl;

    public AuthController(
            RegistrationService registrationService,
            EmailVerificationService emailVerificationService,
            LoginService loginService,

            @Value(
                    "${app.auth.refresh-token-ttl:PT720H}")
            String refreshTokenTtl) {

        this.registrationService =
                registrationService;

        this.emailVerificationService =
                emailVerificationService;

        this.loginService =
                loginService;

        this.refreshTokenTtl =
                Duration.parse(
                        refreshTokenTtl);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @Valid
            @RequestBody
            RegisterRequest request) {

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
            @Valid
            @RequestBody
            VerifyEmailRequest request) {

        emailVerificationService.verify(
                request.token());
    }

    @PostMapping("/login/web")
    public WebLoginResponse loginWeb(
            @Valid
            @RequestBody
            LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        LoginResult result =
                loginService.login(
                        request.email(),
                        request.password(),
                        ClientKind.WEB,
                        remoteAddress(servletRequest),
                        servletRequest.getHeader(
                                HttpHeaders.USER_AGENT));

        ResponseCookie refreshCookie =
                ResponseCookie
                        .from(
                                REFRESH_COOKIE_NAME,
                                result.refreshToken()
                                        .token())
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Strict")
                        .path("/")
                        .maxAge(
                                refreshTokenTtl)
                        .build();

        servletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString());

        return new WebLoginResponse(
                "Bearer",
                result.accessToken()
                        .token(),
                result.accessToken()
                        .expiresAt(),
                result.refreshToken()
                        .expiresAt());
    }

    @PostMapping("/login/android")
    public AndroidLoginResponse loginAndroid(
            @Valid
            @RequestBody
            LoginRequest request,
            HttpServletRequest servletRequest) {

        /*
         * Browser JavaScript must not use the Android transport to obtain
         * a long-lived refresh token in a readable JSON response.
         *
         * Native Android HTTP clients normally do not send Origin.
         */
        if (servletRequest.getHeader(
                HttpHeaders.ORIGIN) != null) {

            throw new AccessDeniedException(
                    "Browser origin is not allowed");
        }

        LoginResult result =
                loginService.login(
                        request.email(),
                        request.password(),
                        ClientKind.ANDROID,
                        remoteAddress(servletRequest),
                        servletRequest.getHeader(
                                HttpHeaders.USER_AGENT));

        return new AndroidLoginResponse(
                "Bearer",
                result.accessToken()
                        .token(),
                result.accessToken()
                        .expiresAt(),
                result.refreshToken()
                        .token(),
                result.refreshToken()
                        .expiresAt());
    }

    private static byte[] remoteAddress(
            HttpServletRequest request) {

        String remoteAddress =
                request.getRemoteAddr();

        if (remoteAddress == null
                || remoteAddress.isBlank()) {

            return null;
        }

        try {
            return InetAddress
                    .getByName(
                            remoteAddress)
                    .getAddress();

        } catch (UnknownHostException exception) {
            return null;
        }
    }
}