package com.smartmealplanner.profile.web;

import java.util.UUID;

import com.smartmealplanner.profile.application.UserProfileService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final UserProfileService profileService;

    public ProfileController(
            UserProfileService profileService) {

        this.profileService =
                profileService;
    }

    @GetMapping("/profile")
    public ProfileResponse getProfile(
            @AuthenticationPrincipal
            Jwt jwt) {

        UUID publicId =
                SecurityPrincipals.authenticatedPublicId(
                        jwt);

        return profileService.getProfile(
                publicId);
    }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(
            @AuthenticationPrincipal
            Jwt jwt,
            @RequestBody
            UpdateProfileRequest request) {

        UUID publicId =
                SecurityPrincipals.authenticatedPublicId(
                        jwt);

        return profileService.updateProfile(
                publicId,
                request);
    }
}
