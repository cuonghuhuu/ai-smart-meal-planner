package com.smartmealplanner.recipe;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipe_tag_assignments")
@IdClass(RecipeTagAssignmentId.class)
class RecipeTagAssignment {

    @Id
    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected RecipeTagAssignment() {
    }

    RecipeTagAssignment(Long recipeId, Long tagId) {
        if (recipeId == null || tagId == null) {
            throw new IllegalArgumentException("recipeId and tagId are required");
        }
        this.recipeId = recipeId;
        this.tagId = tagId;
    }

    Long recipeId() { return recipeId; }
    Long tagId() { return tagId; }
    LocalDateTime createdAt() { return createdAt; }
}
