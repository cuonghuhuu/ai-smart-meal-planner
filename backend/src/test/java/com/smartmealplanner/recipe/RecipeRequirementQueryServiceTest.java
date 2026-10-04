package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceSnapshot;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeRequirementQueryServiceTest {

    private static final UUID RECIPE_ID = UUID.randomUUID();
    private static final UUID INGREDIENT_ID = UUID.randomUUID();

    @Mock RecipeRepository recipes;
    @Mock RecipeIngredientRepository ingredients;
    @Mock IngredientReferenceQueryService ingredientReferences;
    @Mock MeasurementUnitReferenceQueryService unitReferences;

    @Test
    void readsArchivedRecipeCompositionWithoutPublishedCatalogFilter() {
        Recipe recipe = mock(Recipe.class);
        when(recipe.internalId()).thenReturn(11L);
        when(recipe.publicId()).thenReturn(RECIPE_ID);
        when(recipe.servings()).thenReturn((short) 4);
        when(recipes.findAllByPublicIdIn(any())).thenReturn(List.of(recipe));

        RecipeIngredient line = mock(RecipeIngredient.class);
        when(line.recipeId()).thenReturn(11L);
        when(line.lineNumber()).thenReturn((short) 1);
        when(line.ingredientId()).thenReturn(22L);
        when(line.unitId()).thenReturn(33L);
        when(line.quantity()).thenReturn(new BigDecimal("600.0000"));
        when(line.isOptional()).thenReturn(false);
        when(ingredients.findByRecipeIds(any())).thenReturn(List.of(line));

        when(ingredientReferences.resolveByInternalIds(any())).thenReturn(Map.of(
                22L, new IngredientReferenceSnapshot(
                        22L, INGREDIENT_ID, "chicken", "Chicken")));
        when(unitReferences.resolveByInternalIds(any())).thenReturn(Map.of(
                33L, new MeasurementUnitReferenceSnapshot(
                        33L, "g", "Gram")));

        RecipeRequirementQueryService service =
                new RecipeRequirementQueryService(
                        recipes, ingredients, ingredientReferences, unitReferences);

        Map<UUID, RecipeRequirementSnapshot> result =
                service.requirementsFor(List.of(RECIPE_ID));

        assertThat(result).containsOnlyKeys(RECIPE_ID);
        RecipeRequirementSnapshot snapshot = result.get(RECIPE_ID);
        assertThat(snapshot.baseServings()).isEqualByComparingTo("4");
        assertThat(snapshot.ingredients()).singleElement().satisfies(requirement -> {
            assertThat(requirement.ingredientPublicId()).isEqualTo(INGREDIENT_ID);
            assertThat(requirement.ingredientCode()).isEqualTo("chicken");
            assertThat(requirement.ingredientDisplayName()).isEqualTo("Chicken");
            assertThat(requirement.quantity()).isEqualByComparingTo("600.0000");
            assertThat(requirement.unitCode()).isEqualTo("g");
            assertThat(requirement.optional()).isFalse();
        });
    }
}
