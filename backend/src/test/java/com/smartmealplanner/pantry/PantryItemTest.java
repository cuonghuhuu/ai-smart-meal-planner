package com.smartmealplanner.pantry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Version;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PantryItemTest {

    private static final UUID ITEM_PUBLIC_ID = UUID.randomUUID();
    private static final LocalDateTime CLOSED_AT =
            LocalDateTime.of(2026, 9, 19, 12, 0);

    @Test
    void createsAvailableLotWithInitialAndRemainingQuantityEqual() {
        PantryItem item = item(new BigDecimal("500.0000"));

        assertThat(item.publicId()).isEqualTo(ITEM_PUBLIC_ID);
        assertThat(item.quantityInitial()).isEqualByComparingTo("500.0000");
        assertThat(item.quantityRemaining()).isEqualByComparingTo("500.0000");
        assertThat(item.status()).isEqualTo(PantryItemStatus.AVAILABLE);
        assertThat(item.closedAt()).isNull();
        assertThat(item.expiryKind()).isEqualTo(PantryExpiryKind.UNKNOWN);
        assertThat(item.expiryConfidence())
                .isEqualTo(PantryExpiryConfidence.UNKNOWN);
    }

    @Test
    void validatesUnknownAndDatedExpiryWithoutInventingDates() {
        PantryItem unknown = item(new BigDecimal("1.0000"));
        assertThat(unknown.expiryDate()).isNull();

        PantryItem dated = new PantryItem(
                UUID.randomUUID(),
                10L,
                20L,
                null,
                new BigDecimal("1.0000"),
                30L,
                PantryStorageLocation.FRIDGE,
                LocalDate.of(2026, 9, 19),
                LocalDate.of(2026, 9, 22),
                PantryExpiryKind.USE_BY,
                PantryExpiryConfidence.LABELLED,
                null);
        assertThat(dated.expiryDate())
                .isEqualTo(LocalDate.of(2026, 9, 22));

        assertThatThrownBy(() -> new PantryItem(
                UUID.randomUUID(), 10L, 20L, null, new BigDecimal("1.0000"),
                30L, PantryStorageLocation.FRIDGE,
                LocalDate.of(2026, 9, 19), null,
                PantryExpiryKind.USE_BY, PantryExpiryConfidence.LABELLED, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PantryItem(
                UUID.randomUUID(), 10L, 20L, null, new BigDecimal("1.0000"),
                30L, PantryStorageLocation.FRIDGE,
                LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 19),
                PantryExpiryKind.USE_BY, PantryExpiryConfidence.LABELLED, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PantryItem(
                UUID.randomUUID(), 10L, 20L, null, new BigDecimal("1.0000"),
                30L, PantryStorageLocation.FRIDGE,
                LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 22),
                PantryExpiryKind.UNKNOWN, PantryExpiryConfidence.LABELLED, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PantryItem(
                UUID.randomUUID(), 10L, 20L, null, new BigDecimal("1.0000"),
                30L, PantryStorageLocation.FRIDGE,
                LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 22),
                PantryExpiryKind.USE_BY, PantryExpiryConfidence.UNKNOWN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidQuantitiesWithoutSilentDecimalWrapping() {
        assertThatThrownBy(() -> item(new BigDecimal("0.0000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(new BigDecimal("1.00000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(new BigDecimal("100000000.0000")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(PantryItem.fitsDecimal(new BigDecimal("99999999.9999")))
                .isTrue();
    }

    @Test
    void adjustsWithinInitialQuantityAndRejectsZeroOrOverflow() {
        PantryItem item = item(new BigDecimal("500.0000"));

        assertThat(item.adjust(new BigDecimal("-25.0000")))
                .isEqualByComparingTo("475.0000");
        assertThat(item.adjust(new BigDecimal("25.0000")))
                .isEqualByComparingTo("500.0000");

        assertThatThrownBy(() -> item.adjust(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.adjust(new BigDecimal("-500.0000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.adjust(new BigDecimal("-501.0000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.adjust(new BigDecimal("1.0000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(item.quantityRemaining()).isEqualByComparingTo("500.0000");
        assertThat(item.status()).isEqualTo(PantryItemStatus.AVAILABLE);
    }

    @Test
    void partialAndFullConsumeHaveDifferentLifecycleStates() {
        PantryItem item = item(new BigDecimal("500.0000"));

        assertThat(item.consume(new BigDecimal("100.0000"), CLOSED_AT))
                .isEqualByComparingTo("400.0000");
        assertThat(item.status()).isEqualTo(PantryItemStatus.AVAILABLE);
        assertThat(item.closedAt()).isNull();

        assertThat(item.consume(new BigDecimal("400.0000"), CLOSED_AT))
                .isEqualByComparingTo("0.0000");
        assertThat(item.status()).isEqualTo(PantryItemStatus.CONSUMED);
        assertThat(item.closedAt()).isEqualTo(CLOSED_AT);

        assertThatThrownBy(() -> item.consume(new BigDecimal("1.0000"), CLOSED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void discardClosesLotAndReturnsTheDiscardedQuantity() {
        PantryItem item = item(new BigDecimal("250.0000"));

        assertThat(item.discard(CLOSED_AT)).isEqualByComparingTo("250.0000");
        assertThat(item.quantityRemaining()).isEqualByComparingTo("0.0000");
        assertThat(item.status()).isEqualTo(PantryItemStatus.DISCARDED);
        assertThat(item.closedAt()).isEqualTo(CLOSED_AT);

        assertThatThrownBy(() -> item.discard(CLOSED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mapsOptimisticVersionAndKeepsEventsAppendOnlyByConstruction()
            throws NoSuchFieldException {
        assertThat(PantryItem.class.getDeclaredField("version")
                .isAnnotationPresent(Version.class))
                .isTrue();

        PantryItemEvent event = new PantryItemEvent(
                42L,
                PantryItemEventType.CONSUMED,
                new BigDecimal("-10.0000"),
                new BigDecimal("90.0000"),
                "used",
                CLOSED_AT);
        assertThat(event.pantryItemId()).isEqualTo(42L);
        assertThat(event.quantityDelta()).isEqualByComparingTo("-10.0000");
        assertThat(event.quantityAfter()).isEqualByComparingTo("90.0000");
        assertThat(event.mealPlanEntryId()).isNull();
    }

    private static PantryItem item(BigDecimal quantity) {
        return new PantryItem(
                ITEM_PUBLIC_ID,
                10L,
                20L,
                null,
                quantity,
                30L,
                PantryStorageLocation.PANTRY,
                null,
                null,
                PantryExpiryKind.UNKNOWN,
                PantryExpiryConfidence.UNKNOWN,
                null);
    }
}
