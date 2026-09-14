package com.smartmealplanner.nutrition.persistence;

import java.time.LocalDateTime;

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

@Entity
@Table(name = "nutrients")
public class Nutrient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private MeasurementUnit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "nutrient_kind", nullable = false, length = 20)
    private NutrientKind nutrientKind;

    @Column(name = "is_core", nullable = false)
    private boolean core;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

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

    protected Nutrient() {
    }

    public Nutrient(
            String code,
            String displayName,
            MeasurementUnit unit,
            NutrientKind nutrientKind,
            boolean core,
            Short displayOrder) {

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "code is required");
        }

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(
                    "displayName is required");
        }

        if (unit == null) {
            throw new IllegalArgumentException(
                    "unit is required");
        }

        if (nutrientKind == null) {
            throw new IllegalArgumentException(
                    "nutrientKind is required");
        }

        this.code = code;
        this.displayName = displayName;
        this.unit = unit;
        this.nutrientKind = nutrientKind;
        this.core = core;
        this.displayOrder = displayOrder == null
                ? (short) 1000
                : displayOrder;
    }

    public Long id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public MeasurementUnit unit() {
        return unit;
    }

    public NutrientKind nutrientKind() {
        return nutrientKind;
    }

    public boolean isCore() {
        return core;
    }

    public Short displayOrder() {
        return displayOrder;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }
}
