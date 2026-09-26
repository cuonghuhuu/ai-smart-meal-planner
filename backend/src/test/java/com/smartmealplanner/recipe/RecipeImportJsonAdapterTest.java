package com.smartmealplanner.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;

import org.junit.jupiter.api.Test;

class RecipeImportJsonAdapterTest {

    @Test
    void readsSyntheticVietnameseRecipeContractWithUnicode() {
        InputStream input = getClass().getResourceAsStream(
                "/recipe-import/synthetic-vietnam-curated.json");

        RecipeImportDocument document = new RecipeImportJsonAdapter(new ObjectMapper())
                .read(input);

        assertThat(document.dataset()).contains("Vietnamese Recipe");
        assertThat(document.recipes()).hasSize(1);
        assertThat(document.recipes().getFirst().title())
                .isEqualTo("Canh rau muống thử nghiệm");
        assertThat(document.recipes().getFirst().ingredients().getFirst().unitCode())
                .isEqualTo("g");
    }

    @Test
    void malformedJsonUsesFocusedImportFileFailure() {
        assertThatThrownBy(() -> new RecipeImportJsonAdapter(new ObjectMapper())
                .read(new java.io.ByteArrayInputStream("{bad".getBytes())))
                .isInstanceOf(RecipeImportFileException.class);
    }

    @Test
    void validationReportsUnknownIngredientBeforePersistence() {
        RecipeImportService service = new RecipeImportService(
                mock(RecipeRepository.class),
                mock(RecipeIngredientRepository.class),
                mock(RecipeStepRepository.class),
                emptyTags(),
                mock(RecipeTagAssignmentRepository.class),
                emptyMealSlots(),
                mock(RecipeMealSlotTypeRepository.class),
                emptyIngredientReferences(),
                emptyUnitReferences(),
                mock(RecipeNutritionSnapshotRepository.class),
                mock(RecipeNutritionComputationService.class));

        RecipeImportRecipe recipe = new RecipeImportRecipe(
                "TEST_RECIPE_001",
                "Món thử",
                "mon-thu",
                null,
                1,
                1,
                1,
                RecipeDifficulty.EASY,
                null,
                null,
                RecipeSource.CURATED,
                "TEST:001",
                List.of(),
                List.of(),
                List.of(new RecipeImportIngredient(
                        1,
                        "UNKNOWN_INGREDIENT",
                        new java.math.BigDecimal("10"),
                        "g",
                        null,
                        false,
                        true,
                        null)),
                List.of());

        RecipeImportValidationReport report = service.validateDocument(
                new RecipeImportDocument("test", "v1", List.of(recipe)));

        assertThat(report.errors())
                .extracting(RecipeImportIssue::type)
                .contains(RecipeImportIssueType.UNKNOWN_INGREDIENT);
    }

    @Test
    void validationReportsUnknownUnitTagAndMealSlot() {
        RecipeImportService service = new RecipeImportService(
                mock(RecipeRepository.class),
                mock(RecipeIngredientRepository.class),
                mock(RecipeStepRepository.class),
                emptyTags(),
                mock(RecipeTagAssignmentRepository.class),
                emptyMealSlots(),
                mock(RecipeMealSlotTypeRepository.class),
                emptyIngredientReferences(),
                emptyUnitReferences(),
                mock(RecipeNutritionSnapshotRepository.class),
                mock(RecipeNutritionComputationService.class));

        RecipeImportRecipe recipe = recipe(
                "TEST_RECIPE_002",
                "mon-thu-2",
                List.of("NOT_A_TAG"),
                List.of("NOT_A_MEAL_SLOT"),
                new RecipeImportIngredient(
                        1,
                        "UNKNOWN_INGREDIENT",
                        new java.math.BigDecimal("10"),
                        "not-a-unit",
                        null,
                        false,
                        true,
                        null));

        RecipeImportValidationReport report = service.validateDocument(
                new RecipeImportDocument("test", "v1", List.of(recipe)));

        assertThat(report.errors())
                .extracting(RecipeImportIssue::type)
                .contains(
                        RecipeImportIssueType.UNKNOWN_UNIT,
                        RecipeImportIssueType.UNKNOWN_TAG,
                        RecipeImportIssueType.UNKNOWN_MEAL_SLOT);
    }

    @Test
    void validationReportsDuplicateSourceIdentitySlugIngredientAndStep() {
        RecipeImportService service = new RecipeImportService(
                mock(RecipeRepository.class),
                mock(RecipeIngredientRepository.class),
                mock(RecipeStepRepository.class),
                emptyTags(),
                mock(RecipeTagAssignmentRepository.class),
                emptyMealSlots(),
                mock(RecipeMealSlotTypeRepository.class),
                emptyIngredientReferences(),
                emptyUnitReferences(),
                mock(RecipeNutritionSnapshotRepository.class),
                mock(RecipeNutritionComputationService.class));
        RecipeImportRecipe first = recipe(
                "TEST_RECIPE_DUPLICATE",
                "duplicate-slug",
                List.of(),
                List.of(),
                null);
        RecipeImportRecipe second = recipe(
                "TEST_RECIPE_DUPLICATE",
                "duplicate-slug",
                List.of(),
                List.of(),
                null);

        RecipeImportValidationReport report = service.validateDocument(
                new RecipeImportDocument("test", "v1", List.of(first, second)));

        assertThat(report.errors())
                .extracting(RecipeImportIssue::type)
                .contains(
                        RecipeImportIssueType.DUPLICATE_SOURCE_IDENTIFIER,
                        RecipeImportIssueType.DUPLICATE_SLUG,
                        RecipeImportIssueType.DUPLICATE_SOURCE_REFERENCE);
    }

    private static RecipeImportRecipe recipe(
            String sourceIdentifier,
            String slug,
            List<String> tags,
            List<String> mealSlots,
            RecipeImportIngredient ingredient) {
        return new RecipeImportRecipe(
                sourceIdentifier,
                "Món thử",
                slug,
                null,
                1,
                1,
                1,
                RecipeDifficulty.EASY,
                null,
                null,
                RecipeSource.CURATED,
                sourceIdentifier + ":REF",
                tags,
                mealSlots,
                ingredient == null ? List.of() : List.of(ingredient),
                ingredient == null
                        ? List.of()
                        : List.of(new RecipeImportStep(
                                1,
                                "Thực hiện bước thử nghiệm.",
                                1)));
    }

    private static RecipeTagRepository emptyTags() {
        RecipeTagRepository repository = mock(RecipeTagRepository.class);
        when(repository.findAllByCodeIn(anyCollection())).thenReturn(List.of());
        return repository;
    }

    private static MealSlotTypeRepository emptyMealSlots() {
        MealSlotTypeRepository repository = mock(MealSlotTypeRepository.class);
        when(repository.findAllByCodeIn(anyCollection())).thenReturn(List.of());
        return repository;
    }

    private static IngredientReferenceQueryService emptyIngredientReferences() {
        IngredientReferenceQueryService service = mock(
                IngredientReferenceQueryService.class);
        when(service.resolveByCodes(anyCollection())).thenReturn(Map.of());
        return service;
    }

    private static MeasurementUnitReferenceQueryService emptyUnitReferences() {
        MeasurementUnitReferenceQueryService service = mock(
                MeasurementUnitReferenceQueryService.class);
        when(service.resolveByCodes(anyCollection())).thenReturn(Map.of());
        return service;
    }
}
