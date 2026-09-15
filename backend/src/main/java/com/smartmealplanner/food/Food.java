package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.nutrition.persistence.MeasurementUnit;
import com.smartmealplanner.nutrition.persistence.Nutrient;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Module-private mapping of the V001 nutritional fact carrier.
 * Nutrition facts and measured serving facts are owned child rows; their
 * application/query workflows arrive in later P7 slices.
 */
@Entity
@Table(name = "foods")
class Food {

    private static final BigDecimal MAX_DENSITY_G_PER_ML_EXCLUSIVE =
            new BigDecimal("25");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "public_id",
            nullable = false,
            unique = true,
            updatable = false,
            columnDefinition = "BINARY(16)")
    private byte[] publicId;

    @Column(unique = true, length = 80)
    private String code;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(length = 120)
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_category_id")
    private FoodCategory category;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "nutrition_basis", nullable = false, length = 10)
    private NutritionBasis nutritionBasis;

    @Column(name = "density_g_per_ml", precision = 8, scale = 4)
    private BigDecimal densityGPerMl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodSource source;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    /**
     * Provenance revision for nutrition/catalog corrections. This is distinct
     * from the JPA optimistic-lock {@link #version}.
     */
    @Column(nullable = false)
    private Integer revision;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "retired_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime retiredAt;

    @OneToMany(
            mappedBy = "food",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<FoodNutrient> nutrientFacts = new ArrayList<>();

    @OneToMany(
            mappedBy = "food",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<FoodServing> servings = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Food() {
    }

    Food(
            String displayName,
            FoodCategory category) {

        this(
                null,
                displayName,
                null,
                category,
                null,
                NutritionBasis.PER_100_G,
                null,
                FoodSource.CURATED,
                null);
    }

    Food(
            String code,
            String displayName,
            String brand,
            FoodCategory category,
            String description,
            NutritionBasis nutritionBasis,
            BigDecimal densityGPerMl,
            FoodSource source,
            String sourceReference) {

        this.publicId = uuidToBytes(UUID.randomUUID());
        this.code = optionalText(code, 80, "code");
        this.displayName = requireText(displayName, 200, "displayName");
        this.brand = optionalText(brand, 120, "brand");
        this.category = category;
        this.description = optionalText(description, 500, "description");

        if (nutritionBasis == null) {
            throw new IllegalArgumentException("nutritionBasis is required");
        }

        this.nutritionBasis = nutritionBasis;
        this.densityGPerMl = requireValidDensity(densityGPerMl);

        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }

        this.source = source;
        this.sourceReference = optionalText(
                sourceReference,
                255,
                "sourceReference");
        this.revision = 1;
        this.active = true;
        this.retiredAt = null;
    }

    Long internalId() {
        return id;
    }

    byte[] publicIdBytes() {
        return publicId.clone();
    }

    UUID publicId() {
        ByteBuffer bytes = ByteBuffer.wrap(publicId);
        return new UUID(
                bytes.getLong(),
                bytes.getLong());
    }

    String code() {
        return code;
    }

    String displayName() {
        return displayName;
    }

    String brand() {
        return brand;
    }

    FoodCategory category() {
        return category;
    }

    String description() {
        return description;
    }

    NutritionBasis nutritionBasis() {
        return nutritionBasis;
    }

    BigDecimal densityGPerMl() {
        return densityGPerMl;
    }

    FoodSource source() {
        return source;
    }

    String sourceReference() {
        return sourceReference;
    }

    Integer revision() {
        return revision;
    }

    boolean isActive() {
        return active;
    }

    LocalDateTime retiredAt() {
        return retiredAt;
    }

    List<FoodNutrient> nutrientFacts() {
        return List.copyOf(nutrientFacts);
    }

    List<FoodServing> servings() {
        return List.copyOf(servings);
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

    void rename(
            String name) {

        displayName = requireText(name, 200, "displayName");
    }

    void retire(
            LocalDateTime retiredAt) {

        if (retiredAt == null) {
            throw new IllegalArgumentException("retiredAt is required");
        }

        if (!active) {
            throw new IllegalStateException("Food is already retired");
        }

        this.active = false;
        this.retiredAt = retiredAt;
    }

    void reactivate() {
        if (active) {
            throw new IllegalStateException("Food is already active");
        }

        active = true;
        retiredAt = null;
    }

    FoodNutrient addNutrient(
            Nutrient nutrient,
            BigDecimal amount,
            FoodNutrientDataQuality dataQuality) {

        if (nutrient == null) {
            throw new IllegalArgumentException("nutrient is required");
        }

        if (nutrientFacts.stream()
                .anyMatch(existing -> existing.nutrient().code()
                        .equals(nutrient.code()))) {

            throw new IllegalArgumentException(
                    "A food nutrient fact already exists for nutrient "
                            + nutrient.code());
        }

        FoodNutrient foodNutrient = new FoodNutrient(
                this,
                nutrient,
                amount,
                dataQuality);
        nutrientFacts.add(foodNutrient);
        return foodNutrient;
    }

    void correctNutrient(
            Nutrient nutrient,
            BigDecimal amount,
            FoodNutrientDataQuality dataQuality) {

        FoodNutrient foodNutrient = nutrientFacts.stream()
                .filter(existing -> existing.nutrient().code()
                        .equals(nutrient == null ? null : nutrient.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No food nutrient fact exists for nutrient "
                                + (nutrient == null ? null : nutrient.code())));

        foodNutrient.correct(amount, dataQuality);
    }

    FoodServing addServing(
            String displayName,
            BigDecimal quantity,
            MeasurementUnit unit,
            BigDecimal gramWeight,
            BigDecimal milliliters,
            boolean defaultServing) {

        if (servings.stream()
                .anyMatch(existing -> existing.displayName().equals(displayName))) {

            throw new IllegalArgumentException(
                    "A food serving already exists with displayName "
                            + displayName);
        }

        if (defaultServing
                && servings.stream().anyMatch(FoodServing::isDefaultServing)) {

            throw new IllegalStateException(
                    "A food may have only one default serving");
        }

        FoodServing foodServing = new FoodServing(
                this,
                displayName,
                quantity,
                unit,
                gramWeight,
                milliliters,
                defaultServing);
        servings.add(foodServing);
        return foodServing;
    }

    /**
     * Records one complete nutrition-relevant correction after its nutrient,
     * basis, density, or serving bridge facts have been applied. A future
     * curation workflow must call this once per correction command, not per
     * individual field update.
     */
    void recordNutritionCorrection() {
        if (revision == Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Food nutrition revision cannot be incremented further");
        }

        revision++;
    }

    private static String requireText(
            String value,
            int maximumLength,
            String field) {

        if (value == null
                || value.isBlank()
                || value.length() > maximumLength) {

            throw new IllegalArgumentException(
                    "Invalid " + field);
        }

        return value;
    }

    private static String optionalText(
            String value,
            int maximumLength,
            String field) {

        if (value != null
                && value.length() > maximumLength) {

            throw new IllegalArgumentException(
                    "Invalid " + field);
        }

        return value;
    }

    private static BigDecimal requireValidDensity(
            BigDecimal densityGPerMl) {

        if (densityGPerMl != null
                && (densityGPerMl.signum() <= 0
                || densityGPerMl.compareTo(
                MAX_DENSITY_G_PER_ML_EXCLUSIVE) >= 0)) {

            throw new IllegalArgumentException(
                    "densityGPerMl must be greater than zero and less than 25");
        }

        return densityGPerMl;
    }

    private static byte[] uuidToBytes(
            UUID uuid) {

        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
