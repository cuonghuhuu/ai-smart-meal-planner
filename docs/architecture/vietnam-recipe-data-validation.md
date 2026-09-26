# P9C - Vietnamese Recipe data validation note

## Scope and evidence

This note reviews the committed
`backend/src/main/resources/recipe-import/vietnam-curated-v1.json` file. The
file contains 12 project-curated recipes, 30 Ingredient-code occurrences, and
16 distinct canonical codes. Those codes were checked against the SMILING
Vietnam `WP3 FCT` source mapping used by the P8 catalog import. The protected
workbook remains an external input and is not committed.

This is a content/contract review, not a claim that the starter recipes are
official clinical, dietary, or cultural certifications.

## Composition review

The recipes explicitly tagged `VEGAN` are:

- `VN_CURATED_004`
- `VN_CURATED_005`
- `VN_CURATED_012`

Based on the committed ingredient lines, the following recipes are safely
vegetarian by the project's composition review:

- `VN_CURATED_001`
- `VN_CURATED_003`
- `VN_CURATED_004`
- `VN_CURATED_005`
- `VN_CURATED_010`
- `VN_CURATED_012`

The vegan recipes are compositionally compatible with a vegetarian preference,
but P9C does not duplicate a `VEGETARIAN` tag merely to encode that semantic
closure. The current Recipe tag contract stores explicit dataset claims; any
future recommendation layer must define how it interprets vegan recipes for a
vegetarian user.

## Constraints that are not established

The committed dataset does not establish `GLUTEN_FREE` or `DAIRY_FREE` for
any recipe. The available catalog bootstrap does not provide the complete
ingredient-level allergen/provenance evidence needed to make those claims,
and the JSON therefore does not add those tags as guesses.

The dataset also does not establish `HALAL`, `KOSHER`, or `PESCATARIAN`
compliance. Recipe names or a short ingredient list are not sufficient evidence
for those constraints. No medical or safety guarantee is implied.

Nutrition completeness remains a runtime property of the imported Food facts;
this note does not fabricate nutrient values or convert missing facts to zero.

The committed-data integration test creates a small synthetic Food/Ingredient
catalog fixture using these exact canonical codes and a minimal `ENERGY` fact.
That fixture is intentionally not presented as the SMILING workbook or as
real nutrition data. It proves that the committed JSON resolves, persists,
recomputes, and remains idempotent; the separate P8 catalog import tests and
operator workflow remain responsible for validating the protected workbook's
actual Food facts.

## Review conclusion

The current 12-row file is suitable as project-curated starter content for the
P9 Recipe catalog. Its explicit `VIETNAMESE` tags and meal-slot assignments are
reference data claims, while diet/allergen claims remain deliberately limited
to what the committed composition and catalog evidence support.
