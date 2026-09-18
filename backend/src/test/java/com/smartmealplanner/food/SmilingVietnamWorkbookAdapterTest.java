package com.smartmealplanner.food;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmilingVietnamWorkbookAdapterTest {
    private static final List<String> HEADERS = List.of(
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

    @TempDir
    Path temporaryDirectory;

    private final SmilingVietnamWorkbookAdapter adapter =
            new SmilingVietnamWorkbookAdapter();

    @Test
    void readsWp3FctByHeaderAndMapsSupportedFactsWithoutInventingFibre() throws IOException {
        SmilingVietnamWorkbookParseReport report = adapter.readWithReport(
                writeWorkbook("happy.xlsx", baseRows(), Set.of()));

        assertThat(report.rowsParsed()).isEqualTo(3);
        assertThat(report.rowsSkipped()).isEqualTo(1);
        assertThat(report.warnings()).hasSize(30)
                .extracting(CatalogImportIssue::type)
                .containsOnly(CatalogImportIssueType.UNSUPPORTED_NUTRIENT);
        assertThat(report.document().foods()).hasSize(3);
        assertThat(report.distinctFoodGroupCount()).isEqualTo(3);
        assertThat(report.distinctFoodSubgroupCount()).isEqualTo(3);
        assertThat(report.foodsByCategory()).containsEntry("VEGETABLES", 1)
                .containsEntry("GRAINS", 1)
                .containsEntry("DAIRY_MILK", 1);
        assertThat(report.foodsWithNutrient()).containsEntry("ENERGY", 3)
                .containsEntry("PROTEIN", 3)
                .containsEntry("WATER", 2)
                .containsEntry("VITAMIN_D", 2);
        assertThat(report.unmappedGroups()).isEmpty();
        assertThat(report.unmappedSubgroups()).isEmpty();

        CatalogImportFood rauMuong = report.document().foods().getFirst();
        assertThat(rauMuong.sourceIdentifier()).isEqualTo("SMILING_VN:1001");
        assertThat(rauMuong.catalogCode()).isEqualTo("SMILING_VN_1001");
        assertThat(rauMuong.displayName()).isEqualTo("Rau muống");
        assertThat(rauMuong.sourceName()).isEqualTo("Water spinach, raw");
        assertThat(rauMuong.categoryCode()).isEqualTo("VEGETABLES");
        assertThat(rauMuong.nutritionBasis()).isEqualTo(NutritionBasis.PER_100_G);
        assertThat(rauMuong.sourceReference())
                .isEqualTo("SMILING Food Composition Table for Vietnam 2013; code=1001");
        assertThat(report.ingredientMappings()).isEqualTo(3);
        CatalogImportIngredientMapping ingredientMapping = rauMuong.ingredientMapping();
        assertThat(ingredientMapping).isNotNull();
        assertThat(ingredientMapping.ingredientCode()).isEqualTo("ING_SMILING_VN_1001");
        assertThat(ingredientMapping.displayName()).isEqualTo(rauMuong.displayName());
        assertThat(ingredientMapping.categoryCode()).isEqualTo(rauMuong.categoryCode());
        assertThat(ingredientMapping.defaultUnitCode()).isEqualTo("g");
        assertThat(ingredientMapping.preparationState())
                .isEqualTo(IngredientPreparationState.UNSPECIFIED);
        assertThat(ingredientMapping.yieldFactor()).isEqualByComparingTo("1.0000");
        assertThat(ingredientMapping.primary()).isTrue();
        assertThat(ingredientMapping.aliases()).isEmpty();

        Map<String, CatalogImportNutrientFact> supported = rauMuong.nutrientFacts().stream()
                .filter(fact -> fact.canonicalCode() != null)
                .collect(java.util.stream.Collectors.toMap(
                        CatalogImportNutrientFact::canonicalCode,
                        fact -> fact));
        assertThat(supported).containsOnlyKeys(
                "ENERGY",
                "PROTEIN",
                "WATER",
                "FAT_TOTAL",
                "CARBOHYDRATE",
                "CALCIUM",
                "IRON",
                "VITAMIN_C",
                "VITAMIN_A",
                "VITAMIN_D");
        assertThat(supported.get("ENERGY").amount()).isEqualByComparingTo("25.1000");
        assertThat(supported.get("PROTEIN").amount()).isEqualByComparingTo("2.3000");
        assertThat(supported.get("WATER").amount()).isEqualByComparingTo("91.0000");
        assertThat(supported.get("FAT_TOTAL").amount()).isEqualByComparingTo("0.4000");
        assertThat(supported.get("CARBOHYDRATE").amount()).isEqualByComparingTo("3.6000");
        assertThat(supported.get("CALCIUM").amount()).isEqualByComparingTo("77.0000");
        assertThat(supported.get("IRON").amount()).isEqualByComparingTo("1.2000");
        assertThat(supported.get("VITAMIN_C").amount()).isEqualByComparingTo("55.0000");
        assertThat(supported.get("VITAMIN_A").amount()).isEqualByComparingTo("315.0000");
        assertThat(supported.get("VITAMIN_D").amount()).isEqualByComparingTo("0.1000");
        assertThat(supported.values())
                .extracting(CatalogImportNutrientFact::unitCode)
                .containsExactlyInAnyOrder("kcal", "g", "g", "g", "g", "mg", "mg", "mg", "mcg", "mcg");
        assertThat(supported.values())
                .extracting(CatalogImportNutrientFact::dataQuality)
                .containsOnly(FoodNutrientDataQuality.ESTIMATED);

        assertThat(rauMuong.nutrientFacts())
                .noneMatch(fact -> fact.sourceCode().equals("FIBC(g)"));
        assertThat(rauMuong.nutrientFacts())
                .noneMatch(fact -> fact.sourceCode().equals("VITA(mcg)"));
        assertThat(rauMuong.nutrientFacts())
                .noneMatch(fact -> "FIBER".equals(fact.canonicalCode()));
        assertThat(report.warningCounts())
                .containsEntry(CatalogImportIssueType.UNSUPPORTED_NUTRIENT, 30L);
    }

    @Test
    void sourceCode10003RemainsFoodOnly() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        String excludedDisplayName = "S\u1eefa m\u1eb9 (s\u1eefa ng\u01b0\u1eddi)";
        rows.getFirst().put("Code", "10003");
        rows.getFirst().put("FOOD_NAME_LOCAL", excludedDisplayName);

        SmilingVietnamWorkbookParseReport report = adapter.readWithReport(
                writeWorkbook("food-only-breast-milk.xlsx", rows, Set.of()));

        CatalogImportFood food = report.document().foods().getFirst();
        assertThat(food.catalogCode()).isEqualTo("SMILING_VN_10003");
        assertThat(food.displayName()).isEqualTo(excludedDisplayName);
        assertThat(food.ingredientMapping()).isNull();
        assertThat(report.ingredientMappings()).isEqualTo(2);
    }

    @Test
    void missingNumericCellsRemainMissingRatherThanBecomingZero() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.get(1).put("WATER(g)", null);
        rows.get(1).put("VITD(mcg)", null);

        CatalogImportFood food = adapter.readWithReport(
                writeWorkbook("missing-values.xlsx", rows, Set.of()))
                .document()
                .foods()
                .get(1);

        assertThat(food.categoryCode()).isEqualTo("GRAINS");
        assertThat(food.nutrientFacts())
                .extracting(CatalogImportNutrientFact::canonicalCode)
                .doesNotContain("WATER", "VITAMIN_D");
    }

    @Test
    void unknownSubgroupIsBlocking() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.getFirst().put("FOOD_SUB_GROUP", "Not in reviewed mapping");

        assertThatThrownBy(() -> adapter.read(
                writeWorkbook("unknown-subgroup.xlsx", rows, Set.of())))
                .isInstanceOfSatisfying(
                        SmilingVietnamWorkbookParseException.class,
                        exception -> assertThat(exception.issues())
                                .anySatisfy(issue -> {
                                    assertThat(issue.type())
                                            .isEqualTo(CatalogImportIssueType.UNKNOWN_CATEGORY);
                                    assertThat(issue.detail()).contains(
                                            "Excel row 3",
                                            "source Code=1001",
                                            "FOOD_GROUP=Vegetables",
                                            "FOOD_SUB_GROUP=Not in reviewed mapping",
                                            "column='FOOD_SUB_GROUP'");
                                }));
    }

    @Test
    void knownSubgroupUnderIncompatibleGroupIsBlocking() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.getFirst().put("FOOD_SUB_GROUP", "Cheese");

        assertThatThrownBy(() -> adapter.read(
                writeWorkbook("incompatible-subgroup.xlsx", rows, Set.of())))
                .isInstanceOfSatisfying(
                        SmilingVietnamWorkbookParseException.class,
                        exception -> assertThat(exception.issueCounts())
                                .containsEntry(CatalogImportIssueType.UNKNOWN_CATEGORY, 1L));
    }

    @Test
    void duplicateSourceCodeIsRejectedWithSourceLocation() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.get(2).put("Code", "1001");

        assertThatThrownBy(() -> adapter.read(
                writeWorkbook("duplicate-code.xlsx", rows, Set.of())))
                .isInstanceOfSatisfying(
                        SmilingVietnamWorkbookParseException.class,
                        exception -> assertThat(exception.issues())
                                .anySatisfy(issue -> {
                                    assertThat(issue.type())
                                            .isEqualTo(CatalogImportIssueType.DUPLICATE_SOURCE_CODE);
                                    assertThat(issue.detail())
                                            .contains("Excel row 5", "column='Code'");
                                }));
    }

    @Test
    void missingRequiredHeaderIsRejectedBeforeRowsAreRead() throws IOException {
        assertThatThrownBy(() -> adapter.read(
                writeWorkbook("missing-header.xlsx", baseRows(), Set.of("FOOD_GROUP"))))
                .isInstanceOfSatisfying(
                        SmilingVietnamWorkbookParseException.class,
                        exception -> assertThat(exception.issues())
                                .anySatisfy(issue -> {
                                    assertThat(issue.type())
                                            .isEqualTo(CatalogImportIssueType.MISSING_REQUIRED_HEADER);
                                    assertThat(issue.field()).isEqualTo("FOOD_GROUP");
                                    assertThat(issue.detail())
                                            .contains("Excel row 2", "FOOD_GROUP");
                                }));
    }

    @Test
    void invalidNumericValueIsRejectedWithSourceLocation() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.getFirst().put("ENERGY", "not-a-number");

        assertThatThrownBy(() -> adapter.read(
                writeWorkbook("invalid-number.xlsx", rows, Set.of())))
                .isInstanceOfSatisfying(
                        SmilingVietnamWorkbookParseException.class,
                        exception -> assertThat(exception.issues())
                                .anySatisfy(issue -> {
                                    assertThat(issue.type())
                                            .isEqualTo(CatalogImportIssueType.INVALID_CELL_VALUE);
                                    assertThat(issue.detail()).contains(
                                            "Excel row 3",
                                            "source Code=1001",
                                            "FOOD_GROUP=Vegetables",
                                            "FOOD_SUB_GROUP=Other vegetables",
                                            "column='ENERGY'",
                                            "reason=invalid numeric value");
                                }));
    }

    @Test
    void unknownFoodGroupIsBlockingAndIsNotMappedToOther() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.getFirst().put("FOOD_GROUP", "Unreviewed group");

        assertThatThrownBy(() -> adapter.read(
                writeWorkbook("unknown-group.xlsx", rows, Set.of())))
                .isInstanceOfSatisfying(
                        SmilingVietnamWorkbookParseException.class,
                        exception -> assertThat(exception.issues())
                                .anySatisfy(issue -> {
                                    assertThat(issue.type())
                                            .isEqualTo(CatalogImportIssueType.UNKNOWN_CATEGORY);
                                    assertThat(issue.detail()).contains(
                                            "Excel row 3",
                                            "FOOD_GROUP=Unreviewed group",
                                            "FOOD_SUB_GROUP=Other vegetables",
                                            "column='FOOD_GROUP'");
                                }));
    }

    @Test
    void allRealGroupAndSubgroupCombinationsMapWithoutCategoryErrors() throws IOException {
        List<CategoryCase> categoryCases = realCategoryCases();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < categoryCases.size(); index++) {
            CategoryCase categoryCase = categoryCases.get(index);
            Map<String, Object> row = new LinkedHashMap<>(baseRows().getFirst());
            row.put("Code", Integer.toString(2001 + index));
            row.put("FOOD_NAME_ENGLISH", "Synthetic category " + (index + 1));
            row.put("FOOD_NAME_LOCAL", "Thực phẩm thử " + (index + 1));
            row.put("FOOD_GROUP", categoryCase.foodGroup());
            row.put("FOOD_SUB_GROUP", categoryCase.foodSubgroup());
            rows.add(row);
        }

        SmilingVietnamWorkbookParseReport report = adapter.readWithReport(
                writeWorkbook("real-category-vocabulary.xlsx", rows, Set.of()));

        assertThat(report.document().foods()).hasSize(28);
        assertThat(report.document().foods())
                .extracting(CatalogImportFood::categoryCode)
                .containsExactly(categoryCases.stream()
                        .map(CategoryCase::categoryCode)
                        .toArray(String[]::new));
        assertThat(report.warnings())
                .noneMatch(issue -> issue.type() == CatalogImportIssueType.UNKNOWN_CATEGORY);
        assertThat(report.unmappedGroups()).isEmpty();
        assertThat(report.unmappedSubgroups()).isEmpty();
    }

    @Test
    void excelFloatingPointTailDoesNotBecomeInvalidScale() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.getFirst().put("ENERGY", 0.1d + 0.2d);

        CatalogImportNutrientFact energy = adapter.read(
                        writeWorkbook("floating-point-tail.xlsx", rows, Set.of()))
                .foods()
                .getFirst()
                .nutrientFacts()
                .stream()
                .filter(fact -> "ENERGY".equals(fact.canonicalCode()))
                .findFirst()
                .orElseThrow();

        assertThat(energy.amount()).isEqualByComparingTo("0.3");
    }

    @Test
    void parseFailureProvidesCountsAndBoundedDiagnosticFields() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.getFirst().put("FOOD_GROUP", "Unreviewed group");
        rows.getFirst().put("ENERGY", "not-a-number");

        assertThatThrownBy(() -> adapter.read(
                writeWorkbook("diagnostic-fields.xlsx", rows, Set.of())))
                .isInstanceOfSatisfying(
                        SmilingVietnamWorkbookParseException.class,
                        exception -> {
                            assertThat(exception.issueCounts())
                                    .containsEntry(CatalogImportIssueType.UNKNOWN_CATEGORY, 1L)
                                    .containsEntry(CatalogImportIssueType.INVALID_CELL_VALUE, 1L);
                            assertThat(exception.issues())
                                    .allSatisfy(issue -> assertThat(issue.detail()).contains(
                                            "Excel row 3",
                                            "source Code=1001",
                                            "FOOD_GROUP=Unreviewed group",
                                            "FOOD_SUB_GROUP=Other vegetables",
                                            "column=",
                                            "reason="));
                        });
    }

    private Path writeWorkbook(
            String fileName,
            List<Map<String, Object>> rows,
            Set<String> omittedHeaders) throws IOException {
        Path file = temporaryDirectory.resolve(fileName);
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("SMILING FCT");
            workbook.createSheet("Authors");
            workbook.createSheet("Components description");
            Sheet sheet = workbook.createSheet(SmilingVietnamWorkbookAdapter.SHEET_NAME);
            workbook.createSheet("References");
            workbook.createSheet("Sheet3");

            sheet.createRow(0).createCell(0)
                    .setCellValue("Synthetic SMILING Vietnam workbook for parser tests");
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(1);
            int columnIndex = 0;
            for (String header : HEADERS) {
                if (!omittedHeaders.contains(header)) {
                    headerRow.createCell(columnIndex++).setCellValue(header);
                }
            }

            int rowIndex = 2;
            for (Map<String, Object> values : rows) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex++);
                columnIndex = 0;
                for (String header : HEADERS) {
                    if (omittedHeaders.contains(header)) {
                        continue;
                    }
                    Object value = values.get(header);
                    if (value != null) {
                        setCellValue(row.createCell(columnIndex), value);
                    }
                    columnIndex++;
                }
            }
            sheet.createRow(rowIndex);

            try (var output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
        return file;
    }

    private static void setCellValue(org.apache.poi.ss.usermodel.Cell cell, Object value) {
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    @Test
    void supportedHighPrecisionValuesAreRoundedToDatabaseScale() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.getFirst().put("VITA_RAE(mcg)", new BigDecimal("10.7083333333333"));
        rows.getFirst().put("VITC(mg)", new BigDecimal("4.18604651162791"));
        rows.getFirst().put("CHOCDF(g)", new BigDecimal("0.699999999999989"));

        CatalogImportFood food = adapter.read(
                        writeWorkbook("supported-high-precision.xlsx", rows, Set.of()))
                .foods()
                .getFirst();

        Map<String, CatalogImportNutrientFact> facts = food.nutrientFacts().stream()
                .collect(java.util.stream.Collectors.toMap(
                        CatalogImportNutrientFact::canonicalCode,
                        fact -> fact));

        assertThat(facts.get("VITAMIN_A").amount()).isEqualByComparingTo("10.7083");
        assertThat(facts.get("VITAMIN_C").amount()).isEqualByComparingTo("4.1860");
        assertThat(facts.get("CARBOHYDRATE").amount()).isEqualByComparingTo("0.7000");
        assertThat(facts.values())
                .allSatisfy(fact -> assertThat(fact.amount().scale()).isLessThanOrEqualTo(4));
    }

    @Test
    void unsupportedHighPrecisionNutrientsOnlyWarnAndCreateNoFacts() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.getFirst().put("ZN(mg)", new BigDecimal("2.28637413394919"));
        rows.getFirst().put("VITB6A(mg)", new BigDecimal("0.111200923787529"));
        rows.getFirst().put("DFE(mcg)", new BigDecimal("7.27482678983834"));

        SmilingVietnamWorkbookParseReport report = adapter.readWithReport(
                writeWorkbook("unsupported-high-precision.xlsx", rows, Set.of()));

        assertThat(report.warningCounts())
                .containsEntry(CatalogImportIssueType.UNSUPPORTED_NUTRIENT, 30L);

        assertThat(report.document().foods().getFirst().nutrientFacts())
                .noneMatch(fact -> Set.of("ZN(mg)", "VITB6A(mg)", "DFE(mcg)")
                        .contains(fact.sourceCode()));
    }

    @Test
    void negativeSupportedNutrientIsRejected() throws IOException {
        List<Map<String, Object>> rows = baseRows();
        rows.getFirst().put("ENERGY", new BigDecimal("-1.0"));

        assertThatThrownBy(() -> adapter.read(
                        writeWorkbook("negative-energy.xlsx", rows, Set.of())))
                .isInstanceOfSatisfying(
                        SmilingVietnamWorkbookParseException.class,
                        exception -> assertThat(exception.issues())
                                .anySatisfy(issue -> {
                                    assertThat(issue.type())
                                            .isEqualTo(CatalogImportIssueType.INVALID_CELL_VALUE);
                                    assertThat(issue.detail())
                                            .contains("ENERGY", "must be non-negative");
                                }));
    }

    private static List<Map<String, Object>> baseRows() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("Code", new BigDecimal("1001"));
        first.put("FOOD_NAME_ENGLISH", "Water spinach, raw");
        first.put("FOOD_NAME_LOCAL", "Rau muống");
        first.put("FOOD_GROUP", "Vegetables");
        first.put("FOOD_SUB_GROUP", "Other vegetables");
        first.put("ENERGY", new BigDecimal("25.1000"));
        first.put("PROTCNT(g)", new BigDecimal("2.3000"));
        first.put("WATER(g)", new BigDecimal("91.0000"));
        first.put("FAT(g)", new BigDecimal("0.4000"));
        first.put("CHOCDF(g)", new BigDecimal("3.6000"));
        first.put("FIBC(g)", new BigDecimal("1.4000"));
        first.put("Ash(g)", new BigDecimal("1.0000"));
        first.put("CA(mg)", new BigDecimal("77.0000"));
        first.put("FE(mg)", new BigDecimal("1.2000"));
        first.put("ZN(mg)", new BigDecimal("0.5000"));
        first.put("VITC(mg)", new BigDecimal("55.0000"));
        first.put("THIA(mg)", new BigDecimal("0.1000"));
        first.put("RIBF(mg)", new BigDecimal("0.2000"));
        first.put("NIA(mg)", new BigDecimal("0.3000"));
        first.put("VITB6A(mg)", new BigDecimal("0.4000"));
        first.put("DFE(mcg)", new BigDecimal("10.0000"));
        first.put("VITB12(mcg)", new BigDecimal("0.5000"));
        first.put("VITA(mcg)", new BigDecimal("100.0000"));
        first.put("VITA_RAE(mcg)", new BigDecimal("315.0000"));
        first.put("VITD(mcg)", new BigDecimal("0.1000"));

        Map<String, Object> second = new LinkedHashMap<>(first);
        second.put("Code", "1002");
        second.put("FOOD_NAME_ENGLISH", "Glutinous rice");
        second.put("FOOD_NAME_LOCAL", "Gạo nếp cái");
        second.put("FOOD_GROUP", "Grains & grain products");
        second.put(
                "FOOD_SUB_GROUP",
                "Refined grains and products, unenriched/unfortified");
        second.put("WATER(g)", null);
        second.put("VITD(mcg)", null);

        Map<String, Object> third = new LinkedHashMap<>(first);
        third.put("Code", "1003");
        third.put("FOOD_NAME_ENGLISH", "Fresh milk");
        third.put("FOOD_NAME_LOCAL", "Sữa tươi");
        third.put("FOOD_GROUP", "Dairy products");
        third.put("FOOD_SUB_GROUP", "Fluid or powdered milk (non-fortified)");

        return new ArrayList<>(List.of(first, second, third));
    }

    private static List<CategoryCase> realCategoryCases() {
        return List.of(
                category("Added fats", "Other added fats", "FATS_OILS"),
                category("Added fats", "Vegetable oil (unfortified)", "FATS_OILS"),
                category("Added sugars", "Sugar (non-fortified)", "SEASONINGS"),
                category("Dairy products", "Cheese", "DAIRY_CHEESE"),
                category(
                        "Dairy products",
                        "Fluid or powdered milk (non-fortified)",
                        "DAIRY_MILK"),
                category(
                        "Dairy products",
                        "Sweetened dairy products/desserts "
                                + "(flan,custard,sweetened yoghurt,ice cream)",
                        "DAIRY"),
                category(
                        "Dairy products",
                        "Yoghurt, solid and drinkable",
                        "DAIRY"),
                category("Fruits", "Other fruit", "FRUITS"),
                category("Fruits", "Vitamin C-rich fruit", "FRUITS"),
                category(
                        "Grains & grain products",
                        "Refined grains and products, unenriched/unfortified",
                        "GRAINS"),
                category(
                        "Grains & grain products",
                        "Whole grains and products, unenriched/unfortified",
                        "GRAINS"),
                category(
                        "Legumes,nuts & seeds",
                        "Nuts,seeds,and unsweetened products",
                        "PROTEIN"),
                category("Meat,fish & eggs", "Eggs", "PROTEIN_EGG"),
                category(
                        "Meat,fish & eggs",
                        "Fish without bones",
                        "PROTEIN_SEAFOOD"),
                category("Meat,fish & eggs", "MyFoods_Special Meats", "PROTEIN"),
                category("Meat,fish & eggs", "Organ meat", "PROTEIN_MEAT"),
                category("Meat,fish & eggs", "Other animal parts", "PROTEIN"),
                category("Meat,fish & eggs", "Pork", "PROTEIN_MEAT"),
                category("Meat,fish & eggs", "Poultry, rabbit", "PROTEIN"),
                category("Meat,fish & eggs", "Red meat", "PROTEIN_MEAT"),
                category("Meat,fish & eggs", "Seafood", "PROTEIN_SEAFOOD"),
                category(
                        "Savory snacks",
                        "Savory snacks, salted,spiced,fried",
                        "PREPARED"),
                category(
                        "Starchy roots & other starchy plant foods",
                        "Other starchy plant foods",
                        "VEG_ROOT"),
                category(
                        "Starchy roots & other starchy plant foods",
                        "Vitamin C-rich starchy plant foods",
                        "VEG_ROOT"),
                category(
                        "Sweetened snacks & desserts",
                        "Sweet snack foods (candy and chocolate)",
                        "PREPARED"),
                category("Vegetables", "Other vegetables", "VEGETABLES"),
                category(
                        "Vegetables",
                        "Vitamin A source other vegetables",
                        "VEGETABLES"),
                category(
                        "Vegetables",
                        "Vitamin C-rich vegetables",
                        "VEGETABLES"));
    }

    private static CategoryCase category(
            String foodGroup,
            String foodSubgroup,
            String categoryCode) {
        return new CategoryCase(foodGroup, foodSubgroup, categoryCode);
    }

    private record CategoryCase(
            String foodGroup,
            String foodSubgroup,
            String categoryCode) {
    }
}
