package com.smartmealplanner.mealplanning.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.UnfilledSlotReasonCode;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistedMealPlanQueryServiceTest {
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final UUID RECIPE = UUID.randomUUID();
    private static final Long OWNER_ID = 17L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);

    @Mock CurrentUserService users;
    @Mock MealPlanReadPort repository;
    @InjectMocks PersistedMealPlanQueryService service;

    @Test
    void resolvesOwnerBeforeReadingGraphAndReturnsImmutablePublicView() {
        when(users.getIdentity(USER)).thenReturn(new CurrentUserIdentity(
                OWNER_ID, USER, "UTC"));
        when(repository.findOwnedPlan(PLAN, OWNER_ID)).thenReturn(Optional.of(
                new MealPlanReadPort.Header(42L, PLAN, REQUEST,
                        GenerationStatus.DEGRADED, DATE, DATE.plusDays(1),
                        new BigDecimal("2.00"))));
        when(repository.findEntries(42L)).thenReturn(List.of(
                new MealPlanReadPort.Entry(DATE, MealSlotCode.BREAKFAST,
                        RECIPE, "Oatmeal", new BigDecimal("2.00"))));
        when(repository.findUnfilledSlots(42L)).thenReturn(List.of(
                new MealPlanReadPort.UnfilledSlot(DATE.plusDays(1),
                        MealSlotCode.DINNER,
                        UnfilledSlotReasonCode.NO_ELIGIBLE_RECIPE,
                        "No eligible dinner")));

        var detail = service.get(USER, PLAN);

        assertThat(detail.mealPlanPublicId()).isEqualTo(PLAN);
        assertThat(detail.requestPublicId()).isEqualTo(REQUEST);
        assertThat(detail.status()).isEqualTo(GenerationStatus.DEGRADED);
        assertThat(detail.entries()).extracting(PersistedMealPlanQueryService.Entry::recipePublicId)
                .containsExactly(RECIPE);
        assertThat(detail.unfilledSlots()).extracting(
                PersistedMealPlanQueryService.UnfilledSlot::reasonCode)
                .containsExactly(UnfilledSlotReasonCode.NO_ELIGIBLE_RECIPE);
        assertThatThrownBy(() -> detail.entries().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        verify(repository).findOwnedPlan(PLAN, OWNER_ID);
    }

    @Test
    void missingAndCrossOwnerPlansStopBeforeChildQueries() {
        when(users.getIdentity(USER)).thenReturn(new CurrentUserIdentity(
                OWNER_ID, USER, "UTC"));
        when(repository.findOwnedPlan(PLAN, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER, PLAN))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Meal plan not found");
        verify(repository).findOwnedPlan(PLAN, OWNER_ID);
        verifyNoMoreInteractions(repository);
    }
}
