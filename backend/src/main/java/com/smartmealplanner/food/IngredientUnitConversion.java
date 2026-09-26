package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smartmealplanner.nutrition.persistence.MeasurementUnit;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ingredient_unit_conversions", uniqueConstraints = @UniqueConstraint(
        name = "ux_ingredient_unit_conversions_pair", columnNames = {"ingredient_id", "from_unit_id", "to_unit_id"}))
class IngredientUnitConversion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ingredient_id", nullable = false) private Ingredient ingredient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "from_unit_id", nullable = false) private MeasurementUnit fromUnit;
    @Column(name = "from_quantity", nullable = false, precision = 12, scale = 4) private BigDecimal fromQuantity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "to_unit_id", nullable = false) private MeasurementUnit toUnit;
    @Column(name = "to_quantity", nullable = false, precision = 12, scale = 4) private BigDecimal toQuantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private IngredientUnitConversionConfidence confidence;
    @Column(name = "source_note", length = 255) private String sourceNote;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "DATETIME(6)") private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "DATETIME(6)") private LocalDateTime updatedAt;
    protected IngredientUnitConversion() { }
    IngredientUnitConversion(Ingredient ingredient, MeasurementUnit fromUnit, BigDecimal fromQuantity,
            MeasurementUnit toUnit, BigDecimal toQuantity, IngredientUnitConversionConfidence confidence, String sourceNote) {
        if (ingredient == null) throw new IllegalArgumentException("ingredient is required");
        if (fromUnit == null || toUnit == null) throw new IllegalArgumentException("fromUnit and toUnit are required");
        if (fromUnit.id() == null || toUnit.id() == null) throw new IllegalArgumentException("unit IDs are required");
        if (fromUnit.id().equals(toUnit.id())) throw new IllegalArgumentException("fromUnit and toUnit must differ");
        if (fromQuantity == null || fromQuantity.signum() <= 0 || toQuantity == null || toQuantity.signum() <= 0)
            throw new IllegalArgumentException("conversion quantities must be positive");
        if (confidence == null) throw new IllegalArgumentException("confidence is required");
        if (sourceNote != null && sourceNote.length() > 255) throw new IllegalArgumentException("Invalid sourceNote");
        this.ingredient = ingredient; this.fromUnit = fromUnit; this.fromQuantity = fromQuantity;
        this.toUnit = toUnit; this.toQuantity = toQuantity; this.confidence = confidence; this.sourceNote = sourceNote;
    }
    Long ingredientId() { return ingredient == null ? null : ingredient.internalId(); }
    MeasurementUnit fromUnit() { return fromUnit; }
    BigDecimal fromQuantity() { return fromQuantity; }
    MeasurementUnit toUnit() { return toUnit; }
    BigDecimal toQuantity() { return toQuantity; }
    IngredientUnitConversionConfidence confidence() { return confidence; }
    String sourceNote() { return sourceNote; }
}
