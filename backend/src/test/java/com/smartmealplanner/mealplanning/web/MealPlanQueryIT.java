package com.smartmealplanner.mealplanning.web;

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
class MealPlanQueryIT {
    private static final String PATH = "/api/v1/me/meal-plans/{mealPlanPublicId}";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p12_meal_plan_read")
            .withUsername("p12_read_test")
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
    void ownerReadsOrderedEntriesIncludingArchivedRecipe() throws Exception {
        UserAccount owner = user();
        MealPlan plan = plan(owner, RecommendationRequest.Status.SUCCEEDED);
        UUID dinnerRecipe = recipe("Dinner recipe");
        UUID breakfastRecipe = recipe("Oatmeal with Banana");
        Long dinnerSlot = slot("DINNER");
        Long breakfastSlot = slot("BREAKFAST");
        entries.saveAndFlush(new MealPlanEntry(plan, DATE, dinnerSlot, (short) 1,
                recipeId(dinnerRecipe), null, new BigDecimal("2.00"),
                MealPlanEntry.Provenance.AI_GENERATED, null));
        entries.saveAndFlush(new MealPlanEntry(plan, DATE, breakfastSlot, (short) 1,
                recipeId(breakfastRecipe), null, new BigDecimal("2.00"),
                MealPlanEntry.Provenance.AI_GENERATED, null));
        jdbc.update("""
                UPDATE recipes SET status = 'ARCHIVED', archived_at = CURRENT_TIMESTAMP(6)
                WHERE public_id = UNHEX(REPLACE(?, '-', ''))
                """, breakfastRecipe.toString());

        mvc.perform(get(PATH, plan.publicId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String body = mvc.perform(get(PATH, plan.publicId()).with(owner(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealPlanPublicId").value(plan.publicId().toString()))
                .andExpect(jsonPath("$.requestPublicId")
                        .value(plan.sourceRequest().publicId().toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.startDate").value("2026-10-05"))
                .andExpect(jsonPath("$.endDate").value("2026-10-11"))
                .andExpect(jsonPath("$.defaultServings").value(2.00))
                .andExpect(jsonPath("$.entries[0].mealSlotCode").value("BREAKFAST"))
                .andExpect(jsonPath("$.entries[0].recipePublicId")
                        .value(breakfastRecipe.toString()))
                .andExpect(jsonPath("$.entries[0].recipeTitle")
                        .value("Oatmeal with Banana"))
                .andExpect(jsonPath("$.entries[1].mealSlotCode").value("DINNER"))
                .andExpect(jsonPath("$.unfilledSlots.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"id\"", "userId", "recipeId",
                "mealSlotTypeId", "sourceRequestId", "internalId", "version",
                "constraintsHash", "rawRequest", "rawResponse");
    }

    @Test
    void degradedGapsAreOrderedAndOtherOwnerGetsSame404AsUnknownId() throws Exception {
        UserAccount owner = user();
        UserAccount other = user();
        MealPlan plan = plan(owner, RecommendationRequest.Status.DEGRADED);
        UUID filledRecipe = recipe("Degraded lunch recipe");
        entries.saveAndFlush(new MealPlanEntry(plan, DATE, slot("LUNCH"), (short) 1,
                recipeId(filledRecipe), null, new BigDecimal("2.00"),
                MealPlanEntry.Provenance.AI_GENERATED, null));
        unfilledSlots.saveAndFlush(new MealPlanUnfilledSlot(plan, DATE.plusDays(1),
                slot("DINNER"), MealPlanUnfilledSlot.Reason.NO_ELIGIBLE_RECIPE,
                "No eligible dinner"));
        unfilledSlots.saveAndFlush(new MealPlanUnfilledSlot(plan, DATE,
                slot("BREAKFAST"), MealPlanUnfilledSlot.Reason.SEARCH_LIMIT_REACHED,
                "Search budget exhausted"));

        mvc.perform(get(PATH, plan.publicId()).with(owner(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.entries[0].mealSlotCode").value("LUNCH"))
                .andExpect(jsonPath("$.entries[0].recipePublicId")
                        .value(filledRecipe.toString()))
                .andExpect(jsonPath("$.unfilledSlots[0].planDate").value("2026-10-05"))
                .andExpect(jsonPath("$.unfilledSlots[0].mealSlotCode").value("BREAKFAST"))
                .andExpect(jsonPath("$.unfilledSlots[1].planDate").value("2026-10-06"))
                .andExpect(jsonPath("$.unfilledSlots[1].mealSlotCode").value("DINNER"))
                .andExpect(jsonPath("$.unfilledSlots[1].reasonCode")
                        .value("NO_ELIGIBLE_RECIPE"))
                .andExpect(jsonPath("$.unfilledSlots[1].explanation")
                        .value("No eligible dinner"));

        String crossOwner = mvc.perform(get(PATH, plan.publicId()).with(owner(other)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("The resource was not found."))
                .andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(get(PATH, UUID.randomUUID()).with(owner(other)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("The resource was not found."))
                .andReturn().getResponse().getContentAsString();
        assertThat(crossOwner).doesNotContain("\"id\"", "userId", "ownerId",
                "internalId", "recipeId", "mealSlotTypeId", "sourceRequestId",
                "requestPublicId", "rawRequest", "rawResponse");
        assertThat(unknown).doesNotContain("\"id\"", "userId", "ownerId",
                "internalId", "recipeId", "mealSlotTypeId", "sourceRequestId",
                "requestPublicId", "rawRequest", "rawResponse");
    }

    private UserAccount user() {
        UserAccount user = new UserAccount(UUID.randomUUID() + "@example.test",
                "test-hash", "Meal plan read test");
        user.verifyEmail(LocalDateTime.now(ZoneOffset.UTC));
        return users.saveAndFlush(user);
    }

    private MealPlan plan(UserAccount owner, RecommendationRequest.Status status) {
        RecommendationRequest request = requests.saveAndFlush(
                new RecommendationRequest(owner.internalId(),
                        RecommendationRequest.Kind.MEAL_PLAN));
        Long requestId = request.id();
        new TransactionTemplate(transactions).executeWithoutResult(ignored ->
                assertThat(requests.completePending(requestId, status.name(), null, null))
                        .isEqualTo(1));
        request = requests.findById(requestId).orElseThrow();
        return plans.saveAndFlush(new MealPlan(owner.internalId(), DATE,
                DATE.plusDays(6), new BigDecimal("2.00"), request));
    }

    private UUID recipe(String title) {
        UUID publicId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO recipes (public_id, title, slug, status, published_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP(6))
                """, publicId.toString(), title, publicId.toString());
        return publicId;
    }

    private Long recipeId(UUID publicId) {
        return jdbc.queryForObject("""
                SELECT id FROM recipes WHERE public_id = UNHEX(REPLACE(?, '-', ''))
                """, Long.class, publicId.toString());
    }

    private Long slot(String code) {
        return jdbc.queryForObject("SELECT id FROM meal_slot_types WHERE code = ?",
                Long.class, code);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor owner(
            UserAccount account) {
        return jwt().jwt(value -> value.subject(account.publicId().toString()));
    }
}
