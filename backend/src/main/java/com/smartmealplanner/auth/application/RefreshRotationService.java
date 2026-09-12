package com.smartmealplanner.auth.application;

import java.time.LocalDateTime;
import java.util.List;

import com.smartmealplanner.auth.persistence.AccountStatus;
import com.smartmealplanner.auth.persistence.ClientKind;
import com.smartmealplanner.auth.persistence.SessionRevocationReason;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAuthSession;
import com.smartmealplanner.auth.persistence.UserAuthSessionRepository;
import com.smartmealplanner.auth.persistence.UserRoleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshRotationService {

    private final UserAuthSessionRepository sessions;
    private final UserRoleRepository userRoles;
    private final RefreshTokenService refreshTokens;
    private final AccessTokenService accessTokens;

    public RefreshRotationService(
            UserAuthSessionRepository sessions,
            UserRoleRepository userRoles,
            RefreshTokenService refreshTokens,
            AccessTokenService accessTokens) {

        this.sessions = sessions;
        this.userRoles = userRoles;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
    }

    @Transactional(
            noRollbackFor =
                    InvalidRefreshTokenException.class)
    public RefreshResult rotate(
            String plaintextRefreshToken,
            ClientKind expectedClientKind,
            byte[] ipAddress,
            String userAgent) {

        String tokenHash;

        try {
            tokenHash =
                    refreshTokens.hashToken(
                            plaintextRefreshToken);

        } catch (IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException();
        }

        UserAuthSession currentSession =
                sessions.findByRefreshTokenHashForUpdate(
                                tokenHash)
                        .orElseThrow(
                                InvalidRefreshTokenException::new);

        UserAccount account =
                currentSession.user();

        /*
         * Reuse of a token that was already rotated is treated as
         * a possible token-theft event.
         */
        if (currentSession.isRevoked()) {

            if (currentSession.revocationReason()
                    == SessionRevocationReason.ROTATED) {

                revokeActiveSessionsForReuse(
                        account);
            }

            throw new InvalidRefreshTokenException();
        }

        /*
         * MySQL performs the authoritative expiry comparison.
         */
        if (sessions.isExpired(
                currentSession.internalId())) {

            throw new InvalidRefreshTokenException();
        }

        if (account.accountStatus()
                != AccountStatus.ACTIVE) {

            throw new InvalidRefreshTokenException();
        }

        if (expectedClientKind == null
                || expectedClientKind == ClientKind.UNKNOWN
                || currentSession.clientKind()
                != expectedClientKind) {

            throw new InvalidRefreshTokenException();
        }

        CreatedRefreshSession successor =
                refreshTokens.createSession(
                        account,
                        currentSession.clientKind(),
                        ipAddress,
                        userAgent);

        LocalDateTime now =
                refreshTokens.nowUtc();

        currentSession.markUsed(
                now);

        currentSession.revoke(
                now,
                SessionRevocationReason.ROTATED,
                successor.session());

        sessions.flush();

        List<String> roles =
                userRoles.findRoleCodesByUserId(
                        account.internalId());

        if (roles.isEmpty()) {
            throw new IllegalStateException(
                    "Authenticated user has no roles");
        }

        AuthenticatedUser authenticatedUser =
                new AuthenticatedUser(
                        account,
                        account.publicId(),
                        roles);

        IssuedAccessToken accessToken =
                accessTokens.issue(
                        authenticatedUser);

        return new RefreshResult(
                accessToken,
                successor.refreshToken(),
                currentSession.clientKind());
    }

    private void revokeActiveSessionsForReuse(
            UserAccount account) {

        List<UserAuthSession> activeSessions =
                sessions.findActiveByUserIdForUpdate(
                        account.internalId());

        if (activeSessions.isEmpty()) {
            return;
        }

        LocalDateTime now =
                refreshTokens.nowUtc();

        for (UserAuthSession session :
                activeSessions) {

            session.revoke(
                    now,
                    SessionRevocationReason.SUSPECTED_REUSE,
                    null);
        }

        sessions.flush();
    }
}