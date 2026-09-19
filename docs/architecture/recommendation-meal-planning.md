# P11 - Deterministic recommendation and meal planning

## Scope and provenance

P11 is a Java/Spring Boot rule-based generator. It does not call Python, an
external AI API, or a machine-learning model. Every generated request stores
`algorithm_version = RULE_BASED_V1` and a null `model_identifier`.

The existing schema names system-generated meal-plan entries
`AI_GENERATED`. P11 uses that existing value; it does not imply that a
machine-learning model produced the entry.

Recipe remains the composition and nutrition-snapshot owner. Food and
Ingredient remain catalog owners. Pantry remains the owner of physical stock.
The generator consumes public read boundaries from those modules and never
injects their repositories or entities.

## Inputs and hard constraints

`POST /api/v1/me/meal-plans/generate` accepts a required start date, one to
seven days, an optional set of unique seeded meal-slot codes, default servings,
and an optional positive cooking-time limit. Omitted slots are
`BREAKFAST`, `LUNCH`, and `DINNER`.

Only published Recipe candidates are considered. A Recipe with no slot rows is
a wildcard and is eligible for every requested slot. A Recipe with explicit
slots must contain the requested slot. A time limit excludes a candidate whose
known total time exceeds the limit; an unknown total time remains eligible.

`AVOID` Ingredient preferences, known allergen `CONTAINS` and `MAY_CONTAIN`
facts, and exact exclusionary dietary tags are hard exclusions. `DISLIKE` is a
soft penalty. `VEGETARIAN`, `VEGAN`, `GLUTEN_FREE`, and `DAIRY_FREE` require
the corresponding Recipe tag when active. PESCATARIAN, HALAL, and KOSHER are
rejected as unsupported constraints rather than silently ignored. Unknown or
absent allergen facts remain unknown.

## Score

Each eligible candidate receives BigDecimal components normalized to `[0, 1]`:

| Component | Weight |
| --- | ---: |
| PANTRY_COVERAGE | 0.40 |
| NUTRITION_FIT | 0.25 |
| EXPIRY_URGENCY | 0.10 |
| PREFERENCE_MATCH | 0.10 |
| VARIETY | 0.10 |
| EFFORT_FIT | 0.05 |
| DISLIKE_PENALTY | -0.20 |

The total is `0.40*coverage + 0.25*nutrition + 0.10*expiry +
0.10*preference + 0.10*variety + 0.05*effort - 0.20*dislike`, rounded to
DECIMAL(10,4) with HALF_UP. Ties are resolved by total score descending,
pantry coverage descending, nutrition fit descending, title ascending, then
Recipe public UUID ascending. No random selection is used.

Pantry coverage scales a Recipe line by generated servings divided by the
Recipe serving count. Only universal same-type unit metadata is used: mass
units normalize through their base unit and volume units do the same. Count
units match only by the same code. No volume-to-mass, piece-to-mass, density,
or other guessed conversion is introduced by P11. An unquantified line can
receive presence coverage when a usable lot for that Ingredient exists.

The generator copies AVAILABLE pantry lots into an in-memory virtual balance.
After each selected meal it subtracts the selected quantities from that copy.
The real Pantry is never mutated, and RESERVED lots are not read as available.

Known stock whose expiry is before the target plan date is unusable. Urgency is
1.00 for expiry on the target date or next day, 0.75 within three days, 0.50
within seven days, 0.25 later, and 0.00 for unknown expiry. Past-dated stock
receives no urgency credit.

Nutrition fit uses the current nutrition target when available and divides the
daily target by the requested meals per day. It evaluates only ENERGY,
PROTEIN, CARBOHYDRATE, and FAT_TOTAL when both target and current Recipe
snapshot values exist. Missing nutrient data is not zero. If no core nutrient
is evaluable, nutrition fit is the documented neutral 0.5000.

## Persistence and lifecycle

One generation transaction writes a `recommendation_requests` row, one
`recommendation_results` row per generated entry, all seven normalized score
rows, a DRAFT `meal_plans` row, and its `meal_plan_entries`. Each entry points
to its result through `source_result_id`; generated entries use
`AI_GENERATED`, `PLANNED`, and the requested default servings.

If some date/slot pairs have no eligible candidate, the plan is persisted with
the available entries and the request is `DEGRADED`; the response identifies
the unfilled pairs. If no entry can be produced, no empty plan is persisted and
the request is rejected. Accept changes only DRAFT to ACCEPTED and does not
reserve or deduct Pantry stock in P11.

Plan list and detail endpoints are owner-scoped. They return public UUIDs,
Recipe references, meal-slot metadata, score/explanation provenance, and no
database surrogate ids.

## Boundaries and limitations

Recipe candidates, slot metadata, tags, ingredient lines, units, and current
nutrition snapshots are batch-loaded by `RecipeRecommendationQueryService`.
Pantry availability, ingredient allergen facts, preferences, dislikes, and
nutrition targets are obtained through existing read boundaries. P11 has no
reservation, deduction, editing, adherence, notification, scheduler, or
shopping-list workflow. An absent nutrition target is neutral; unsupported
dietary constraints fail clearly; incomplete catalog facts are not fabricated.
