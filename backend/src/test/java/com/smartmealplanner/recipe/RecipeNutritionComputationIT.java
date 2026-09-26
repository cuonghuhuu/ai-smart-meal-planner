package com.smartmealplanner.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** MySQL coverage for explicit Recipe nutrition snapshot recomputation. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class RecipeNutritionComputationIT {

    private static final String SLUG_PREFIX = "p9b-nutrition-it-";
    private static final String SOURCE_REFERENCE = "P9B nutrition test";
    private static final String INGREDIENT_PREFIX = "P9B_NUTRITION_";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p9b_recipe_nutrition")
            .withUsername("p9b_recipe_nutrition_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    RecipeNutritionComputationService computation;

    @BeforeEach
    void cleanSyntheticRows() {
        jdbc.update("""
                delete from recipe_nutrition_values
                where snapshot_id in (
                    select id from recipe_nutrition_snapshots
                    where recipe_id in (
                        select id from recipes where slug like ?))
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_nutrition_snapshots
                where recipe_id in (select id from recipes where slug like ?)
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_ingredients
                where recipe_id in (select id from recipes where slug like ?)
                """, SLUG_PREFIX + "%");
        jdbc.update("delete from recipes where slug like ?", SLUG_PREFIX + "%");
        jdbc.update("""
                delete from ingredient_foods
                where ingredient_id in (
                    select id from ingredients where code like ?)
                """, INGREDIENT_PREFIX + "%");
        jdbc.update("delete from ingredients where code like ?",
                INGREDIENT_PREFIX + "%");
        jdbc.update("delete from foods where source_reference = ?",
                SOURCE_REFERENCE);
    }

    @Test
    void computesPerServingNutritionReplacesCurrentSnapshotAndFeedsDetailApi()
            throws Exception {
        Fixture fixture = insertFixture(new BigDecimal("50.0000"));

        RecipeNutritionComputationResult first = computation.recompute(
                fixture.recipePublicId());
        assertThat(first.completenessRatio()).isEqualByComparingTo("1.0000");
        assertThat(first.ingredientRevision()).isEqualTo(1);
        assertThat(first.resolvedLineCount()).isEqualTo(1);

        List<Map<String, Object>> firstValues = jdbc.queryForList("""
                select nutrition_value.amount_per_serving
                from recipe_nutrition_values nutrition_value
                join recipe_nutrition_snapshots snapshot
                  on snapshot.id = nutrition_value.snapshot_id
                where snapshot.recipe_id = ?
                """, fixture.recipeId());
        assertThat(firstValues).hasSize(1);
        assertThat(firstValues.getFirst().get("amount_per_serving"))
                .isEqualTo(new BigDecimal("50.0000"));

        jdbc.update("""
                update food_nutrients
                set amount = 100.0000
                where food_id = ?
                  and nutrient_id = (select id from nutrients where code = 'ENERGY')
                """, fixture.foodId());
        jdbc.update("update foods set revision = 2 where id = ?", fixture.foodId());
        RecipeNutritionComputationResult second = computation.recompute(
                fixture.recipePublicId());

        List<Map<String, Object>> snapshots = jdbc.queryForList("""
                select id, is_current
                from recipe_nutrition_snapshots
                where recipe_id = ?
                order by id asc
                """, fixture.recipeId());
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).get("is_current")).isEqualTo(false);
        assertThat(snapshots.get(1).get("is_current")).isEqualTo(true);
        assertThat(jdbc.queryForObject("""
                select count(*) from recipe_nutrition_snapshots
                where recipe_id = ? and is_current = true
                """, Integer.class, fixture.recipeId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from recipe_nutrition_values nutrition_value
                join recipe_nutrition_snapshots snapshot
                  on snapshot.id = nutrition_value.snapshot_id
                where snapshot.recipe_id = ?
                """, Integer.class, fixture.recipeId())).isEqualTo(2);
        List<Map<String, Object>> historicalValues = jdbc.queryForList("""
                select snapshot.id, nutrition_value.amount_per_serving
                from recipe_nutrition_snapshots snapshot
                join recipe_nutrition_values nutrition_value
                  on nutrition_value.snapshot_id = snapshot.id
                where snapshot.recipe_id = ?
                order by snapshot.id asc
                """, fixture.recipeId());
        assertThat(historicalValues).hasSize(2);
        assertThat(historicalValues.get(0).get("amount_per_serving"))
                .isEqualTo(new BigDecimal("50.0000"));
        assertThat(historicalValues.get(1).get("amount_per_serving"))
                .isEqualTo(new BigDecimal("25.0000"));

        mvc.perform(get("/api/v1/recipes/{publicId}", fixture.recipePublicId())
                .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrition.completenessRatio")
                        .value(1.0))
                .andExpect(jsonPath("$.nutrition.values[0].nutrientCode")
                        .value("ENERGY"))
                .andExpect(jsonPath("$.nutrition.values[0].amountPerServing")
                        .value(25.0))
                .andExpect(jsonPath("$.nutrition.computedAt")
                        .value(second.computedAt().toString()));
    }

    @Test
    void pinnedFoodOverridesIngredientDefaultAndPrimaryFood() {
        Fixture fixture = insertFixture(new BigDecimal("50.0000"));
        Long pinnedFoodId = insertAdditionalFood(
                "PINNED",
                new BigDecimal("400.0000"),
                7);
        jdbc.update("""
                insert into ingredient_foods
                    (ingredient_id, food_id, preparation_state, yield_factor, is_primary)
                values (?, ?, 'UNSPECIFIED', 1.0000, false)
                """, fixture.ingredientId(), pinnedFoodId);
        jdbc.update("""
                update recipe_ingredients
                set food_id = ?
                where recipe_id = ?
                """, pinnedFoodId, fixture.recipeId());

        RecipeNutritionComputationResult result = computation.recompute(
                fixture.recipePublicId());

        assertThat(result.ingredientRevision()).isEqualTo(7);
        assertThat(currentNutrientAmount(fixture.recipeId(), "ENERGY"))
                .isEqualByComparingTo("100.0000");
    }

    @Test
    void contradictoryDefaultAndPrimaryFoodMappingIsCorrupted() {
        Fixture fixture = insertFixture(new BigDecimal("50.0000"));
        Long primaryFoodId = insertAdditionalFood(
                "CONTRADICTORY",
                new BigDecimal("300.0000"),
                3);
        jdbc.update("""
                update ingredient_foods
                set is_primary = false
                where ingredient_id = ? and food_id = ?
                """, fixture.ingredientId(), fixture.foodId());
        jdbc.update("""
                insert into ingredient_foods
                    (ingredient_id, food_id, preparation_state, yield_factor, is_primary)
                values (?, ?, 'UNSPECIFIED', 1.0000, true)
                """, fixture.ingredientId(), primaryFoodId);

        assertThatThrownBy(() -> computation.recompute(fixture.recipePublicId()))
                .isInstanceOf(RecipeException.class)
                .satisfies(exception -> assertThat(
                        ((RecipeException) exception).failure())
                        .isEqualTo(RecipeFailure.CORRUPTED_RECIPE_DATA));
    }

    @Test
    void missingQuantityAndMissingNutrientRemainIncompleteAndNotZero()
            throws Exception {
        Fixture fixture = insertFixture(new BigDecimal("50.0000"));
        Long gramUnitId = jdbc.queryForObject(
                "select id from measurement_units where code = 'g'", Long.class);
        Long secondIngredientId = insertAdditionalIngredient(
                fixture.foodId(),
                gramUnitId);
        jdbc.update("""
                insert into recipe_ingredients
                    (recipe_id, line_number, ingredient_id, food_id, quantity,
                     unit_id, is_optional, allow_substitution)
                values (?, 2, ?, null, null, null, false, true)
                """, fixture.recipeId(), secondIngredientId);

        computation.recompute(fixture.recipePublicId());

        assertThat(jdbc.queryForObject("""
                select completeness_ratio
                from recipe_nutrition_snapshots
                where recipe_id = ? and is_current = true
                """, BigDecimal.class, fixture.recipeId()))
                .isEqualByComparingTo("0.5000");
        assertThat(jdbc.queryForObject("""
                select count(*)
                from recipe_nutrition_values value_row
                join nutrients nutrient on nutrient.id = value_row.nutrient_id
                join recipe_nutrition_snapshots snapshot
                  on snapshot.id = value_row.snapshot_id
                where snapshot.recipe_id = ? and snapshot.is_current = true
                  and nutrient.code = 'PROTEIN'
                """, Integer.class, fixture.recipeId())).isZero();
        mvc.perform(get("/api/v1/recipes/{publicId}", fixture.recipePublicId())
                .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrition.values[?(@.nutrientCode == 'PROTEIN')]")
                        .isEmpty());
    }

    @Test
    void missingNutrientsStayAbsentWhileStoredZeroRemainsKnown() {
        Fixture fixture = insertFixture(new BigDecimal("50.0000"));
        Long proteinId = jdbc.queryForObject(
                "select id from nutrients where code = 'PROTEIN'", Long.class);
        jdbc.update("""
                insert into food_nutrients (food_id, nutrient_id, amount, data_quality)
                values (?, ?, 0.0000, 'ANALYTICAL')
                """, fixture.foodId(), proteinId);

        computation.recompute(fixture.recipePublicId());

        assertThat(jdbc.queryForObject("""
                select count(*)
                from recipe_nutrition_values value_row
                join recipe_nutrition_snapshots snapshot
                  on snapshot.id = value_row.snapshot_id
                join nutrients nutrient on nutrient.id = value_row.nutrient_id
                where snapshot.recipe_id = ? and snapshot.is_current = true
                  and nutrient.code = 'PROTEIN'
                """, Integer.class, fixture.recipeId())).isEqualTo(1);
        assertThat(currentNutrientAmount(fixture.recipeId(), "PROTEIN"))
                .isEqualByComparingTo("0.0000");
        assertThat(jdbc.queryForObject("""
                select count(*)
                from recipe_nutrition_values value_row
                join recipe_nutrition_snapshots snapshot
                  on snapshot.id = value_row.snapshot_id
                join nutrients nutrient on nutrient.id = value_row.nutrient_id
                where snapshot.recipe_id = ? and snapshot.is_current = true
                  and nutrient.code = 'FAT_TOTAL'
                """, Integer.class, fixture.recipeId())).isZero();
    }

    @Test
    void computedTimestampUsesPersistedMicrosecondPrecision() {
        Fixture fixture = insertFixture(new BigDecimal("50.0000"));

        RecipeNutritionComputationResult result = computation.recompute(
                fixture.recipePublicId());
        Timestamp persisted = jdbc.queryForObject("""
                select computed_at
                from recipe_nutrition_snapshots
                where recipe_id = ? and is_current = true
                """, Timestamp.class, fixture.recipeId());

        assertThat(persisted).isNotNull();
        assertThat(result.computedAt())
                .isEqualTo(persisted.toLocalDateTime());
        assertThat(result.computedAt().getNano() % 1_000).isZero();
    }

    @Test
    void roundsPerServingOnlyWhenPersistingTheSnapshotValue() {
        Fixture fixture = insertFixture(new BigDecimal("1.0000"));
        jdbc.update("update recipes set servings = 3 where id = ?",
                fixture.recipeId());
        jdbc.update("""
                update food_nutrients
                set amount = 1.0000
                where food_id = ?
                  and nutrient_id = (select id from nutrients where code = 'ENERGY')
                """, fixture.foodId());

        computation.recompute(fixture.recipePublicId());

        assertThat(jdbc.queryForObject("""
                select nutrition_value.amount_per_serving
                from recipe_nutrition_values nutrition_value
                join recipe_nutrition_snapshots snapshot
                  on snapshot.id = nutrition_value.snapshot_id
                where snapshot.recipe_id = ? and snapshot.is_current = true
                """, BigDecimal.class, fixture.recipeId()))
                .isEqualByComparingTo("0.0033");
    }

    @Test
    void failedRecomputationDoesNotLeaveOldCurrentSnapshotDemoted() {
        Fixture fixture = insertFixture(new BigDecimal("50.0000"));
        computation.recompute(fixture.recipePublicId());

        jdbc.update("""
                update food_nutrients
                set amount = 99999999.9999
                where food_id = ?
                  and nutrient_id = (select id from nutrients where code = 'ENERGY')
                """, fixture.foodId());
        jdbc.update("""
                update recipe_ingredients
                set quantity = 99999999.9999
                where recipe_id = ?
                """, fixture.recipeId());

        assertThatThrownBy(() -> computation.recompute(fixture.recipePublicId()))
                .isInstanceOf(RecipeException.class);
        assertThat(jdbc.queryForObject("""
                select count(*) from recipe_nutrition_snapshots
                where recipe_id = ? and is_current = true
                """, Integer.class, fixture.recipeId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from recipe_nutrition_snapshots
                where recipe_id = ?
                """, Integer.class, fixture.recipeId())).isEqualTo(1);
    }

    private Fixture insertFixture(BigDecimal quantity) {
        Long gramUnitId = jdbc.queryForObject(
                "select id from measurement_units where code = 'g'", Long.class);
        Long energyId = jdbc.queryForObject(
                "select id from nutrients where code = 'ENERGY'", Long.class);
        Long categoryId = jdbc.queryForObject(
                "select id from food_categories where code = 'PROTEIN'", Long.class);

        UUID foodPublicId = UUID.randomUUID();
        jdbc.update("""
                insert into foods
                    (public_id, code, display_name, food_category_id,
                     nutrition_basis, source, source_reference, revision,
                     is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P9B nutrition food', ?,
                        'PER_100_G', 'CURATED', ?, 1, true, 0)
                """, foodPublicId.toString(), "P9B_FOOD_" + foodPublicId,
                categoryId, SOURCE_REFERENCE);
        Long foodId = jdbc.queryForObject("""
                select id from foods
                where public_id = unhex(replace(?, '-', ''))
                """, Long.class, foodPublicId.toString());
        jdbc.update("""
                insert into food_nutrients (food_id, nutrient_id, amount, data_quality)
                values (?, ?, 200.0000, 'CALCULATED')
                """, foodId, energyId);

        UUID ingredientPublicId = UUID.randomUUID();
        String ingredientCode = INGREDIENT_PREFIX + ingredientPublicId;
        jdbc.update("""
                insert into ingredients
                    (public_id, code, display_name, food_category_id,
                     default_food_id, default_unit_id, is_staple, is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P9B nutrition ingredient',
                        ?, ?, ?, false, true, 0)
                """, ingredientPublicId.toString(), ingredientCode, categoryId,
                foodId, gramUnitId);
        Long ingredientId = jdbc.queryForObject(
                "select id from ingredients where code = ?", Long.class,
                ingredientCode);
        jdbc.update("""
                insert into ingredient_foods
                    (ingredient_id, food_id, preparation_state, yield_factor, is_primary)
                values (?, ?, 'UNSPECIFIED', 1.0000, true)
                """, ingredientId, foodId);

        UUID recipePublicId = UUID.randomUUID();
        String slug = SLUG_PREFIX + recipePublicId;
        jdbc.update("""
                insert into recipes
                    (public_id, title, slug, summary, servings, prep_minutes,
                     cook_minutes, difficulty, source, source_reference, status,
                     published_at, version)
                values (unhex(replace(?, '-', '')), 'P9B nutrition recipe', ?,
                        'P9B test', 2, 10, 10, 'EASY', 'CURATED',
                        ?, 'PUBLISHED', ?, 0)
                """, recipePublicId.toString(), slug, SOURCE_REFERENCE,
                timestamp(LocalDateTime.now().minusMinutes(1)));
        Long recipeId = jdbc.queryForObject(
                "select id from recipes where slug = ?", Long.class, slug);
        if (quantity != null) {
            jdbc.update("""
                    insert into recipe_ingredients
                        (recipe_id, line_number, ingredient_id, food_id, quantity,
                         unit_id, is_optional, allow_substitution)
                    values (?, 1, ?, null, ?, ?, false, true)
                    """, recipeId, ingredientId, quantity, gramUnitId);
        }
        return new Fixture(recipePublicId, recipeId, ingredientId, foodId);
    }

    private Long insertAdditionalIngredient(Long foodId, Long unitId) {
        UUID publicId = UUID.randomUUID();
        String code = INGREDIENT_PREFIX + publicId;
        jdbc.update("""
                insert into ingredients
                    (public_id, code, display_name, default_food_id,
                     default_unit_id, is_staple, is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P9B second ingredient',
                        ?, ?, false, true, 0)
                """, publicId.toString(), code, foodId, unitId);
        return jdbc.queryForObject(
                "select id from ingredients where code = ?", Long.class, code);
    }

    private Long insertAdditionalFood(
            String suffix,
            BigDecimal energyAmount,
            int revision) {
        Long energyId = jdbc.queryForObject(
                "select id from nutrients where code = 'ENERGY'", Long.class);
        Long categoryId = jdbc.queryForObject(
                "select id from food_categories where code = 'PROTEIN'", Long.class);
        UUID publicId = UUID.randomUUID();
        String code = "P9B_PINNED_" + suffix + "_" + publicId;
        jdbc.update("""
                insert into foods
                    (public_id, code, display_name, food_category_id,
                     nutrition_basis, source, source_reference, revision,
                     is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P9B additional food', ?,
                        'PER_100_G', 'CURATED', ?, ?, true, 0)
                """, publicId.toString(), code, categoryId, SOURCE_REFERENCE,
                revision);
        Long foodId = jdbc.queryForObject(
                "select id from foods where code = ?", Long.class, code);
        jdbc.update("""
                insert into food_nutrients (food_id, nutrient_id, amount, data_quality)
                values (?, ?, ?, 'CALCULATED')
                """, foodId, energyId, energyAmount);
        return foodId;
    }

    private BigDecimal currentNutrientAmount(Long recipeId, String nutrientCode) {
        return jdbc.queryForObject("""
                select value_row.amount_per_serving
                from recipe_nutrition_values value_row
                join recipe_nutrition_snapshots snapshot
                  on snapshot.id = value_row.snapshot_id
                join nutrients nutrient on nutrient.id = value_row.nutrient_id
                where snapshot.recipe_id = ? and snapshot.is_current = true
                  and nutrient.code = ?
                """, BigDecimal.class, recipeId, nutrientCode);
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    private record Fixture(
            UUID recipePublicId,
            Long recipeId,
            Long ingredientId,
            Long foodId) {
    }
}
