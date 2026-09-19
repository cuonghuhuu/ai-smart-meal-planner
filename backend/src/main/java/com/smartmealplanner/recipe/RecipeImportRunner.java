package com.smartmealplanner.recipe;

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

/** Explicit offline Recipe import command; disabled during normal startup. */
@Component
@Profile("recipe-import")
@ConditionalOnProperty(
        prefix = "app.recipe.import",
        name = "enabled",
        havingValue = "true")
final class RecipeImportRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(RecipeImportRunner.class);
    private static final int MAX_REPORTED_ISSUES = 20;

    private final RecipeImportService importer;
    private final ObjectMapper objectMapper;
    private final String configuredSourceFile;

    RecipeImportRunner(
            RecipeImportService importer,
            ObjectMapper objectMapper,
            @Value("${app.recipe.import.source-file:}") String configuredSourceFile) {
        this.importer = importer;
        this.objectMapper = objectMapper;
        this.configuredSourceFile = configuredSourceFile;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String sourceFile = option(arguments, "recipe-import-file");
        if (sourceFile == null || sourceFile.isBlank()) {
            sourceFile = configuredSourceFile;
        }
        if (sourceFile == null || sourceFile.isBlank()) {
            throw new IllegalStateException(
                    "Recipe import requires --recipe-import-file or "
                            + "app.recipe.import.source-file");
        }

        RecipeImportDocument document = new RecipeImportJsonAdapter(objectMapper)
                .read(Path.of(sourceFile));
        if (arguments.containsOption("recipe-import-dry-run")) {
            RecipeImportValidationReport validation = importer.validateDocument(document);
            LOG.info(
                    "Recipe import dry run: recipesRead={}, errors={}, warnings={}",
                    document.recipes().size(),
                    validation.errors().size(),
                    validation.warnings().size());
            logIssues(validation.errors());
            if (!validation.isValid()) {
                throw new RecipeImportValidationException(validation.errors());
            }
            return;
        }

        RecipeImportReport report = importer.importDocument(document);
        LOG.info(
                "Recipe import completed: recipesRead={}, recipesCreated={}, "
                        + "recipesUpdated={}, recipesUnchanged={}, ingredientLinesWritten={}, "
                        + "stepsWritten={}, nutritionSnapshotsComputed={}, warnings={}",
                report.recipesRead(),
                report.recipesCreated(),
                report.recipesUpdated(),
                report.recipesUnchanged(),
                report.ingredientLinesWritten(),
                report.stepsWritten(),
                report.nutritionSnapshotsComputed(),
                report.warnings().size());
        logIssues(report.warnings());
    }

    private static void logIssues(List<RecipeImportIssue> issues) {
        int reported = Math.min(issues.size(), MAX_REPORTED_ISSUES);
        for (int index = 0; index < reported; index++) {
            RecipeImportIssue issue = issues.get(index);
            LOG.warn(
                    "Recipe import issue {}/{}: type={}, sourceIdentifier={}, "
                            + "field={}, {}",
                    index + 1,
                    issues.size(),
                    issue.type(),
                    issue.sourceIdentifier(),
                    issue.field(),
                    issue.detail());
        }
        if (issues.size() > reported) {
            LOG.warn(
                    "{} additional Recipe import issues omitted; only the first {} logged",
                    issues.size() - reported,
                    MAX_REPORTED_ISSUES);
        }
    }

    private static String option(ApplicationArguments arguments, String name) {
        List<String> values = arguments.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
