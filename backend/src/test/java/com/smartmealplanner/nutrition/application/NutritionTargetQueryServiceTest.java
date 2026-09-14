package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.nutrition.persistence.MeasurementUnit;
import com.smartmealplanner.nutrition.persistence.Nutrient;
import com.smartmealplanner.nutrition.persistence.NutritionTargetOrigin;
import com.smartmealplanner.nutrition.persistence.UserNutritionTarget;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetRepository;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValue;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValueId;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValueRepository;
import com.smartmealplanner.profile.application.NutritionProfileQueryService;
import com.smartmealplanner.profile.application.NutritionProfileReferenceCodes;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionTargetQueryServiceTest {

    private static final UUID USER_A_PUBLIC_ID = UUID.randomUUID();

    private static final UUID USER_B_PUBLIC_ID = UUID.randomUUID();

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-09-15T00:30:00Z");

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private NutritionProfileQueryService profileQueryService;

    @Mock
    private UserNutritionTargetRepository targetRepository;

    @Mock
    private UserNutritionTargetValueRepository targetValueRepository;

    private NutritionTargetQueryService service;

    @BeforeEach
    void setUp() {
        service = new NutritionTargetQueryService(
                currentUserService,
                profileQueryService,
                targetRepository,
                targetValueRepository,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    void currentTargetUsesEachUsersLocalCalendarDate() {
        UserNutritionTarget targetA = target(
                101L,
                1L,
                LocalDate.of(2026, 9, 15),
                null,
                null);
        UserNutritionTarget targetB = target(
                202L,
                2L,
                LocalDate.of(2026, 9, 14),
                null,
                null);
        UserNutritionTargetValue valueA = value(
                101L,
                301L,
                "ENERGY",
                "kcal",
                "kilocalorie",
                new BigDecimal("2200.00"));
        UserNutritionTargetValue valueB = value(
                202L,
                302L,
                "ENERGY",
                "kcal",
                "kilocalorie",
                new BigDecimal("1800.00"));

        when(currentUserService.getIdentity(USER_A_PUBLIC_ID))
                .thenReturn(new CurrentUserIdentity(
                        1L,
                        USER_A_PUBLIC_ID,
                        "UTC"));
        when(currentUserService.getIdentity(USER_B_PUBLIC_ID))
                .thenReturn(new CurrentUserIdentity(
                        2L,
                        USER_B_PUBLIC_ID,
                        "America/Los_Angeles"));
        when(targetRepository.findByUserIdAndEffectiveOn(
                1L,
                LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of(targetA));
        when(targetRepository.findByUserIdAndEffectiveOn(
                2L,
                LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of(targetB));
        when(targetValueRepository
                .findAllByTargetIdInWithNutrientAndUnit(anySet()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.Set<Long> targetIds = invocation.getArgument(0);
                    return targetIds.contains(101L)
                            ? List.of(valueA)
                            : List.of(valueB);
                });
        when(profileQueryService.getNutritionReferenceCodes(
                anySet(),
                anySet())).thenReturn(
                        new NutritionProfileReferenceCodes(
                                Map.of(),
                                Map.of()));

        NutritionTargetView viewA = service.getCurrentTarget(
                USER_A_PUBLIC_ID);
        NutritionTargetView viewB = service.getCurrentTarget(
                USER_B_PUBLIC_ID);

        assertThat(viewA.effectiveFrom())
                .isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(viewA.nutrientValues().get(0).targetAmount())
                .isEqualByComparingTo("2200.00");
        assertThat(viewB.effectiveFrom())
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(viewB.nutrientValues().get(0).targetAmount())
                .isEqualByComparingTo("1800.00");

        verify(targetRepository).findByUserIdAndEffectiveOn(
                1L,
                LocalDate.of(2026, 9, 15));
        verify(targetRepository).findByUserIdAndEffectiveOn(
                2L,
                LocalDate.of(2026, 9, 14));
    }

    @Test
    void invalidTimeZoneIsStructuredAndDoesNotQueryTargets() {
        when(currentUserService.getIdentity(USER_A_PUBLIC_ID))
                .thenReturn(new CurrentUserIdentity(
                        1L,
                        USER_A_PUBLIC_ID,
                        "Not/AZone"));

        assertFailure(
                () -> service.getCurrentTarget(USER_A_PUBLIC_ID),
                NutritionApplicationFailure.INVALID_TIME_ZONE);

        verify(targetRepository, never())
                .findByUserIdAndEffectiveOn(eq(1L), any());
    }

    @Test
    void noCurrentTargetIsNotConvertedToAnEmptySuccessfulView() {
        when(currentUserService.getIdentity(USER_A_PUBLIC_ID))
                .thenReturn(new CurrentUserIdentity(
                        1L,
                        USER_A_PUBLIC_ID,
                        "UTC"));
        when(targetRepository.findByUserIdAndEffectiveOn(
                1L,
                LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of());

        assertFailure(
                () -> service.getCurrentTarget(USER_A_PUBLIC_ID),
                NutritionApplicationFailure.NO_CURRENT_TARGET);
    }

    @Test
    void multipleCurrentMatchesAreReportedAsCorruptedTimeline() {
        when(currentUserService.getIdentity(USER_A_PUBLIC_ID))
                .thenReturn(new CurrentUserIdentity(
                        1L,
                        USER_A_PUBLIC_ID,
                        "UTC"));
        UserNutritionTarget first = org.mockito.Mockito.mock(
                UserNutritionTarget.class);
        UserNutritionTarget second = org.mockito.Mockito.mock(
                UserNutritionTarget.class);
        when(targetRepository.findByUserIdAndEffectiveOn(
                1L,
                LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of(first, second));

        assertFailure(
                () -> service.getCurrentTarget(USER_A_PUBLIC_ID),
                NutritionApplicationFailure.CORRUPTED_TARGET_TIMELINE);

        verify(targetValueRepository, never())
                .findAllByTargetIdInWithNutrientAndUnit(anySet());
    }

    @Test
    void historyUsesDeterministicNewestFirstRepositoryPagination() {
        UserNutritionTarget newest = target(
                103L,
                1L,
                LocalDate.of(2026, 12, 1),
                7L,
                9L);
        UserNutritionTarget older = target(
                104L,
                1L,
                LocalDate.of(2026, 10, 1),
                7L,
                9L);
        UserNutritionTargetValue newestValue = value(
                103L,
                303L,
                "PROTEIN",
                "g",
                "gram",
                null);
        UserNutritionTargetValue olderValue = value(
                104L,
                304L,
                "ENERGY",
                "kcal",
                "kilocalorie",
                new BigDecimal("2100.00"));

        when(currentUserService.getIdentity(USER_A_PUBLIC_ID))
                .thenReturn(new CurrentUserIdentity(
                        1L,
                        USER_A_PUBLIC_ID,
                        "UTC"));
        when(targetRepository.findByUserIdOrderByEffectiveFromDescIdDesc(
                eq(1L),
                eq(PageRequest.of(1, 2))))
                .thenReturn(new PageImpl<>(
                        List.of(newest, older),
                        PageRequest.of(1, 2),
                        4));
        when(profileQueryService.getNutritionReferenceCodes(
                eq(java.util.Set.of(7L)),
                eq(java.util.Set.of(9L))))
                .thenReturn(new NutritionProfileReferenceCodes(
                        Map.of(7L, "MODERATE"),
                        Map.of(9L, "MAINTAIN")));
        when(targetValueRepository
                .findAllByTargetIdInWithNutrientAndUnit(anySet()))
                .thenReturn(List.of(newestValue, olderValue));

        NutritionTargetHistoryPage result = service.getTargetHistory(
                USER_A_PUBLIC_ID,
                1,
                2);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(4);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.content())
                .extracting(NutritionTargetView::effectiveFrom)
                .containsExactly(
                        LocalDate.of(2026, 12, 1),
                        LocalDate.of(2026, 10, 1));
        assertThat(result.content().get(0).activityLevelCode())
                .isEqualTo("MODERATE");
        assertThat(result.content().get(0).nutritionGoalCode())
                .isEqualTo("MAINTAIN");
        assertThat(result.content().get(0).nutrientValues().get(0)
                .nutrientCode()).isEqualTo("PROTEIN");
    }

    @Test
    void historyResolvesIdentityBeforeRejectingInvalidPagination() {
        when(currentUserService.getIdentity(USER_A_PUBLIC_ID))
                .thenReturn(new CurrentUserIdentity(
                        1L,
                        USER_A_PUBLIC_ID,
                        "UTC"));
        assertFailure(
                () -> service.getTargetHistory(USER_A_PUBLIC_ID, -1, 10),
                NutritionApplicationFailure.INVALID_REQUEST);
        assertFailure(
                () -> service.getTargetHistory(USER_A_PUBLIC_ID, 0, 0),
                NutritionApplicationFailure.INVALID_REQUEST);
        assertFailure(
                () -> service.getTargetHistory(USER_A_PUBLIC_ID, 0, 101),
                NutritionApplicationFailure.INVALID_REQUEST);

        verify(currentUserService, times(3))
                .getIdentity(USER_A_PUBLIC_ID);
        verifyNoInteractions(targetRepository);
    }

    @Test
    void historyResolvesOnlyTheAuthenticatedUsersInternalId() {
        when(currentUserService.getIdentity(USER_B_PUBLIC_ID))
                .thenReturn(new CurrentUserIdentity(
                        2L,
                        USER_B_PUBLIC_ID,
                        "UTC"));
        when(targetRepository.findByUserIdOrderByEffectiveFromDescIdDesc(
                eq(2L),
                eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(0, 10),
                        0));
        when(profileQueryService.getNutritionReferenceCodes(
                anySet(),
                anySet())).thenReturn(
                        new NutritionProfileReferenceCodes(
                                Map.of(),
                                Map.of()));

        NutritionTargetHistoryPage result = service.getTargetHistory(
                USER_B_PUBLIC_ID,
                0,
                10);

        assertThat(result.content()).isEmpty();
        verify(targetRepository)
                .findByUserIdOrderByEffectiveFromDescIdDesc(
                        2L,
                        PageRequest.of(0, 10));
    }

    private static UserNutritionTarget target(
            Long id,
            Long userId,
            LocalDate effectiveFrom,
            Long activityLevelId,
            Long nutritionGoalId) {

        UserNutritionTarget target = org.mockito.Mockito.mock(
                UserNutritionTarget.class);
        when(target.id()).thenReturn(id);
        when(target.effectiveFrom()).thenReturn(effectiveFrom);
        when(target.effectiveTo()).thenReturn(null);
        when(target.origin()).thenReturn(NutritionTargetOrigin.USER_DEFINED);
        when(target.activityLevelId()).thenReturn(activityLevelId);
        when(target.nutritionGoalId()).thenReturn(nutritionGoalId);
        when(target.calculationMethod()).thenReturn(null);
        return target;
    }

    private static UserNutritionTargetValue value(
            Long targetId,
            Long nutrientId,
            String nutrientCode,
            String unitCode,
            String unitDisplayName,
            BigDecimal targetAmount) {

        MeasurementUnit unit = org.mockito.Mockito.mock(
                MeasurementUnit.class);
        when(unit.id()).thenReturn(nutrientId + 1000L);
        when(unit.code()).thenReturn(unitCode);
        when(unit.displayName()).thenReturn(unitDisplayName);

        Nutrient nutrient = org.mockito.Mockito.mock(Nutrient.class);
        when(nutrient.id()).thenReturn(nutrientId);
        when(nutrient.code()).thenReturn(nutrientCode);
        when(nutrient.unit()).thenReturn(unit);

        UserNutritionTargetValue value = org.mockito.Mockito.mock(
                UserNutritionTargetValue.class);
        when(value.id()).thenReturn(new UserNutritionTargetValueId(
                targetId,
                nutrientId));
        when(value.nutrient()).thenReturn(nutrient);
        when(value.targetAmount()).thenReturn(targetAmount);
        when(value.minAmount()).thenReturn(
                targetAmount == null ? BigDecimal.ONE : null);
        when(value.maxAmount()).thenReturn(
                targetAmount == null ? new BigDecimal("2.00") : null);
        when(value.isHardLimit()).thenReturn(false);
        return value;
    }

    private static void assertFailure(
            ThrowingCallable operation,
            NutritionApplicationFailure expectedFailure) {

        assertThatThrownBy(operation)
                .isInstanceOfSatisfying(
                        NutritionApplicationException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(expectedFailure));
    }
}
