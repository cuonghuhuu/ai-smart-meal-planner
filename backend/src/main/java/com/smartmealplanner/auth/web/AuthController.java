package com.smartmealplanner.auth.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserResult;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.auth.application.EmailVerificationService;
import com.smartmealplanner.auth.application.LoginResult;
import com.smartmealplanner.auth.application.LoginService;
import com.smartmealplanner.auth.application.LogoutService;
import com.smartmealplanner.auth.application.PasswordResetService;
import com.smartmealplanner.auth.application.RefreshResult;
import com.smartmealplanner.auth.application.RefreshRotationService;
import com.smartmealplanner.auth.application.RegistrationResult;
import com.smartmealplanner.auth.application.RegistrationService;
import com.smartmealplanner.auth.application.VerificationResendService;
import com.smartmealplanner.auth.persistence.ClientKind;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final CurrentUserService currentUserService;
    private final EmailVerificationService emailVerificationService;
    private final LoginService loginService;
    private final RefreshRotationService refreshRotationService;
    private final LogoutService logoutService;
    private final VerificationResendService verificationResendService;
    private final PasswordResetService passwordResetService;
    private final Duration refreshTokenTtl;

    public AuthController(
            RegistrationService registrationService,
            CurrentUserService currentUserService,
            EmailVerificationService emailVerificationService,
            LoginService loginService,
            RefreshRotationService refreshRotationService,
            LogoutService logoutService,
            VerificationResendService verificationResendService,
            PasswordResetService passwordResetService,

            @Value(
                    "${app.auth.refresh-token-ttl:PT720H}")
            String refreshTokenTtl) {

        this.registrationService =
                registrationService;

        this.currentUserService =
                currentUserService;

        this.emailVerificationService =
                emailVerificationService;

        this.loginService =
                loginService;

        this.refreshRotationService =
                refreshRotationService;

        this.logoutService =
                logoutService;

        this.verificationResendService =
                verificationResendService;

        this.passwordResetService =
                passwordResetService;

        this.refreshTokenTtl =
                Duration.parse(
                        refreshTokenTtl);
    }

    @GetMapping("/me")
    public MeResponse me(
            @AuthenticationPrincipal
            Jwt jwt) {

        CurrentUserResult result =
                currentUserService.getCurrentUser(
                        authenticatedPublicId(
                                jwt));

        return new MeResponse(
                result.publicId(),
                result.email(),
                result.roles());
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

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(
            @Valid
            @RequestBody
            EmailRequest request) {

        verificationResendService.resend(
                request.email());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(
            @Valid
            @RequestBody
            EmailRequest request) {

        /*
         * Deliberately ignore whether a matching active account exists.
         * The HTTP response is always identical for syntactically valid
         * requests, preventing account enumeration.
         */
        passwordResetService.requestReset(
                request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @Valid
            @RequestBody
            ResetPasswordRequest request) {

        passwordResetService.resetPassword(
                request.token(),
                request.password());
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

        writeRefreshCookie(
                servletResponse,
                result.refreshToken().token());

        return new WebLoginResponse(
                "Bearer",
                result.accessToken().token(),
                result.accessToken().expiresAt(),
                result.refreshToken().expiresAt());
    }

    @PostMapping("/login/android")
    public AndroidLoginResponse loginAndroid(
            @Valid
            @RequestBody
            LoginRequest request,
            HttpServletRequest servletRequest) {

        rejectBrowserAndroidTransport(
                servletRequest);

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
                result.accessToken().token(),
                result.accessToken().expiresAt(),
                result.refreshToken().token(),
                result.refreshToken().expiresAt());
    }

    @PostMapping("/refresh")
    public Object refresh(
            @RequestBody(required = false)
            RefreshRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        String webRefreshToken =
                refreshCookieValue(
                        servletRequest);

        if (webRefreshToken != null) {

            RefreshResult result =
                    refreshRotationService.rotate(
                            webRefreshToken,
                            ClientKind.WEB,
                            remoteAddress(servletRequest),
                            servletRequest.getHeader(
                                    HttpHeaders.USER_AGENT));

            writeRefreshCookie(
                    servletResponse,
                    result.refreshToken().token());

            return new WebRefreshResponse(
                    "Bearer",
                    result.accessToken().token(),
                    result.accessToken().expiresAt(),
                    result.refreshToken().expiresAt());
        }

        rejectBrowserAndroidTransport(
                servletRequest);

        String androidRefreshToken =
                request == null
                        ? null
                        : request.refreshToken();

        RefreshResult result =
                refreshRotationService.rotate(
                        androidRefreshToken,
                        ClientKind.ANDROID,
                        remoteAddress(servletRequest),
                        servletRequest.getHeader(
                                HttpHeaders.USER_AGENT));

        return new AndroidRefreshResponse(
                "Bearer",
                result.accessToken().token(),
                result.accessToken().expiresAt(),
                result.refreshToken().token(),
                result.refreshToken().expiresAt());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestBody(required = false)
            RefreshRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        String webRefreshToken =
                refreshCookieValue(
                        servletRequest);

        if (webRefreshToken != null) {

            logoutService.logoutCurrentSession(
                    webRefreshToken);

            clearRefreshCookie(
                    servletResponse);

            return;
        }

        rejectBrowserAndroidTransport(
                servletRequest);

        String androidRefreshToken =
                request == null
                        ? null
                        : request.refreshToken();

        logoutService.logoutCurrentSession(
                androidRefreshToken);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(
            @AuthenticationPrincipal
            Jwt jwt) {

        logoutService.logoutAll(
                authenticatedPublicId(
                        jwt));
    }

    private void writeRefreshCookie(
            HttpServletResponse response,
            String refreshToken) {

        ResponseCookie refreshCookie =
                ResponseCookie
                        .from(
                                REFRESH_COOKIE_NAME,
                                refreshToken)
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Strict")
                        .path("/")
                        .maxAge(
                                refreshTokenTtl)
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString());
    }

    private static void clearRefreshCookie(
            HttpServletResponse response) {

        ResponseCookie expiredCookie =
                ResponseCookie
                        .from(
                                REFRESH_COOKIE_NAME,
                                "")
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Strict")
                        .path("/")
                        .maxAge(
                                Duration.ZERO)
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                expiredCookie.toString());
    }

    private static String refreshCookieValue(
            HttpServletRequest request) {

        Cookie[] cookies =
                request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {

            if (REFRESH_COOKIE_NAME.equals(
                    cookie.getName())) {

                String value =
                        cookie.getValue();

                return value == null
                        || value.isBlank()
                        ? null
                        : value;
            }
        }

        return null;
    }

    private static void rejectBrowserAndroidTransport(
            HttpServletRequest request) {

        if (request.getHeader(
                HttpHeaders.ORIGIN) != null) {

            throw new AccessDeniedException(
                    "Browser origin is not allowed");
        }
    }

    private static UUID authenticatedPublicId(
            Jwt jwt) {

        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {

            throw new BadCredentialsException(
                    "Access token subject is invalid");
        }

        try {
            return UUID.fromString(
                    jwt.getSubject());

        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                    "Access token subject is invalid");
        }
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
