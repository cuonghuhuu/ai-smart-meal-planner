package com.smartmealplanner.mealplanning.application;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Lightweight source boundary check without another runtime dependency. */
class MealPlanningArchitectureTest {
    @Test
    void applicationNeverImportsAnotherModulesPersistenceOrWebLayer() throws Exception {
        Path root = Path.of("src", "main", "java", "com", "smartmealplanner",
                "mealplanning", "application");
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (!line.startsWith("import com.smartmealplanner.")) { continue; }
                    assertThat(line).as(file.getFileName().toString())
                            .doesNotContain(".persistence.", ".web.", "Repository", "PantryItem");
                }
            }
        }
    }

    @Test
    void remoteCallOrchestratorIsNotTransactional() throws Exception {
        Path service = Path.of("src", "main", "java", "com", "smartmealplanner",
                "mealplanning", "application", "MealPlanGenerationIntegrationService.java");
        assertThat(Files.readString(service)).doesNotContain("@Transactional");
    }
}
