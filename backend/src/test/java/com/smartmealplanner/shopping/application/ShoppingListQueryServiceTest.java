package com.smartmealplanner.shopping.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartmealplanner.mealplanning.application.PersistedMealPlanQueryService;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.nutrition.application.PlanningUnitQueryService;
import com.smartmealplanner.nutrition.application.PlanningUnitSnapshot;
import com.smartmealplanner.pantry.PantryAvailabilityQueryService;
import com.smartmealplanner.pantry.PantryAvailabilitySnapshot;
import com.smartmealplanner.pantry.PantryExpiryKind;
import com.smartmealplanner.pantry.PantryStorageLocation;
import com.smartmealplanner.recipe.RecipeRequirementQueryService;
import com.smartmealplanner.recipe.RecipeRequirementSnapshot;
import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingListQueryServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final UUID RECIPE_A = UUID.randomUUID();
    private static final UUID RECIPE_B = UUID.randomUUID();
    private static final UUID CHICKEN = UUID.randomUUID();
    private static final UUID SALT = UUID.randomUUID();
    private static final UUID GARNISH = UUID.randomUUID();
    private static final LocalDate DAY_ONE = LocalDate.of(2026, 10, 5);

    @Mock PersistedMealPlanQueryService mealPlans;
    @Mock RecipeRequirementQueryService recipes;
    @Mock PantryAvailabilityQueryService pantry;
    @Mock PlanningUnitQueryService units;
    @InjectMocks ShoppingListQueryService service;

    @Test
    void scalesAggregatesAndAllocatesPantryByMealDateAndExpiry() {
        when(mealPlans.get(USER, PLAN)).thenReturn(plan(
                List.of(
                        entry(DAY_ONE, RECIPE_A, "2.00"),
                        entry(DAY_ONE.plusDays(1), RECIPE_B, "2.00")),
                GenerationStatus.DEGRADED));
        when(recipes.requirementsFor(any())).thenReturn(Map.of(
                RECIPE_A, recipe(RECIPE_A, "4",
                        quantified(CHICKEN, "chicken", "Chicken", "600", "g", false),
                        unquantified(SALT, "salt", "Salt", false),
                        quantified(GARNISH, "herb", "Herb", "20", "g", true)),
                RECIPE_B, recipe(RECIPE_B, "2",
                        quantified(CHICKEN, "chicken", "Chicken", "0.5", "kg", false))));
        when(pantry.availableFor(USER)).thenReturn(List.of(
                lot(CHICKEN, "400", "g", DAY_ONE, PantryExpiryKind.USE_BY),
                lot(CHICKEN, "0.25", "kg", null, PantryExpiryKind.UNKNOWN)));
        when(units.definitionsFor(any())).thenReturn(List.of(
                new PlanningUnitSnapshot("g", "MASS", "g", BigDecimal.ONE),
                new PlanningUnitSnapshot("kg", "MASS", "g", new BigDecimal("1000"))));

        ShoppingListQueryService.Result result = service.get(USER, PLAN);

        assertThat(result.status()).isEqualTo(GenerationStatus.DEGRADED);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.ingredientPublicId()).isEqualTo(CHICKEN);
            assertThat(item.requiredQuantity()).isEqualByComparingTo("800.0000");
            assertThat(item.pantryCoveredQuantity()).isEqualByComparingTo("550.0000");
            assertThat(item.quantityToBuy()).isEqualByComparingTo("250.0000");
            assertThat(item.unitCode()).isEqualTo("g");
        });
        assertThat(result.unquantifiedItems()).singleElement().satisfies(item -> {
            assertThat(item.ingredientPublicId()).isEqualTo(SALT);
            assertThat(item.ingredientDisplayName()).isEqualTo("Salt");
        });
        assertThat(result.items()).noneMatch(item ->
                item.ingredientPublicId().equals(GARNISH));
    }

    @Test
    void doesNotUseCrossDimensionPantry() {
        when(mealPlans.get(USER, PLAN)).thenReturn(plan(
                List.of(entry(DAY_ONE, RECIPE_A, "1.00")),
                GenerationStatus.SUCCEEDED));
        when(recipes.requirementsFor(any())).thenReturn(Map.of(
                RECIPE_A, recipe(RECIPE_A, "1",
                        quantified(CHICKEN, "chicken", "Chicken", "100", "g", false))));
        when(pantry.availableFor(USER)).thenReturn(List.of(
                lot(CHICKEN, "10", "piece", null, PantryExpiryKind.UNKNOWN)));
        when(units.definitionsFor(any())).thenReturn(List.of(
                new PlanningUnitSnapshot("g", "MASS", "g", BigDecimal.ONE),
                new PlanningUnitSnapshot("piece", "COUNT", "piece", BigDecimal.ONE)));

        ShoppingListQueryService.Item item = service.get(USER, PLAN).items().getFirst();

        assertThat(item.requiredQuantity()).isEqualByComparingTo("100.0000");
        assertThat(item.pantryCoveredQuantity()).isEqualByComparingTo("0.0000");
        assertThat(item.quantityToBuy()).isEqualByComparingTo("100.0000");
        assertThat(item.unitCode()).isEqualTo("g");
    }

    @Test
    void pantryExcessCapsCoverageAtRequirement() {
        when(mealPlans.get(USER, PLAN)).thenReturn(plan(
                List.of(entry(DAY_ONE, RECIPE_A, "1.00")),
                GenerationStatus.SUCCEEDED));
        when(recipes.requirementsFor(any())).thenReturn(Map.of(
                RECIPE_A, recipe(RECIPE_A, "1",
                        quantified(CHICKEN, "chicken", "Chicken", "100", "g", false))));
        when(pantry.availableFor(USER)).thenReturn(List.of(
                lot(CHICKEN, "1", "kg", null, PantryExpiryKind.UNKNOWN)));
        when(units.definitionsFor(any())).thenReturn(List.of(
                new PlanningUnitSnapshot("g", "MASS", "g", BigDecimal.ONE),
                new PlanningUnitSnapshot("kg", "MASS", "g", new BigDecimal("1000"))));

        ShoppingListQueryService.Item item = service.get(USER, PLAN).items().getFirst();

        assertThat(item.pantryCoveredQuantity()).isEqualByComparingTo("100.0000");
        assertThat(item.quantityToBuy()).isEqualByComparingTo("0.0000");
    }

    @Test
    void missingRecipeCompositionFailsClosed() {
        when(mealPlans.get(USER, PLAN)).thenReturn(plan(
                List.of(entry(DAY_ONE, RECIPE_A, "1.00")),
                GenerationStatus.SUCCEEDED));
        when(recipes.requirementsFor(any())).thenReturn(Map.of());
        when(pantry.availableFor(USER)).thenReturn(List.of());
        when(units.definitionsFor(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.get(USER, PLAN))
                .isInstanceOf(ReferenceDataIntegrityException.class)
                .hasMessage("Shopping list source data is inconsistent");
    }

    private static PersistedMealPlanQueryService.Detail plan(
            List<PersistedMealPlanQueryService.Entry> entries,
            GenerationStatus status) {
        return new PersistedMealPlanQueryService.Detail(
                PLAN, REQUEST, status, DAY_ONE, DAY_ONE.plusDays(1),
                new BigDecimal("2.00"), entries, List.of());
    }

    private static PersistedMealPlanQueryService.Entry entry(
            LocalDate date, UUID recipeId, String servings) {
        return new PersistedMealPlanQueryService.Entry(
                date, MealSlotCode.DINNER, recipeId, "Recipe",
                new BigDecimal(servings));
    }

    private static RecipeRequirementSnapshot recipe(
            UUID recipeId, String servings,
            RecipeRequirementSnapshot.IngredientRequirement... requirements) {
        return new RecipeRequirementSnapshot(
                recipeId, new BigDecimal(servings), List.of(requirements));
    }

    private static RecipeRequirementSnapshot.IngredientRequirement quantified(
            UUID ingredientId, String code, String name, String quantity,
            String unit, boolean optional) {
        return new RecipeRequirementSnapshot.IngredientRequirement(
                ingredientId, code, name, new BigDecimal(quantity), unit, optional);
    }

    private static RecipeRequirementSnapshot.IngredientRequirement unquantified(
            UUID ingredientId, String code, String name, boolean optional) {
        return new RecipeRequirementSnapshot.IngredientRequirement(
                ingredientId, code, name, null, null, optional);
    }

    private static PantryAvailabilitySnapshot lot(
            UUID ingredientId, String quantity, String unit,
            LocalDate expiryDate, PantryExpiryKind expiryKind) {
        return new PantryAvailabilitySnapshot(
                UUID.randomUUID(), ingredientId, null,
                new BigDecimal(quantity), unit, expiryDate, expiryKind,
                PantryStorageLocation.FRIDGE);
    }
}
