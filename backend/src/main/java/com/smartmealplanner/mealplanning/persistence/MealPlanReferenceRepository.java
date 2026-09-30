package com.smartmealplanner.mealplanning.persistence;

import java.util.EnumMap;
import java.util.Map;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.ScoreComponentCode;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the existing reference vocabularies without duplicating their JPA mappings. */
@Repository
class MealPlanReferenceRepository {
    record Slot(Long id, String code, int displayOrder) { }

    private final JdbcTemplate jdbc;

    MealPlanReferenceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Map<MealSlotCode, Slot> slots() {
        Map<MealSlotCode, Slot> byCode = new EnumMap<>(MealSlotCode.class);
        jdbc.query("SELECT id, code, display_order FROM meal_slot_types", result -> {
            try {
                MealSlotCode code = MealSlotCode.valueOf(result.getString("code"));
                byCode.put(code, new Slot(result.getLong("id"), code.name(),
                        result.getInt("display_order")));
            } catch (IllegalArgumentException ignored) {
                // The database may add slots outside contract V1.
            }
        });
        return Map.copyOf(byCode);
    }

    Map<ScoreComponentCode, Long> scoreComponents() {
        Map<ScoreComponentCode, Long> byCode = new EnumMap<>(ScoreComponentCode.class);
        jdbc.query("SELECT id, code FROM ai_score_components", result -> {
            try {
                ScoreComponentCode code = ScoreComponentCode.valueOf(result.getString("code"));
                byCode.put(code, result.getLong("id"));
            } catch (IllegalArgumentException ignored) {
                // Other algorithms may declare additional components.
            }
        });
        return Map.copyOf(byCode);
    }

}
