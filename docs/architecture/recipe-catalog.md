# P9A — Recipe domain and read catalog

## Ownership and module boundaries

Recipe persistence is owned by the Java `recipe` module. The module maps the
existing P2 tables without adding a migration. `Recipe` is the meal composition
aggregate, while `Ingredient` remains the canonical culinary identity and
`Food` remains the nutritional fact carrier owned by the food module.

Recipe does not inject Food or Ingredient repositories. It resolves persisted
scalar foreign-key references through the food module's read-only
`FoodReferenceQueryService` and `IngredientReferenceQueryService`. Nutrition
metadata and measurement units are resolved through nutrition-owned read-only
query boundaries. Public responses contain only public UUIDs and stable codes;
surrogate database IDs remain internal persistence handles.

## Recipe lifecycle and invariants

Recipes use the existing `recipes` table and its optimistic-lock `version`:

- `DRAFT` has no `publishedAt` or `archivedAt`.
- `PUBLISHED` has `publishedAt` and no `archivedAt`.
- `ARCHIVED` has both timestamps.

The domain mapping validates title, slug, servings, preparation/cooking
minutes, difficulty, source, ingredient quantity/unit pairs, ingredient-line
uniqueness assumptions, and ordered step constraints in accordance with V001.
The generated `total_minutes` column is read-only in JPA.

## Read API

All endpoints are authenticated by the existing Spring Security policy:

```text
GET /api/v1/recipes
GET /api/v1/recipes/{publicId}
GET /api/v1/reference/recipe-tags
GET /api/v1/reference/meal-slot-types
```

The list accepts `q`, `mealSlotCode`, `tagCode`, `maxMinutes`, `page`, and
`size`. `q` uses the V001 FULLTEXT index on recipe title and summary. Browse
and search ordering is deterministic, with published time and public UUID as
tie-breakers. Only published recipes are visible; draft and archived records
return neither list results nor details.

When a meal slot is requested, a recipe matches when it is explicitly assigned
to that slot **or** has no `recipe_meal_slot_types` rows. An unclassified recipe
therefore remains eligible for every slot filter. Tag filters require an
explicit tag assignment. Unknown filter codes and invalid pagination/time
values return `INVALID_REQUEST`.

Detail children are ordered by line number, step number, tag display name/code,
and meal-slot display order/code. Missing catalog or nutrition reference rows
are reported as `CORRUPTED_RECIPE_DATA`; they are never replaced with invented
display data or surrogate IDs.

## Nutrition snapshot behavior

P9A only reads the current `recipe_nutrition_snapshots` row and its
`recipe_nutrition_values`. It does not calculate, create, update, or refresh a
snapshot during a GET request. If no current snapshot exists, `nutrition` is
`null`. Missing nutrient values remain missing rather than becoming zero.

P9B will own nutrition computation and snapshot lifecycle. The snapshot's
`ingredientRevision`, completeness ratio, computation note, and per-serving
values remain visible so a later computation workflow can report provenance and
completeness honestly.

## Persistence authority and future phases

The Java backend is authoritative for Recipe persistence and API validation.
P9A does not import recipes, add user recipe CRUD, calculate nutrition, connect
Pantry or Recommendation workflows, or call the Python AI service. Those
responsibilities remain future phases.
