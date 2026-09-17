# P8.7A1 - Vietnam Food Catalog Import Foundation

## Decision

Catalog importing is an offline, operator-triggered workflow. It is separate
from Flyway reference data and does not add food or ingredient rows to
`database/seed/R001__reference_data.sql`, a repeatable seed, or a versioned
migration.

The implemented boundary is:

```text
local normalized JSON
        -> CatalogImportJsonAdapter
        -> FoodCatalogImportService validation
        -> existing Food/Ingredient JPA aggregates
        -> MySQL catalog tables
```

`CatalogImportRunner` is available only with the `catalog-import` Spring
profile and `app.catalog.import.enabled=true`. It accepts
`--catalog-import-file=<path>` or `app.catalog.import.source-file`. Normal
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

Ingredient creation is never inferred from a food row. The optional mapping
must supply the canonical ingredient code, display name, preparation state,
yield factor, and any aliases. Without that object, only the Food is imported.

The adapter uses the existing Jackson dependency. No XLSX parser, network
client, queue, batch framework, or new service is required by the application.

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
  food relationship. Existing aliases and mappings are not duplicated.
- The whole document is one transaction. A validation or persistence failure
  rolls back all foods, nutrient facts, ingredients, aliases and mappings from
  that document.

## Official source handoff

The primary source is the FAO/INFOODS directory entry for **(Viet Nam, 2013)
SMILING Food composition table for Vietnam**. It links to:

- `D3_5a_SMILING_FCT_Vietnam_180713_protected.xlsx` (Table A);
- `D3_5b_SMILING_FCT_QA_Vietnam_180713.xlsx` (Table B / quality assessment).

The binary workbooks are intentionally not committed. Before a real import,
the operator must:

1. Obtain both files from the official FAO directory entry into a local,
   ignored working directory.
2. Inspect the actual workbook sheet names, headers, row identifiers, nutrient
   labels, units, missing-value markers and QA fields. The application does not
   guess any of these positions.
3. Build a normalized JSON document using the exact source row identifiers and
   row-level `sourceReference` values. Map only nutrients whose correspondence
   to the existing codes is verified; leave other `canonicalCode` values null.
   Omit missing amounts. Resolve every category explicitly to an existing
   category code; do not use `OTHER` as an automatic fallback.
4. Add only deliberately curated `ingredientMapping` objects. A food with no
   reliable culinary identity remains a Food-only row.
5. Run the import explicitly, for example:

   ```text
   mvn -f backend/pom.xml spring-boot:run \
     -Dspring-boot.run.profiles=catalog-import \
     -Dspring-boot.run.arguments="--app.catalog.import.enabled=true --catalog-import-file=C:/path/to/normalized-vietnam-catalog.json"
   ```

The workbooks themselves were not imported by P8.7A1 because the binary source
files were unavailable to the implementation environment. Automated tests use
`backend/src/test/resources/catalog-import/synthetic-vietnam.json`, which is
explicitly synthetic and is not production catalog data.
