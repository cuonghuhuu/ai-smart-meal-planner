package com.smartmealplanner.nutrition.persistence;

import java.math.BigDecimal;
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
@Table(name = "measurement_units")
public class MeasurementUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 10)
    private MeasurementUnitType unitType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_unit_id")
    private MeasurementUnit baseUnit;

    @Column(name = "factor_to_base_unit", precision = 24, scale = 12)
    private BigDecimal factorToBaseUnit;

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

    protected MeasurementUnit() {
    }

    public MeasurementUnit(
            String code,
            String displayName,
            MeasurementUnitType unitType,
            MeasurementUnit baseUnit,
            BigDecimal factorToBaseUnit) {

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "code is required");
        }

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(
                    "displayName is required");
        }

        if (unitType == null) {
            throw new IllegalArgumentException(
                    "unitType is required");
        }

        if ((baseUnit == null) != (factorToBaseUnit == null)) {
            throw new IllegalArgumentException(
                    "baseUnit and factorToBaseUnit must be specified together");
        }

        if (factorToBaseUnit != null
                && factorToBaseUnit.signum() <= 0) {

            throw new IllegalArgumentException(
                    "factorToBaseUnit must be positive");
        }

        this.code = code;
        this.displayName = displayName;
        this.unitType = unitType;
        this.baseUnit = baseUnit;
        this.factorToBaseUnit = factorToBaseUnit;
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

    public MeasurementUnitType unitType() {
        return unitType;
    }

    public MeasurementUnit baseUnit() {
        return baseUnit;
    }

    public BigDecimal factorToBaseUnit() {
        return factorToBaseUnit;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }
}
