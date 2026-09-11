package com.smartmealplanner.auth.application;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.SessionRevocationReason;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserAuthSession;
import com.smartmealplanner.auth.persistence.UserAuthSessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {

    private final UserAuthSessionRepository sessions;
    private final UserAccountRepository accounts;
    private final RefreshTokenService refreshTokens;

    public LogoutService(
            UserAuthSessionRepository sessions,
            UserAccountRepository accounts,
            RefreshTokenService refreshTokens) {

        this.sessions = sessions;
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public void logoutCurrentSession(
            String plaintextRefreshToken) {

        String tokenHash;

        try {
            tokenHash =
                    refreshTokens.hashToken(
                            plaintextRefreshToken);

        } catch (IllegalArgumentException exception) {

            /*
             * Logout is intentionally idempotent.
             * Missing/invalid tokens do not reveal session state.
             */
            return;
        }

        sessions.findByRefreshTokenHashForUpdate(
                        tokenHash)
                .ifPresent(
                        this::revokeForLogout);
    }

    @Transactional
    public void logoutAll(
            UUID publicId) {

        if (publicId == null) {
            throw new IllegalArgumentException(
                    "publicId is required");
        }

        Optional<UserAccount> accountCandidate =
                accounts.findByPublicId(
                        uuidToBytes(
                                publicId));

        /*
         * A short-lived access JWT can outlive account removal.
         * In that case there are no sessions left to revoke, so logout-all
         * remains idempotent.
         */
        if (accountCandidate.isEmpty()) {
            return;
        }

        UserAccount account =
                accountCandidate.get();

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
                    SessionRevocationReason.USER_LOGOUT,
                    null);
        }

        sessions.flush();
    }

    private void revokeForLogout(
            UserAuthSession session) {

        if (session.isRevoked()) {
            return;
        }

        session.revoke(
                refreshTokens.nowUtc(),
                SessionRevocationReason.USER_LOGOUT,
                null);

        sessions.flush();
    }

    private static byte[] uuidToBytes(
            UUID uuid) {

        return ByteBuffer.allocate(16)
                .putLong(
                        uuid.getMostSignificantBits())
                .putLong(
                        uuid.getLeastSignificantBits())
                .array();
    }
}
