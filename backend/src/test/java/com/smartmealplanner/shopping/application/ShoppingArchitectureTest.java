package com.smartmealplanner.shopping.application;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps Shopping on public module boundaries rather than foreign persistence. */
class ShoppingArchitectureTest {

    @Test
    void shoppingApplicationDoesNotImportForeignRepositoriesOrPersistence()
            throws Exception {
        Path root = Path.of("src", "main", "java", "com", "smartmealplanner",
                "shopping", "application");
        try (var files = Files.list(root)) {
            for (Path file : files.filter(
                    path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (!line.startsWith("import com.smartmealplanner.")) {
                        continue;
                    }
                    assertThat(line).as(file.getFileName().toString())
                            .doesNotContain(".persistence.", "Repository");
                }
            }
        }
    }
}
