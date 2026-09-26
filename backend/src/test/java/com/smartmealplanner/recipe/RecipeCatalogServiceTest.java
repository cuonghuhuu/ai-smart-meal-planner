package com.smartmealplanner.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.smartmealplanner.food.FoodReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.NutritionReferenceQueryService;
import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** Unit coverage for the Recipe catalog reference-integrity boundary. */
class RecipeCatalogServiceTest {

    @Test
    void mapsKnownReferenceIntegrityFailureToCorruptedRecipeData() {
        RecipeCatalogService service = serviceWithIngredientFailure(
                new ReferenceDataIntegrityException("invalid ingredient row"));

        Throwable thrown = catchThrowable(
                () -> service.getRecipe(UUID.randomUUID()));

        assertThat(thrown).isInstanceOf(RecipeException.class);
        assertThat(((RecipeException) thrown).failure())
                .isEqualTo(RecipeFailure.CORRUPTED_RECIPE_DATA);
    }

    @Test
    void propagatesInfrastructureFailureInsteadOfCallingItCorruptedData() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        RecipeCatalogService service = serviceWithIngredientFailure(failure);

        Throwable thrown = catchThrowable(
                () -> service.getRecipe(UUID.randomUUID()));

        assertThat(thrown).isSameAs(failure);
    }

    private static RecipeCatalogService serviceWithIngredientFailure(
            RuntimeException failure) {
        RecipeRepository recipes = mock(RecipeRepository.class);
        Recipe recipe = mock(Recipe.class);
        when(recipe.internalId()).thenReturn(1L);
        when(recipe.publicId()).thenReturn(UUID.randomUUID());
        when(recipes.findPublishedByPublicId(any(byte[].class)))
                .thenReturn(java.util.Optional.of(recipe));

        RecipeIngredientRepository ingredients = mock(RecipeIngredientRepository.class);
        RecipeIngredient line = new RecipeIngredient(
                1L,
                1,
                2L,
                null,
                null,
                null,
                null,
                false,
                true,
                null);
        when(ingredients.findByRecipeIdOrderByLineNumberAsc(1L))
                .thenReturn(List.of(line));

        IngredientReferenceQueryService ingredientReferences =
                mock(IngredientReferenceQueryService.class);
        when(ingredientReferences.resolveByInternalIds(anyCollection()))
                .thenThrow(failure);

        return new RecipeCatalogService(
                recipes,
                ingredients,
                mock(RecipeStepRepository.class),
                mock(RecipeTagRepository.class),
                mock(MealSlotTypeRepository.class),
                mock(RecipeNutritionSnapshotRepository.class),
                mock(RecipeNutritionValueRepository.class),
                ingredientReferences,
                mock(FoodReferenceQueryService.class),
                mock(MeasurementUnitReferenceQueryService.class),
                mock(NutritionReferenceQueryService.class));
    }
}
