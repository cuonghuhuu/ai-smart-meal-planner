package com.smartmealplanner.mealplanning.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.food.DislikedIngredientStrength;
import com.smartmealplanner.food.DislikedIngredientView;
import com.smartmealplanner.food.IngredientAllergenPresence;
import com.smartmealplanner.food.IngredientSafetyQueryService;
import com.smartmealplanner.food.IngredientSafetySnapshot;
import com.smartmealplanner.food.UserDislikedIngredientService;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractJson;
import com.smartmealplanner.nutrition.application.NutritionTargetQueryService;
import com.smartmealplanner.nutrition.application.NutritionTargetValueView;
import com.smartmealplanner.nutrition.application.NutritionTargetView;
import com.smartmealplanner.nutrition.application.PlanningUnitQueryService;
import com.smartmealplanner.nutrition.application.PlanningUnitSnapshot;
import com.smartmealplanner.nutrition.persistence.NutritionTargetOrigin;
import com.smartmealplanner.pantry.PantryAvailabilityQueryService;
import com.smartmealplanner.pantry.PantryAvailabilitySnapshot;
import com.smartmealplanner.pantry.PantryExpiryKind;
import com.smartmealplanner.pantry.PantryStorageLocation;
import com.smartmealplanner.profile.application.MealPlanningPreferencesQueryService;
import com.smartmealplanner.profile.application.MealPlanningPreferencesSnapshot;
import com.smartmealplanner.recipe.RecipeRecommendationQueryService;
import com.smartmealplanner.recipe.RecipeRecommendationSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MealPlanningSnapshotAssemblerTest {
    private static final UUID USER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID REQUEST = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID INGREDIENT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID UNKNOWN_INGREDIENT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2");
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final PantryAvailabilityQueryService pantry = mock(PantryAvailabilityQueryService.class);
    private final RecipeRecommendationQueryService recipes = mock(RecipeRecommendationQueryService.class);
    private final MealPlanningPreferencesQueryService preferences = mock(MealPlanningPreferencesQueryService.class);
    private final UserDislikedIngredientService disliked = mock(UserDislikedIngredientService.class);
    private final NutritionTargetQueryService nutrition = mock(NutritionTargetQueryService.class);
    private final IngredientSafetyQueryService facts = mock(IngredientSafetyQueryService.class);
    private final PlanningUnitQueryService units = mock(PlanningUnitQueryService.class);
    private final MealPlanningSnapshotAssembler assembler = new MealPlanningSnapshotAssembler(
            users, pantry, recipes, preferences, disliked, nutrition, facts, units,
            MealPlanningContractJson.createDefault());

    @BeforeEach
    void snapshots() {
        when(users.getIdentity(USER)).thenReturn(new CurrentUserIdentity(1L, USER, "UTC"));
        when(pantry.availableFor(USER)).thenReturn(List.of(
                lot("cccccccc-cccc-4ccc-8ccc-ccccccccccc1", "floz", "2.0000"),
                lot("cccccccc-cccc-4ccc-8ccc-ccccccccccc2", "ml", "25.0000")));
        RecipeRecommendationSnapshot recipe = new RecipeRecommendationSnapshot(
                UUID.fromString("dddddddd-dddd-4ddd-8ddd-ddddddddddd1"),
                new BigDecimal("2.00"), 25, List.of("BREAKFAST"),
                List.of("VEGETARIAN"), List.of("HIGH_PROTEIN"),
                List.of(new RecipeRecommendationSnapshot.IngredientRequirement(
                        INGREDIENT, new BigDecimal("50.0000"), "ml", false, false),
                        new RecipeRecommendationSnapshot.IngredientRequirement(
                                UNKNOWN_INGREDIENT, null, null, true, false)), null);
        when(recipes.candidates(List.of("BREAKFAST"), 200)).thenReturn(List.of(recipe));
        when(preferences.forUser(USER)).thenReturn(new MealPlanningPreferencesSnapshot(
                List.of("PEANUT"), List.of("VEGETARIAN"), List.of("HIGH_PROTEIN")));
        when(disliked.getPreferences(USER)).thenReturn(List.of(
                new DislikedIngredientView(INGREDIENT, "INGREDIENT", "Ingredient",
                        null, DislikedIngredientStrength.DISLIKE, null),
                new DislikedIngredientView(UNKNOWN_INGREDIENT, "OTHER", "Other",
                        null, DislikedIngredientStrength.AVOID, null)));
        when(nutrition.getCurrentTarget(USER)).thenReturn(new NutritionTargetView(
                LocalDate.of(2026, 1, 1), null, NutritionTargetOrigin.CALCULATED,
                null, null, null, List.of(new NutritionTargetValueView(
                        "ENERGY", "kcal", "kilocalorie", new BigDecimal("2000.0000"),
                        null, null, false))));
        when(facts.forIngredients(any())).thenReturn(List.of(
                new IngredientSafetySnapshot(INGREDIENT,
                        List.of(new IngredientSafetySnapshot.AllergenEvidence(
                                "PEANUT", IngredientAllergenPresence.FREE_FROM))),
                new IngredientSafetySnapshot(UNKNOWN_INGREDIENT, List.of())));
        when(units.definitionsFor(any())).thenReturn(List.of(
                new PlanningUnitSnapshot("ml", "VOLUME", "ml", BigDecimal.ONE),
                new PlanningUnitSnapshot("floz", "VOLUME", "ml",
                        new BigDecimal("29.573529562500")),
                new PlanningUnitSnapshot("kcal", "ENERGY", "kcal", BigDecimal.ONE),
                new PlanningUnitSnapshot("kj", "ENERGY", "kcal",
                        new BigDecimal("0.239005736138"))));
    }

    @Test
    void assemblesLotLevelCanonicalUnitsAndUnknownFactsWithoutFabrication() {
        MealPlanGenerationRequest result = assembler.assemble(USER, REQUEST, command());
        assertThat(result.pantryLots()).hasSize(2);
        assertThat(result.pantryLots()).extracting(MealPlanGenerationRequest.PantryLot::pantryItemPublicId)
                .doesNotHaveDuplicates();
        assertThat(result.pantryLots()).extracting(MealPlanGenerationRequest.PantryLot::unitCode)
                .containsExactly("floz", "ml");
        assertThat(result.unitDefinitions()).anySatisfy(unit -> {
            assertThat(unit.unitCode()).isEqualTo("ml");
            assertThat(unit.baseUnitCode()).isEqualTo("ml");
            assertThat(unit.toBaseFactor()).isEqualByComparingTo(BigDecimal.ONE);
        });
        assertThat(result.unitDefinitions()).anySatisfy(unit -> {
            assertThat(unit.unitCode()).isEqualTo("floz");
            assertThat(unit.toBaseFactor()).isEqualByComparingTo("29.573529562500");
        });
        assertThat(result.unitDefinitions()).anySatisfy(unit -> {
            assertThat(unit.unitCode()).isEqualTo("kj");
            assertThat(unit.toBaseFactor()).isEqualByComparingTo("0.239005736138");
        });
        assertThat(result.recipeCandidates().get(0).nutrition()).isNull();
        assertThat(result.allergenEvidenceFor(UNKNOWN_INGREDIENT, "PEANUT").name())
                .isEqualTo("UNKNOWN");
        assertThat(result.hardConstraints().avoidIngredientPublicIds())
                .containsExactly(UNKNOWN_INGREDIENT);
        assertThat(result.softPreferences().dislikeIngredientPublicIds())
                .containsExactly(INGREDIENT);
        verify(pantry).availableFor(USER);
        verify(recipes).candidates(List.of("BREAKFAST"), 200);
        verify(facts).forIngredients(eq(java.util.Set.of(INGREDIENT, UNKNOWN_INGREDIENT)));
    }

    @Test
    void rejectsInvalidInputBeforeReadingPantry() {
        MealPlanGenerationCommand invalid = new MealPlanGenerationCommand(
                LocalDate.of(2026, 10, 1), 8, List.of(MealSlotCode.BREAKFAST),
                BigDecimal.ONE, null);
        assertThatThrownBy(() -> assembler.assemble(USER, REQUEST, invalid))
                .isInstanceOfSatisfying(MealPlanningIntegrationException.class,
                        error -> assertThat(error.failure()).isEqualTo(
                                MealPlanningIntegrationFailure.INVALID_GENERATION_INPUT));
        verifyNoInteractions(pantry);
    }

    @Test
    void refusesOversizedPantryWithoutTruncation() {
        when(pantry.availableFor(USER)).thenReturn(java.util.Collections.nCopies(
                2001, lot("cccccccc-cccc-4ccc-8ccc-ccccccccccc1", "ml", "1.0000")));
        assertThatThrownBy(() -> assembler.assemble(USER, REQUEST, command()))
                .isInstanceOfSatisfying(MealPlanningIntegrationException.class,
                        error -> assertThat(error.failure()).isEqualTo(
                                MealPlanningIntegrationFailure.SNAPSHOT_LIMIT_EXCEEDED));
    }

    private static PantryAvailabilitySnapshot lot(String id, String unit, String quantity) {
        return new PantryAvailabilitySnapshot(UUID.fromString(id), INGREDIENT, null,
                new BigDecimal(quantity), unit, null, PantryExpiryKind.UNKNOWN,
                PantryStorageLocation.PANTRY);
    }

    private static MealPlanGenerationCommand command() {
        return new MealPlanGenerationCommand(LocalDate.of(2026, 10, 1), 1,
                List.of(MealSlotCode.BREAKFAST), new BigDecimal("2.00"), null);
    }
}
