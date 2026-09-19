package com.smartmealplanner.mealplanning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;
import com.smartmealplanner.nutrition.persistence.MeasurementUnitType;
import com.smartmealplanner.pantry.PantryAvailabilitySnapshot;
import com.smartmealplanner.pantry.PantryExpiryKind;
import com.smartmealplanner.pantry.PantryStorageLocation;
import com.smartmealplanner.recipe.RecipeRecommendationCandidate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MealPlanScoringTest {

    @Test
    void appliesTheDocumentedDeterministicFormula() {
        RecommendationScore score = MealPlanScoring.score(
                new BigDecimal("1.0000"),
                new BigDecimal("0.8000"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.7000"),
                new BigDecimal("1.0000"),
                new BigDecimal("1.0000"),
                new BigDecimal("0.2500"));

        assertThat(score.totalScore()).isEqualByComparingTo("0.8200");
        assertThat(score.pantryCoverage()).isEqualByComparingTo("1.0000");
        assertThat(score.dislikePenalty()).isEqualByComparingTo("0.2500");
    }

    @Test
    void clampsComponentInputsAndKeepsTheTotalDeterministic() {
        RecommendationScore score = MealPlanScoring.score(
                new BigDecimal("2"), new BigDecimal("-1"), new BigDecimal(".2"),
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE);

        assertThat(score.pantryCoverage()).isEqualByComparingTo("1.0000");
        assertThat(score.nutritionFit()).isEqualByComparingTo("0.0000");
        assertThat(score.totalScore()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    void virtualPantryUsesUnitMetadataAndDepletesAcrossSelections() {
        UUID ingredientId = UUID.randomUUID();
        MeasurementUnitReferenceSnapshot gram = new MeasurementUnitReferenceSnapshot(
                1L, "g", "gram", MeasurementUnitType.MASS, null, null, null);
        PantryAvailabilitySnapshot stock = new PantryAvailabilitySnapshot(
                UUID.randomUUID(), ingredientId, null, new BigDecimal("75"), "g",
                LocalDate.of(2026, 9, 25), PantryExpiryKind.USE_BY,
                PantryStorageLocation.FRIDGE);
        RecipeRecommendationCandidate candidate = new RecipeRecommendationCandidate(
                10L, UUID.randomUUID(), "Test recipe", (short) 2, (short) 10,
                List.of(new RecipeRecommendationCandidate.Ingredient(
                        20L, ingredientId, "TEST_INGREDIENT", new BigDecimal("100"),
                        "g", false)),
                List.of(), List.of(), Map.of());

        VirtualPantry pantry = VirtualPantry.from(List.of(stock), Map.of("g", gram));
        assertThat(pantry.coverage(candidate, 1, LocalDate.of(2026, 9, 21), Map.of("g", gram)))
                .isEqualByComparingTo("1.000000000000");
        pantry.consume(candidate, 1, LocalDate.of(2026, 9, 21), Map.of("g", gram));
        assertThat(pantry.coverage(candidate, 1, LocalDate.of(2026, 9, 21), Map.of("g", gram)))
                .isEqualByComparingTo("0.500000000000");
    }
}
