package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.nutrition.persistence.MeasurementUnit;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Canonical culinary identity. It deliberately remains distinct from Food. */
@Entity
@Table(name = "ingredients")
class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false,
            columnDefinition = "BINARY(16)")
    private byte[] publicId;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_category_id")
    private FoodCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_food_id")
    private Food defaultFood;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_unit_id")
    private MeasurementUnit defaultUnit;

    @Column(name = "piece_gram_weight", precision = 10, scale = 4)
    private BigDecimal pieceGramWeight;

    @Column(name = "typical_shelf_life_days")
    private Short typicalShelfLifeDays;

    @Column(name = "is_staple", nullable = false)
    private boolean staple;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "retired_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime retiredAt;

    @OneToMany(mappedBy = "ingredient", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IngredientAlias> aliases = new ArrayList<>();

    @OneToMany(mappedBy = "ingredient", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IngredientFood> foodMappings = new ArrayList<>();

    @OneToMany(mappedBy = "ingredient", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IngredientAllergen> allergenFacts = new ArrayList<>();

    @OneToMany(mappedBy = "ingredient", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IngredientUnitConversion> unitConversions = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Ingredient() {
    }

    Ingredient(String code, String displayName, FoodCategory category,
            Food defaultFood, MeasurementUnit defaultUnit,
            BigDecimal pieceGramWeight, Short typicalShelfLifeDays,
            boolean staple) {
        this.publicId = CatalogIds.uuidToBytes(UUID.randomUUID());
        this.code = requireText(code, 80, "code");
        this.displayName = requireText(displayName, 150, "displayName");
        this.category = category;
        this.defaultFood = defaultFood;
        this.defaultUnit = defaultUnit;
        this.pieceGramWeight = positiveOrNull(pieceGramWeight, "pieceGramWeight");
        if (typicalShelfLifeDays != null
                && (typicalShelfLifeDays <= 0 || typicalShelfLifeDays > 3650)) {
            throw new IllegalArgumentException("typicalShelfLifeDays must be between 1 and 3650");
        }
        this.typicalShelfLifeDays = typicalShelfLifeDays;
        this.staple = staple;
        this.active = true;
    }

    Long internalId() { return id; }
    byte[] publicIdBytes() { return publicId.clone(); }
    UUID publicId() {
        ByteBuffer bytes = ByteBuffer.wrap(publicId);
        return new UUID(bytes.getLong(), bytes.getLong());
    }
    String code() { return code; }
    String displayName() { return displayName; }
    FoodCategory category() { return category; }
    Food defaultFood() { return defaultFood; }
    MeasurementUnit defaultUnit() { return defaultUnit; }
    BigDecimal pieceGramWeight() { return pieceGramWeight; }
    Short typicalShelfLifeDays() { return typicalShelfLifeDays; }
    boolean isStaple() { return staple; }
    boolean isActive() { return active; }
    LocalDateTime retiredAt() { return retiredAt; }
    Long version() { return version; }
    LocalDateTime createdAt() { return createdAt; }
    LocalDateTime updatedAt() { return updatedAt; }
    List<IngredientAlias> aliases() { return List.copyOf(aliases); }
    List<IngredientFood> foodMappings() { return List.copyOf(foodMappings); }
    List<IngredientAllergen> allergenFacts() { return List.copyOf(allergenFacts); }
    List<IngredientUnitConversion> unitConversions() { return List.copyOf(unitConversions); }

    void retire(LocalDateTime retirementTime) {
        if (retirementTime == null) throw new IllegalArgumentException("retiredAt is required");
        if (!active) throw new IllegalStateException("Ingredient is already retired");
        active = false;
        retiredAt = retirementTime;
    }

    void reactivate() {
        if (active) throw new IllegalStateException("Ingredient is already active");
        active = true;
        retiredAt = null;
    }

    IngredientAlias addAlias(String alias, String locale) {
        if (aliases.stream().anyMatch(existing -> existing.alias().equals(alias))) {
            throw new IllegalArgumentException("An ingredient alias already exists: " + alias);
        }
        IngredientAlias value = new IngredientAlias(this, alias, locale);
        aliases.add(value);
        return value;
    }

    IngredientFood addFoodMapping(Food food, IngredientPreparationState preparationState,
            BigDecimal yieldFactor, boolean primary) {
        if (food == null) throw new IllegalArgumentException("food is required");
        if (foodMappings.stream().anyMatch(existing -> existing.food().internalId().equals(food.internalId()))) {
            throw new IllegalArgumentException("A mapping already exists for this food");
        }
        if (primary && foodMappings.stream().anyMatch(IngredientFood::isPrimary)) {
            throw new IllegalStateException("An ingredient may have only one primary food mapping");
        }
        if (primary && defaultFood != null && !defaultFood.internalId().equals(food.internalId())) {
            throw new IllegalArgumentException("Primary food mapping must match defaultFood");
        }
        if (primary && defaultFood == null) defaultFood = food;
        IngredientFood value = new IngredientFood(this, food, preparationState, yieldFactor, primary);
        foodMappings.add(value);
        return value;
    }

    IngredientAllergen addAllergenFact(Long allergenId,
            IngredientAllergenPresence presence, String note) {
        if (allergenFacts.stream().anyMatch(existing -> existing.allergenId().equals(allergenId))) {
            throw new IllegalArgumentException("An allergen fact already exists for this allergen");
        }
        IngredientAllergen value = new IngredientAllergen(this, allergenId, presence, note);
        allergenFacts.add(value);
        return value;
    }

    IngredientUnitConversion addUnitConversion(MeasurementUnit fromUnit, BigDecimal fromQuantity,
            MeasurementUnit toUnit, BigDecimal toQuantity,
            IngredientUnitConversionConfidence confidence, String sourceNote) {
        if (unitConversions.stream().anyMatch(existing -> existing.fromUnit().code().equals(fromUnit == null ? null : fromUnit.code())
                && existing.toUnit().code().equals(toUnit == null ? null : toUnit.code()))) {
            throw new IllegalArgumentException("A conversion already exists for this ordered unit pair");
        }
        IngredientUnitConversion value = new IngredientUnitConversion(this, fromUnit, fromQuantity,
                toUnit, toQuantity, confidence, sourceNote);
        unitConversions.add(value);
        return value;
    }

    private static String requireText(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value;
    }

    private static BigDecimal positiveOrNull(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
