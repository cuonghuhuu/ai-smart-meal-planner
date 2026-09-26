package com.smartmealplanner.recipe;

import java.io.Serializable;
import java.util.Objects;

final class RecipeTagAssignmentId implements Serializable {
    private Long recipeId;
    private Long tagId;

    protected RecipeTagAssignmentId() {
    }

    RecipeTagAssignmentId(Long recipeId, Long tagId) {
        this.recipeId = recipeId;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeTagAssignmentId value)) return false;
        return Objects.equals(recipeId, value.recipeId)
                && Objects.equals(tagId, value.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipeId, tagId);
    }
}
