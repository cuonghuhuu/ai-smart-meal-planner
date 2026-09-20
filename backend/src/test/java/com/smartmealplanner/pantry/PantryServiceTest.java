package com.smartmealplanner.pantry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.food.FoodReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceSnapshot;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;
import com.smartmealplanner.pantry.web.CreatePantryItemRequest;
import com.smartmealplanner.pantry.web.PantryResponse;
import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PantryServiceTest {

    private static final UUID USER_PUBLIC_ID = UUID.randomUUID();
    private static final UUID ITEM_PUBLIC_ID = UUID.randomUUID();
    private static final UUID INGREDIENT_PUBLIC_ID = UUID.randomUUID();
    private static final Long USER_ID = 10L;
    private static final Long ITEM_ID = 20L;
    private static final Long INGREDIENT_ID = 30L;
    private static final Long UNIT_ID = 40L;
    private static final Instant CLOCK_INSTANT =
            Instant.parse("2026-09-20T12:34:56.123456789Z");
    private static final LocalDateTime EXPECTED_TIMESTAMP =
            LocalDateTime.of(2026, 9, 20, 12, 34, 56, 123_456_000);

    @Mock
    private PantryItemRepository items;

    @Mock
    private PantryItemEventRepository events;

    @Mock
    private CurrentUserService currentUsers;

    @Mock
    private IngredientReferenceQueryService ingredients;

    @Mock
    private FoodReferenceQueryService foods;

    @Mock
    private MeasurementUnitReferenceQueryService units;

    private PantryService service;

    @BeforeEach
    void setUp() {
        service = new PantryService(
                items,
                events,
                currentUsers,
                ingredients,
                foods,
                units,
                Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC));
        when(currentUsers.getIdentity(USER_PUBLIC_ID))
                .thenReturn(new CurrentUserIdentity(
                        USER_ID,
                        USER_PUBLIC_ID,
                        "UTC"));
    }

    @Test
    void createNormalizesTheAddedEventTimestampToDatabaseMicroseconds() {
        AtomicReference<PantryItem> saved = new AtomicReference<>();
        stubCreateReferences();
        when(items.saveAndFlush(any(PantryItem.class))).thenAnswer(invocation -> {
            PantryItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", ITEM_ID);
            saved.set(item);
            return item;
        });
        when(items.findByPublicIdAndUserId(any(byte[].class), eq(USER_ID)))
                .thenAnswer(invocation -> Optional.of(saved.get()));

        service.create(USER_PUBLIC_ID, new CreatePantryItemRequest(
                INGREDIENT_PUBLIC_ID,
                null,
                new BigDecimal("5"),
                "piece",
                PantryStorageLocation.FRIDGE,
                null,
                null,
                null,
                null,
                null));

        ArgumentCaptor<PantryItemEvent> event = ArgumentCaptor.forClass(
                PantryItemEvent.class);
        verify(events).saveAndFlush(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo(PantryItemEventType.ADDED);
        assertThat(event.getValue().occurredAt()).isEqualTo(EXPECTED_TIMESTAMP);
        assertThat(event.getValue().occurredAt().getNano() % 1_000)
                .isEqualTo(0);
    }

    @Test
    void discardUsesOneMicrosecondTimestampForLotClosureAndEvent() {
        PantryItem item = item();
        stubResponseReferences(item);
        when(items.saveAndFlush(item)).thenReturn(item);

        PantryResponse.Item response = service.discard(
                USER_PUBLIC_ID,
                ITEM_PUBLIC_ID,
                "waste");

        ArgumentCaptor<PantryItemEvent> event = ArgumentCaptor.forClass(
                PantryItemEvent.class);
        verify(events).saveAndFlush(event.capture());
        assertThat(item.closedAt()).isEqualTo(EXPECTED_TIMESTAMP);
        assertThat(event.getValue().occurredAt()).isEqualTo(EXPECTED_TIMESTAMP);
        assertThat(response.closedAt()).isEqualTo(EXPECTED_TIMESTAMP);
        assertThat(event.getValue().quantityDelta())
                .isEqualByComparingTo("-5.0000");
        assertThat(event.getValue().quantityAfter())
                .isEqualByComparingTo("0");
    }

    @Test
    void propagatesReferenceIntegrityWithoutRelabelingItAsPantryFailure() {
        MeasurementUnitReferenceSnapshot unit = unit();
        when(units.resolveByCodes(anySet())).thenReturn(Map.of("piece", unit));
        ReferenceDataIntegrityException failure =
                new ReferenceDataIntegrityException("invalid ingredient row");
        when(ingredients.resolveActiveByPublicId(INGREDIENT_PUBLIC_ID))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.create(
                USER_PUBLIC_ID,
                createRequest()))
                .isSameAs(failure);
        verifyNoInteractions(items, events);
    }

    @Test
    void classifiesMissingPersistedCatalogReferencesPrecisely() {
        PantryItem item = item();
        when(items.findByPublicIdAndUserId(any(byte[].class), eq(USER_ID)))
                .thenReturn(Optional.of(item));
        when(ingredients.resolveByInternalIds(anySet())).thenReturn(Map.of());
        when(units.resolveByInternalIds(anySet()))
                .thenReturn(Map.of(UNIT_ID, unit()));

        assertThatThrownBy(() -> service.get(USER_PUBLIC_ID, ITEM_PUBLIC_ID))
                .isExactlyInstanceOf(ReferenceDataIntegrityException.class);
    }

    @Test
    void propagatesInfrastructureFailuresWithoutRelabelingThemAsReferenceData() {
        when(ingredients.resolveActiveByPublicId(INGREDIENT_PUBLIC_ID))
                .thenReturn(Optional.of(ingredient()));
        when(units.resolveByCodes(anySet())).thenReturn(Map.of("piece", unit()));
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(items.saveAndFlush(any(PantryItem.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.create(
                USER_PUBLIC_ID,
                createRequest()))
                .isSameAs(failure);
        verify(events, never()).saveAndFlush(any(PantryItemEvent.class));
    }

    private void stubCreateReferences() {
        IngredientReferenceSnapshot ingredient = ingredient();
        MeasurementUnitReferenceSnapshot unit = unit();
        when(ingredients.resolveActiveByPublicId(INGREDIENT_PUBLIC_ID))
                .thenReturn(Optional.of(ingredient));
        when(ingredients.resolveByInternalIds(anySet()))
                .thenReturn(Map.of(INGREDIENT_ID, ingredient));
        when(units.resolveByCodes(anySet())).thenReturn(Map.of("piece", unit));
        when(units.resolveByInternalIds(anySet()))
                .thenReturn(Map.of(UNIT_ID, unit));
    }

    private void stubResponseReferences(PantryItem item) {
        when(items.findByPublicIdAndUserId(any(byte[].class), eq(USER_ID)))
                .thenReturn(Optional.of(item));
        when(ingredients.resolveByInternalIds(anySet()))
                .thenReturn(Map.of(INGREDIENT_ID, ingredient()));
        when(units.resolveByInternalIds(anySet()))
                .thenReturn(Map.of(UNIT_ID, unit()));
    }

    private static PantryItem item() {
        PantryItem item = new PantryItem(
                ITEM_PUBLIC_ID,
                USER_ID,
                INGREDIENT_ID,
                null,
                new BigDecimal("5.0000"),
                UNIT_ID,
                PantryStorageLocation.FRIDGE,
                null,
                null,
                PantryExpiryKind.UNKNOWN,
                PantryExpiryConfidence.UNKNOWN,
                null);
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }

    private static CreatePantryItemRequest createRequest() {
        return new CreatePantryItemRequest(
                INGREDIENT_PUBLIC_ID,
                null,
                new BigDecimal("5"),
                "piece",
                PantryStorageLocation.FRIDGE,
                null,
                null,
                null,
                null,
                null);
    }

    private static IngredientReferenceSnapshot ingredient() {
        return new IngredientReferenceSnapshot(
                INGREDIENT_ID,
                INGREDIENT_PUBLIC_ID,
                "egg",
                "Trứng gà");
    }

    private static MeasurementUnitReferenceSnapshot unit() {
        return new MeasurementUnitReferenceSnapshot(
                UNIT_ID,
                "piece",
                "piece");
    }
}
