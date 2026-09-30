package com.smartmealplanner.mealplanning;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies V002 against existing V001 rows and the real MySQL constraints. */
@Testcontainers
class V002MigrationIT {
    private static final int MYSQL_CHECK_VIOLATION = 3819;
    private static final int MYSQL_DUPLICATE_KEY = 1062;
    private static final int MYSQL_MISSING_PARENT_ROW = 1452;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p11_v002_upgrade")
            .withUsername("p11_test")
            .withPassword(java.util.UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");

    @Test
    void upgradesV001DataWithoutLossAndEnforcesGateEConstraints() {
        Flyway v001 = flyway(MigrationVersion.fromVersion("001"));
        assertThat(v001.migrate().migrationsExecuted).isEqualTo(2);
        JdbcTemplate jdbc = jdbc();
        insertV001Rows(jdbc);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM recommendation_requests
                WHERE status <> 'FAILED' AND failure_reason IS NOT NULL
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT source_request_id FROM meal_plans
                    WHERE source_request_id IS NOT NULL
                    GROUP BY source_request_id HAVING COUNT(*) > 1
                ) duplicates
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM meal_plans mp
                JOIN recommendation_requests rr ON rr.id = mp.source_request_id
                WHERE rr.request_kind <> 'MEAL_PLAN' OR rr.user_id <> mp.user_id
                """, Integer.class)).isZero();

        Flyway current = flyway(null);
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(Arrays.stream(current.info().applied()).map(info -> info.getScript()).toList())
                .containsExactlyInAnyOrder("V001__initial_schema.sql",
                        "R001__reference_data.sql",
                        "V002__p11_meal_plan_persistence.sql");
        assertThat(current.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'meal_plans'
                  AND index_name = 'ux_meal_plans_source_request' AND non_unique = 0
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'meal_plans'
                  AND index_name = 'ix_meal_plans_source_request'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT default_servings FROM meal_plans WHERE id = 401", BigDecimal.class))
                .isEqualByComparingTo("2.00");
        assertThat(jdbc.queryForObject(
                "SELECT total_score FROM recommendation_results WHERE id = 501", BigDecimal.class))
                .isEqualByComparingTo("0.123400");
        assertThat(jdbc.queryForObject("""
                SELECT score_value FROM recommendation_result_scores
                WHERE result_id = 501
                """, BigDecimal.class)).isEqualByComparingTo("0.456700");

        verifyDecimalFidelity(jdbc);
        verifyRequestStates(jdbc);
        verifyPlanUniquenessAndServings(jdbc);
        verifyUnfilledSlots(jdbc);
    }

    private static void insertV001Rows(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO users (id, public_id, email, password_hash, display_name)
                VALUES (101, UUID_TO_BIN(UUID()), 'p11-v002@example.test', 'test-hash', 'V002 test')
                """);
        jdbc.update("""
                INSERT INTO foods (id, public_id, display_name)
                VALUES (201, UUID_TO_BIN(UUID()), 'Migration food')
                """);
        jdbc.update("""
                INSERT INTO recommendation_requests
                    (id, public_id, user_id, request_kind, status, completed_at)
                VALUES (301, UUID_TO_BIN(UUID()), 101, 'MEAL_PLAN',
                        'SUCCEEDED', CURRENT_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO meal_plans
                    (id, public_id, user_id, start_date, end_date,
                     default_servings, source_request_id)
                VALUES (401, UUID_TO_BIN(UUID()), 101, '2026-10-01',
                        '2026-10-02', 2, 301)
                """);
        jdbc.update("""
                INSERT INTO recommendation_results
                    (id, request_id, rank_position, food_id, total_score)
                VALUES (501, 301, 1, 201, 0.1234)
                """);
        jdbc.update("""
                INSERT INTO recommendation_result_scores
                    (result_id, score_component_id, score_value, weight)
                SELECT 501, id, 0.4567, 0.4000
                FROM ai_score_components WHERE code = 'PANTRY_COVERAGE'
                """);
    }

    private static void verifyDecimalFidelity(JdbcTemplate jdbc) {
        jdbc.update("UPDATE recommendation_results SET total_score = 0.123456 WHERE id = 501");
        jdbc.update("""
                UPDATE recommendation_result_scores SET score_value = 0.654321
                WHERE result_id = 501
                """);
        assertThat(jdbc.queryForObject(
                "SELECT total_score FROM recommendation_results WHERE id = 501", BigDecimal.class))
                .isEqualByComparingTo("0.123456");
        assertThat(jdbc.queryForObject("""
                SELECT score_value FROM recommendation_result_scores
                WHERE result_id = 501
                """, BigDecimal.class)).isEqualByComparingTo("0.654321");
    }

    private static void verifyRequestStates(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO recommendation_requests
                    (id, public_id, user_id, request_kind, status, completed_at)
                VALUES (302, UUID_TO_BIN(UUID()), 101, 'MEAL_PLAN',
                        'INFEASIBLE', CURRENT_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO recommendation_requests
                    (id, public_id, user_id, request_kind, status,
                     completed_at, failure_reason)
                VALUES (303, UUID_TO_BIN(UUID()), 101, 'MEAL_PLAN',
                        'FAILED', CURRENT_TIMESTAMP(6), 'AI_SERVICE_TIMEOUT')
                """);
        assertSqlErrorCode(() -> jdbc.update("""
                INSERT INTO recommendation_requests
                    (public_id, user_id, request_kind, status, completed_at)
                VALUES (UUID_TO_BIN(UUID()), 101, 'MEAL_PLAN',
                        'UNKNOWN', CURRENT_TIMESTAMP(6))
                """), MYSQL_CHECK_VIOLATION);
        assertSqlErrorCode(() -> jdbc.update("""
                INSERT INTO recommendation_requests
                    (public_id, user_id, request_kind, status, completed_at)
                VALUES (UUID_TO_BIN(UUID()), 101, 'MEAL_PLAN',
                        'FAILED', CURRENT_TIMESTAMP(6))
                """), MYSQL_CHECK_VIOLATION);
        assertSqlErrorCode(() -> jdbc.update("""
                INSERT INTO recommendation_requests
                    (public_id, user_id, request_kind, status,
                     completed_at, failure_reason)
                VALUES (UUID_TO_BIN(UUID()), 101, 'MEAL_PLAN',
                        'DEGRADED', CURRENT_TIMESTAMP(6), 'not technical')
                """), MYSQL_CHECK_VIOLATION);
    }

    private static void verifyPlanUniquenessAndServings(JdbcTemplate jdbc) {
        for (String servings : new String[] {"0.50", "1.50", "50.00"}) {
            jdbc.update("""
                    INSERT INTO meal_plans
                        (public_id, user_id, start_date, end_date, default_servings)
                    VALUES (UUID_TO_BIN(UUID()), 101, '2026-10-01',
                            '2026-10-02', ?)
                    """, new BigDecimal(servings));
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM meal_plans WHERE source_request_id IS NULL
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT default_servings FROM meal_plans
                WHERE default_servings = 1.50
                """, BigDecimal.class)).isEqualByComparingTo("1.50");
        for (String servings : new String[] {"0", "-0.01", "50.01"}) {
            assertSqlErrorCode(() -> jdbc.update("""
                    INSERT INTO meal_plans
                        (public_id, user_id, start_date, end_date, default_servings)
                    VALUES (UUID_TO_BIN(UUID()), 101, '2026-10-01',
                            '2026-10-02', ?)
                    """, new BigDecimal(servings)), MYSQL_CHECK_VIOLATION);
        }
        assertSqlErrorCode(() -> jdbc.update("""
                INSERT INTO meal_plans
                    (public_id, user_id, start_date, end_date, source_request_id)
                VALUES (UUID_TO_BIN(UUID()), 101, '2026-10-01',
                        '2026-10-02', 301)
                """), MYSQL_DUPLICATE_KEY);
    }

    private static void verifyUnfilledSlots(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO recommendation_requests
                    (id, public_id, user_id, request_kind, status, completed_at)
                VALUES (304, UUID_TO_BIN(UUID()), 101, 'MEAL_PLAN',
                        'DEGRADED', CURRENT_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO meal_plans
                    (id, public_id, user_id, start_date, end_date, source_request_id)
                VALUES (406, UUID_TO_BIN(UUID()), 101, '2026-10-01',
                        '2026-10-02', 304)
                """);
        Long slotId = jdbc.queryForObject(
                "SELECT id FROM meal_slot_types WHERE code = 'BREAKFAST'", Long.class);
        jdbc.update("""
                INSERT INTO meal_plan_unfilled_slots
                    (meal_plan_id, plan_date, meal_slot_type_id, reason_code)
                VALUES (406, '2026-10-01', ?, 'NO_ELIGIBLE_RECIPE')
                """, slotId);
        assertSqlErrorCode(() -> jdbc.update("""
                INSERT INTO meal_plan_unfilled_slots
                    (meal_plan_id, plan_date, meal_slot_type_id, reason_code)
                VALUES (406, '2026-10-01', ?, 'NO_ELIGIBLE_RECIPE')
                """, slotId), MYSQL_DUPLICATE_KEY);
        assertSqlErrorCode(() -> jdbc.update("""
                INSERT INTO meal_plan_unfilled_slots
                    (meal_plan_id, plan_date, meal_slot_type_id, reason_code)
                VALUES (406, '2026-10-02', ?, 'UNKNOWN_REASON')
                """, slotId), MYSQL_CHECK_VIOLATION);
        assertSqlErrorCode(() -> jdbc.update("""
                INSERT INTO meal_plan_unfilled_slots
                    (meal_plan_id, plan_date, meal_slot_type_id, reason_code)
                VALUES (406, '2026-10-02', 999999, 'NO_ELIGIBLE_RECIPE')
                """), MYSQL_MISSING_PARENT_ROW);
        jdbc.update("DELETE FROM meal_plans WHERE id = 406");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM meal_plan_unfilled_slots WHERE meal_plan_id = 406
                """, Integer.class)).isZero();
    }

    private static Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .repeatableSqlMigrationPrefix("R001");
        if (target != null) { configuration.target(target); }
        return configuration.load();
    }

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    private static void assertSqlErrorCode(Runnable action, int expectedMysqlErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(DataAccessException.class, exception ->
                        assertThat(exception.getMostSpecificCause())
                                .isInstanceOfSatisfying(SQLException.class, sqlException ->
                                        assertThat(sqlException.getErrorCode())
                                                .isEqualTo(expectedMysqlErrorCode)));
    }
}
