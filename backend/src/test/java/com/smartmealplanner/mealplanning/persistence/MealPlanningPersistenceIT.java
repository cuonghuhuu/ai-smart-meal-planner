package com.smartmealplanner.mealplanning.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.recommendation.persistence.RecommendationRequest;
import com.smartmealplanner.recommendation.persistence.RecommendationRequestRepository;
import com.smartmealplanner.recommendation.persistence.RecommendationResult;
import com.smartmealplanner.recommendation.persistence.RecommendationResultRepository;
import com.smartmealplanner.recommendation.persistence.RecommendationResultScore;
import com.smartmealplanner.recommendation.persistence.RecommendationResultScoreRepository;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MealPlanningPersistenceIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p11_mapping")
            .withUsername("p11_mapping_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired UserAccountRepository users;
    @Autowired RecommendationRequestRepository requests;
    @Autowired RecommendationResultRepository results;
    @Autowired RecommendationResultScoreRepository scores;
    @Autowired MealPlanRepository plans;
    @Autowired MealPlanEntryRepository entries;
    @Autowired MealPlanUnfilledSlotRepository unfilledSlots;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entityManager;

    @Test
    void requestStatesAndSixDecimalScoresRoundTripThroughJpa() {
        Long userId = user();
        Long foodId = food();
        Long componentId = jdbc.queryForObject(
                "SELECT id FROM ai_score_components WHERE code = 'PANTRY_COVERAGE'", Long.class);

        RecommendationRequest infeasible = new RecommendationRequest(userId,
                RecommendationRequest.Kind.MEAL_PLAN);
        infeasible.finish(RecommendationRequest.Status.INFEASIBLE,
                LocalDateTime.now().plusSeconds(1), null);
        infeasible = requests.saveAndFlush(infeasible);
        assertThat(requests.findById(infeasible.id()).orElseThrow().status())
                .isEqualTo(RecommendationRequest.Status.INFEASIBLE);
        assertThat(requests.findByPublicId(infeasible.publicId()))
                .isPresent();

        RecommendationRequest failed = new RecommendationRequest(userId,
                RecommendationRequest.Kind.MEAL_PLAN);
        failed.finish(RecommendationRequest.Status.FAILED,
                LocalDateTime.now().plusSeconds(1), "AI_SERVICE_TIMEOUT");
        failed = requests.saveAndFlush(failed);
        assertThat(requests.findById(failed.id()).orElseThrow().failureReason())
                .isEqualTo("AI_SERVICE_TIMEOUT");

        RecommendationResult result = results.saveAndFlush(new RecommendationResult(
                infeasible, (short) 1, null, foodId, new BigDecimal("0.123456")));
        RecommendationResultScore score = scores.saveAndFlush(new RecommendationResultScore(
                result, componentId, new BigDecimal("0.654321"), new BigDecimal("0.4000")));

        Long infeasibleId = infeasible.id();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            entityManager.clear();
            RecommendationResult readResult = results.findById(result.id()).orElseThrow();
            RecommendationResultScore readScore = scores.findById(score.id()).orElseThrow();
            assertThat(readResult.totalScore()).isEqualByComparingTo("0.123456");
            assertThat(readScore.scoreValue()).isEqualByComparingTo("0.654321");
            assertThat(readScore.weight()).isEqualByComparingTo("0.4000");
            assertThat(readScore.result().request().id()).isEqualTo(infeasibleId);
            assertThat(readResult.foodId()).isEqualTo(foodId);
            assertThat(readScore.id().scoreComponentId()).isEqualTo(componentId);
        });
    }

    @Test
    void plansPreserveFractionalServingsNullSourcesUniqueSourcesAndDatabaseCascade() {
        Long userId = user();
        Long foodId = food();
        Long slotId = jdbc.queryForObject(
                "SELECT id FROM meal_slot_types WHERE code = 'BREAKFAST'", Long.class);
        LocalDate date = LocalDate.of(2026, 10, 1);

        MealPlan first = plans.saveAndFlush(new MealPlan(userId, date, date,
                new BigDecimal("1.50"), null));
        MealPlan second = plans.saveAndFlush(new MealPlan(userId, date, date,
                new BigDecimal("2.00"), null));
        assertThat(plans.findById(first.id()).orElseThrow().defaultServings())
                .isEqualByComparingTo("1.50");
        assertThat(first.id()).isNotEqualTo(second.id());

        RecommendationRequest request = requests.saveAndFlush(
                new RecommendationRequest(userId, RecommendationRequest.Kind.MEAL_PLAN));
        MealPlan sourced = plans.saveAndFlush(new MealPlan(userId, date, date.plusDays(1),
                new BigDecimal("1.50"), request));
        assertThat(plans.findBySourceRequestId(request.id())).isPresent();
        assertThatThrownBy(() -> plans.saveAndFlush(new MealPlan(userId, date, date,
                new BigDecimal("1.50"), request)))
                .isInstanceOf(DataIntegrityViolationException.class);

        MealPlanEntry entry = entries.saveAndFlush(new MealPlanEntry(sourced, date, slotId,
                (short) 1, null, foodId, new BigDecimal("1.50"),
                MealPlanEntry.Provenance.AI_GENERATED, null));
        MealPlanUnfilledSlot gap = unfilledSlots.saveAndFlush(new MealPlanUnfilledSlot(
                sourced, date.plusDays(1), slotId, MealPlanUnfilledSlot.Reason.SEARCH_LIMIT_REACHED,
                "Search budget exhausted"));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            entityManager.clear();
            MealPlanEntry readEntry = entries.findById(entry.id()).orElseThrow();
            MealPlanUnfilledSlot readGap = unfilledSlots.findById(gap.id()).orElseThrow();
            assertThat(readEntry.plan().id()).isEqualTo(sourced.id());
            assertThat(readEntry.foodId()).isEqualTo(foodId);
            assertThat(readEntry.mealSlotTypeId()).isEqualTo(slotId);
            assertThat(readEntry.servings()).isEqualByComparingTo("1.50");
            assertThat(readGap.plan().id()).isEqualTo(sourced.id());
            assertThat(readGap.mealSlotTypeId()).isEqualTo(slotId);
            assertThat(readGap.reasonCode()).isEqualTo(
                    MealPlanUnfilledSlot.Reason.SEARCH_LIMIT_REACHED);
            assertThat(readGap.createdAt()).isNotNull();
        });
        jdbc.update("DELETE FROM meal_plans WHERE id = ?", sourced.id());
        assertThat(unfilledSlots.findById(gap.id())).isEmpty();
        assertThat(entries.findById(entry.id())).isEmpty();
    }

    private Long user() {
        return users.saveAndFlush(new UserAccount(
                UUID.randomUUID() + "@example.test", "test-hash", "Mapping test"))
                .internalId();
    }

    private Long food() {
        jdbc.update("INSERT INTO foods (public_id, display_name) VALUES (UUID_TO_BIN(UUID()), 'Mapping food')");
        return jdbc.queryForObject("SELECT MAX(id) FROM foods", Long.class);
    }
}