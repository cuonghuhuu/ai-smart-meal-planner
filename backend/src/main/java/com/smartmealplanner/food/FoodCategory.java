package com.smartmealplanner.food;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

/**
 * Module-private food-category reference mapping. Vocabulary lookup uses code,
 * never hard-coded surrogate IDs.
 */
@Entity
@BatchSize(size = 100)
@Table(name = "food_categories")
class FoodCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private FoodCategory parentCategory;

    @Column(length = 255)
    private String description;

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

    protected FoodCategory() {
    }

    FoodCategory(
            String code,
            String displayName,
            FoodCategory parentCategory,
            String description) {

        this.code = requireText(code, 60, "code");
        this.displayName = requireText(displayName, 120, "displayName");
        this.parentCategory = parentCategory;
        this.description = optionalText(description, 255, "description");
    }

    String code() {
        return code;
    }

    String displayName() {
        return displayName;
    }

    FoodCategory parentCategory() {
        return parentCategory;
    }

    String description() {
        return description;
    }

    LocalDateTime createdAt() {
        return createdAt;
    }

    LocalDateTime updatedAt() {
        return updatedAt;
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
}
