package com.smartmealplanner.shopping.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.mealplanning.persistence.MealPlan;
import com.smartmealplanner.mealplanning.persistence.MealPlanEntry;
import com.smartmealplanner.mealplanning.persistence.MealPlanEntryRepository;
import com.smartmealplanner.mealplanning.persistence.MealPlanRepository;
import com.smartmealplanner.mealplanning.persistence.MealPlanUnfilledSlot;
import com.smartmealplanner.mealplanning.persistence.MealPlanUnfilledSlotRepository;
import com.smartmealplanner.mealplanning.persistence.RecommendationRequest;
import com.smartmealplanner.mealplanning.persistence.RecommendationRequestRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class ShoppingListIT {
    private static final String PATH =
            "/api/v1/me/meal-plans/{mealPlanPublicId}/shopping-list";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p13_shopping_list")
            .withUsername("p13_shopping_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RecommendationRequestRepository requests;
    @Autowired MealPlanRepository plans;
    @Autowired MealPlanEntryRepository entries;
    @Autowired MealPlanUnfilledSlotRepository unfilledSlots;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void ownerGetsScaledShoppingListFromArchivedRecipeAndAvailablePantryOnly()
            throws Exception {
        UserAccount owner = user();
        UserAccount other = user();
        MealPlan plan = plan(owner, RecommendationRequest.Status.SUCCEEDED);
        Ingredient chicken = ingredient("Chicken", "g");
        Ingredient salt = ingredient("Salt", null);
        Ingredient garnish = ingredient("Garnish", "g");
        UUID firstRecipe = recipe("First chicken recipe", 4);
        UUID secondRecipe = recipe("Second chicken recipe", 2);
        recipeLine(firstRecipe, 1, chicken, "600.0000", "g", false);
        recipeLine(firstRecipe, 2, salt, null, null, false);
        recipeLine(firstRecipe, 3, garnish, "20.0000", "g", true);
        recipeLine(secondRecipe, 1, chicken, "0.5000", "kg", false);
        entry(plan, DATE, "LUNCH", firstRecipe);
        entry(plan, DATE.plusDays(1), "DINNER", secondRecipe);
        jdbc.update("""
                UPDATE recipes SET status = 'ARCHIVED', archived_at = CURRENT_TIMESTAMP(6)
                WHERE public_id = UNHEX(REPLACE(?, '-', ''))
                """, firstRecipe.toString());

        pantryLot(owner, chicken, "400.0000", "g", DATE, "AVAILABLE");
        pantryLot(owner, chicken, "0.2500", "kg", null, "AVAILABLE");
        pantryLot(owner, chicken, "1000.0000", "g", null, "RESERVED");
        pantryLot(other, chicken, "1000.0000", "g", null, "AVAILABLE");

        mvc.perform(get(PATH, plan.publicId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String body = mvc.perform(get(PATH, plan.publicId()).with(owner(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealPlanPublicId")
                        .value(plan.publicId().toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].ingredientPublicId")
                        .value(chicken.publicId().toString()))
                .andExpect(jsonPath("$.items[0].ingredientCode")
                        .value(chicken.code()))
                .andExpect(jsonPath("$.items[0].ingredientDisplayName")
                        .value("Chicken"))
                .andExpect(jsonPath("$.items[0].requiredQuantity").value(800.0))
                .andExpect(jsonPath("$.items[0].pantryCoveredQuantity").value(550.0))
                .andExpect(jsonPath("$.items[0].quantityToBuy").value(250.0))
                .andExpect(jsonPath("$.items[0].unitCode").value("g"))
                .andExpect(jsonPath("$.unquantifiedItems.length()").value(1))
                .andExpect(jsonPath("$.unquantifiedItems[0].ingredientPublicId")
                        .value(salt.publicId().toString()))
                .andExpect(jsonPath("$.unquantifiedItems[0].ingredientCode")
                        .value(salt.code()))
                .andExpect(jsonPath("$.unquantifiedItems[0].ingredientDisplayName")
                        .value("Salt"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Garnish", garnish.code(), "\"id\"",
                "userId", "ownerId", "recipeId", "pantryItemId", "internalId",
                "version", "sourceRequestId", "rawRequest", "rawResponse");

        String crossOwner = notFound(other, plan.publicId());
        String unknown = notFound(other, UUID.randomUUID());
        assertThat(crossOwner).doesNotContain("userId", "ownerId", "internalId",
                "recipeId", "pantryItemId", "sourceRequestId", "rawRequest",
                "rawResponse", "version");
        assertThat(unknown).doesNotContain("userId", "ownerId", "internalId",
                "recipeId", "pantryItemId", "sourceRequestId", "rawRequest",
                "rawResponse", "version");
    }

    @Test
    void degradedPlanCalculatesOnlyPersistedFilledEntries() throws Exception {
        UserAccount owner = user();
        MealPlan plan = plan(owner, RecommendationRequest.Status.DEGRADED);
        Ingredient chicken = ingredient("Chicken", "g");
        UUID recipe = recipe("Degraded chicken recipe", 4);
        recipeLine(recipe, 1, chicken, "600.0000", "g", false);
        entry(plan, DATE, "LUNCH", recipe);
        unfilledSlots.saveAndFlush(new MealPlanUnfilledSlot(plan,
                DATE.plusDays(1), slot("DINNER"),
                MealPlanUnfilledSlot.Reason.NO_ELIGIBLE_RECIPE,
                "No eligible dinner"));

        mvc.perform(get(PATH, plan.publicId()).with(owner(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].requiredQuantity").value(300.0))
                .andExpect(jsonPath("$.items[0].pantryCoveredQuantity").value(0.0))
                .andExpect(jsonPath("$.items[0].quantityToBuy").value(300.0))
                .andExpect(jsonPath("$.unquantifiedItems.length()").value(0));
    }

    private String notFound(UserAccount requester, UUID planPublicId)
            throws Exception {
        return mvc.perform(get(PATH, planPublicId).with(owner(requester)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail")
                        .value("The resource was not found."))
                .andReturn().getResponse().getContentAsString();
    }

    private UserAccount user() {
        UserAccount user = new UserAccount(UUID.randomUUID() + "@example.test",
                "test-hash", "Shopping list test");
        user.verifyEmail(LocalDateTime.now(ZoneOffset.UTC));
        return users.saveAndFlush(user);
    }

    private MealPlan plan(UserAccount owner, RecommendationRequest.Status status) {
        RecommendationRequest request = requests.saveAndFlush(
                new RecommendationRequest(owner.internalId(),
                        RecommendationRequest.Kind.MEAL_PLAN));
        Long requestId = request.id();
        new TransactionTemplate(transactions).executeWithoutResult(ignored ->
                assertThat(requests.completePending(requestId, status.name(),
                        null, null)).isEqualTo(1));
        request = requests.findById(requestId).orElseThrow();
        return plans.saveAndFlush(new MealPlan(owner.internalId(), DATE,
                DATE.plusDays(6), new BigDecimal("2.00"), request));
    }

    private Ingredient ingredient(String displayName, String defaultUnitCode) {
        UUID publicId = UUID.randomUUID();
        String code = "p13_" + publicId;
        Long defaultUnitId = defaultUnitCode == null ? null : unit(defaultUnitCode);
        jdbc.update("""
                INSERT INTO ingredients
                    (public_id, code, display_name, default_unit_id,
                     is_staple, is_active, version)
                VALUES (UNHEX(REPLACE(?, '-', '')), ?, ?, ?, false, true, 0)
                """, publicId.toString(), code, displayName, defaultUnitId);
        Long id = jdbc.queryForObject("SELECT id FROM ingredients WHERE code = ?",
                Long.class, code);
        return new Ingredient(publicId, id, code);
    }

    private UUID recipe(String title, int servings) {
        UUID publicId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO recipes
                    (public_id, title, slug, servings, status, published_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), ?, ?, ?, 'PUBLISHED',
                        CURRENT_TIMESTAMP(6))
                """, publicId.toString(), title, publicId.toString(), servings);
        return publicId;
    }

    private void recipeLine(UUID recipePublicId, int lineNumber,
            Ingredient ingredient, String quantity, String unitCode,
            boolean optional) {
        jdbc.update("""
                INSERT INTO recipe_ingredients
                    (recipe_id, line_number, ingredient_id, quantity, unit_id,
                     is_optional)
                VALUES (?, ?, ?, ?, ?, ?)
                """, recipeId(recipePublicId), lineNumber, ingredient.id(),
                quantity == null ? null : new BigDecimal(quantity),
                unitCode == null ? null : unit(unitCode), optional);
    }

    private void entry(MealPlan plan, LocalDate date, String slotCode,
            UUID recipePublicId) {
        entries.saveAndFlush(new MealPlanEntry(plan, date, slot(slotCode),
                (short) 1, recipeId(recipePublicId), null,
                new BigDecimal("2.00"),
                MealPlanEntry.Provenance.AI_GENERATED, null));
    }

    private void pantryLot(UserAccount owner, Ingredient ingredient,
            String quantity, String unitCode, LocalDate expiryDate,
            String pantryStatus) {
        UUID publicId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pantry_items
                    (public_id, user_id, ingredient_id, quantity_initial,
                     quantity_remaining, unit_id, acquired_on, expiry_date,
                     expiry_kind, expiry_confidence, status)
                VALUES (UNHEX(REPLACE(?, '-', '')), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, publicId.toString(), owner.internalId(), ingredient.id(),
                new BigDecimal(quantity), new BigDecimal(quantity),
                unit(unitCode), DATE.minusDays(1), expiryDate,
                expiryDate == null ? "UNKNOWN" : "USE_BY",
                expiryDate == null ? "UNKNOWN" : "LABELLED", pantryStatus);
    }

    private Long recipeId(UUID publicId) {
        return jdbc.queryForObject("""
                SELECT id FROM recipes WHERE public_id = UNHEX(REPLACE(?, '-', ''))
                """, Long.class, publicId.toString());
    }

    private Long unit(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM measurement_units WHERE code = ?",
                Long.class, code);
    }

    private Long slot(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM meal_slot_types WHERE code = ?",
                Long.class, code);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor
            owner(UserAccount account) {
        return jwt().jwt(value -> value.subject(account.publicId().toString()));
    }

    private record Ingredient(UUID publicId, Long id, String code) {
    }
}
