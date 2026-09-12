package com.smartmealplanner.profile.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_body_measurements")
public class UserBodyMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "measured_on", nullable = false)
    private LocalDate measuredOn;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "body_fat_percent", precision = 4, scale = 1)
    private BigDecimal bodyFatPercent;

    @Column(name = "waist_cm", precision = 5, scale = 1)
    private BigDecimal waistCm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeasurementSource source;

    @Column(length = 255)
    private String note;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected UserBodyMeasurement() {
    }

    public UserBodyMeasurement(
            Long userId,
            LocalDate measuredOn,
            BigDecimal weightKg,
            BigDecimal bodyFatPercent,
            BigDecimal waistCm,
            MeasurementSource source,
            String note) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId is required");
        }

        if (measuredOn == null) {
            throw new IllegalArgumentException(
                    "measuredOn is required");
        }

        if (weightKg == null) {
            throw new IllegalArgumentException(
                    "weightKg is required");
        }

        this.userId = userId;
        this.measuredOn = measuredOn;
        this.weightKg = weightKg;
        this.bodyFatPercent = bodyFatPercent;
        this.waistCm = waistCm;
        this.source = source == null ? MeasurementSource.USER_ENTERED : source;
        this.note = note;
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public LocalDate measuredOn() {
        return measuredOn;
    }

    public BigDecimal weightKg() {
        return weightKg;
    }

    public BigDecimal bodyFatPercent() {
        return bodyFatPercent;
    }

    public BigDecimal waistCm() {
        return waistCm;
    }

    public MeasurementSource source() {
        return source;
    }

    public String note() {
        return note;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public void update(
            BigDecimal weightKg,
            BigDecimal bodyFatPercent,
            BigDecimal waistCm,
            MeasurementSource source,
            String note) {

        if (weightKg == null) {
            throw new IllegalArgumentException(
                    "weightKg is required");
        }

        this.weightKg = weightKg;
        this.bodyFatPercent = bodyFatPercent;
        this.waistCm = waistCm;
        this.source = source == null ? MeasurementSource.CORRECTED : source;
        this.note = note;
    }
}
