package com.smartmealplanner.auth.application;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.AccountStatus;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserRoleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final UserAccountRepository accounts;
    private final UserRoleRepository userRoles;

    public CurrentUserService(
            UserAccountRepository accounts,
            UserRoleRepository userRoles) {

        this.accounts = accounts;
        this.userRoles = userRoles;
    }

    @Transactional(readOnly = true)
    public CurrentUserResult getCurrentUser(
            UUID publicId) {

        if (publicId == null) {
            throw new AuthenticationFailedException();
        }

        UserAccount account =
                accounts.findByPublicId(
                                uuidToBytes(
                                        publicId))
                        .orElseThrow(
                                AuthenticationFailedException::new);

        if (account.accountStatus()
                != AccountStatus.ACTIVE) {

            throw new AuthenticationFailedException();
        }

        List<String> roles =
                userRoles.findRoleCodesByUserId(
                        account.internalId());

        if (roles.isEmpty()) {
            throw new IllegalStateException(
                    "Authenticated user has no roles");
        }

        return new CurrentUserResult(
                account.publicId(),
                account.email(),
                roles);
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
