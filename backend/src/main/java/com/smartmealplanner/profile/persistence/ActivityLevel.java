package com.smartmealplanner.profile.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "activity_levels")
public class ActivityLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(length = 255)
    private String description;

    @Column(name = "energy_factor", nullable = false, precision = 4, scale = 3)
    private BigDecimal energyFactor;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected ActivityLevel() {
    }

    public ActivityLevel(
            String code,
            String displayName,
            String description,
            BigDecimal energyFactor,
            Integer displayOrder) {

        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.energyFactor = energyFactor;
        this.displayOrder = displayOrder == null ? 1000 : displayOrder;
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

    public String description() {
        return description;
    }

    public BigDecimal energyFactor() {
        return energyFactor;
    }

    public Integer displayOrder() {
        return displayOrder;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }
}
