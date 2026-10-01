package com.smartmealplanner.mealplanning.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.AlgorithmVersion;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.ScoreComponentCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.UnfilledSlotReasonCode;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Begin;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Entry;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Gap;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.GeneratedPlan;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Score;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Started;

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
class MealPlanWritersIT {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p11_writers")
            .withUsername("p11_writers_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired MealPlanRequestWriter beginWriter;
    @Autowired MealPlanTerminalWriter terminalWriter;
    @Autowired MealPlanFailureWriter failureWriter;
    @Autowired RecommendationRequestRepository requests;
    @Autowired UserAccountRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void beginCommitsAndSucceededStoresCompleteGraphInStorageOrder() {
        Long userId = user();
        Long breakfastRecipe = recipe();
        Long lunchRecipe = recipe();
        UUID publicId = UUID.randomUUID();
        Started started = beginWriter.begin(new Begin(userId, publicId,
                AlgorithmVersion.HEURISTIC_MEAL_PLAN_V1, "a".repeat(64)));

        RecommendationRequest pending = requests.findById(started.requestId()).orElseThrow();
        assertThat(pending.status()).isEqualTo(RecommendationRequest.Status.PENDING);
        assertThat(pending.publicId()).isEqualTo(publicId);
        assertThat(pending.requestKind()).isEqualTo(RecommendationRequest.Kind.MEAL_PLAN);
        assertThat(pending.algorithmVersion()).isEqualTo("HEURISTIC_MEAL_PLAN_V1");
        assertThat(pending.constraintsHash()).isEqualTo("a".repeat(64));
        assertThat(pending.requestedAt()).isNotNull();
        assertEmptyGraph(started.requestId());

        GeneratedPlan command = generated(started.requestId(), GenerationStatus.SUCCEEDED,
                List.of(entry(MealSlotCode.LUNCH, lunchRecipe),
                        entry(MealSlotCode.BREAKFAST, breakfastRecipe)), List.of());
        assertReason(() -> terminalWriter.persistGenerated(generated(started.requestId(),
                GenerationStatus.SUCCEEDED,
                List.of(entry(MealSlotCode.BREAKFAST, breakfastRecipe)),
                List.of(new Gap(DATE, MealSlotCode.LUNCH,
                        UnfilledSlotReasonCode.PANTRY_INFEASIBLE, null)))),
                MealPlanPersistenceException.Reason.INVALID_COMMAND);
        assertThat(requests.findById(started.requestId()).orElseThrow().status())
                .isEqualTo(RecommendationRequest.Status.PENDING);
        Long planId = terminalWriter.persistGenerated(command);
        RecommendationRequest terminal = requests.findById(started.requestId()).orElseThrow();
        assertThat(terminal.status()).isEqualTo(RecommendationRequest.Status.SUCCEEDED);
        assertThat(terminal.failureReason()).isNull();
        assertThat(terminal.completedAt()).isNotNull();
        assertCompletionOrder(started.requestId());
        assertThat(terminal.durationMs()).isEqualTo(42);
        assertThat(count("recommendation_results", "request_id", started.requestId())).isEqualTo(2);
        assertThat(scoreCount(started.requestId())).isEqualTo(14);
        assertThat(count("meal_plans", "source_request_id", started.requestId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM meal_plans WHERE id = ?",
                String.class, planId)).isEqualTo("DRAFT");
        assertThat(entryCount(started.requestId())).isEqualTo(2);
        assertThat(gapCount(started.requestId())).isZero();
        assertThat(jdbc.queryForList("""
                SELECT slot.code FROM recommendation_results result
                JOIN meal_plan_entries entry ON entry.source_result_id = result.id
                JOIN meal_slot_types slot ON slot.id = entry.meal_slot_type_id
                WHERE result.request_id = ? ORDER BY result.rank_position
                """, String.class, started.requestId()))
                .containsExactly("BREAKFAST", "LUNCH");

        assertReason(() -> terminalWriter.persistGenerated(command),
                MealPlanPersistenceException.Reason.INVALID_TRANSITION);
        assertReason(() -> failureWriter.markFailed(started.requestId(), 45,
                "PERSISTENCE_WRITE_FAILED"),
                MealPlanPersistenceException.Reason.INVALID_TRANSITION);
    }

    @Test
    void degradedPersistsOnlyExplicitGapsAndInfeasiblePersistsNoGraph() {
        Long userId = user();
        Long recipeId = recipe();
        Started degraded = begin(userId);
        terminalWriter.persistGenerated(generated(degraded.requestId(),
                GenerationStatus.DEGRADED,
                List.of(entry(MealSlotCode.BREAKFAST, recipeId)),
                List.of(new Gap(DATE, MealSlotCode.LUNCH,
                        UnfilledSlotReasonCode.PANTRY_INFEASIBLE, "No eligible pantry recipe"))));
        assertThat(requests.findById(degraded.requestId()).orElseThrow().status())
                .isEqualTo(RecommendationRequest.Status.DEGRADED);
        assertCompletionOrder(degraded.requestId());
        assertThat(entryCount(degraded.requestId())).isEqualTo(1);
        assertThat(gapCount(degraded.requestId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT gap.reason_code FROM meal_plan_unfilled_slots gap
                JOIN meal_plans plan ON plan.id = gap.meal_plan_id
                WHERE plan.source_request_id = ?
                """, String.class, degraded.requestId()))
                .isEqualTo("PANTRY_INFEASIBLE");

        Started infeasible = begin(userId);
        terminalWriter.markInfeasible(infeasible.requestId(), 17);
        RecommendationRequest request = requests.findById(infeasible.requestId()).orElseThrow();
        assertThat(request.status()).isEqualTo(RecommendationRequest.Status.INFEASIBLE);
        assertThat(request.failureReason()).isNull();
        assertThat(request.durationMs()).isEqualTo(17);
        assertCompletionOrder(infeasible.requestId());
        assertEmptyGraph(infeasible.requestId());
        assertReason(() -> failureWriter.markFailed(infeasible.requestId(), 18,
                "AI_SERVICE_TIMEOUT"),
                MealPlanPersistenceException.Reason.INVALID_TRANSITION);
    }

    @Test
    void failedWriterCommitsIndependentlyOfRollbackOnlyAmbientTransaction() {
        Started started = begin(user());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            status.setRollbackOnly();
            failureWriter.markFailed(started.requestId(), 91, "AI_SERVICE_TIMEOUT");
        });
        RecommendationRequest request = requests.findById(started.requestId()).orElseThrow();
        assertThat(request.status()).isEqualTo(RecommendationRequest.Status.FAILED);
        assertThat(request.failureReason()).isEqualTo("AI_SERVICE_TIMEOUT");
        assertThat(request.durationMs()).isEqualTo(91);
        assertCompletionOrder(started.requestId());
        assertEmptyGraph(started.requestId());
        assertReason(() -> terminalWriter.markInfeasible(started.requestId(), 92),
                MealPlanPersistenceException.Reason.INVALID_TRANSITION);
        assertReason(() -> failureWriter.markFailed(started.requestId(), 93, null),
                MealPlanPersistenceException.Reason.INVALID_COMMAND);
    }

    @Test
    void failedTerminalTransactionRollsBackPartialScoresThenFailedCommits() {
        Long userId = user();
        Long validRecipe = recipe();
        Started started = begin(userId);
        GeneratedPlan command = generated(started.requestId(), GenerationStatus.SUCCEEDED,
                List.of(entry(MealSlotCode.BREAKFAST, validRecipe),
                        entry(MealSlotCode.LUNCH, Long.MAX_VALUE)), List.of());
        assertThatThrownBy(() -> terminalWriter.persistGenerated(command))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(requests.findById(started.requestId()).orElseThrow().status())
                .isEqualTo(RecommendationRequest.Status.PENDING);
        assertEmptyGraph(started.requestId());

        failureWriter.markFailed(started.requestId(), 53, "PERSISTENCE_WRITE_FAILED");
        RecommendationRequest failed = requests.findById(started.requestId()).orElseThrow();
        assertThat(failed.status()).isEqualTo(RecommendationRequest.Status.FAILED);
        assertThat(failed.failureReason()).isEqualTo("PERSISTENCE_WRITE_FAILED");
        assertCompletionOrder(started.requestId());
        assertEmptyGraph(started.requestId());
        assertReason(() -> terminalWriter.persistGenerated(command),
                MealPlanPersistenceException.Reason.INVALID_TRANSITION);
    }

    private Started begin(Long userId) {
        Started started = beginWriter.begin(new Begin(userId, UUID.randomUUID(),
                AlgorithmVersion.HEURISTIC_MEAL_PLAN_V1, "b".repeat(64)));
        assertThat(requests.findById(started.requestId()).orElseThrow().requestedAt())
                .isNotNull();
        return started;
    }

    private void assertCompletionOrder(Long requestId) {
        jdbc.query("""
                SELECT requested_at, completed_at FROM recommendation_requests WHERE id = ?
                """, result -> {
            assertThat(result.next()).isTrue();
            LocalDateTime requestedAt = result.getObject("requested_at", LocalDateTime.class);
            LocalDateTime completedAt = result.getObject("completed_at", LocalDateTime.class);
            assertThat(requestedAt).isNotNull();
            assertThat(completedAt).isNotNull().isAfterOrEqualTo(requestedAt);
            assertThat(result.next()).isFalse();
            return null;
        }, requestId);
    }

    private GeneratedPlan generated(Long requestId, GenerationStatus status,
            List<Entry> entries, List<Gap> gaps) {
        return new GeneratedPlan(requestId, status, DATE, DATE,
                new BigDecimal("1.50"), 42,
                List.of(MealSlotCode.BREAKFAST, MealSlotCode.LUNCH), entries, gaps);
    }

    private Entry entry(MealSlotCode slot, Long recipeId) {
        return new Entry(DATE, slot, (short) 1, recipeId, new BigDecimal("1.50"),
                new BigDecimal("0.123456"), "Uses available ingredients", scores());
    }

    private List<Score> scores() {
        return Arrays.stream(ScoreComponentCode.values())
                .map(code -> new Score(code, new BigDecimal("0.654321"),
                        code.expectedPositiveWeight()))
                .toList();
    }

    private Long user() {
        return users.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.test",
                "test-hash", "Writer test")).internalId();
    }

    private Long recipe() {
        String slug = "writer-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO recipes (public_id, title, slug, status, published_at)
                VALUES (UUID_TO_BIN(UUID()), 'Writer recipe', ?, 'PUBLISHED', CURRENT_TIMESTAMP(6))
                """, slug);
        return jdbc.queryForObject("SELECT id FROM recipes WHERE slug = ?", Long.class, slug);
    }

    private int count(String table, String column, Long requestId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, requestId);
    }

    private int scoreCount(Long requestId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM recommendation_result_scores score
                JOIN recommendation_results result ON result.id = score.result_id
                WHERE result.request_id = ?
                """, Integer.class, requestId);
    }

    private int entryCount(Long requestId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM meal_plan_entries entry
                JOIN meal_plans plan ON plan.id = entry.meal_plan_id
                WHERE plan.source_request_id = ?
                """, Integer.class, requestId);
    }

    private int gapCount(Long requestId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM meal_plan_unfilled_slots gap
                JOIN meal_plans plan ON plan.id = gap.meal_plan_id
                WHERE plan.source_request_id = ?
                """, Integer.class, requestId);
    }

    private void assertEmptyGraph(Long requestId) {
        assertThat(count("recommendation_results", "request_id", requestId)).isZero();
        assertThat(scoreCount(requestId)).isZero();
        assertThat(count("meal_plans", "source_request_id", requestId)).isZero();
        assertThat(entryCount(requestId)).isZero();
        assertThat(gapCount(requestId)).isZero();
    }

    private void assertReason(Runnable action, MealPlanPersistenceException.Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(MealPlanPersistenceException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason));
    }
}
