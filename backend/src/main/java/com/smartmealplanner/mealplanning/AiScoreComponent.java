package com.smartmealplanner.mealplanning;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_score_components")
class AiScoreComponent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 60)
    private String code;
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;
    @Column(name = "scale_min", nullable = false, precision = 10, scale = 4)
    private BigDecimal scaleMin;
    @Column(name = "scale_max", nullable = false, precision = 10, scale = 4)
    private BigDecimal scaleMax;
    @Column(name = "higher_is_better", nullable = false)
    private boolean higherIsBetter;

    protected AiScoreComponent() {
    }

    Long id() { return id; }
    String code() { return code; }
    BigDecimal scaleMin() { return scaleMin; }
    BigDecimal scaleMax() { return scaleMax; }
    boolean higherIsBetter() { return higherIsBetter; }
}
