package com.smartmealplanner.auth.application;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("ownershipAuthorization")
public class OwnershipAuthorization {

    /*
     * Ownership is derived from the authenticated JWT subject.
     *
     * Roles are deliberately ignored. ROLE_ADMIN therefore does not
     * automatically bypass ownership of user-owned resources.
     */
    public boolean isOwner(
            Authentication authentication,
            UUID ownerPublicId) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || ownerPublicId == null) {

            return false;
        }

        return ownerPublicId
                .toString()
                .equals(
                        authentication.getName());
    }
}
