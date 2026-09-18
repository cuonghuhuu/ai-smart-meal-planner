package com.smartmealplanner.food;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
    private static final int MAX_REPORTED_WORKBOOK_ERRORS = 20;

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

        Path sourcePath = Path.of(sourceFile);
        boolean dryRun = arguments.containsOption("catalog-import-dry-run");
        int rowsParsed;
        int rowsSkipped;
        int parseWarnings;
        CatalogImportDocument document;
        SmilingVietnamWorkbookParseReport workbookReport = null;

        try {
            if (sourceFile.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                workbookReport =
                        new SmilingVietnamWorkbookAdapter().readWithReport(sourcePath);
                document = workbookReport.document();
                rowsParsed = workbookReport.rowsParsed();
                rowsSkipped = workbookReport.rowsSkipped();
                parseWarnings = workbookReport.warnings().size();
            } else {
                document = new CatalogImportJsonAdapter(objectMapper).read(sourcePath);
                rowsParsed = document.foods().size();
                rowsSkipped = 0;
                parseWarnings = 0;
            }
        } catch (SmilingVietnamWorkbookParseException exception) {
            LOG.error(
                    "Catalog workbook rejected: rowsParsed={}, rowsSkipped={}, "
                            + "warnings={}, errors={}, warningsByType={}, errorsByType={}",
                    exception.rowsParsed(),
                    exception.rowsSkipped(),
                    exception.warnings().size(),
                    exception.issues().size(),
                    exception.warningCounts(),
                    exception.issueCounts());
            logWorkbookErrors(exception);
            throw exception;
        }

        if (dryRun) {
            if (workbookReport == null) {
                LOG.info(
                        "Catalog import dry run completed: rowsParsed={}, foodsImported={}, "
                                + "rowsSkipped={}, warnings={}, errors={}",
                        rowsParsed,
                        document.foods().size(),
                        rowsSkipped,
                        parseWarnings,
                        0);
            } else {
                logWorkbookSummary(workbookReport, null, parseWarnings, true);
            }
            return;
        }

        CatalogImportReport report;
        try {
            report = importer.importDocument(document);
        } catch (CatalogImportValidationException exception) {
            LOG.error(
                    "Catalog import rejected: rowsParsed={}, rowsSkipped={}, "
                            + "warnings={}, errors={}",
                    rowsParsed,
                    rowsSkipped,
                    parseWarnings,
                    exception.issues().size());
            throw exception;
        }
        if (workbookReport == null) {
            LOG.info(
                    "Catalog import completed: rowsParsed={}, foodsImported={}, "
                            + "rowsSkipped={}, foodsRead={}, foodsCreated={}, foodsUpdated={}, "
                            + "ingredientsCreated={}, ingredientsUpdated={}, warnings={}, errors={}",
                    rowsParsed,
                    report.foodsCreated() + report.foodsUpdated(),
                    rowsSkipped,
                    report.foodsRead(),
                    report.foodsCreated(),
                    report.foodsUpdated(),
                    report.ingredientsCreated(),
                    report.ingredientsUpdated(),
                    parseWarnings + report.warnings().size(),
                    0);
        } else {
            logWorkbookSummary(workbookReport, report, parseWarnings, false);
        }
    }

    private void logWorkbookSummary(
            SmilingVietnamWorkbookParseReport workbookReport,
            CatalogImportReport importReport,
            int parseWarnings,
            boolean dryRun) {
        int foodsImported = importReport == null
                ? workbookReport.foodsImported()
                : importReport.foodsCreated() + importReport.foodsUpdated();
        int importWarnings = importReport == null ? 0 : importReport.warnings().size();
        int parserWarnings = dryRun
                ? parseWarnings
                : (int) workbookReport.warnings().stream()
                        .filter(issue -> issue.type() != CatalogImportIssueType.UNSUPPORTED_NUTRIENT)
                        .count();
        LOG.info(
                "SMILING catalog {}: rowsParsed={}, foodsImported={}, rowsSkipped={}, "
                        + "foodGroups={}, foodSubgroups={}, "
                        + "foodsByCategory={}, foodsWithNutrients={}, unmappedGroups={}, "
                        + "unmappedSubgroups={}, warnings={}, warningsByType={}, errors={}",
                dryRun ? "dry run completed" : "import completed",
                workbookReport.rowsParsed(),
                foodsImported,
                workbookReport.rowsSkipped(),
                workbookReport.foodGroups(),
                workbookReport.foodSubgroups(),
                workbookReport.foodsByCategory(),
                workbookReport.foodsWithNutrient(),
                workbookReport.unmappedGroups(),
                workbookReport.unmappedSubgroups(),
                parserWarnings + importWarnings,
                workbookReport.warningCounts(),
                0);
    }

    private static void logWorkbookErrors(
            SmilingVietnamWorkbookParseException exception) {
        int reported = Math.min(
                exception.issues().size(),
                MAX_REPORTED_WORKBOOK_ERRORS);
        for (int index = 0; index < reported; index++) {
            CatalogImportIssue issue = exception.issues().get(index);
            LOG.error(
                    "Workbook parse error {}/{}: type={}, field={}, foodCode={}, {}",
                    index + 1,
                    exception.issues().size(),
                    issue.type(),
                    issue.field(),
                    issue.foodCode(),
                    issue.detail());
        }
        if (exception.issues().size() > reported) {
            LOG.error(
                    "{} additional workbook parse errors omitted; "
                            + "only the first {} are logged",
                    exception.issues().size() - reported,
                    MAX_REPORTED_WORKBOOK_ERRORS);
        }
    }

    private static String option(ApplicationArguments arguments, String name) {
        List<String> values = arguments.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
