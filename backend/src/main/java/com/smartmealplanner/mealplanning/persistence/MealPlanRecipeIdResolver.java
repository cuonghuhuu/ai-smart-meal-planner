package com.smartmealplanner.mealplanning.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Resolves only selected, validated recipe subjects after the AI response. */
@Repository
public class MealPlanRecipeIdResolver {
    private final JdbcTemplate jdbc;

    public MealPlanRecipeIdResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long publishedRecipeId(UUID publicId) {
        return jdbc.queryForObject("""
                SELECT id FROM recipes WHERE public_id = ? AND status = 'PUBLISHED'
                """, Long.class, PublicIds.bytes(publicId));
    }
}
