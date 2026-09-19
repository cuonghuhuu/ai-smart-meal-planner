package com.smartmealplanner.admin;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.auth.persistence.AccountStatus;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserRoleCodeView;
import com.smartmealplanner.auth.persistence.UserRoleRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Minimal owner-safe administrative workflow for user accounts. */
@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserAccountRepository accounts;
    private final UserRoleRepository userRoles;
    private final CurrentUserService currentUsers;

    public AdminUserService(
            UserAccountRepository accounts,
            UserRoleRepository userRoles,
            CurrentUserService currentUsers) {
        this.accounts = accounts;
        this.userRoles = userRoles;
        this.currentUsers = currentUsers;
    }

    @Transactional(readOnly = true)
    public AdminUserPage list(
            String query,
            String status,
            int page,
            int size) {

        validatePage(page, size);
        String normalizedQuery = normalizeQuery(query);
        AccountStatus normalizedStatus = parseStatus(status);

        Page<UserAccount> result = accounts.findForAdmin(
                normalizedQuery,
                normalizedStatus == null ? null : normalizedStatus.name(),
                PageRequest.of(page, size));
        Map<Long, List<String>> rolesByUser = batchRoles(result.getContent());

        return new AdminUserPage(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getContent().stream()
                        .map(account -> view(
                                account,
                                rolesByUser.getOrDefault(
                                        account.internalId(), List.of())))
                        .toList());
    }

    @Transactional(readOnly = true)
    public AdminUserView get(UUID publicId) {
        UserAccount account = accounts.findByPublicId(uuidToBytes(publicId))
                .orElseThrow(() -> notFound());
        return view(account, userRoles.findRoleCodesByUserId(account.internalId()));
    }

    @Transactional
    public AdminUserView changeStatus(
            UUID actorPublicId,
            UUID targetPublicId,
            String requestedStatus) {

        if (actorPublicId == null || targetPublicId == null) {
            throw invalidRequest();
        }

        CurrentUserIdentity actor = currentUsers.getIdentity(actorPublicId);
        if (requestedStatus == null || requestedStatus.isBlank()) {
            throw invalidRequest();
        }
        AccountStatus requested = parseStatus(requestedStatus);
        if (requested == AccountStatus.SUSPENDED
                && actor.publicId().equals(targetPublicId)) {
            throw new AdminException(AdminFailure.SELF_SUSPENSION_NOT_ALLOWED);
        }
        if (requested != AccountStatus.ACTIVE
                && requested != AccountStatus.SUSPENDED) {
            throw new AdminException(AdminFailure.INVALID_LIFECYCLE);
        }

        UserAccount account = accounts.findByPublicIdForUpdate(
                        uuidToBytes(targetPublicId))
                .orElseThrow(() -> notFound());
        try {
            if (requested == AccountStatus.SUSPENDED) {
                account.suspend();
            } else {
                account.reactivate();
            }
        } catch (IllegalStateException exception) {
            throw new AdminException(AdminFailure.INVALID_LIFECYCLE);
        }

        UserAccount saved = accounts.save(account);
        return view(saved, userRoles.findRoleCodesByUserId(saved.internalId()));
    }

    private Map<Long, List<String>> batchRoles(List<UserAccount> accounts) {
        List<Long> userIds = accounts.stream()
                .map(UserAccount::internalId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> rolesByUser = new LinkedHashMap<>();
        for (UserRoleCodeView role : userRoles.findRoleCodesByUserIds(userIds)) {
            if (role == null || role.userId() == null || role.code() == null) {
                throw new AdminException(AdminFailure.CORRUPTED_DATA);
            }
            rolesByUser.computeIfAbsent(role.userId(), ignored -> new ArrayList<>())
                    .add(role.code());
        }
        return rolesByUser;
    }

    private static AdminUserView view(UserAccount account, List<String> roles) {
        if (account == null || account.internalId() == null
                || account.publicId() == null || account.email() == null
                || account.displayName() == null || account.accountStatus() == null) {
            throw new AdminException(AdminFailure.CORRUPTED_DATA);
        }
        return new AdminUserView(
                account.publicId(),
                account.email(),
                account.displayName(),
                account.accountStatus(),
                List.copyOf(roles),
                account.emailVerifiedAt(),
                account.lastLoginAt(),
                account.createdAt());
    }

    private static AccountStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AccountStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    private static String normalizeQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidRequest();
        }
    }

    private static byte[] uuidToBytes(UUID value) {
        if (value == null) {
            throw invalidRequest();
        }
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private static AdminException invalidRequest() {
        return new AdminException(AdminFailure.INVALID_REQUEST);
    }

    private static AdminException notFound() {
        return new AdminException(AdminFailure.RESOURCE_NOT_FOUND);
    }
}
