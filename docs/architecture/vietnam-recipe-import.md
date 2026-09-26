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

## Reproducible local sequence

Starting from a fresh migrated local database, use this order from the
repository root in Windows PowerShell:

1. Run the normal Flyway migration and repeatable reference seed. Do not add
   recipe rows to `R001__reference_data.sql`.
2. Prepare the Food/Ingredient catalog with the documented offline SMILING
   import in [Food catalog import](food-catalog-import.md). That runner also
   requires both the `catalog-import` profile and
   `app.catalog.import.enabled=true`; the linked guide shows the complete
   PowerShell activation. The verified bootstrap currently produces 164 Foods
   and 163 Ingredient mappings; source code `10003` intentionally has no
   Ingredient mapping.
3. Resolve the committed Recipe file to an absolute path. Using an environment
   variable keeps paths containing spaces safe when the Spring Boot process is
   started by Maven:

   ```powershell
   $recipeImportFile = (Resolve-Path (Join-Path (Get-Location) 'backend/src/main/resources/recipe-import/vietnam-curated-v1.json')).Path
   $env:RECIPE_IMPORT_SOURCE_FILE = $recipeImportFile
   ```

4. Validate the committed Recipe file without persistence. The runner is
   guarded by both the `recipe-import` profile and the enabled property, so the
   command explicitly supplies `--app.recipe.import.enabled=true`:

   ```powershell
   mvn -f backend/pom.xml spring-boot:run `
     '-Dspring-boot.run.profiles=recipe-import' `
     '-Dspring-boot.run.arguments=--app.recipe.import.enabled=true --recipe-import-dry-run'
   ```

   A successful run logs `Recipe import dry run: recipesRead=12, errors=0`.
   The dry run performs validation only and persists nothing. Stop the operator
   process with `Ctrl+C` when it remains running, then clear the temporary
   variable:

   ```powershell
   Remove-Item Env:RECIPE_IMPORT_SOURCE_FILE -ErrorAction SilentlyContinue
   ```

5. Perform the actual import. Resolve/set the same absolute source path again
   if it was cleared after the dry run:

   ```powershell
   $recipeImportFile = (Resolve-Path (Join-Path (Get-Location) 'backend/src/main/resources/recipe-import/vietnam-curated-v1.json')).Path
   $env:RECIPE_IMPORT_SOURCE_FILE = $recipeImportFile
   mvn -f backend/pom.xml spring-boot:run `
     '-Dspring-boot.run.profiles=recipe-import' `
     '-Dspring-boot.run.arguments=--app.recipe.import.enabled=true'
   ```

   A successful first import reports `recipesRead=12`,
   `recipesCreated=12`, `recipesUpdated=0`, and
   `nutritionSnapshotsComputed=12` (the warning count may reflect incomplete
   nutrition). Verify that 12 `CURATED` Recipes exist with source references
   beginning `AI_MEAL_PLANNER_VN_CURATED_V1:` and that each has a current
   nutrition snapshot before using the public catalog. Stop the operator
   process with `Ctrl+C` and clear `RECIPE_IMPORT_SOURCE_FILE` afterward.

6. Run the same actual-import command a second time:

   ```powershell
   $recipeImportFile = (Resolve-Path (Join-Path (Get-Location) 'backend/src/main/resources/recipe-import/vietnam-curated-v1.json')).Path
   $env:RECIPE_IMPORT_SOURCE_FILE = $recipeImportFile
   mvn -f backend/pom.xml spring-boot:run `
     '-Dspring-boot.run.profiles=recipe-import' `
     '-Dspring-boot.run.arguments=--app.recipe.import.enabled=true'
   ```

   An identical import should report `recipesRead=12`, `recipesCreated=0`,
   `recipesUpdated=0`, `recipesUnchanged=12`, and
   `nutritionSnapshotsComputed=0`. It must preserve public UUIDs and create no
   additional current nutrition snapshots. Stop the operator process with
   `Ctrl+C`, then run:

   ```powershell
   Remove-Item Env:RECIPE_IMPORT_SOURCE_FILE -ErrorAction SilentlyContinue
   ```

The catalog workbook path is an operator-supplied external path. It is not a
repository resource, credential, migration, or startup prerequisite for
ordinary application launches. If the verified SMILING catalog is not
available, the Recipe file must not be imported into a database that lacks its
canonical Ingredient rows; the dry run should be used to expose those missing
references. There is no public import endpoint.

Normal application startup does not activate either offline runner: the
`recipe-import`/`catalog-import` profile and the corresponding enabled property
must both be supplied explicitly for an operator task.

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
