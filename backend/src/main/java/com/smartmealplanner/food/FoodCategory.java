package com.smartmealplanner.food;

import jakarta.persistence.*;

/** Module-private reference mapping. Vocabulary lookup uses code, never hardcoded IDs. */
@Entity
@Table(name = "food_categories")
class FoodCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 60)
    private String code;
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    protected FoodCategory() {}

    String code() { return code; }
}
