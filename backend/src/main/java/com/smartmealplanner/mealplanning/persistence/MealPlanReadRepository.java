package com.smartmealplanner.mealplanning.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartmealplanner.mealplanning.application.MealPlanReadPort;
import com.smartmealplanner.mealplanning.application.MealPlanReadPort.Entry;
import com.smartmealplanner.mealplanning.application.MealPlanReadPort.Header;
import com.smartmealplanner.mealplanning.application.MealPlanReadPort.UnfilledSlot;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.UnfilledSlotReasonCode;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MealPlanReadRepository implements MealPlanReadPort {

    private final JdbcTemplate jdbc;

    public MealPlanReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Header> findOwnedPlan(UUID mealPlanPublicId, Long userId) {
        if (mealPlanPublicId == null || userId == null) {
            return Optional.empty();
        }

        return jdbc.query("""
                SELECT plan.id,
                       plan.public_id,
                       request.public_id AS request_public_id,
                       request.status AS generation_status,
                       plan.start_date,
                       plan.end_date,
                       plan.default_servings
                FROM meal_plans plan
                JOIN recommendation_requests request
                  ON request.id = plan.source_request_id
                WHERE plan.public_id = ?
                  AND plan.user_id = ?
                  AND request.user_id = plan.user_id
                  AND request.request_kind = 'MEAL_PLAN'
                  AND request.status IN ('SUCCEEDED', 'DEGRADED')
                """,
                result -> {
                    if (!result.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(new Header(
                            result.getLong("id"),
                            PublicIds.uuid(result.getBytes("public_id")),
                            PublicIds.uuid(result.getBytes("request_public_id")),
                            GenerationStatus.valueOf(
                                    result.getString("generation_status")),
                            result.getObject("start_date", LocalDate.class),
                            result.getObject("end_date", LocalDate.class),
                            result.getBigDecimal("default_servings")));
                },
                PublicIds.bytes(mealPlanPublicId),
                userId);
    }

    @Override
    public List<Entry> findEntries(Long mealPlanId) {
        return jdbc.query("""
                SELECT entry.plan_date,
                       slot.code AS meal_slot_code,
                       recipe.public_id AS recipe_public_id,
                       recipe.title AS recipe_title,
                       entry.servings
                FROM meal_plan_entries entry
                JOIN meal_slot_types slot
                  ON slot.id = entry.meal_slot_type_id
                JOIN recipes recipe
                  ON recipe.id = entry.recipe_id
                WHERE entry.meal_plan_id = ?
                ORDER BY entry.plan_date,
                         slot.display_order,
                         entry.position_in_slot
                """,
                (result, row) -> new Entry(
                        result.getObject("plan_date", LocalDate.class),
                        MealSlotCode.valueOf(
                                result.getString("meal_slot_code")),
                        PublicIds.uuid(
                                result.getBytes("recipe_public_id")),
                        result.getString("recipe_title"),
                        result.getBigDecimal("servings")),
                mealPlanId);
    }

    @Override
    public List<UnfilledSlot> findUnfilledSlots(Long mealPlanId) {
        return jdbc.query("""
                SELECT gap.plan_date,
                       slot.code AS meal_slot_code,
                       gap.reason_code,
                       gap.explanation
                FROM meal_plan_unfilled_slots gap
                JOIN meal_slot_types slot
                  ON slot.id = gap.meal_slot_type_id
                WHERE gap.meal_plan_id = ?
                ORDER BY gap.plan_date,
                         slot.display_order
                """,
                (result, row) -> new UnfilledSlot(
                        result.getObject("plan_date", LocalDate.class),
                        MealSlotCode.valueOf(
                                result.getString("meal_slot_code")),
                        UnfilledSlotReasonCode.valueOf(
                                result.getString("reason_code")),
                        result.getString("explanation")),
                mealPlanId);
    }

}
