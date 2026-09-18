# P9B — Recipe nutrition calculation and snapshots

## Ownership

Recipe owns the calculation orchestration, aggregation, per-serving arithmetic,
and the `recipe_nutrition_snapshots` lifecycle. Food owns Foods, Ingredients,
FoodNutrients, FoodServings, IngredientFood mappings, and ingredient-specific
conversion facts. Nutrition owns measurement-unit and nutrient vocabulary.

Recipe consumes immutable batch snapshots from public read boundaries; it does
not inject Food, Ingredient, or Nutrition repositories, and no internal
surrogate ID is exposed through HTTP.

## Calculation flow

`RecipeNutritionComputationService.recompute(publicId)` is an explicit Java
application capability. It locks the Recipe row, loads all ingredient lines
once, batch-resolves catalog and unit facts, calculates with `BigDecimal`, and
replaces the current snapshot in one transaction. The old snapshot is retained
and only its `is_current` flag changes. GET requests never trigger a
recomputation, and P9B adds no user-facing recompute endpoint.

For each line, an explicitly pinned `food_id` wins. When it is absent, the
Ingredient `default_food_id` or primary IngredientFood mapping is used. A
contradictory default/primary pair is corrupted data; an Ingredient with no
Food remains an unresolved line. A pinned Food is not silently replaced by the
default Food.

## Unit conversion

The target quantity is grams for `PER_100_G` Foods and millilitres for
`PER_100_ML` Foods. Conversion uses this conservative layered order:

1. universal same-type unit metadata and `factor_to_base_unit`;
2. a direct Ingredient-specific conversion fact for a cross-type path;
3. `piece_gram_weight` for the recorded `piece` unit;
4. the selected Food's `density_g_per_ml`;
5. an unambiguous FoodServing bridge.

No water-density assumption, generic volume-to-mass factor, or arbitrary
conversion chain is introduced. Multiple serving facts for the same source
unit are accepted only when they imply the same measurable result; otherwise
the line is unresolved. A non-one IngredientFood `yield_factor` is applied
once, only for default Ingredient-to-Food resolution. It requires a measurable
mass path so a mass yield is not guessed for an unmeasurable volume.

## Missing data and completeness

Food nutrient rows are sparse facts. An absent row is unknown, never zero; a
stored zero remains a known zero. Only nutrient facts from computable lines are
aggregated. A null quantity/unit pair (for example, “to taste”), an unknown
conversion, missing Food mapping, or Food with no nutrient facts leaves the
line unresolved.

`completeness_ratio` is resolved ingredient lines divided by total ingredient
lines. A recipe with no lines has `0.0000`. The computation note states the
resolved count and that missing nutrient facts were not treated as zero.

Food nutrient amounts are per the Food basis. Each total is divided by the
Recipe serving count for the stored per-serving value. Intermediate arithmetic
uses high-precision `BigDecimal`; only the persistence boundary rounds to
`DECIMAL(12,4)` with `HALF_UP`, rejecting overflow.

`ingredient_revision` is the maximum `foods.revision` among selected Food
records. If no Food can be selected, P9B records `0` as the explicit
no-resolved-Food convention.

## Snapshot lifecycle

Recomputation first locks the Recipe row, then demotes the previous current
snapshot and flushes that update before inserting the replacement. This order
respects `ux_recipe_nutrition_snapshots_current`. The new snapshot and all of
its values are inserted before commit. Any computation or persistence failure
rolls the transaction back, preserving the previous current snapshot; old
snapshots and their values are never deleted or rewritten.

P9C may call this capability during recipe import/curation. Scheduled work,
queues, recommendation filtering, and nutrition recalculation on GET remain
out of scope.
