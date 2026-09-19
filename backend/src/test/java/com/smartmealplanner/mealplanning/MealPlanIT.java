package com.smartmealplanner.mealplanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartmealplanner.mealplanning.web.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.web.MealPlanResponse;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** MySQL coverage for the authenticated deterministic P11 meal-plan flow. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class MealPlanIT {

    private static final String EMAIL_PREFIX = "p11-meal-plan-it-";
    private static final String SLUG_PREFIX = "p11-meal-plan-it-";
    private static final String INGREDIENT_PREFIX = "P11_MEAL_PLAN_INGREDIENT_";
    private static final String FOOD_PREFIX = "P11_MEAL_PLAN_FOOD_";
    private static final String SOURCE_PREFIX = "P11_MEAL_PLAN_IT:";
    private static final LocalDate PLAN_DATE = LocalDate.of(2026, 9, 21);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p11_meal_plan")
            .withUsername("p11_meal_plan_test")
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
    MealPlanService mealPlans;

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void cleanSyntheticRows() {
        String userPattern = EMAIL_PREFIX + "%";

        jdbc.update("""
                delete from meal_plan_entries
                where meal_plan_id in (
                    select id from meal_plans
                    where user_id in (select id from users where email like ?))
                """, userPattern);
        jdbc.update("""
                delete from meal_plans
                where user_id in (select id from users where email like ?)
                """, userPattern);
        jdbc.update("""
                delete from recommendation_result_scores
                where result_id in (
                    select id from recommendation_results
                    where request_id in (
                        select id from recommendation_requests
                        where user_id in (select id from users where email like ?)))
                """, userPattern);
        jdbc.update("""
                delete from recommendation_results
                where request_id in (
                    select id from recommendation_requests
                    where user_id in (select id from users where email like ?))
                """, userPattern);
        jdbc.update("""
                delete from recommendation_requests
                where user_id in (select id from users where email like ?)
                """, userPattern);
        jdbc.update("""
                delete from pantry_item_events
                where pantry_item_id in (
                    select id from pantry_items
                    where user_id in (select id from users where email like ?))
                """, userPattern);
        jdbc.update("""
                delete from pantry_items
                where user_id in (select id from users where email like ?)
                """, userPattern);

        jdbc.update("""
                delete from recipe_nutrition_values
                where snapshot_id in (
                    select id from recipe_nutrition_snapshots
                    where recipe_id in (select id from recipes where slug like ?))
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_nutrition_snapshots
                where recipe_id in (select id from recipes where slug like ?)
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_tag_assignments
                where recipe_id in (select id from recipes where slug like ?)
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_meal_slot_types
                where recipe_id in (select id from recipes where slug like ?)
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_steps
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
        jdbc.update("delete from ingredients where code like ?", INGREDIENT_PREFIX + "%");
        jdbc.update("""
                delete food_nutrient
                from food_nutrients food_nutrient
                join foods food on food.id = food_nutrient.food_id
                where food.code like ?
                """, FOOD_PREFIX + "%");
        jdbc.update("delete from foods where code like ?", FOOD_PREFIX + "%");
        jdbc.update("delete from users where email like ?", userPattern);
    }

    @Test
    void generateMealPlanCreatesDraftPlanAndRecommendationProvenance() {
        Fixture fixture = insertFixture();

        MealPlanResponse.Generation generation = generate(fixture);
        MealPlanResponse.Detail plan = generation.plan();

        assertThat(generation.warnings()).isEmpty();
        assertThat(plan.publicId()).isNotNull();
        assertThat(plan.status()).isEqualTo(MealPlanStatus.DRAFT);
        assertThat(plan.startDate()).isEqualTo(PLAN_DATE);
        assertThat(plan.endDate()).isEqualTo(PLAN_DATE);
        assertThat(plan.dayCount()).isEqualTo(1);
        assertThat(plan.defaultServings()).isEqualTo(1);
        assertThat(plan.entries()).hasSize(1);
        assertThat(plan.entries().get(0).recipePublicId())
                .isEqualTo(fixture.recipePublicId());
        assertThat(plan.entries().get(0).provenance())
                .isEqualTo(MealPlanEntryProvenance.AI_GENERATED);
        assertThat(plan.entries().get(0).consumptionStatus())
                .isEqualTo(MealPlanConsumptionStatus.PLANNED);

        Map<String, Object> request = jdbc.queryForMap("""
                select id, request_kind, algorithm_version, model_identifier, status
                from recommendation_requests
                where user_id = ?
                order by id desc
                limit 1
                """, fixture.owner().internalId());
        Long requestId = ((Number) request.get("id")).longValue();
        assertThat(request.get("request_kind")).isEqualTo("MEAL_PLAN");
        assertThat(request.get("algorithm_version"))
                .isEqualTo(MealPlanService.ALGORITHM_VERSION);
        assertThat(request.get("model_identifier")).isNull();
        assertThat(request.get("status")).isEqualTo("SUCCEEDED");

        Long planId = jdbc.queryForObject("""
                select id from meal_plans
                where public_id = unhex(replace(?, '-', ''))
                """, Long.class, plan.publicId().toString());
        assertThat(jdbc.queryForObject(
                "select source_request_id from meal_plans where id = ?",
                Long.class, planId)).isEqualTo(requestId);
        assertThat(jdbc.queryForObject("""
                select count(*) from meal_plan_entries
                where meal_plan_id = ?
                """, Integer.class, planId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select status from recipes where id = (
                    select recipe_id from meal_plan_entries where meal_plan_id = ?)
                """, String.class, planId)).isEqualTo("PUBLISHED");
        Long sourceResultId = jdbc.queryForObject("""
                select source_result_id from meal_plan_entries
                where meal_plan_id = ?
                """, Long.class, planId);
        assertThat(sourceResultId).isNotNull();
        assertThat(jdbc.queryForObject("""
                select request_id from recommendation_results where id = ?
                """, Long.class, sourceResultId)).isEqualTo(requestId);
        assertThat(jdbc.queryForObject("""
                select count(*) from recommendation_results
                where request_id = ?
                """, Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*)
                from recommendation_result_scores score
                join recommendation_results result on result.id = score.result_id
                where result.request_id = ?
                """, Integer.class, requestId)).isEqualTo(7);
    }

    @Test
    void generationUsesPantryButDoesNotMutateRealPantry() {
        Fixture fixture = insertFixture();
        PantryStock stock = insertPantryStock(fixture, new BigDecimal("250.0000"));
        BigDecimal quantityBefore = pantryQuantity(stock.itemId());
        int eventsBefore = pantryEventCount(stock.itemId());

        MealPlanResponse.Generation generation = generate(fixture);
        UUID resultPlanId = generation.plan().publicId();
        Long resultId = jdbc.queryForObject("""
                select source_result_id from meal_plan_entries
                where meal_plan_id = (
                    select id from meal_plans
                    where public_id = unhex(replace(?, '-', '')))
                """, Long.class, resultPlanId.toString());

        BigDecimal quantityAfter = pantryQuantity(stock.itemId());
        int eventsAfter = pantryEventCount(stock.itemId());
        BigDecimal pantryCoverage = jdbc.queryForObject("""
                select score.score_value
                from recommendation_result_scores score
                join ai_score_components component
                  on component.id = score.score_component_id
                where score.result_id = ?
                  and component.code = 'PANTRY_COVERAGE'
                """, BigDecimal.class, resultId);

        assertThat(quantityAfter).isEqualByComparingTo(quantityBefore);
        assertThat(eventsAfter).isEqualTo(eventsBefore);
        assertThat(pantryCoverage).isEqualByComparingTo("1.0000");
    }

    @Test
    void ownerIsolationForMealPlanDetail() throws Exception {
        Fixture fixture = insertFixture();
        Account otherUser = insertAccount("other");
        UUID planPublicId = generate(fixture).plan().publicId();

        mvc.perform(get("/api/v1/me/meal-plans/{publicId}", planPublicId)
                        .with(auth(otherUser.publicId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEAL_PLAN_NOT_FOUND"));
    }

    @Test
    void acceptDraftPlan() {
        Fixture fixture = insertFixture();
        PantryStock stock = insertPantryStock(fixture, new BigDecimal("250.0000"));
        BigDecimal quantityBefore = pantryQuantity(stock.itemId());
        int eventsBefore = pantryEventCount(stock.itemId());
        UUID planPublicId = generate(fixture).plan().publicId();

        MealPlanResponse.Detail accepted = mealPlans.accept(
                fixture.owner().publicId(), planPublicId);
        MealPlanResponse.Detail repeated = mealPlans.accept(
                fixture.owner().publicId(), planPublicId);

        assertThat(accepted.status()).isEqualTo(MealPlanStatus.ACCEPTED);
        assertThat(accepted.acceptedAt()).isNotNull();
        assertThat(repeated.status()).isEqualTo(MealPlanStatus.ACCEPTED);
        assertThat(repeated.acceptedAt()).isEqualTo(accepted.acceptedAt());
        assertThat(pantryQuantity(stock.itemId()))
                .isEqualByComparingTo(quantityBefore);
        assertThat(pantryEventCount(stock.itemId())).isEqualTo(eventsBefore);
    }

    @Test
    void zeroCandidateGenerationDoesNotPersistEmptyPlan() {
        Fixture fixture = insertFixture();
        MealPlanException failure = null;

        try {
            mealPlans.generate(
                    fixture.owner().publicId(),
                    new MealPlanGenerationRequest(
                            PLAN_DATE, 1, List.of("BREAKFAST"), 1, 1));
        } catch (MealPlanException exception) {
            failure = exception;
        }

        assertThat(failure).isNotNull();
        assertThat(failure.failure()).isEqualTo(MealPlanFailure.NO_ELIGIBLE_RECIPE);
        assertThat(count("select count(*) from meal_plans where user_id = ?",
                fixture.owner().internalId())).isZero();
        assertThat(count("select count(*) from recommendation_requests where user_id = ?",
                fixture.owner().internalId())).isZero();
        assertThat(count("""
                select count(*) from recommendation_results result
                join recommendation_requests request on request.id = result.request_id
                where request.user_id = ?
                """, fixture.owner().internalId())).isZero();
    }

    private MealPlanResponse.Generation generate(Fixture fixture) {
        return mealPlans.generate(
                fixture.owner().publicId(),
                new MealPlanGenerationRequest(
                        PLAN_DATE, 1, List.of("BREAKFAST"), 1, null));
    }

    private Fixture insertFixture() {
        Account owner = insertAccount("owner");
        Long gramUnitId = jdbc.queryForObject(
                "select id from measurement_units where code = 'g'", Long.class);

        UUID foodPublicId = UUID.randomUUID();
        String foodCode = FOOD_PREFIX + foodPublicId;
        jdbc.update("""
                insert into foods
                    (public_id, code, display_name, nutrition_basis, source,
                     source_reference, revision, is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P11 test food',
                        'PER_100_G', 'CURATED', ?, 1, true, 0)
                """, foodPublicId.toString(), foodCode,
                SOURCE_PREFIX + "FOOD:" + foodPublicId);
        Long foodId = jdbc.queryForObject(
                "select id from foods where code = ?", Long.class, foodCode);

        UUID ingredientPublicId = UUID.randomUUID();
        String ingredientCode = INGREDIENT_PREFIX + ingredientPublicId;
        jdbc.update("""
                insert into ingredients
                    (public_id, code, display_name, default_food_id,
                     default_unit_id, is_staple, is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P11 test ingredient',
                        ?, ?, false, true, 0)
                """, ingredientPublicId.toString(), ingredientCode,
                foodId, gramUnitId);
        Long ingredientId = jdbc.queryForObject(
                "select id from ingredients where code = ?", Long.class, ingredientCode);
        jdbc.update("""
                insert into ingredient_foods
                    (ingredient_id, food_id, preparation_state, yield_factor, is_primary)
                values (?, ?, 'UNSPECIFIED', 1.0000, true)
                """, ingredientId, foodId);

        UUID recipePublicId = UUID.randomUUID();
        String recipeSlug = SLUG_PREFIX + recipePublicId;
        jdbc.update("""
                insert into recipes
                    (public_id, title, slug, summary, servings, prep_minutes,
                     cook_minutes, difficulty, created_by_user_id, source,
                     source_reference, status, published_at, version)
                values (unhex(replace(?, '-', '')), 'P11 test recipe', ?,
                        'Synthetic P11 integration recipe', 1, 5, 5, 'EASY',
                        null, 'CURATED', ?, 'PUBLISHED', ?, 0)
                """, recipePublicId.toString(), recipeSlug,
                SOURCE_PREFIX + "RECIPE:" + recipePublicId,
                Timestamp.valueOf(LocalDateTime.of(2026, 9, 19, 0, 0)));
        Long recipeId = jdbc.queryForObject(
                "select id from recipes where slug = ?", Long.class, recipeSlug);
        jdbc.update("""
                insert into recipe_ingredients
                    (recipe_id, line_number, ingredient_id, food_id, quantity,
                     unit_id, is_optional, allow_substitution)
                values (?, 1, ?, null, 100.0000, ?, false, true)
                """, recipeId, ingredientId, gramUnitId);
        Long mealSlotId = jdbc.queryForObject(
                "select id from meal_slot_types where code = 'BREAKFAST'", Long.class);
        jdbc.update("""
                insert into recipe_meal_slot_types (recipe_id, meal_slot_type_id)
                values (?, ?)
                """, recipeId, mealSlotId);

        return new Fixture(owner, ingredientPublicId, ingredientId,
                foodPublicId, foodId, recipePublicId, recipeId, gramUnitId);
    }

    private PantryStock insertPantryStock(Fixture fixture, BigDecimal quantity) {
        UUID pantryPublicId = UUID.randomUUID();
        jdbc.update("""
                insert into pantry_items
                    (public_id, user_id, ingredient_id, food_id,
                     quantity_initial, quantity_remaining, unit_id,
                     storage_location, acquired_on, expiry_date, expiry_kind,
                     expiry_confidence, status, closed_at, note, version)
                values (unhex(replace(?, '-', '')), ?, ?, ?, ?, ?, ?,
                        'FRIDGE', ?, ?, 'USE_BY', 'LABELLED', 'AVAILABLE',
                        null, null, 0)
                """, pantryPublicId.toString(), fixture.owner().internalId(),
                fixture.ingredientId(), fixture.foodId(), quantity, quantity,
                fixture.gramUnitId(), LocalDate.of(2026, 9, 19),
                LocalDate.of(2026, 9, 22));
        Long itemId = jdbc.queryForObject("""
                select id from pantry_items
                where public_id = unhex(replace(?, '-', ''))
                """, Long.class, pantryPublicId.toString());
        jdbc.update("""
                insert into pantry_item_events
                    (pantry_item_id, event_type, quantity_delta, quantity_after, note)
                values (?, 'ADDED', ?, ?, 'P11 fixture stock')
                """, itemId, quantity, quantity);
        return new PantryStock(itemId);
    }

    private Account insertAccount(String role) {
        UUID publicId = UUID.randomUUID();
        String email = EMAIL_PREFIX + role + "-" + publicId + "@example.test";
        jdbc.update("""
                insert into users
                    (public_id, email, password_hash, display_name,
                     account_status, email_verified_at, time_zone, locale, version)
                values (unhex(replace(?, '-', '')), ?, 'test-hash',
                        'P11 Meal Plan User', 'ACTIVE', ?, 'UTC', 'vi', 0)
                """, publicId.toString(), email,
                Timestamp.valueOf(LocalDateTime.of(2026, 9, 19, 0, 0)));
        Long internalId = jdbc.queryForObject(
                "select id from users where email = ?", Long.class, email);
        return new Account(publicId, internalId);
    }

    private BigDecimal pantryQuantity(Long itemId) {
        return jdbc.queryForObject(
                "select quantity_remaining from pantry_items where id = ?",
                BigDecimal.class, itemId);
    }

    private int pantryEventCount(Long itemId) {
        return count("select count(*) from pantry_item_events where pantry_item_id = ?",
                itemId);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private RequestPostProcessor auth(UUID publicId) {
        return jwt().jwt(token -> token.subject(publicId.toString()));
    }

    private record Account(UUID publicId, Long internalId) {
    }

    private record Fixture(
            Account owner,
            UUID ingredientPublicId,
            Long ingredientId,
            UUID foodPublicId,
            Long foodId,
            UUID recipePublicId,
            Long recipeId,
            Long gramUnitId) {
    }

    private record PantryStock(Long itemId) {
    }
}
