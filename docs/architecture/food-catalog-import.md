# P8.7A2/A3 - SMILING Vietnam Food Composition and Ingredient Import

## Decision

Catalog importing is an offline, operator-triggered workflow. It is separate
from Flyway reference data and does not add food or ingredient rows to
`database/seed/R001__reference_data.sql`, a repeatable seed, or a versioned
migration.

The implemented boundaries are:

```text
external SMILING XLSX                 local normalized JSON
        -> SmilingVietnamWorkbookAdapter       -> CatalogImportJsonAdapter
                         \                       /
                          -> CatalogImportDocument
                          -> FoodCatalogImportService validation
                          -> existing Food/Ingredient JPA aggregates
                          -> MySQL catalog tables
```

`CatalogImportRunner` is available only with the `catalog-import` Spring
profile and `app.catalog.import.enabled=true`. It accepts
`--catalog-import-file=<path>` or `app.catalog.import.source-file`. An `.xlsx`
path uses the dedicated SMILING adapter; other paths retain the normalized JSON
adapter. `--catalog-import-dry-run` parses and reports without persisting. Normal
application startup has no import runner and makes no catalog import attempt.

## Normalized interchange contract

The JSON file is a project-owned normalized handoff, not a copy of an upstream
workbook. Each food supplies:

- `sourceIdentifier`: stable identifier from the upstream row;
- `catalogCode`: stable unique code stored in `foods.code`;
- `displayName`: Vietnamese display name when available;
- `sourceName`: original/source name when useful for the handoff;
- `categoryCode`: one existing `food_categories.code` value;
- `nutritionBasis`, optional density and description;
- `source: "IMPORTED"`;
- `sourceReference`: dataset plus sheet/row or another useful row locator;
- `nutrientFacts`: canonical nutrient code, amount, unit and explicit quality;
- optional `ingredientMapping`.

Missing nutrients are omitted from `nutrientFacts`. They are not represented by
zero. A source nutrient that cannot be mapped to one of the 16 existing
canonical nutrient codes is represented with a null `canonicalCode`, reported
as a warning, and not persisted.

Food and Ingredient remain separate concepts: Food carries nutrition facts,
while Ingredient is the canonical culinary identity used by recipes,
preferences, and search. P8.7A3 bootstraps the current SMILING Vietnam catalog
with one conservative Ingredient identity per imported Food, without merging
similarly named foods or inferring culinary equivalence. The dedicated
`SmilingVietnamIngredientMapper` creates `ING_SMILING_VN_<Code>` with the
normalized Vietnamese display name, the Food's category, default unit `g`,
`UNSPECIFIED` preparation state, yield factor `1.0000`, `primary=true`, and no
aliases. English names, parenthetical text, preparation claims, and aliases
are intentionally deferred for later curation.

The current workbook has 164 imported Foods and produces 163 Ingredient
mappings. Source code `10003` (`Sữa mẹ (sữa người)`) deliberately remains
Food-only because it is a valid composition record but not a culinary
ingredient for the meal-planning product. This exclusion is based only on the
stable source code; the Food is not deleted or deactivated.

The optional `ingredientMapping` remains the normalized handoff contract for
other adapters. Without that object, only the Food is imported.

The JSON adapter uses Jackson. The SMILING adapter uses Apache POI's XLSX
support. No network client, queue, batch framework, or public import endpoint is
part of this workflow.

## SMILING workbook adapter

`SmilingVietnamWorkbookAdapter` accepts an explicit `Path` or `InputStream` and
requires the `WP3 FCT` sheet. It resolves all columns by the exact row-2 header
text, then reads rows 3 onward. Fully blank rows are skipped; every row with a
nonblank `Code` is parsed. Numeric values are converted to `BigDecimal`, blank
nutrient cells are omitted, and parse errors include the source row, source
code, group, subgroup, column, and reason. Numeric cells use Apache POI's Excel
number rendering before `BigDecimal` conversion so binary floating-point tails
are not mistaken for source precision. Duplicate source codes, missing required
headers, invalid numbers, unknown groups, and every unreviewed group/subgroup
pair are blocking errors.

The parser returns row accounting (`rowsParsed`, `rowsSkipped`, imported food
count, and ingredient mapping count) for the operator log. The import report
adds created/updated Food and Ingredient counts, warnings, and errors. The
parser never changes the workbook and does not copy it into the repository.

## Source identity and provenance

The source is the **SMILING Food Composition Table for Vietnam 2013**, produced
with the National Institute of Nutrition Vietnam / Wageningen provenance and
distributed through the [FAO/INFOODS directory entry](https://www.fao.org/food-composition/tables-and-databases/detail/%28vietnam--2013%29-smiling-food-composition-table-for-vietnam/en).
The workbook filename is
`D3_5a_SMILING_FCT_Vietnam_180713_protected.xlsx`; the adapter reads the
`WP3 FCT` sheet. The sheet states that nutrient content is per 100 g edible
portion, so every imported food uses `PER_100_G`.

For source code `1001`, the stable normalized identity is:

```text
sourceIdentifier = SMILING_VN:1001
catalogCode      = SMILING_VN_1001
displayName      = FOOD_NAME_LOCAL (when present)
sourceReference  = SMILING Food Composition Table for Vietnam 2013; code=1001
```

The English name is retained as normalized `sourceName`; no new schema column is
introduced. The local workbook path is never stored in `sourceReference`.

The workbook and any QA workbook are external developer inputs and are not
committed. They are not copied into Flyway migrations or reference seed data.

## Nutrient mapping

Only these ten mappings are persisted:

| Source header | Existing nutrient | Unit |
| --- | --- | --- |
| `ENERGY` | `ENERGY` | `kcal` |
| `PROTCNT(g)` | `PROTEIN` | `g` |
| `WATER(g)` | `WATER` | `g` |
| `FAT(g)` | `FAT_TOTAL` | `g` |
| `CHOCDF(g)` | `CARBOHYDRATE` | `g` |
| `CA(mg)` | `CALCIUM` | `mg` |
| `FE(mg)` | `IRON` | `mg` |
| `VITC(mg)` | `VITAMIN_C` | `mg` |
| `VITA_RAE(mcg)` | `VITAMIN_A` | `mcg` |
| `VITD(mcg)` | `VITAMIN_D` | `mcg` |

`FIBC(g)` is crude fibre, not dietary fibre, so it is deliberately retained as
an unsupported warning candidate and is never mapped to `FIBER`. `Ash(g)`,
`ZN(mg)`, `THIA(mg)`, `RIBF(mg)`, `NIA(mg)`, `VITB6A(mg)`, `DFE(mcg)`,
`VITB12(mcg)`, `VITA(mcg)` are likewise not mapped. Existing nutrients such as
cholesterol, saturated fat, fibre, potassium, sodium, and sugars remain missing
rather than being stored as zero. Unsupported nonblank source values are
reported by the existing import validation and are not persisted.

The existing `FoodNutrientDataQuality` vocabulary has no per-value provenance
model. Because the workbook references laboratory, analytical-method, and
external/reference values, imported facts use the conservative `ESTIMATED`
value rather than claiming that every fact is directly laboratory-measured.
This is a documented limitation of the current schema.

## Category mapping

Category resolution uses only the existing 26 seeded categories and is based on
`FOOD_GROUP` plus the explicit `FOOD_SUB_GROUP` vocabulary. The reviewed mapping
decisions are:

| Exact source group | Exact source subgroup | Existing category |
| --- | --- | --- |
| `Added fats` | `Other added fats`; `Vegetable oil (unfortified)` | `FATS_OILS` |
| `Added sugars` | `Sugar (non-fortified)` | `SEASONINGS` |
| `Dairy products` | `Cheese` | `DAIRY_CHEESE` |
| `Dairy products` | `Fluid or powdered milk (non-fortified)` | `DAIRY_MILK` |
| `Dairy products` | `Sweetened dairy products/desserts (flan,custard,sweetened yoghurt,ice cream)`; `Yoghurt, solid and drinkable` | `DAIRY` |
| `Fruits` | `Other fruit`; `Vitamin C-rich fruit` | `FRUITS` |
| `Grains & grain products` | `Refined grains and products, unenriched/unfortified`; `Whole grains and products, unenriched/unfortified` | `GRAINS` |
| `Legumes,nuts & seeds` | `Nuts,seeds,and unsweetened products` | `PROTEIN` |
| `Meat,fish & eggs` | `Eggs` | `PROTEIN_EGG` |
| `Meat,fish & eggs` | `Fish without bones`; `Seafood` | `PROTEIN_SEAFOOD` |
| `Meat,fish & eggs` | `Organ meat`; `Pork`; `Red meat` | `PROTEIN_MEAT` |
| `Meat,fish & eggs` | `MyFoods_Special Meats`; `Other animal parts`; `Poultry, rabbit` | `PROTEIN` |
| `Savory snacks` | `Savory snacks, salted,spiced,fried` | `PREPARED` |
| `Starchy roots & other starchy plant foods` | `Other starchy plant foods`; `Vitamin C-rich starchy plant foods` | `VEG_ROOT` |
| `Sweetened snacks & desserts` | `Sweet snack foods (candy and chocolate)` | `PREPARED` |
| `Vegetables` | `Other vegetables`; `Vitamin A source other vegetables`; `Vitamin C-rich vegetables` | `VEGETABLES` |

These 28 pairs are the complete reviewed vocabulary for this workbook revision.
No food-name inference is used for rice, bread, pasta, leafy vegetables,
legumes, nuts, or poultry. An unknown group or an unreviewed group/subgroup pair
is blocking and is never silently mapped to a parent or to `OTHER`.

## Ingredient strategy

P8.7A3 uses the existing `CatalogImportIngredientMapping` contract and
`FoodCatalogImportService` upsert path. The bootstrap is intentionally
one-to-one and source-code based: it creates a separate canonical Ingredient
for each eligible SMILING Food rather than asserting that similarly named Foods
are the same culinary identity. The mapped Ingredient's provenance is
traceable through its `defaultFood` and the corresponding `ingredient_foods`
row.

No automatic aliases, allergen facts, conversions, piece weights, shelf-life
values, staple flags, recipes, or preparation-state inference are added in
this phase. The source XLSX remains an external, uncommitted input, and
re-running the importer is idempotent for both Food and Ingredient identities.

## Validation and persistence rules

- Categories, nutrients and units are resolved by code, never by surrogate ID.
- Unknown food or ingredient categories and units block the transaction.
- Duplicate food codes/source identifiers, duplicate mapped nutrient codes and
  conflicting aliases/mappings block the transaction.
- Nutrient units must already equal the unit owned by the referenced canonical
  nutrient. Any conversion must be performed exactly by the offline handoff
  and then recorded in the normalized file; this service does not guess.
- Amounts are non-negative and must fit the existing `DECIMAL(12,4)` storage
  without rounding. Missing amounts are absent facts.
- An existing non-imported Food with the same code is a blocking collision.
- An existing imported Food is updated by code, retaining its public UUID.
  Missing facts remove stale imported facts so an old value is not presented as
  current. Nutrition changes increment `foods.revision` once per import unit.
- Imported ingredient mappings are upserted by their stable ingredient code and
  food relationship. Existing aliases and mappings are not duplicated. A
  rerun preserves both Food and Ingredient public IDs, keeps one
  `ingredient_foods` mapping, preserves `default_food_id`, and does not change
  Food nutrition revision when only the already-matching Ingredient mapping is
  encountered.
- The whole document is one transaction. A validation or persistence failure
  rolls back all foods, nutrient facts, ingredients, aliases and mappings from
  that document.

## Explicit local commands

`CatalogImportRunner` is guarded by both the `catalog-import` Spring profile
and `app.catalog.import.enabled=true`; the profile alone is not sufficient.
From the repository root, the following Windows PowerShell commands use an
absolute environment-backed source path, so paths containing spaces are safe:

```powershell
$catalogImportFile = (Resolve-Path 'C:\temp\smiling-vietnam\D3_5a_SMILING_FCT_Vietnam_180713_protected.xlsx').Path
$env:CATALOG_IMPORT_SOURCE_FILE = $catalogImportFile
mvn -f backend/pom.xml spring-boot:run `
  '-Dspring-boot.run.profiles=catalog-import' `
  '-Dspring-boot.run.arguments=--app.catalog.import.enabled=true --catalog-import-dry-run'
```

After reviewing the dry-run counts, stop the operator process with `Ctrl+C` if
it remains running, then run the actual import by setting the source variable
again and removing `--catalog-import-dry-run`:

```powershell
$catalogImportFile = (Resolve-Path 'C:\temp\smiling-vietnam\D3_5a_SMILING_FCT_Vietnam_180713_protected.xlsx').Path
$env:CATALOG_IMPORT_SOURCE_FILE = $catalogImportFile
mvn -f backend/pom.xml spring-boot:run `
  '-Dspring-boot.run.profiles=catalog-import' `
  '-Dspring-boot.run.arguments=--app.catalog.import.enabled=true'
```

Stop the operator process with `Ctrl+C` afterward and clear the temporary
variable:

```powershell
Remove-Item Env:CATALOG_IMPORT_SOURCE_FILE -ErrorAction SilentlyContinue
```

The configured equivalent is `CATALOG_IMPORT_SOURCE_FILE=<external path>` with
the `catalog-import` profile and `CATALOG_IMPORT_ENABLED=true`. Normal
application startup does not activate the catalog importer.

Automated tests generate a tiny project-owned workbook with the same six sheet
names, title/header layout, Unicode names, missing cells, unsupported nutrient
columns, and validation failures. The official XLSX is never a test resource.
