# P13 Shopping List

P13 adds an authenticated, deterministic Shopping List projection derived from
a persisted meal plan. Shopping List is ordinary Java application logic; it
does not call the Python AI service and P13 does not introduce a persisted
shopping-list table.

## Current-projection semantics

`GET /api/v1/me/meal-plans/{mealPlanPublicId}/shopping-list` is owner-scoped
through the existing persisted Meal Plan read path. A plan owned by another
user has the same not-found semantics as an unknown plan UUID.

The response is deliberately a **current projection**. It combines:

1. the persisted Meal Plan entry recipe IDs, dates, and requested servings;
2. the current Recipe definition, including archived recipes still referenced
   by historical Meal Plan entries; and
3. the authenticated user's current AVAILABLE Pantry lots.

P11/P12 did not persist a recipe-ingredient snapshot with each plan entry.
Therefore P13 does not claim historical immutability if a Recipe is later
re-imported. Persisting immutable shopping requirements is deferred unless a
future product requirement needs historical reproduction.

## Quantity calculation

Recipe ingredient quantities are written for `recipes.servings`. For every
quantified, mandatory recipe ingredient, Shopping List calculates:

```text
entry requirement
  = recipe ingredient quantity
  * meal_plan_entry.servings
  / recipe.servings
```

All arithmetic uses `BigDecimal`. Compatible units are normalized through the
Nutrition-owned planning unit graph and aggregated in that dimension's
canonical base unit. Output quantities are rounded HALF_UP to four decimal
places.

Shopping List never guesses cross-dimension conversion. For example, `kg`
and `g` may normalize to the same mass base unit, while `piece` does not
silently convert to `g`, and `ml` does not silently convert to `g`.
Ingredient-specific cross-dimension conversions remain out of P13's MVP.

Optional recipe ingredients do not increase mandatory purchase quantities.
A mandatory line with no quantity/unit is not treated as zero; it appears in
`unquantifiedItems` so the client can render an explicit “as needed” item.

## Pantry allocation

Shopping List consumes an in-memory copy of Pantry availability and never
mutates Pantry rows or writes ledger events. It uses the Pantry-owned
`PantryAvailabilityQueryService`, which supplies AVAILABLE lots only and
therefore excludes RESERVED stock.

Requirements are processed in Meal Plan date order. Compatible lots are used
by earliest known expiry first, with USE_BY before BEST_BEFORE on an equal
date and public UUID as the final deterministic tie-breaker. A dated lot cannot
cover a meal occurring after that lot's expiry date. Unknown expiry sorts last.

Each quantified response item exposes:

- `requiredQuantity`;
- `pantryCoveredQuantity`;
- `quantityToBuy`; and
- canonical `unitCode`.

`quantityToBuy` is never negative. Items remain in the projection when Pantry
fully covers them so the calculation stays explainable to clients.

## Module boundaries

Shopping List reads other modules only through public application boundaries:

```text
ShoppingListQueryService
  -> PersistedMealPlanQueryService
  -> RecipeRequirementQueryService
  -> PantryAvailabilityQueryService
  -> PlanningUnitQueryService
```

`RecipeRequirementQueryService` belongs to the Recipe module and intentionally
does not apply the published-only catalog filter. Shopping does not import
Recipe or Pantry repositories/entities.

No migration, remote AI call, reservation, Pantry mutation, manual shopping
item, purchased/checked state, or Flutter UI is part of this P13 backend slice.
