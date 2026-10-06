package com.smartmealplanner.recommendation;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the reusable Recommendation module independent from Meal Planning. */
class RecommendationArchitectureTest {
    @Test
    void recommendationModuleNeverImportsMealPlanning() throws Exception {
        Path root = Path.of("src", "main", "java", "com", "smartmealplanner",
                "recommendation");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file))
                        .as(file.toString())
                        .doesNotContain("com.smartmealplanner.mealplanning");
            }
        }
    }

    @Test
    void recommendationPersistenceOwnsGenericRecommendationTables() throws Exception {
        Path root = Path.of("src", "main", "java", "com", "smartmealplanner",
                "recommendation", "persistence");
        assertThat(Files.readString(root.resolve("RecommendationRequest.java")))
                .contains("@Table(name = \"recommendation_requests\")");
        assertThat(Files.readString(root.resolve("RecommendationResult.java")))
                .contains("@Table(name = \"recommendation_results\")");
        assertThat(Files.readString(root.resolve("RecommendationResultScore.java")))
                .contains("@Table(name = \"recommendation_result_scores\")");
    }
}
