# P16 Personalized Recipe Recommendation Contract V1

## Scope and boundary

P16 exposes personalized, pantry-aware Recipe ranking while keeping Java as the
application authority.

Public client boundary:

    POST /api/v1/me/recipe-recommendations

Internal Java-to-Python boundary:

    POST /internal/v1/recipe-recommendations/rank

Java authenticates the user and resolves authoritative profile, preferences,
Pantry availability, nutrition targets, published Recipe candidates, unit
catalog, allergen evidence, and recent recommendation history. Python receives
only a normalized public-ID snapshot. Python does not authenticate end users,
access MySQL, mutate Pantry, or persist application state.

The normal Recipe catalog remains independent. Failure of the AI service must
not make GET /api/v1/recipes or Recipe detail unavailable.

## Versioning and JSON rules

Every internal request and successful algorithm response carries:

- contractVersion: exactly "1";
- requestId: UUID;
- algorithmVersion: exactly HEURISTIC_RECIPE_RANK_V1.

Breaking transport changes require a new major internal path and contract
version. All objects reject unknown fields. Arrays are required even when
empty. Dates use ISO YYYY-MM-DD. Numeric database IDs are forbidden across the
Python boundary; only public UUIDs and stable reference codes are transported.

Decimal quantities bind to Java BigDecimal and Python Decimal. NaN and infinity
are invalid. Score-visible values use four decimal places and ROUND_HALF_UP.
Measurement-unit codes remain case-sensitive catalog codes. Semantic reference
codes retain the existing uppercase reference-data namespace.

## Public API

POST /api/v1/me/recipe-recommendations requires authentication.

### Public request

| Field | Type | Required | Rule |
| --- | --- | ---: | --- |
| mealSlotCode | enum | yes | One of the six V1 meal slots. |
| servings | decimal | yes | Greater than 0, at most 50, scale at most 2. |
| maxMinutes | integer or null | yes | Null or 1 through 1440. |
| limit | integer | no | Defaults to 5; 1 through 20. |

The public body does not accept allergens, dietary constraints, disliked
ingredients, Pantry state, nutrition targets, target date, score weights, or
algorithm version. Java derives these from authenticated server-side state.

The target date is the authenticated user's current local calendar date using
the Java-owned profile timezone policy.

### Public response

A successful public response contains requestId, status, algorithmVersion,
targetDate, mealSlotCode, items, and nullable infeasibleReason.

status is SUCCEEDED or INFEASIBLE.

Each item contains rank, recipe, totalScore, exactly seven scoreComponents, and
a nonblank explanation of at most 500 characters.

recipe reuses the existing RecipeResponse.Item shape from the published catalog:
publicId, title, summary, servings, prepMinutes, cookMinutes, totalMinutes,
difficulty, imageUrl, source, tags, and mealSlots. P16 does not create a second
incompatible Recipe card contract.

SUCCEEDED requires at least one safe item. Returning fewer than limit is valid
when fewer candidates survive hard constraints. INFEASIBLE requires an empty
items array and a non-null safe reason.

AI timeout, unavailable service, malformed internal JSON, unsafe returned IDs,
invalid score arithmetic, or response-size violations are technical failures,
not INFEASIBLE. Java records them as FAILED and returns the existing safe HTTP
503 service-unavailable error. Java must not label ordinary catalog ordering as
personalized fallback.

## Internal request hierarchy

    RecipeRankingRequest
    |- context
    |- hardConstraints
    |- softPreferences
    |- nutritionTargets[]
    |- recentRecipeCounts[]
    |- unitDefinitions[]
    |- pantryLots[]
    |- ingredientFacts[]
    |- recipeCandidates[]

### Context

| Field | Type | Rule |
| --- | --- | --- |
| targetDate | ISO date | User-local date resolved by Java. |
| mealSlotCode | enum | Required target slot. |
| servings | decimal | Greater than 0, at most 50, scale at most 2. |
| maxMinutes | integer or null | Null or 1 through 1440. |
| resultLimit | integer | 1 through 20. |

Meal-slot values are BREAKFAST, MORNING_SNACK, LUNCH, AFTERNOON_SNACK, DINNER,
and EVENING_SNACK.

### Hard constraints

The shape is reused from Meal Planning V1:

- allergenCodes: unique reference codes;
- exclusionaryDietaryCodes: unique reference codes;
- avoidIngredientPublicIds: unique Ingredient UUIDs.

Supported exclusionary diet codes are VEGETARIAN, VEGAN, PESCATARIAN, HALAL,
KOSHER, GLUTEN_FREE, and DAIRY_FREE. Exact explicit Recipe evidence is required.
No dietary implication is inferred.

For every declared allergen, every listed Recipe ingredient, including optional
ingredients, requires explicit FREE_FROM evidence. CONTAINS, MAY_CONTAIN,
UNKNOWN, absent Ingredient facts, and absent allergen facts fail closed.

### Soft preferences

The shape is reused from Meal Planning V1:

- dislikeIngredientPublicIds;
- preferenceCodes.

AVOID is a hard exclusion. DISLIKE is only a score penalty.

### Nutrition targets

NutritionTarget is reused from Meal Planning V1 with nutrientCode, nullable
targetValue, nullable minValue, nullable maxValue, hardLimit, and unitCode.

An empty nutritionTargets array is valid. Missing current nutrition targets
must not make standalone Recipe recommendation unavailable.

The fixed V1 slot shares used only for NUTRITION_FIT are:

| Meal slot | Daily share |
| --- | ---: |
| BREAKFAST | 0.25 |
| MORNING_SNACK | 0.05 |
| LUNCH | 0.30 |
| AFTERNOON_SNACK | 0.05 |
| DINNER | 0.30 |
| EVENING_SNACK | 0.05 |

The shares sum to 1.00 and are ranking heuristics, not medical prescriptions.

For a hardLimit target with maxValue, a candidate is rejected when the
nutrition of the requested servings alone exceeds the full daily maximum.
A daily minimum is not transformed into a single-meal hard minimum.
targetValue and minValue may still contribute to heuristic nutrition fit.

### Recent Recipe counts

Each RecentRecipeCount contains recipePublicId and count. count is an integer
from 1 through 365.

Java sends only nonzero counts for candidate Recipes. V1 uses a rolling 30-day
window and counts prior terminal recommendation history for MEAL_PLAN and
RECIPE_SUGGESTION request kinds. The current PENDING request is excluded.

This signal is only recent-use variety. P16 does not learn preferences from
ACCEPTED, REJECTED, or IGNORED decisions.

### Reused P11 snapshot structures

UnitDefinition, PantryLot, IngredientFact, AllergenFact, RecipeCandidate,
IngredientRequirement, NutritionData, and NutritionValue retain their Meal
Planning V1 meanings and precision rules.

Retained rules include:

- candidate Recipes are published-only;
- Recipe nutrition values are per serving;
- Ingredient quantities scale by requested servings divided by base servings;
- Pantry lots remain independent and exclude reserved inventory;
- unknown mandatory quantity or unit rejects the candidate;
- unit conversion is same-dimension multiplicative conversion only;
- missing nutrition is unknown, never zero.

## Internal response

    RecipeRankingResponse
    |- rankedRecipes[]
    |  |- scoreComponents[]
    |- infeasibleReason

status is SUCCEEDED or INFEASIBLE only.

For SUCCEEDED:

- rankedRecipes has 1 through resultLimit items;
- ranks are contiguous from 1;
- each recipePublicId belongs to the request candidate set;
- recipePublicId values are unique;
- each result has exactly seven score components;
- explanation is nonblank and at most 500 characters;
- infeasibleReason is null.

For INFEASIBLE:

- rankedRecipes is empty;
- infeasibleReason is one of NO_ELIGIBLE_RECIPE,
  UNSUPPORTED_HARD_CONSTRAINT, or NUTRITION_INFEASIBLE.

FAILED and DEGRADED are not valid algorithm statuses for this endpoint.

## Score components

V1 reuses the seven seeded components and P11 weights:

| Component | Weight | Operation |
| --- | ---: | --- |
| PANTRY_COVERAGE | 0.40 | add |
| NUTRITION_FIT | 0.25 | add |
| EXPIRY_URGENCY | 0.10 | add |
| PREFERENCE_MATCH | 0.10 | add |
| VARIETY | 0.10 | add |
| EFFORT_FIT | 0.05 | add |
| DISLIKE_PENALTY | 0.20 | subtract |

Every component value is in 0 through 1. totalScore is in -0.20 through 1.00.
Visible component values are quantized before the weighted total is calculated.

Java validates component codes, values, weights, total arithmetic, returned
IDs, rank continuity, result limit, and hard constraints before accepting an
internal response.

Final ordering is deterministic: totalScore descending, then recipePublicId
lexicographically ascending.

## Contract limits

| Limit | V1 maximum |
| --- | ---: |
| Public result limit | 20 |
| Candidate Recipes | 200 |
| Recent Recipe counts | 200 |
| Allergen codes | 32 |
| Dietary constraint codes | 32 |
| Soft preference codes | 32 |
| AVOID or DISLIKE Ingredient UUIDs per list | 500 |
| Nutrition targets | 64 |
| Unit definitions | 128 |
| Pantry lots | 2,000 |
| Ingredient facts | 2,500 |
| Allergen facts per Ingredient | 64 |
| Ingredients per Recipe | 50 |
| Tags per Recipe | 64 |
| Nutrition values per Recipe | 64 |
| Score components per result | exactly 7 |
| Explanation | 500 characters |
| Internal JSON request body | 5 MiB |

These limits are versioned interoperability rules and cannot drift
independently between Java and Python.

## Internal service security

P16 reuses the existing internal-service security model:

- the endpoint is private and not exposed to mobile ingress;
- Java sends X-Internal-Service-Token;
- the credential comes from AI_INTERNAL_SERVICE_TOKEN and is never logged;
- Python compares credentials in constant time;
- Java never forwards the end-user JWT to Python;
- logs must not contain full profile or Pantry snapshots;
- body size, response size, and transport timeouts are bounded;
- there is no unbounded retry;
- TLS is required outside a trusted local development interface.

## Persistence semantics

Java persists request_kind RECIPE_SUGGESTION using the existing
recommendation_requests, recommendation_results, and
recommendation_result_scores tables. No schema migration is required when P16
is implemented according to this V1 contract.

For RECIPE_SUGGESTION, rank_position is shortlist rank. Existing MEAL_PLAN rows
retain their phase-specific ordering semantics.

## Shared fixtures

Canonical Java/Python fixtures live under:

    contract_fixtures/recipe_recommendation/v1/

Both language test suites must read this shared source. Language-specific
duplicate copies are forbidden.

The initial fixture set covers a valid request, valid SUCCEEDED and INFEASIBLE
responses, unknown-field rejection, algorithm-version rejection,
noncontiguous-rank rejection, and invalid score-component shape.

## Deferred from P16

P16 excludes collaborative filtering, embeddings, vector databases, LLM
ranking, model training, learned user feedback, automatic substitutions, Pantry
mutation, Shopping List mutation, Meal Plan mutation, Recipe CRUD,
ratings/reviews, favorites, notifications, and dataset expansion.
