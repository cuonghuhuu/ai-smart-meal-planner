# P11-B Meal Planning Internal Contract V1

## Scope

This document defines the typed transport boundary for:

```text
POST /internal/v1/meal-plans/generate
```

It does not define or implement scoring, constraint solving, Beam Search,
Backtracking, Virtual Pantry transitions, persistence orchestration, Flutter UI,
or computer vision.

Java remains the application authority. Python receives a fully normalized,
surrogate-ID-free snapshot and performs no authentication of end users, CRUD,
database access, or Pantry mutation.

## Versioning

Every request and successful algorithm response carries:

- `contractVersion`: exactly `"1"`;
- `requestId`: a UUID generated or accepted by Java for correlation;
- `algorithmVersion`: exactly `HEURISTIC_MEAL_PLAN_V1` in V1.

Breaking transport changes require a new major path and contract version.
Reference-data additions that are valid under an existing code field are not by
themselves breaking, but Java still validates every code against authoritative
reference data before sending it.

All JSON objects reject unknown fields. All arrays are required, even when
empty. Nullable fields are explicitly identified below. Dates use ISO 8601
`YYYY-MM-DD`. UUIDs are public UUIDs; numeric database IDs are forbidden.

Decimal quantities are JSON numbers bound to Java `BigDecimal` and Python
`Decimal`. Contract calculations must not use binary floating-point
arithmetic. NaN and infinity are rejected, and both implementations validate
the declared precision and scale limits.

Semantic reference-data codes (allergens, dietary preferences, nutrients, recipe
tags, and similar codes) use `^[A-Z][A-Z0-9_]{0,63}$`. Closed transport
vocabularies use enums. Measurement-unit codes are a separate, case-sensitive
catalog namespace: `^[a-z][a-z0-9_]{0,63}$`. Java must copy canonical unit
codes such as `g`, `ml`, `piece`, and `kcal` exactly from the authoritative
measurement-unit catalog. Neither Java nor Python case-normalizes them.

## Request hierarchy

```text
MealPlanGenerationRequest
|- planning: Planning
|- hardConstraints: HardConstraints
|- softPreferences: SoftPreferences
|- nutritionTargets[]: NutritionTarget
|- unitDefinitions[]: UnitDefinition
|- pantryLots[]: PantryLot
|- ingredientFacts[]: IngredientFact
|  `- allergenFacts[]: AllergenFact
`- recipeCandidates[]: RecipeCandidate
   |- ingredients[]: IngredientRequirement
   `- nutrition: NutritionData | null
      `- values[]: NutritionValue
```

### Planning

| Field | Type | Required | Semantics |
|---|---|---:|---|
| `startDate` | ISO date | yes | First local calendar date in the plan. |
| `days` | integer `1..7` | yes | Consecutive days beginning at `startDate`. |
| `requestedMealSlots` | unique meal-slot enum array | yes, non-empty | The requested slots repeat on each planning day. |
| `defaultServings` | decimal `(0,50]`, scale <= 2 | yes | Default requested servings per entry. |
| `maxMinutesPerMeal` | integer `1..1440` or null | yes | Null means no cooking-time hard constraint. |

Meal-slot enums are the six V1 reference codes: `BREAKFAST`, `MORNING_SNACK`,
`LUNCH`, `AFTERNOON_SNACK`, `DINNER`, and `EVENING_SNACK`.

### Hard constraints

| Field | Type | Required |
|---|---|---:|
| `allergenCodes` | unique reference-code array | yes |
| `exclusionaryDietaryCodes` | unique reference-code array | yes |
| `avoidIngredientPublicIds` | unique UUID array | yes |

Unsupported exclusionary rules are not ignored. The P11-C algorithm produces an
unsupported/infeasible result when a requested rule cannot be enforced safely.

### Soft preferences

| Field | Type | Required |
|---|---|---:|
| `dislikeIngredientPublicIds` | unique UUID array | yes |
| `preferenceCodes` | unique non-exclusionary reference-code array | yes |

`AVOID` and `DISLIKE` are deliberately transported separately.

### Nutrition target

Each item contains `nutrientCode`, nullable `targetValue`, nullable `minValue`,
nullable `maxValue`, `hardLimit`, and `unitCode`. At least one numeric value is
required. Values are non-negative, `minValue <= maxValue`, and a target must lie
inside supplied bounds. A missing value is unknown/absent, not zero.

### Unit definition

Each definition contains:

- `unitCode`;
- `dimension`: `MASS`, `VOLUME`, `COUNT`, or `ENERGY`;
- `baseUnitCode` for that dimension;
- positive `toBaseFactor`, an exact decimal with at most 10 integer and 12
  fractional digits.

The relation is `base quantity = quantity * toBaseFactor`. The V1 precision
preserves catalog factors such as `29.573529562500` (`floz` to `ml`) and
`0.239005736138` (`kj` to `kcal`) without rounding or score quantization.
Numeric JSON values may serialize without trailing zeroes, but their Decimal
value must remain exact. The relation supports only same-dimension
multiplicative conversion. It does not authorize mass/volume,
count/mass, density, or speculative serving conversions.

Every referenced unit must have a definition. Every `baseUnitCode` must also be
included, use the same dimension, refer to itself, and have factor `1`. This
makes an incomplete or cross-dimension conversion graph contract-invalid.

### Pantry lot

Each lot contains `pantryItemPublicId`, `ingredientPublicId`, nullable
`foodPublicId`, positive `quantityRemaining`, `unitCode`, nullable `expiryDate`,
`expiryKind`, and `storageLocation`.

Lots are never aggregated. Java must obtain them only through
`PantryAvailabilityQueryService`, which excludes reserved inventory. A null
expiry date requires `expiryKind: UNKNOWN`.

### Ingredient safety facts

Each `IngredientFact` identifies one public Ingredient UUID and carries unique
allergen facts. Evidence is one of:

- `CONTAINS`
- `MAY_CONTAIN`
- `FREE_FROM`
- `UNKNOWN`

For every declared user allergen, only explicit `FREE_FROM` evidence is safe.
`CONTAINS`, `MAY_CONTAIN`, and `UNKNOWN` are non-eligible. An absent
ingredient fact or an absent allergen code inside a present fact resolves to
`UNKNOWN`, never `FREE_FROM`. The contract provides this evidence lookup;
The P11-C algorithm applies it during recipe eligibility filtering. Java must not invent
facts while assembling the snapshot.

### Recipe candidate

Each candidate contains:

- `recipePublicId`;
- positive base `servings`;
- nullable `totalMinutes`;
- non-empty unique `mealSlotCodes`;
- unique `dietaryCodes` and `tagCodes`;
- one to 50 unique ingredient requirements;
- nullable nutrition data.

An ingredient requirement contains the public Ingredient UUID, a nullable
quantity/unit pair, `optional`, and `allowSubstitution`. Quantity and unit must
either both be present or both be null.

Nutrition data contains a completeness ratio in `[0,1]` and unique nutrient
values. A null nutrition object or missing nutrient item is unknown, not zero.

## Response hierarchy and outcomes

```text
MealPlanGenerationResponse
|- entries[]: MealPlanEntry
|  `- scoreComponents[]: ScoreComponent
`- unfilledSlots[]: UnfilledSlot
```

`status` is a closed AI outcome:

| Status | Required shape |
|---|---|
| `SUCCEEDED` | At least one entry and no unfilled slots. Every requested date/slot must have an entry; Java verifies this against the original request before accepting the result. |
| `DEGRADED` | At least one entry and at least one unfilled slot. |
| `INFEASIBLE` | No entries and at least one unfilled slot; there is no recommendation result. |

`FAILED` is not accepted from Python. Java uses `FAILED` only for technical
execution states such as timeout, unavailable service, transport failure,
malformed JSON, or an unsafe response.

A response entry contains `planDate`, `mealSlotCode`, `recipePublicId`, positive
`servings`, `totalScore`, exactly seven score components, and a non-blank
explanation of at most 500 characters.

Every component value is in `[0,1]`. Stored weights remain positive:

| Component | Weight | Algorithm use |
|---|---:|---|
| `PANTRY_COVERAGE` | `0.40` | add |
| `NUTRITION_FIT` | `0.25` | add |
| `EXPIRY_URGENCY` | `0.10` | add |
| `PREFERENCE_MATCH` | `0.10` | add |
| `VARIETY` | `0.10` | add |
| `EFFORT_FIT` | `0.05` | add |
| `DISLIKE_PENALTY` | `0.20` | subtract |

The total score range is `[-0.20,1.00]`. The contract layer validates components,
weights and ranges; the P11-C algorithm calculates the total.

An unfilled slot contains `planDate`, `mealSlotCode`, a stable reason enum, and
an optional explanation of at most 500 characters. V1 reason codes are:

- `NO_ELIGIBLE_RECIPE`
- `HARD_CONSTRAINT_CONFLICT`
- `UNSUPPORTED_HARD_CONSTRAINT`
- `PANTRY_INFEASIBLE`
- `NUTRITION_INFEASIBLE`
- `SEARCH_LIMIT_REACHED`

Duplicate date/slot pairs and overlap between filled and unfilled slots are
invalid. The combined number of filled and unfilled slots cannot exceed 42.
The response alone does not contain the requested schedule, so its structural
validation cannot prove `SUCCEEDED` is complete or that dates and slots belong
to the request. Java must compare the response with the original request in a
later integration gate.

## Contract limits

| Limit | V1 maximum |
|---|---:|
| Plan days | 7 |
| Requested meal slots per day | 6 |
| Expanded date/slot combinations | 42 |
| Allergen codes | 32 |
| Dietary constraint codes | 32 |
| Non-exclusionary preference codes | 32 |
| AVOID or DISLIKE Ingredient UUIDs per list | 500 |
| Nutrition targets | 64 |
| Unit definitions | 128 |
| Pantry lots | 2,000 |
| Ingredient facts | 2,500 |
| Allergen facts per ingredient | 64 |
| Recipe candidates | 200 |
| Ingredients per recipe | 50 |
| Tags per recipe | 64 |
| Nutrition values per recipe | 64 |
| Score components | exactly 7 |
| Explanation | 500 characters |
| JSON request body | 5 MiB |
| Future Beam width | 50 |

These values are versioned interoperability ceilings and therefore cannot be
configured independently in Java and Python. The shared boundary fixtures and
both language test suites are the drift detector. A deployment may lower the Python
body limit with `AI_MAX_REQUEST_BYTES`, but never above 5 MiB. P11-C configures
Beam width in the range `1..50`; it is deliberately not a V1
request field in V1.

## Internal service security

- The endpoint is deployed only on a private service network and is not routed
  through public/mobile ingress.
- Java authenticates with `X-Internal-Service-Token`; Python compares it in
  constant time.
- The token comes only from `AI_INTERNAL_SERVICE_TOKEN`, must contain at least
  32 characters, and is never committed or logged.
- A missing server credential fails closed with 503; missing or incorrect caller
  credentials return 401.
- TLS is required whenever the services do not communicate over a trusted local
  development interface.
- The application enforces the 5 MiB body limit even without `Content-Length`.
  The reverse proxy/container should enforce the same or a lower limit.
- Logs may contain request ID, contract/algorithm version, duration, outcome and
  bounded failure code. They must not contain service tokens or full user
  snapshots.
- Java never forwards an end-user JWT to Python.

P11-C returns HTTP 200 with a validated `SUCCEEDED`, `DEGRADED`, or
`INFEASIBLE` algorithm outcome after authentication and contract validation.
The former Gate-B HTTP 501 placeholder has been removed. A technical Python
failure is not represented as `INFEASIBLE`. If the configured search budget is
exhausted before any safe entry can be returned, Python sends HTTP 503 with
stable code `AI_SEARCH_BUDGET_EXHAUSTED`; Java must treat it as a technical
`FAILED` execution in its later integration gate. A safe nonempty partial
result may instead be `DEGRADED` with `SEARCH_LIMIT_REACHED` unfilled slots.

## Shared fixtures

Canonical fixtures live under `contract_fixtures/meal_planning/v1/`. Java copies
them to its test classpath during Maven test-resource processing; Python reads
the same source files directly. Language-specific fixture copies are forbidden.
