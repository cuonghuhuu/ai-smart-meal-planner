package com.smartmealplanner.food;

import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Explicit command-line entry point for a local normalized catalog document.
 * The profile and property keep this runner out of normal application startup.
 */
@Component
@Profile("catalog-import")
@ConditionalOnProperty(
        prefix = "app.catalog.import",
        name = "enabled",
        havingValue = "true")
final class CatalogImportRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogImportRunner.class);

    private final FoodCatalogImportService importer;
    private final ObjectMapper objectMapper;
    private final String configuredSourceFile;

    CatalogImportRunner(
            FoodCatalogImportService importer,
            ObjectMapper objectMapper,
            @Value("${app.catalog.import.source-file:}") String configuredSourceFile) {
        this.importer = importer;
        this.objectMapper = objectMapper;
        this.configuredSourceFile = configuredSourceFile;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String sourceFile = option(arguments, "catalog-import-file");
        if (sourceFile == null || sourceFile.isBlank()) {
            sourceFile = configuredSourceFile;
        }
        if (sourceFile == null || sourceFile.isBlank()) {
            throw new IllegalStateException(
                    "Catalog import requires --catalog-import-file or "
                            + "app.catalog.import.source-file");
        }

        CatalogImportDocument document = new CatalogImportJsonAdapter(objectMapper)
                .read(Path.of(sourceFile));
        CatalogImportReport report = importer.importDocument(document);
        LOG.info(
                "Catalog import completed: foodsRead={}, foodsCreated={}, "
                        + "foodsUpdated={}, ingredientsCreated={}, ingredientsUpdated={}, "
                        + "warnings={}",
                report.foodsRead(),
                report.foodsCreated(),
                report.foodsUpdated(),
                report.ingredientsCreated(),
                report.ingredientsUpdated(),
                report.warnings().size());
    }

    private static String option(ApplicationArguments arguments, String name) {
        List<String> values = arguments.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
