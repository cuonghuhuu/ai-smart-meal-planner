package com.smartmealplanner.mealplanning.orchestration;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.mealplanning.application.MealPlanGenerationCommand;
import com.smartmealplanner.mealplanning.application.MealPlanningAiClient;
import com.smartmealplanner.mealplanning.application.MealPlanningIntegrationException;
import com.smartmealplanner.mealplanning.application.MealPlanningIntegrationFailure;
import com.smartmealplanner.mealplanning.application.MealPlanningSnapshotAssembler;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationResponse;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractJson;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PersistedMealPlanGenerationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p11_orchestration")
            .withUsername("p11_orchestration_test")
            .withPassword(UUID.randomUUID().toString())
            .withCommand("--log-bin-trust-function-creators=1")
            .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired PersistedMealPlanGenerationService service;
    @Autowired UserAccountRepository users;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MealPlanningSnapshotAssembler snapshots;
    @MockitoBean MealPlanningAiClient ai;

    @Test
    void succeededCommitsPendingBeforeHttpAndStoresDeterministicRanks() throws Exception {
        UUID user = user();
        recipes();
        MealPlanGenerationRequest fixture = requestFixture();
        when(snapshots.assemble(any(), any(), any())).thenAnswer(call -> {
            UUID id = call.getArgument(1);
            assertPending(id);
            return withId(fixture, id);
        });
        when(ai.generate(any())).thenAnswer(call -> {
            MealPlanGenerationRequest request = call.getArgument(0);
            assertPending(request.requestId());
            var fixtureResponse = responseFixture("valid_succeeded_response.json");
            var breakfast = fixtureResponse.entries().get(0);
            var dinner = fixtureResponse.entries().get(1);
            // Dinner has the higher score and arrives first; rank remains slot order.
            breakfast = withScore(breakfast, "0.100000");
            dinner = withScore(withRecipe(dinner, breakfast.recipePublicId()), "0.900000");
            return new MealPlanGenerationResponse(fixtureResponse.contractVersion(),
                    request.requestId(), fixtureResponse.algorithmVersion(),
                    GenerationStatus.SUCCEEDED, List.of(dinner, breakfast), List.of());
        });

        var completed = service.generate(user, command(fixture));
        assertThat(completed.status()).isEqualTo(GenerationStatus.SUCCEEDED);
        Long requestId = requestId(completed.requestPublicId());
        assertTerminal(requestId, "SUCCEEDED", null, 2, 14, 1, 2, 0);
        assertThat(completed.mealPlanId()).isNotNull();
        assertThat(jdbc.queryForList("""
                SELECT slot.code FROM recommendation_results result
                JOIN meal_plan_entries entry ON entry.source_result_id = result.id
                JOIN meal_slot_types slot ON slot.id = entry.meal_slot_type_id
                WHERE result.request_id = ? ORDER BY result.rank_position
                """, String.class, requestId)).containsExactly("BREAKFAST", "DINNER");
        assertThat(jdbc.queryForList("""
                SELECT total_score FROM recommendation_results WHERE request_id = ?
                ORDER BY rank_position
                """, BigDecimal.class, requestId))
                .containsExactly(new BigDecimal("0.100000"), new BigDecimal("0.900000"));
    }

    @Test
    void degradedStoresOnlyExplicitGaps() throws Exception {
        UUID user = user();
        recipes();
        MealPlanGenerationRequest fixture = requestFixture();
        snapshot(fixture);
        respond("valid_degraded_response.json");
        var completed = service.generate(user, command(fixture));
        Long id = requestId(completed.requestPublicId());
        assertThat(completed.status()).isEqualTo(GenerationStatus.DEGRADED);
        assertTerminal(id, "DEGRADED", null, 1, 7, 1, 1, 1);
        assertThat(jdbc.queryForObject("""
                SELECT gap.reason_code FROM meal_plan_unfilled_slots gap
                JOIN meal_plans plan ON plan.id = gap.meal_plan_id
                WHERE plan.source_request_id = ?
                """, String.class, id)).isEqualTo("NO_ELIGIBLE_RECIPE");
    }

    @Test
    void infeasibleStoresNoPlanGraph() throws Exception {
        UUID user = user();
        MealPlanGenerationRequest fixture = requestFixture();
        snapshot(fixture);
        respond("valid_infeasible_response.json");
        var completed = service.generate(user, command(fixture));
        assertThat(completed.status()).isEqualTo(GenerationStatus.INFEASIBLE);
        assertThat(completed.mealPlanId()).isNull();
        assertTerminal(requestId(completed.requestPublicId()), "INFEASIBLE", null,
                0, 0, 0, 0, 0);
    }

    @Test
    void httpFailureCommitsFailedWithoutGraph() throws Exception {
        UUID user = user();
        MealPlanGenerationRequest fixture = requestFixture();
        snapshot(fixture);
        when(ai.generate(any())).thenAnswer(call -> {
            assertPending(((MealPlanGenerationRequest) call.getArgument(0)).requestId());
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.AI_SERVICE_TIMEOUT);
        });
        assertThatThrownBy(() -> service.generate(user, command(fixture)))
                .isInstanceOf(MealPlanningIntegrationException.class);
        assertLatestFailed(user, "AI_SERVICE_TIMEOUT");
    }

    @Test
    void strictValidationFailureCommitsFailedWithoutGraph() throws Exception {
        UUID user = user();
        MealPlanGenerationRequest fixture = requestFixture();
        snapshot(fixture);
        // The fixture has a fixed request UUID, so Gate D must reject it.
        when(ai.generate(any())).thenAnswer(call -> {
            assertPending(((MealPlanGenerationRequest) call.getArgument(0)).requestId());
            return responseFixture("valid_succeeded_response.json");
        });
        assertThatThrownBy(() -> service.generate(user, command(fixture)))
                .isInstanceOf(MealPlanningIntegrationException.class);
        assertLatestFailed(user, "AI_BAD_RESPONSE");
    }

    @Test
    void unavailableSelectedRecipeCommitsFailedWithoutGraph() throws Exception {
        UUID user = user();
        recipes();
        MealPlanGenerationRequest fixture = requestFixture();
        snapshot(fixture);
        respond("valid_succeeded_response.json");
        UUID selected = fixture.recipeCandidates().get(0).recipePublicId();
        jdbc.update("""
                UPDATE recipes SET status = 'ARCHIVED', archived_at = CURRENT_TIMESTAMP(6)
                WHERE public_id = UNHEX(REPLACE(?, '-', ''))
                """, selected.toString());
        try {
            assertThatThrownBy(() -> service.generate(user, command(fixture)))
                    .isInstanceOf(RuntimeException.class);
            assertLatestFailed(user, "PERSISTENCE_WRITE_FAILED");
        } finally {
            jdbc.update("""
                    UPDATE recipes SET status = 'PUBLISHED', archived_at = NULL
                    WHERE public_id = UNHEX(REPLACE(?, '-', ''))
                    """, selected.toString());
        }
    }

    @Test
    void terminalRollbackIsFollowedByIndependentFailedCommit() throws Exception {
        UUID user = user();
        recipes();
        MealPlanGenerationRequest fixture = requestFixture();
        snapshot(fixture);
        respond("valid_succeeded_response.json");
        jdbc.execute("""
                CREATE TRIGGER fail_second_result BEFORE INSERT ON recommendation_results
                FOR EACH ROW BEGIN
                    IF NEW.rank_position = 2 THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced terminal failure';
                    END IF;
                END
                """);
        try {
            assertThatThrownBy(() -> service.generate(user, command(fixture)))
                    .isInstanceOf(RuntimeException.class);
            assertLatestFailed(user, "PERSISTENCE_WRITE_FAILED");
        } finally {
            jdbc.execute("DROP TRIGGER fail_second_result");
        }
    }

    private void snapshot(MealPlanGenerationRequest fixture) {
        when(snapshots.assemble(any(), any(), any())).thenAnswer(call -> {
            UUID id = call.getArgument(1);
            assertPending(id);
            return withId(fixture, id);
        });
    }

    private void respond(String name) throws Exception {
        MealPlanGenerationResponse fixture = responseFixture(name);
        when(ai.generate(any())).thenAnswer(call -> {
            MealPlanGenerationRequest request = call.getArgument(0);
            assertPending(request.requestId());
            List<MealPlanGenerationResponse.Entry> entries = fixture.entries();
            if (fixture.status() == GenerationStatus.SUCCEEDED) {
                entries = List.of(entries.get(0), withRecipe(entries.get(1),
                        entries.get(0).recipePublicId()));
            }
            return new MealPlanGenerationResponse(fixture.contractVersion(), request.requestId(),
                    fixture.algorithmVersion(), fixture.status(), entries,
                    fixture.unfilledSlots());
        });
    }

    private void assertPending(UUID publicId) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        jdbc.query("""
                SELECT status, algorithm_version, constraints_hash, correlation_id,
                       requested_at, completed_at
                FROM recommendation_requests
                WHERE public_id = UNHEX(REPLACE(?, '-', ''))
                """, rs -> {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status")).isEqualTo("PENDING");
            assertThat(rs.getString("algorithm_version"))
                    .isEqualTo("HEURISTIC_MEAL_PLAN_V1");
            assertThat(rs.getString("constraints_hash")).matches("[0-9a-f]{64}");
            assertThat(rs.getString("correlation_id")).isEqualTo(publicId.toString());
            assertThat(rs.getObject("requested_at", LocalDateTime.class)).isNotNull();
            assertThat(rs.getObject("completed_at", LocalDateTime.class)).isNull();
            return null;
        }, publicId.toString());
    }

    private void assertLatestFailed(UUID user, String reason) {
        Long id = jdbc.queryForObject("""
                SELECT request.id FROM recommendation_requests request
                JOIN users u ON u.id = request.user_id
                WHERE u.public_id = UNHEX(REPLACE(?, '-', ''))
                ORDER BY request.id DESC LIMIT 1
                """, Long.class, user.toString());
        assertTerminal(id, "FAILED", reason, 0, 0, 0, 0, 0);
    }

    private void assertTerminal(Long id, String status, String reason, int results,
            int scores, int plans, int entries, int gaps) {
        jdbc.query("""
                SELECT status, failure_reason, requested_at, completed_at
                FROM recommendation_requests WHERE id = ?
                """, rs -> {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status")).isEqualTo(status);
            assertThat(rs.getString("failure_reason")).isEqualTo(reason);
            LocalDateTime requested = rs.getObject("requested_at", LocalDateTime.class);
            LocalDateTime completed = rs.getObject("completed_at", LocalDateTime.class);
            assertThat(requested).isNotNull();
            assertThat(completed).isNotNull().isAfterOrEqualTo(requested);
            return null;
        }, id);
        assertThat(count("SELECT COUNT(*) FROM recommendation_results WHERE request_id = ?", id))
                .isEqualTo(results);
        assertThat(count("""
                SELECT COUNT(*) FROM recommendation_result_scores score
                JOIN recommendation_results result ON result.id = score.result_id
                WHERE result.request_id = ?
                """, id)).isEqualTo(scores);
        assertThat(count("SELECT COUNT(*) FROM meal_plans WHERE source_request_id = ?", id))
                .isEqualTo(plans);
        if (plans == 1) {
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM meal_plans WHERE source_request_id = ?
                    """, String.class, id)).isEqualTo("DRAFT");
        }
        assertThat(count("""
                SELECT COUNT(*) FROM meal_plan_entries entry
                JOIN meal_plans plan ON plan.id = entry.meal_plan_id
                WHERE plan.source_request_id = ?
                """, id)).isEqualTo(entries);
        assertThat(count("""
                SELECT COUNT(*) FROM meal_plan_unfilled_slots gap
                JOIN meal_plans plan ON plan.id = gap.meal_plan_id
                WHERE plan.source_request_id = ?
                """, id)).isEqualTo(gaps);
    }

    private int count(String sql, Long id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private Long requestId(UUID publicId) {
        return jdbc.queryForObject("""
                SELECT id FROM recommendation_requests WHERE public_id = UNHEX(REPLACE(?, '-', ''))
                """, Long.class, publicId.toString());
    }

    private UUID user() {
        UserAccount account = new UserAccount(UUID.randomUUID() + "@example.test",
                "test-hash", "Orchestration test");
        account.verifyEmail(LocalDateTime.now(ZoneOffset.UTC));
        return users.saveAndFlush(account).publicId();
    }

    private void recipes() {
        for (int number = 1; number <= 2; number++) {
            String id = "dddddddd-dddd-4ddd-8ddd-ddddddddddd" + number;
            jdbc.update("""
                    INSERT INTO recipes (public_id, title, slug, status, published_at)
                    VALUES (UNHEX(REPLACE(?, '-', '')), 'Orchestration recipe', ?,
                            'PUBLISHED', CURRENT_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE id = id
                    """, id, "orchestration-" + number);
        }
    }

    private static MealPlanGenerationCommand command(MealPlanGenerationRequest request) {
        var planning = request.planning();
        return new MealPlanGenerationCommand(planning.startDate(), planning.days(),
                planning.requestedMealSlots(), planning.defaultServings(),
                planning.maxMinutesPerMeal());
    }

    private static MealPlanGenerationRequest withId(MealPlanGenerationRequest request, UUID id) {
        return new MealPlanGenerationRequest(request.contractVersion(), id,
                request.algorithmVersion(), request.planning(), request.hardConstraints(),
                request.softPreferences(), request.nutritionTargets(), request.unitDefinitions(),
                request.pantryLots(), request.ingredientFacts(), request.recipeCandidates());
    }

    private static MealPlanGenerationResponse.Entry withScore(
            MealPlanGenerationResponse.Entry entry, String score) {
        return new MealPlanGenerationResponse.Entry(entry.planDate(), entry.mealSlotCode(),
                entry.recipePublicId(), entry.servings(), new BigDecimal(score),
                entry.scoreComponents(), entry.explanation());
    }

    private static MealPlanGenerationResponse.Entry withRecipe(
            MealPlanGenerationResponse.Entry entry, UUID recipePublicId) {
        return new MealPlanGenerationResponse.Entry(entry.planDate(), entry.mealSlotCode(),
                recipePublicId, entry.servings(), entry.totalScore(),
                entry.scoreComponents(), entry.explanation());
    }

    private static MealPlanGenerationRequest requestFixture() throws Exception {
        return MealPlanningContractJson.createDefault().readRequest(fixture("valid_request.json"));
    }

    private static MealPlanGenerationResponse responseFixture(String name) throws Exception {
        return MealPlanningContractJson.createDefault().readResponse(fixture(name));
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("..", "contract_fixtures", "meal_planning", "v1", name));
    }
}
