package com.smartmealplanner.auth.application;

import com.smartmealplanner.auth.persistence.ClientKind;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    private final CredentialAuthenticationService authentication;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;

    public LoginService(
            CredentialAuthenticationService authentication,
            AccessTokenService accessTokens,
            RefreshTokenService refreshTokens) {

        this.authentication = authentication;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public LoginResult login(
            String email,
            String password,
            ClientKind clientKind,
            byte[] ipAddress,
            String userAgent) {

        if (clientKind == null
                || clientKind == ClientKind.UNKNOWN) {

            throw new IllegalArgumentException(
                    "Supported client kind is required");
        }

        AuthenticatedUser user =
                authentication.authenticate(
                        email,
                        password);

        /*
         * Persist the refresh session before issuing the access token.
         * The outer transaction ensures that a later failure rolls the
         * session creation back.
         */
        IssuedRefreshToken refreshToken =
                refreshTokens.issue(
                        user.account(),
                        clientKind,
                        ipAddress,
                        userAgent);

        IssuedAccessToken accessToken =
                accessTokens.issue(
                        user);

        return new LoginResult(
                accessToken,
                refreshToken,
                clientKind);
    }
}