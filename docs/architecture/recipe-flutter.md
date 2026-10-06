# Flutter Recipe catalog (P15)

## Scope and ownership

The authenticated Flutter client reads the backend's published Recipe catalog.
It provides browse, search, filters, pagination, and detail views on Android and
Web. The Java Recipe module owns persistence, validation, ordering, published
visibility, and nutrition snapshots. Flutter does not compute nutrition or
rank Recipes from Pantry contents.

## Client architecture and API

`HttpRecipeRepository` uses the app's shared authenticated `ApiClient`. Its
strict models follow the [Recipe read contract](recipe-catalog.md); malformed
successful responses raise `ApiResponseFormatException`, while HTTP and
transport failures retain their distinct exception types. Public IDs remain
strings in Flutter.

| Read | Endpoint |
| --- | --- |
| Published catalog | `GET /api/v1/recipes` |
| Published detail | `GET /api/v1/recipes/{publicId}` |
| Recipe tags | `GET /api/v1/reference/recipe-tags` |
| Meal slot types | `GET /api/v1/reference/meal-slot-types` |

The catalog sends `q`, `mealSlotCode`, `tagCode`, `maxMinutes`, `page`, and
`size` as server-side parameters. Optional text is trimmed and blank filters
are omitted. Search, filtering, ordering, and page boundaries are defined by
the backend; the client does not filter or sort returned Recipes.

## State and presentation

The app owns one `RecipeController` for its authenticated lifecycle. It exposes
independent catalog, detail, tag, and meal-slot states with loading, safe error,
and retry behavior. Catalog state carries filters, items, page metadata, and a
separate load-more error so earlier cards remain visible after a later page
fails. Search and filter changes restart at page zero. Duplicate or terminal
load-more requests are ignored.

`RecipeBrowsePage` displays cards, search, reference-backed meal-slot and tag
filters, a maximum-total-minutes control, clear filters, and manual load more.
Reference failures can be retried without removing catalog content. A Recipe
card opens `/recipes/{publicId}`. `RecipeDetailPage` shows metadata, ingredients
and sections, cooking steps, and the current nutrition snapshot in backend
order. Quantities and nutrient amounts use returned units and values without
client conversion. When `nutrition` is null, the page shows an unavailable
state rather than zeros. Missing or failed network images use a fallback.

Both views use the existing responsive content widths. Browse uses one column
on narrow screens and a grid on wide screens; detail uses a single flow on
narrow screens and side-by-side sections when space permits. User-facing text
comes from `AppStrings`.

## Routes, errors, and session safety

GoRouter protects `/recipes` and `/recipes/:publicId` like other authenticated
destinations. The Drawer and NavigationRail select Recipes on either route.
Safe login return-to handling accepts internal Recipe routes and rejects
external or invalid detail destinations. The detail route validates public UUID
shape consistently with existing catalog routes; invalid paths show the safe
not-found page. Repository/backend detail failures use the controller's safe
message and retry state, without exposing raw server text.

On logout or authenticated identity change, the app invokes
`RecipeController.resetForSessionChange()`. Request generations invalidate
pending catalog, detail, and reference responses; list items, detail, filters,
pagination, and references are cleared. An active Recipe page remounts for the
new identity and loads through the same controller. Rebuilds within one
identity do not create a new controller.

## Verification and deferred scope

Model and repository tests cover strict parsing, nullability, authenticated
requests, filters, encoding, and failure types. Controller tests cover
pagination, retries, independent reference/detail states, and stale responses.
Widget and router tests cover browse/detail states, responsive layouts,
navigation, protected routes, and session resets. Final acceptance uses
`flutter analyze`, `flutter test`, and Git diff checks.

User Recipe CRUD, favorites, ratings/reviews, personalized Recipe
recommendations, Pantry-aware Recipe ranking, ingredient substitution, admin
Recipe management, notifications, and Shopping List integration are deferred.
