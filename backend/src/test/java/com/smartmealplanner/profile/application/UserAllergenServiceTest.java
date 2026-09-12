package com.smartmealplanner.profile.application;

import java.util.List;
import java.util.Optional;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAllergenServiceTest {

    @Mock
    private UserAllergenRepository userAllergenRepository;

    @Mock
    private AllergenRepository referenceRepository;

    @Mock
    private CurrentUserService currentUserService;

    private UserAllergenService service;

    private final UUID publicId = UUID.randomUUID();
    private final Long internalId = 101L;

    @BeforeEach
    void setUp() {
        service = new UserAllergenService(
                userAllergenRepository,
                referenceRepository,
                currentUserService);
    }

    @Test
    void getAllergensReturnsMappedResponses() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        Allergen peanut = new Allergen("PEANUT", "Peanuts", "Peanut allergen", (short) 50);
        UserAllergen ua = new UserAllergen(internalId, peanut, ReactionKind.ALLERGY, "Anaphylaxis");

        when(userAllergenRepository.findByUserIdWithAllergen(internalId))
                .thenReturn(List.of(ua));

        List<UserAllergenResponse> result = service.getAllergens(publicId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).allergen()).isEqualTo("PEANUT");
        assertThat(result.get(0).displayName()).isEqualTo("Peanuts");
        assertThat(result.get(0).reactionKind()).isEqualTo(ReactionKind.ALLERGY);
        assertThat(result.get(0).note()).isEqualTo("Anaphylaxis");
    }

    @Test
    void replaceAllergensAtomicallyReplacesEntries() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        Allergen egg = new Allergen("EGG", "Eggs", null, (short) 30);
        Allergen milk = new Allergen("MILK", "Milk", null, (short) 70);

        when(referenceRepository.findByCode("EGG")).thenReturn(Optional.of(egg));
        when(referenceRepository.findByCode("MILK")).thenReturn(Optional.of(milk));

        UserAllergen ua1 = new UserAllergen(internalId, egg, ReactionKind.ALLERGY, null);
        UserAllergen ua2 = new UserAllergen(internalId, milk, ReactionKind.INTOLERANCE, "Lactose intolerant");

        when(userAllergenRepository.findByUserIdWithAllergen(internalId))
                .thenReturn(List.of(ua1, ua2));

        ReplaceAllergensRequest request = new ReplaceAllergensRequest(
                List.of(
                        new UserAllergenItemRequest("EGG", ReactionKind.ALLERGY, null),
                        new UserAllergenItemRequest("MILK", ReactionKind.INTOLERANCE, "Lactose intolerant")));

        List<UserAllergenResponse> responses = service.replaceAllergens(publicId, request);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).allergen()).isEqualTo("EGG");
        assertThat(responses.get(1).allergen()).isEqualTo("MILK");

        verify(userAllergenRepository).deleteByUserId(internalId);
        verify(userAllergenRepository).flush();
        verify(userAllergenRepository).saveAllAndFlush(any());
    }

    @Test
    void replaceAllergensRejectsDuplicatesInRequest() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        ReplaceAllergensRequest request = new ReplaceAllergensRequest(
                List.of(
                        new UserAllergenItemRequest("SOY", ReactionKind.ALLERGY, null),
                        new UserAllergenItemRequest("SOY", ReactionKind.INTOLERANCE, null)));

        assertThatThrownBy(() -> service.replaceAllergens(publicId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Duplicate allergen code");
    }

    @Test
    void replaceAllergensRejectsUnknownAllergen() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        when(referenceRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        ReplaceAllergensRequest request = new ReplaceAllergensRequest(
                List.of(new UserAllergenItemRequest("UNKNOWN", ReactionKind.ALLERGY, null)));

        assertThatThrownBy(() -> service.replaceAllergens(publicId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown allergen code");
    }

    @Test
    void replaceAllergensRejectsNoteExceedingMaxLength() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        ReplaceAllergensRequest request = new ReplaceAllergensRequest(
                List.of(new UserAllergenItemRequest("PEANUT", null, "x".repeat(256))));

        assertThatThrownBy(() -> service.replaceAllergens(publicId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Allergen note must not exceed 255 characters");
    }
}
