package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeTest {

    @Test
    void validatesRecipeFieldsAndLifecycle() {
        Recipe draft = recipe(
                RecipeStatus.DRAFT,
                null,
                null);

        assertThat(draft.status()).isEqualTo(RecipeStatus.DRAFT);

        LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 19, 10, 0);
        Recipe published = recipe(
                RecipeStatus.PUBLISHED,
                publishedAt,
                null);

        assertThat(published.publishedAt()).isEqualTo(publishedAt);
        assertThat(published.archivedAt()).isNull();

        Recipe upperBound = new Recipe(
                UUID.randomUUID(),
                "P9 upper bound recipe",
                "p9-upper-bound-" + UUID.randomUUID(),
                100,
                10080,
                10080,
                RecipeDifficulty.EASY,
                null,
                null,
                null,
                RecipeSource.CURATED,
                "P9 test",
                RecipeStatus.DRAFT,
                null,
                null);
        assertThat(upperBound.servings()).isEqualTo((short) 100);
        assertThat(upperBound.prepMinutes()).isEqualTo((short) 10080);
        assertThat(upperBound.cookMinutes()).isEqualTo((short) 10080);

        assertThatThrownBy(() -> recipe(
                RecipeStatus.DRAFT,
                publishedAt,
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> recipe(
                RecipeStatus.ARCHIVED,
                publishedAt,
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Recipe(
                " ",
                "invalid-title",
                2,
                null,
                null,
                RecipeDifficulty.EASY,
                null,
                null,
                RecipeSource.CURATED,
                null,
                RecipeStatus.DRAFT,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Recipe(
                "A valid title",
                "valid-title",
                101,
                null,
                null,
                RecipeDifficulty.EASY,
                null,
                null,
                RecipeSource.CURATED,
                null,
                RecipeStatus.DRAFT,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesIngredientQuantityUnitPairAndStepRules() {
        assertThatThrownBy(() -> new RecipeIngredient(
                1L,
                1,
                2L,
                null,
                new BigDecimal("1.0000"),
                null,
                null,
                false,
                true,
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RecipeIngredient(
                1L,
                32768,
                2L,
                null,
                null,
                null,
                null,
                false,
                true,
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RecipeIngredient(
                1L,
                1,
                2L,
                null,
                null,
                3L,
                null,
                false,
                true,
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RecipeIngredient(
                1L,
                1,
                2L,
                null,
                BigDecimal.ZERO,
                3L,
                null,
                false,
                true,
                null))
                .isInstanceOf(IllegalArgumentException.class);

        RecipeIngredient toTaste = new RecipeIngredient(
                1L,
                1,
                2L,
                null,
                null,
                null,
                "to taste",
                false,
                true,
                "Sauce");
        assertThat(toTaste.quantity()).isNull();
        assertThat(toTaste.unitId()).isNull();

        RecipeIngredient duplicateIngredient = new RecipeIngredient(
                1L,
                2,
                2L,
                null,
                null,
                null,
                null,
                false,
                true,
                null);
        assertThatThrownBy(() -> Recipe.validateIngredientLines(
                List.of(toTaste, duplicateIngredient)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RecipeStep(
                1L,
                0,
                "Mix",
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RecipeStep(
                1L,
                1,
                " ",
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RecipeStep(
                1L,
                1,
                "Mix",
                10081))
                .isInstanceOf(IllegalArgumentException.class);

        RecipeStep upperBoundStep = new RecipeStep(
                1L,
                32767,
                "Mix",
                10080);
        assertThat(upperBoundStep.stepNumber()).isEqualTo((short) 32767);
        assertThat(upperBoundStep.durationMinutes()).isEqualTo((short) 10080);

        assertThatThrownBy(() -> new RecipeStep(
                1L,
                32768,
                "Mix",
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsVersionForOptimisticLocking() throws NoSuchFieldException {
        assertThat(Recipe.class.getDeclaredField("version")
                .isAnnotationPresent(Version.class))
                .isTrue();
    }

    private static Recipe recipe(
            RecipeStatus status,
            LocalDateTime publishedAt,
            LocalDateTime archivedAt) {

        return new Recipe(
                UUID.randomUUID(),
                "P9 Recipe",
                "p9-recipe-" + UUID.randomUUID(),
                2,
                10,
                20,
                RecipeDifficulty.EASY,
                null,
                null,
                null,
                RecipeSource.CURATED,
                "P9 test",
                status,
                publishedAt,
                archivedAt);
    }
}
