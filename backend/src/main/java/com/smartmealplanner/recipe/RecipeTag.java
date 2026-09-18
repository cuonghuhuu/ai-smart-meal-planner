package com.smartmealplanner.recipe;

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
@Table(name = "recipe_tags")
class RecipeTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_kind", nullable = false, length = 20)
    private RecipeTagKind tagKind;

    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected RecipeTag() {
    }

    RecipeTag(String code, String displayName, RecipeTagKind tagKind) {
        if (code == null || code.isBlank() || code.length() > 60) {
            throw new IllegalArgumentException("Invalid code");
        }
        if (displayName == null || displayName.isBlank()
                || displayName.length() > 120) {
            throw new IllegalArgumentException("Invalid displayName");
        }
        if (tagKind == null) {
            throw new IllegalArgumentException("tagKind is required");
        }
        this.code = code;
        this.displayName = displayName;
        this.tagKind = tagKind;
    }

    Long internalId() { return id; }
    String code() { return code; }
    String displayName() { return displayName; }
    RecipeTagKind tagKind() { return tagKind; }
    LocalDateTime createdAt() { return createdAt; }
    LocalDateTime updatedAt() { return updatedAt; }
}
