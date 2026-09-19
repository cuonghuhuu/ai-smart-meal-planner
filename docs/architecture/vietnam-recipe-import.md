# P9C — Vietnamese Recipe dataset and offline import

## Scope and provenance

P9C adds a Java-only offline importer for a small project-curated Vietnamese
starter catalog. The committed file is
`backend/src/main/resources/recipe-import/vietnam-curated-v1.json` and contains
12 independently written recipes. The names, quantities, summaries, and
instructions are project content; they are not copied recipe prose and are not
claimed as official National Institute of Nutrition, FAO, blog, or commercial
recipe data.

SMILING Food Composition Table for Vietnam 2013 is used only as the verified
nutrition/Food and Ingredient source. Recipe ingredients use canonical codes of
the form `ING_SMILING_VN_<source-code>`. The importer never matches by display
name, creates an Ingredient, or invents an alias. The 12 committed recipes use
30 Ingredient-code occurrences across 16 distinct verified SMILING source
codes; the code-to-name checks were performed against the workbook's `WP3 FCT`
sheet. No raw workbook is committed.

## Normalized contract

`RecipeImportDocument` contains a dataset name, version, and typed recipe
records. Each recipe has a stable `sourceIdentifier`, slug, metadata, tags,
meal slots, ordered ingredient lines, and ordered steps. Ingredient identity is
`ingredientCode`; measurement identity is `unitCode`; reference identities are
tag and meal-slot codes. There are no database surrogate IDs in the JSON.

The starter file uses `CURATED`, stable `VN_CURATED_001`–`VN_CURATED_012`
identities, deterministic source references, gram quantities, and
`createdByUserId = null` when persisted. Nutrition values are never present in
the file.

## Validation and dry run

The entire document is preflighted before any write. The preflight batches
Ingredient and measurement-unit resolution through their owning modules and
checks Recipe-owned tags and meal slots. It rejects duplicate identities,
unknown references, invalid Recipe bounds, duplicate or non-contiguous child
positions, quantity/unit mismatches, and source or slug conflicts. A blocking
error prevents all persistence.

The runner is disabled during normal startup. Enable the explicit profile and
property only for a local operator task:

```text
mvn -f backend/pom.xml spring-boot:run \
  -Dspring-boot.run.profiles=recipe-import \
  -Dspring-boot.run.arguments="--recipe-import-file=C:\path\vietnam-curated-v1.json --recipe-import-dry-run"
```

For an actual import, omit `--recipe-import-dry-run` and set
`RECIPE_IMPORT_ENABLED=true`, or use the equivalent
`app.recipe.import.enabled=true` configuration. The source path is external to
the repository in normal local workflows. There is no public import endpoint.

## Idempotency and child replacement

The stable identity is `RecipeSource.CURATED` plus `sourceReference`. The first
import creates a published Recipe and its children. An identical rerun keeps
the generated public UUID and `publishedAt`, does not duplicate children, and
does not create another nutrition snapshot when a current snapshot exists.
Material updates replace ingredient, step, tag, and meal-slot children in one
transaction while preserving the Recipe identity. A slug collision with a
different source identity blocks the document. User-owned Recipes are never
overwritten.

## Nutrition integration

After a new Recipe, a nutrition-affecting composition/servings change, or a
missing current snapshot is persisted and flushed, the importer calls the
existing `RecipeNutritionComputationService`. P9B remains the only authority
for conversion, aggregation, completeness, and immutable snapshot replacement.
Incomplete nutrition is a report warning; it is not a reason to fabricate
nutrient zeros or fail an otherwise valid import.

Dry runs perform preflight only and persist nothing. A write failure rolls back
the whole document. Normal application startup never imports this file.
