package com.smartmealplanner.auth.application;

import java.util.Optional;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.AccountStatus;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserRoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UserAccountRepository accounts;

    @Mock
    private UserRoleRepository userRoles;

    private CurrentUserService service;

    @BeforeEach
    void setUp() {
        service = new CurrentUserService(
                accounts,
                userRoles);
    }

    @Test
    void getIdentityReturnsAccountTimeZoneAndExistingIdentityFields() {
        UUID publicId = UUID.randomUUID();
        UserAccount account = account(
                publicId,
                42L,
                "Asia/Ho_Chi_Minh",
                AccountStatus.ACTIVE);

        when(accounts.findByPublicId(any(byte[].class)))
                .thenReturn(Optional.of(account));

        CurrentUserIdentity identity =
                service.getIdentity(publicId);

        assertThat(identity.internalId()).isEqualTo(42L);
        assertThat(identity.publicId()).isEqualTo(publicId);
        assertThat(identity.timeZone())
                .isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void getIdentityStillRejectsInactiveAccounts() {
        UUID publicId = UUID.randomUUID();

        UserAccount account =
                mock(UserAccount.class);

        when(account.accountStatus())
                .thenReturn(AccountStatus.PENDING_VERIFICATION);

        when(accounts.findByPublicId(any(byte[].class)))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(
                () -> service.getIdentity(publicId))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    private static UserAccount account(
            UUID publicId,
            Long internalId,
            String timeZone,
            AccountStatus status) {

        UserAccount account =
                mock(UserAccount.class);

        when(account.publicId()).thenReturn(publicId);
        when(account.internalId()).thenReturn(internalId);
        when(account.timeZone()).thenReturn(timeZone);
        when(account.accountStatus()).thenReturn(status);

        return account;
    }
}
