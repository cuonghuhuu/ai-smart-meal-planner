package com.smartmealplanner.food;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.NumberToTextConverter;

/** Converts the official SMILING Vietnam XLSX workbook into the import contract. */
public final class SmilingVietnamWorkbookAdapter {
    public static final String SHEET_NAME = "WP3 FCT";

    private static final int HEADER_ROW_INDEX = 1;
    private static final int FIRST_DATA_ROW_INDEX = 2;
    private static final String DATASET =
            "SMILING Food Composition Table for Vietnam 2013";
    private static final String DATASET_VERSION = "2013";

    private static final List<String> REQUIRED_HEADERS = List.of(
            "Code",
            "FOOD_NAME_ENGLISH",
            "FOOD_NAME_LOCAL",
            "FOOD_GROUP",
            "FOOD_SUB_GROUP",
            "ENERGY",
            "PROTCNT(g)",
            "WATER(g)",
            "FAT(g)",
            "CHOCDF(g)",
            "FIBC(g)",
            "Ash(g)",
            "CA(mg)",
            "FE(mg)",
            "ZN(mg)",
            "VITC(mg)",
            "THIA(mg)",
            "RIBF(mg)",
            "NIA(mg)",
            "VITB6A(mg)",
            "DFE(mcg)",
            "VITB12(mcg)",
            "VITA(mcg)",
            "VITA_RAE(mcg)",
            "VITD(mcg)");

    private static final List<NutrientMapping> NUTRIENT_MAPPINGS = List.of(
            new NutrientMapping("ENERGY", "ENERGY", "kcal"),
            new NutrientMapping("PROTCNT(g)", "PROTEIN", "g"),
            new NutrientMapping("WATER(g)", "WATER", "g"),
            new NutrientMapping("FAT(g)", "FAT_TOTAL", "g"),
            new NutrientMapping("CHOCDF(g)", "CARBOHYDRATE", "g"),
            new NutrientMapping("FIBC(g)", null, "g"),
            new NutrientMapping("Ash(g)", null, "g"),
            new NutrientMapping("CA(mg)", "CALCIUM", "mg"),
            new NutrientMapping("FE(mg)", "IRON", "mg"),
            new NutrientMapping("ZN(mg)", null, "mg"),
            new NutrientMapping("VITC(mg)", "VITAMIN_C", "mg"),
            new NutrientMapping("THIA(mg)", null, "mg"),
            new NutrientMapping("RIBF(mg)", null, "mg"),
            new NutrientMapping("NIA(mg)", null, "mg"),
            new NutrientMapping("VITB6A(mg)", null, "mg"),
            new NutrientMapping("DFE(mcg)", null, "mcg"),
            new NutrientMapping("VITB12(mcg)", null, "mcg"),
            new NutrientMapping("VITA(mcg)", null, "mcg"),
            new NutrientMapping("VITA_RAE(mcg)", "VITAMIN_A", "mcg"),
            new NutrientMapping("VITD(mcg)", "VITAMIN_D", "mcg"));

    /** Reads an XLSX path and returns the normalized document. */
    public CatalogImportDocument read(Path sourceFile) {
        return readWithReport(sourceFile).document();
    }

    /** Reads an XLSX path and retains row/warning accounting for operator reporting. */
    public SmilingVietnamWorkbookParseReport readWithReport(Path sourceFile) {
        if (sourceFile == null) {
            throw new IllegalArgumentException("sourceFile is required");
        }
        try (InputStream input = Files.newInputStream(sourceFile)) {
            return readWithReport(input);
        } catch (IOException exception) {
            throw new CatalogImportFileException(
                    "Unable to read SMILING Vietnam workbook", exception);
        }
    }

    /** Reads an XLSX stream and returns the normalized document. */
    public CatalogImportDocument read(InputStream input) {
        return readWithReport(input).document();
    }

    /** Reads an XLSX stream and retains row/warning accounting for operator reporting. */
    public SmilingVietnamWorkbookParseReport readWithReport(InputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        try (Workbook workbook = WorkbookFactory.create(input)) {
            return parse(workbook);
        } catch (SmilingVietnamWorkbookParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CatalogImportFileException(
                    "Unable to decode SMILING Vietnam workbook", exception);
        }
    }

    private SmilingVietnamWorkbookParseReport parse(Workbook workbook) {
        Sheet sheet = workbook.getSheet(SHEET_NAME);
        if (sheet == null) {
            throw new SmilingVietnamWorkbookParseException(
                    List.of(new CatalogImportIssue(
                            CatalogImportIssueType.UNKNOWN_SHEET,
                            null,
                            "sheet",
                            "row 1, column 'sheet': required sheet '"
                                    + SHEET_NAME + "' was not found")),
                    List.of(),
                    0,
                    0);
        }

        List<CatalogImportIssue> errors = new ArrayList<>();
        Map<String, Integer> columns = locateColumns(sheet, errors);
        if (!errors.isEmpty()) {
            throw new SmilingVietnamWorkbookParseException(errors, 0, 0);
        }

        List<CatalogImportFood> foods = new ArrayList<>();
        List<CatalogImportIssue> warnings = new ArrayList<>();
        Set<String> sourceCodes = new HashSet<>();
        Set<String> foodGroups = new LinkedHashSet<>();
        Set<String> foodSubgroups = new LinkedHashSet<>();
        Set<String> unmappedGroups = new LinkedHashSet<>();
        Set<String> unmappedSubgroups = new LinkedHashSet<>();
        int rowsParsed = 0;
        int rowsSkipped = 0;

        for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlankRow(row)) {
                rowsSkipped++;
                continue;
            }

            int rowNumber = rowIndex + 1;
            int rowErrorsBefore = errors.size();
            String foodGroup = readText(row, columns.get("FOOD_GROUP"));
            String foodSubgroup = readText(row, columns.get("FOOD_SUB_GROUP"));
            RowContext rowContext = new RowContext(
                    rowNumber,
                    null,
                    foodGroup,
                    foodSubgroup);
            String sourceCode = readCode(row, columns.get("Code"), rowContext, errors);
            if (sourceCode == null) {
                if (errors.size() == rowErrorsBefore) {
                    errors.add(issue(
                            CatalogImportIssueType.INVALID_SOURCE,
                            null,
                            "Code",
                            rowContext,
                            "Code must be nonblank"));
                }
                continue;
            }
            rowsParsed++;

            String catalogCode = "SMILING_VN_" + sourceCode;
            rowContext = new RowContext(
                    rowNumber,
                    sourceCode,
                    foodGroup,
                    foodSubgroup);
            if (!sourceCodes.add(sourceCode)) {
                errors.add(issue(
                        CatalogImportIssueType.DUPLICATE_SOURCE_CODE,
                        catalogCode,
                        "Code",
                        rowContext,
                        "source Code occurs more than once: " + sourceCode));
            }

            String englishName = readText(row, columns.get("FOOD_NAME_ENGLISH"));
            String localName = readText(row, columns.get("FOOD_NAME_LOCAL"));
            if (foodGroup != null) {
                foodGroups.add(foodGroup);
            }
            if (foodSubgroup != null) {
                foodSubgroups.add(foodSubgroup);
            }
            String displayName = localName == null ? englishName : localName;
            if (displayName == null) {
                errors.add(issue(
                        CatalogImportIssueType.INVALID_SOURCE,
                        catalogCode,
                        "FOOD_NAME_LOCAL",
                        rowContext,
                        "local and English food names are both blank"));
            }

            SmilingVietnamCategoryMapper.Resolution category =
                    SmilingVietnamCategoryMapper.resolve(foodGroup, foodSubgroup);
            if (!category.groupKnown()) {
                if (foodGroup != null) {
                    unmappedGroups.add(foodGroup);
                }
                errors.add(issue(
                        CatalogImportIssueType.UNKNOWN_CATEGORY,
                        catalogCode,
                        "FOOD_GROUP",
                        rowContext,
                        "unmapped FOOD_GROUP '" + printable(foodGroup) + "'"));
            } else if (!category.subgroupCompatible()) {
                if (foodSubgroup != null) {
                    unmappedSubgroups.add(foodSubgroup);
                }
                errors.add(issue(
                        CatalogImportIssueType.UNKNOWN_CATEGORY,
                        catalogCode,
                        "FOOD_SUB_GROUP",
                        rowContext,
                        "FOOD_SUB_GROUP '" + printable(foodSubgroup)
                                + "' is incompatible with FOOD_GROUP '"
                                + printable(foodGroup) + "'"));
            }

            List<CatalogImportNutrientFact> nutrientFacts = readNutrientFacts(
                    row,
                    columns,
                    catalogCode,
                    rowContext,
                    warnings,
                    errors);

            if (errors.size() != rowErrorsBefore) {
                continue;
            }

            try {
                foods.add(new CatalogImportFood(
                        "SMILING_VN:" + sourceCode,
                        catalogCode,
                        displayName,
                        englishName,
                        category.categoryCode(),
                        null,
                        NutritionBasis.PER_100_G,
                        null,
                        FoodSource.IMPORTED,
                        DATASET + "; code=" + sourceCode,
                        nutrientFacts,
                        null));
            } catch (IllegalArgumentException exception) {
                errors.add(issue(
                        CatalogImportIssueType.INVALID_CELL_VALUE,
                        catalogCode,
                        "record",
                        rowContext,
                        exception.getMessage()));
            }
        }

        if (rowsParsed == 0 && errors.isEmpty()) {
            errors.add(issue(
                    CatalogImportIssueType.INVALID_WORKBOOK,
                    null,
                    "Code",
                    FIRST_DATA_ROW_INDEX + 1,
                    "No row with a nonblank Code was found"));
        }
        if (!errors.isEmpty()) {
            throw new SmilingVietnamWorkbookParseException(
                    errors,
                    warnings,
                    rowsParsed,
                    rowsSkipped);
        }

        CatalogImportDocument document =
                new CatalogImportDocument(DATASET, DATASET_VERSION, foods);
        Map<String, Integer> foodsByCategory = new LinkedHashMap<>();
        Map<String, Integer> foodsWithNutrient = new LinkedHashMap<>();
        for (CatalogImportFood food : foods) {
            foodsByCategory.merge(food.categoryCode(), 1, Integer::sum);
            for (CatalogImportNutrientFact fact : food.nutrientFacts()) {
                if (fact.canonicalCode() != null) {
                    foodsWithNutrient.merge(fact.canonicalCode(), 1, Integer::sum);
                }
            }
        }

        return new SmilingVietnamWorkbookParseReport(
                document,
                rowsParsed,
                rowsSkipped,
                warnings,
                foodGroups,
                foodSubgroups,
                foodsByCategory,
                foodsWithNutrient,
                unmappedGroups,
                unmappedSubgroups);
    }

    private static Map<String, Integer> locateColumns(
            Sheet sheet,
            List<CatalogImportIssue> errors) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        Row header = sheet.getRow(HEADER_ROW_INDEX);
        if (header == null) {
            errors.add(issue(
                    CatalogImportIssueType.MISSING_REQUIRED_HEADER,
                    null,
                    "header",
                    HEADER_ROW_INDEX + 1,
                    "header row is missing"));
            return columns;
        }

        short lastCell = header.getLastCellNum();
        for (int columnIndex = 0; columnIndex < lastCell; columnIndex++) {
            String name = readText(header.getCell(columnIndex));
            if (name == null) {
                continue;
            }
            if (columns.putIfAbsent(name, columnIndex) != null) {
                errors.add(issue(
                        CatalogImportIssueType.DUPLICATE_HEADER,
                        null,
                        name,
                        HEADER_ROW_INDEX + 1,
                        "header occurs more than once"));
            }
        }

        for (String required : REQUIRED_HEADERS) {
            if (!columns.containsKey(required)) {
                errors.add(issue(
                        CatalogImportIssueType.MISSING_REQUIRED_HEADER,
                        null,
                        required,
                        HEADER_ROW_INDEX + 1,
                        "required header is missing"));
            }
        }
        return columns;
    }

    private static List<CatalogImportNutrientFact> readNutrientFacts(
            Row row,
            Map<String, Integer> columns,
            String catalogCode,
            RowContext rowContext,
            List<CatalogImportIssue> warnings,
            List<CatalogImportIssue> errors) {
        List<CatalogImportNutrientFact> facts = new ArrayList<>();
        for (NutrientMapping mapping : NUTRIENT_MAPPINGS) {
            if (mapping.canonicalCode() == null) {
                if (hasNonBlankValue(row, columns.get(mapping.sourceColumn()))) {
                    warnings.add(issue(
                            CatalogImportIssueType.UNSUPPORTED_NUTRIENT,
                            catalogCode,
                            mapping.sourceColumn(),
                            rowContext,
                            "source nutrient is intentionally not mapped to V001"));
                }
                continue;
            }

            BigDecimal amount = readNumber(
                    row,
                    columns.get(mapping.sourceColumn()),
                    mapping.sourceColumn(),
                    catalogCode,
                    rowContext,
                    errors);
            if (amount == null) {
                continue;
            }
            BigDecimal normalizedAmount = normalizeSupportedAmount(
                    amount,
                    mapping.sourceColumn(),
                    catalogCode,
                    rowContext,
                    errors);
            if (normalizedAmount == null) {
                continue;
            }
            try {
                facts.add(new CatalogImportNutrientFact(
                        mapping.sourceColumn(),
                        mapping.canonicalCode(),
                        normalizedAmount,
                        mapping.unitCode(),
                        FoodNutrientDataQuality.ESTIMATED));
            } catch (IllegalArgumentException exception) {
                errors.add(issue(
                        CatalogImportIssueType.INVALID_CELL_VALUE,
                        catalogCode,
                        mapping.sourceColumn(),
                        rowContext,
                        "invalid nutrient value '" + amount.toPlainString()
                                + "': " + exception.getMessage()));
            }
        }
        return facts;
    }

    private static BigDecimal normalizeSupportedAmount(
            BigDecimal amount,
            String columnName,
            String foodCode,
            RowContext rowContext,
            List<CatalogImportIssue> errors) {
        if (amount.signum() < 0) {
            errors.add(issue(
                    CatalogImportIssueType.INVALID_CELL_VALUE,
                    foodCode,
                    columnName,
                    rowContext,
                    "supported nutrient value must be non-negative: "
                            + amount.toPlainString()));
            return null;
        }

        BigDecimal scaled = amount.setScale(4, RoundingMode.HALF_UP);
        if (scaled.precision() > 12) {
            errors.add(issue(
                    CatalogImportIssueType.INVALID_CELL_VALUE,
                    foodCode,
                    columnName,
                    rowContext,
                    "supported nutrient value exceeds DECIMAL(12,4) after rounding: "
                            + scaled.toPlainString()));
            return null;
        }
        return scaled.stripTrailingZeros();
    }

    private static boolean hasNonBlankValue(Row row, Integer columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return false;
        }
        return cell.getCellType() != CellType.STRING
                || !cell.getStringCellValue().isBlank();
    }

    private static BigDecimal readNumber(
            Row row,
            int columnIndex,
            String columnName,
            String foodCode,
            RowContext rowContext,
            List<CatalogImportIssue> errors) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> {
                    yield numericValue(cell);
                }
                case STRING -> {
                    String text = cell.getStringCellValue().trim();
                    yield text.isEmpty() ? null : new BigDecimal(text);
                }
                default -> throw new NumberFormatException(
                        "expected a numeric or blank cell, found " + cell.getCellType());
            };
        } catch (NumberFormatException exception) {
            errors.add(issue(
                    CatalogImportIssueType.INVALID_CELL_VALUE,
                    foodCode,
                    columnName,
                    rowContext,
                    "invalid numeric value '" + cell + "'"));
            return null;
        }
    }

    private static String readCode(
            Row row,
            int columnIndex,
            RowContext rowContext,
            List<CatalogImportIssue> errors) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> {
                    yield numericValue(cell).stripTrailingZeros().toPlainString();
                }
                case STRING -> trimToNull(cell.getStringCellValue());
                default -> throw new NumberFormatException(
                        "expected a text or numeric Code, found " + cell.getCellType());
            };
        } catch (NumberFormatException exception) {
            errors.add(issue(
                    CatalogImportIssueType.INVALID_CELL_VALUE,
                    null,
                    "Code",
                    rowContext,
                    "invalid source Code value '" + cell + "'"));
            return null;
        }
    }

    private static String readText(Row row, Integer columnIndex) {
        return columnIndex == null ? null : readText(row.getCell(columnIndex));
    }

    private static String readText(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> trimToNull(cell.getStringCellValue());
            case NUMERIC -> numericValue(cell).stripTrailingZeros().toPlainString();
            default -> trimToNull(cell.toString());
        };
    }

    private static boolean isBlankRow(Row row) {
        if (row == null || row.getFirstCellNum() < 0) {
            return true;
        }
        for (int columnIndex = row.getFirstCellNum(); columnIndex < row.getLastCellNum(); columnIndex++) {
            Cell cell = row.getCell(columnIndex);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                continue;
            }
            if (cell.getCellType() != CellType.STRING || !cell.getStringCellValue().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal numericValue(Cell cell) {
        double value = cell.getNumericCellValue();
        if (!Double.isFinite(value)) {
            throw new NumberFormatException("non-finite numeric value");
        }
        return new BigDecimal(NumberToTextConverter.toText(value));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String printable(String value) {
        return value == null ? "" : value;
    }

    private static CatalogImportIssue issue(
            CatalogImportIssueType type,
            String foodCode,
            String field,
            int rowNumber,
            String detail) {
        return issue(
                type,
                foodCode,
                field,
                new RowContext(rowNumber, null, null, null),
                detail);
    }

    private static CatalogImportIssue issue(
            CatalogImportIssueType type,
            String foodCode,
            String field,
            RowContext rowContext,
            String detail) {
        return new CatalogImportIssue(
                type,
                foodCode,
                field,
                "Excel row " + rowContext.rowNumber()
                        + "; source Code=" + printable(rowContext.sourceCode())
                        + "; FOOD_GROUP=" + printable(rowContext.foodGroup())
                        + "; FOOD_SUB_GROUP=" + printable(rowContext.foodSubgroup())
                        + "; column='" + field + "'; reason=" + detail);
    }

    private record RowContext(
            int rowNumber,
            String sourceCode,
            String foodGroup,
            String foodSubgroup) {
    }

    private record NutrientMapping(
            String sourceColumn,
            String canonicalCode,
            String unitCode) {
    }
}
