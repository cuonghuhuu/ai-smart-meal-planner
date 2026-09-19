package com.smartmealplanner.pantry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** One owner-scoped physical pantry lot. */
@Entity
@Table(name = "pantry_items")
class PantryItem {

    private static final int MAX_DECIMAL_PRECISION = 12;
    private static final int MAX_DECIMAL_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true,
            updatable = false, columnDefinition = "BINARY(16)")
    private byte[] publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    @Column(name = "food_id")
    private Long foodId;

    @Column(name = "quantity_initial", nullable = false,
            precision = 12, scale = 4)
    private BigDecimal quantityInitial;

    @Column(name = "quantity_remaining", nullable = false,
            precision = 12, scale = 4)
    private BigDecimal quantityRemaining;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_location", nullable = false, length = 20)
    private PantryStorageLocation storageLocation;

    @Column(name = "acquired_on")
    private LocalDate acquiredOn;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "expiry_kind", nullable = false, length = 20)
    private PantryExpiryKind expiryKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "expiry_confidence", nullable = false, length = 20)
    private PantryExpiryConfidence expiryConfidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PantryItemStatus status;

    @Column(name = "closed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime closedAt;

    @Column(length = 255)
    private String note;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected PantryItem() {
    }

    PantryItem(
            UUID publicId,
            Long userId,
            Long ingredientId,
            Long foodId,
            BigDecimal quantity,
            Long unitId,
            PantryStorageLocation storageLocation,
            LocalDate acquiredOn,
            LocalDate expiryDate,
            PantryExpiryKind expiryKind,
            PantryExpiryConfidence expiryConfidence,
            String note) {

        if (publicId == null || userId == null || ingredientId == null
                || unitId == null) {
            throw new IllegalArgumentException("Pantry identity is required");
        }
        this.publicId = PantryIds.uuidToBytes(publicId);
        this.userId = userId;
        this.ingredientId = ingredientId;
        this.foodId = foodId;
        this.quantityInitial = requirePositiveQuantity(quantity, "quantity");
        this.quantityRemaining = this.quantityInitial;
        this.unitId = unitId;
        this.storageLocation = Objects.requireNonNull(
                storageLocation, "storageLocation is required");
        validateExpiry(acquiredOn, expiryDate, expiryKind, expiryConfidence);
        this.acquiredOn = acquiredOn;
        this.expiryDate = expiryDate;
        this.expiryKind = expiryKind;
        this.expiryConfidence = expiryConfidence;
        this.status = PantryItemStatus.AVAILABLE;
        this.closedAt = null;
        this.note = optionalNote(note);
    }

    Long internalId() {
        return id;
    }

    UUID publicId() {
        return PantryIds.bytesToUuid(publicId);
    }

    Long userId() {
        return userId;
    }

    Long ingredientId() {
        return ingredientId;
    }

    Long foodId() {
        return foodId;
    }

    BigDecimal quantityInitial() {
        return quantityInitial;
    }

    BigDecimal quantityRemaining() {
        return quantityRemaining;
    }

    Long unitId() {
        return unitId;
    }

    PantryStorageLocation storageLocation() {
        return storageLocation;
    }

    LocalDate acquiredOn() {
        return acquiredOn;
    }

    LocalDate expiryDate() {
        return expiryDate;
    }

    PantryExpiryKind expiryKind() {
        return expiryKind;
    }

    PantryExpiryConfidence expiryConfidence() {
        return expiryConfidence;
    }

    PantryItemStatus status() {
        return status;
    }

    LocalDateTime closedAt() {
        return closedAt;
    }

    String note() {
        return note;
    }

    Long version() {
        return version;
    }

    LocalDateTime createdAt() {
        return createdAt;
    }

    LocalDateTime updatedAt() {
        return updatedAt;
    }

    void updateMetadata(
            PantryStorageLocation storageLocation,
            LocalDate acquiredOn,
            LocalDate expiryDate,
            PantryExpiryKind expiryKind,
            PantryExpiryConfidence expiryConfidence,
            String note) {

        this.storageLocation = Objects.requireNonNull(
                storageLocation, "storageLocation is required");
        validateExpiry(acquiredOn, expiryDate, expiryKind, expiryConfidence);
        this.acquiredOn = acquiredOn;
        this.expiryDate = expiryDate;
        this.expiryKind = expiryKind;
        this.expiryConfidence = expiryConfidence;
        this.note = optionalNote(note);
    }

    BigDecimal adjust(BigDecimal quantityDelta) {
        requireValidQuantityDelta(quantityDelta);
        requireAvailable("adjust");
        BigDecimal newRemaining = quantityRemaining.add(quantityDelta);
        if (newRemaining.signum() <= 0
                || newRemaining.compareTo(quantityInitial) > 0
                || !fitsDecimal(newRemaining)) {
            throw new IllegalArgumentException(
                    "Adjustment must leave positive remaining quantity within initial quantity");
        }
        quantityRemaining = newRemaining;
        return quantityRemaining;
    }

    BigDecimal consume(BigDecimal quantity, LocalDateTime closedAt) {
        requirePositiveQuantity(quantity, "quantity");
        requireAvailable("consume");
        if (quantity.compareTo(quantityRemaining) > 0) {
            throw new IllegalArgumentException(
                    "Consumed quantity exceeds remaining quantity");
        }
        quantityRemaining = quantityRemaining.subtract(quantity);
        if (quantityRemaining.signum() == 0) {
            if (closedAt == null) {
                throw new IllegalArgumentException("closedAt is required");
            }
            status = PantryItemStatus.CONSUMED;
            this.closedAt = closedAt;
        }
        return quantityRemaining;
    }

    BigDecimal discard(LocalDateTime closedAt) {
        if (status != PantryItemStatus.AVAILABLE
                && status != PantryItemStatus.RESERVED) {
            throw new IllegalStateException("Pantry item is already closed");
        }
        if (quantityRemaining.signum() <= 0 || closedAt == null) {
            throw new IllegalStateException("Pantry item has no discardable quantity");
        }
        BigDecimal previousRemaining = quantityRemaining;
        quantityRemaining = BigDecimal.ZERO;
        status = PantryItemStatus.DISCARDED;
        this.closedAt = closedAt;
        return previousRemaining;
    }

    private void requireAvailable(String operation) {
        if (status != PantryItemStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Only AVAILABLE pantry items can be " + operation);
        }
    }

    static BigDecimal requirePositiveQuantity(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || !fitsDecimal(value)) {
            throw new IllegalArgumentException(
                    field + " must be positive and fit DECIMAL(12,4)");
        }
        return value;
    }

    static void requireValidQuantityDelta(BigDecimal value) {
        if (value == null || value.signum() == 0 || !fitsDecimal(value)) {
            throw new IllegalArgumentException(
                    "quantityDelta must be non-zero and fit DECIMAL(12,4)");
        }
    }

    static boolean fitsDecimal(BigDecimal value) {
        if (value == null || value.scale() > MAX_DECIMAL_SCALE) {
            return false;
        }
        try {
            return value.setScale(MAX_DECIMAL_SCALE, RoundingMode.UNNECESSARY)
                    .precision() <= MAX_DECIMAL_PRECISION;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    static void validateExpiry(
            LocalDate acquiredOn,
            LocalDate expiryDate,
            PantryExpiryKind expiryKind,
            PantryExpiryConfidence expiryConfidence) {

        if (expiryKind == null || expiryConfidence == null) {
            throw new IllegalArgumentException("Expiry kind and confidence are required");
        }
        if (expiryDate == null) {
            if (expiryKind != PantryExpiryKind.UNKNOWN
                    || expiryConfidence != PantryExpiryConfidence.UNKNOWN) {
                throw new IllegalArgumentException(
                        "Unknown expiry requires UNKNOWN kind and confidence");
            }
        } else if (expiryKind == PantryExpiryKind.UNKNOWN
                || expiryConfidence == PantryExpiryConfidence.UNKNOWN) {
            throw new IllegalArgumentException(
                    "A dated expiry requires a known kind and confidence");
        }
        if (acquiredOn != null && expiryDate != null
                && expiryDate.isBefore(acquiredOn)) {
            throw new IllegalArgumentException(
                    "expiryDate must not be before acquiredOn");
        }
    }

    private static String optionalNote(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 255) {
            throw new IllegalArgumentException("note must not exceed 255 characters");
        }
        return normalized;
    }
}
