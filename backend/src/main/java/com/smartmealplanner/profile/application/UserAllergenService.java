package com.smartmealplanner.profile.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.profile.persistence.Allergen;
import com.smartmealplanner.profile.persistence.AllergenRepository;
import com.smartmealplanner.profile.persistence.ReactionKind;
import com.smartmealplanner.profile.persistence.UserAllergen;
import com.smartmealplanner.profile.persistence.UserAllergenRepository;
import com.smartmealplanner.profile.web.ReplaceAllergensRequest;
import com.smartmealplanner.profile.web.UserAllergenItemRequest;
import com.smartmealplanner.profile.web.UserAllergenResponse;
import com.smartmealplanner.shared.web.InvalidRequestException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAllergenService {

    private static final int MAX_NOTE_LENGTH =
            255;

    private final UserAllergenRepository userAllergenRepository;
    private final AllergenRepository referenceRepository;
    private final CurrentUserService currentUserService;

    public UserAllergenService(
            UserAllergenRepository userAllergenRepository,
            AllergenRepository referenceRepository,
            CurrentUserService currentUserService) {

        this.userAllergenRepository =
                userAllergenRepository;

        this.referenceRepository =
                referenceRepository;

        this.currentUserService =
                currentUserService;
    }

    @Transactional(readOnly = true)
    public List<UserAllergenResponse> getAllergens(
            UUID publicId) {

        CurrentUserIdentity identity =
                currentUserService.getIdentity(
                        publicId);

        return userAllergenRepository.findByUserIdWithAllergen(
                        identity.internalId())
                .stream()
                .map(UserAllergenService::toResponse)
                .toList();
    }

    /*
     * Safety Semantics:
     * EVERY declared allergen row in user_allergens is a hard exclusion for
     * meal planning and recipe recommendations. ReactionKind exists solely for UI
     * explanation and severity context, and must never be interpreted as permission
     * to include an allergen.
     */
    @Transactional
    public List<UserAllergenResponse> replaceAllergens(
            UUID publicId,
            ReplaceAllergensRequest request) {

        if (request == null
                || request.allergens() == null) {

            throw new InvalidRequestException(
                    "Allergens list is required");
        }

        CurrentUserIdentity identity =
                currentUserService.getIdentity(
                        publicId);

        Set<String> seenCodes =
                new HashSet<>();

        for (UserAllergenItemRequest item : request.allergens()) {

            if (item == null
                    || item.allergen() == null
                    || item.allergen().isBlank()) {

                throw new InvalidRequestException(
                        "Allergen code must not be blank");
            }

            String code =
                    item.allergen().trim();

            if (!seenCodes.add(code)) {
                throw new InvalidRequestException(
                        "Duplicate allergen code in request: " + code);
            }

            if (item.note() != null
                    && item.note().length() > MAX_NOTE_LENGTH) {

                throw new InvalidRequestException(
                        "Allergen note must not exceed 255 characters");
            }
        }

        List<UserAllergen> newEntities =
                new ArrayList<>();

        for (UserAllergenItemRequest item : request.allergens()) {

            String code =
                    item.allergen().trim();

            Allergen ref =
                    referenceRepository.findByCode(
                                    code)
                            .orElseThrow(
                                    () -> new InvalidRequestException(
                                            "Unknown allergen code: " + code));

            ReactionKind reactionKind =
                    item.reactionKind() == null
                            ? ReactionKind.UNSPECIFIED
                            : item.reactionKind();

            String note =
                    item.note() == null
                            ? null
                            : item.note().trim();

            newEntities.add(
                    new UserAllergen(
                            identity.internalId(),
                            ref,
                            reactionKind,
                            note));
        }

        userAllergenRepository.deleteByUserId(
                identity.internalId());

        userAllergenRepository.flush();

        userAllergenRepository.saveAllAndFlush(
                newEntities);

        return getAllergens(
                publicId);
    }

    private static UserAllergenResponse toResponse(
            UserAllergen userAllergen) {

        return new UserAllergenResponse(
                userAllergen.allergen().code(),
                userAllergen.allergen().displayName(),
                userAllergen.allergen().description(),
                userAllergen.reactionKind(),
                userAllergen.note());
    }
}
