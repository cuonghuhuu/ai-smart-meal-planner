# P6 — Nutrition Targets & Deterministic Calculation

## 1. Objective

Phase P6 turns the profile and measurement data delivered in P5 into dated,
versioned nutrition-target snapshots that later Food, Recipe, Recommendation,
and Meal Planning phases can consume.

P6 remains inside the Spring Boot modular monolith. It does not introduce the
Python AI service, Food Catalog CRUD, recipe logic, pantry logic, recommendation
ranking, meal-plan optimization, Flutter, or notifications.

The primary invariant is that a nutrition target is an auditable snapshot of the
inputs and calculation method that applied for a defined period. Historical
meal plans must never be reinterpreted using today's profile values.

## 2. Dependency boundary

P6 depends on capabilities already delivered by P2, P4, and P5:

```text
P2 schema/reference data
        |
        +-- nutrients / measurement_units
        +-- activity_levels / nutrition_goals
        +-- user_nutrition_targets
        +-- user_nutrition_target_values
        |
P4 authenticated identity
        |
        +-- CurrentUserService -> CurrentUserIdentity
        |
P5 profile + measurements
        |
        +-- birth date
        +-- sex
        +-- height
        +-- activity level
        +-- nutrition goal
        +-- target weight / weekly-change preference
        +-- body-measurement history
        |
        v
P6 Nutrition
        |
        +-- deterministic calculation preview
        +-- dated target persistence
        +-- current/history queries
        +-- nutrient reference API
```

P6 must not query authentication persistence directly. The authenticated public
UUID is resolved through the existing auth application boundary, as P5 already
does.

P6 also does not depend on the current representative `food` mappings left from
P3. Food composition and food-nutrient catalog ownership belong to a later
catalog phase.

## 3. Existing database contract

No P6 schema migration is required for the initial implementation. P2 already
provides the required relational model.

### `user_nutrition_targets`

A target set records:

- owning `user_id`;
- `effective_from` and nullable `effective_to`;
- `origin`: `CALCULATED`, `USER_DEFINED`, or `ADJUSTED`;
- the activity-level and nutrition-goal references used at creation time;
- a versioned `calculation_method` when the target is calculated.

The unique key `(user_id, effective_from)` prevents two target sets starting on
the same date. The database ensures `effective_to >= effective_from`, but MySQL
cannot enforce non-overlapping periods across rows. The Nutrition application
service therefore owns the non-overlap invariant.

### `user_nutrition_target_values`

Values are normalized by nutrient. A row may contain:

- `target_amount`;
- `min_amount`;
- `max_amount`;
- `is_hard_limit`.

The nutrient owns its unit through the existing `nutrients -> measurement_units`
relationship. Public APIs expose stable nutrient/unit codes and never expose
surrogate database IDs.

## 4. P6 calculation method

The first automatic calculation method is named:

```text
MIFFLIN_ST_JEOR_V1
```

It is intentionally deterministic and versioned. A future formula or policy
change creates a new method identifier instead of silently changing the meaning
of historical calculated rows.

### 4.1 Required inputs

Automatic calculation requires all of the following at `effectiveFrom`:

1. an authenticated active user;
2. a P5 profile;
3. `birthDate`;
4. `heightCm`;
5. `sex` equal to `MALE` or `FEMALE`;
6. an activity level with a valid `energyFactor`;
7. a body measurement with `weightKg` on or before `effectiveFrom`;
8. age of at least 19 years on `effectiveFrom`.

The selected weight is the measurement with the greatest `measuredOn` such that
`measuredOn <= effectiveFrom`. A later measurement must never affect a target
calculated for an earlier date.

Missing required input is a domain validation failure. The service must not
invent defaults for missing height, weight, activity level, birth date, or sex.

### 4.2 Unsupported automatic-calculation cases

The P5 `Sex` enum contains `MALE`, `FEMALE`, `OTHER`, and
`PREFER_NOT_TO_SAY`. `MIFFLIN_ST_JEOR_V1` is defined only for the male/female
terms in the published equation, so P6 must not silently map `OTHER` or
`PREFER_NOT_TO_SAY` to one of them.

For those values, and for users younger than 19, automatic calculation is
reported as unsupported. User-defined nutrition targets remain available.

P6 does not model pregnancy, breastfeeding, diagnosed disease-specific dietary
therapy, or pediatric clinical nutrition. The feature is an academic/wellness
planning baseline, not medical advice.

### 4.3 Resting energy equation

For weight `W` in kilograms, height `H` in centimetres, and age `A` in complete
years on `effectiveFrom`:

```text
MALE:
RMR = 10 * W + 6.25 * H - 5 * A + 5

FEMALE:
RMR = 10 * W + 6.25 * H - 5 * A - 161
```

The calculation keeps decimal precision internally with `BigDecimal`. Display
rounding must not be used as an intermediate calculation input.

The simplified equation is the published Mifflin-St Jeor form. The original
study covered healthy adults aged 19–78 and the equation is a prediction rather
than a direct metabolic measurement.

### 4.4 Activity adjustment

Maintenance energy is:

```text
maintenanceEnergyKcal = RMR * activityLevel.energyFactor
```

The factor comes from P2 reference data. It is not hard-coded in the calculator.
This preserves the existing vocabulary boundary and keeps the method independent
from surrogate IDs.

### 4.5 Weight-change goals

`MIFFLIN_ST_JEOR_V1` does **not** convert `weeklyChangeKg` into a calorie delta.
The common fixed "3500 kcal per pound / ~7700 kcal per kg" rule is deliberately
not encoded because static energy-to-weight conversion is known to ignore the
body's dynamic metabolic adaptation.

Therefore the first P6 automatic method is a **maintenance-energy baseline**.
For `MAINTAIN`, `EAT_HEALTHIER`, and `REDUCE_WASTE`, that baseline may be
persisted directly as a calculated target.

For `LOSE_WEIGHT`, `GAIN_WEIGHT`, or `BUILD_MUSCLE`, the calculation preview
returns RMR and maintenance energy but does not fabricate a goal-adjusted calorie
target. A user may save a `USER_DEFINED` target. A later evaluated/versioned
method may add goal adjustment without changing `MIFFLIN_ST_JEOR_V1`.

This limitation is explicit in the API response and documentation; the system
must never present maintenance energy as a weight-loss or weight-gain
prescription.

### 4.6 Adult macronutrient ranges

For supported adults, P6 may derive baseline macronutrient ranges from the adult
Acceptable Macronutrient Distribution Ranges (AMDR):

| Nutrient | Percent of energy |
| --- | ---: |
| `CARBOHYDRATE` | 45–65% |
| `FAT_TOTAL` | 20–35% |
| `PROTEIN` | 10–35% |

Conversion uses 4 kcal/g for carbohydrate and protein and 9 kcal/g for fat.
Calculated macro rows use `min_amount` and `max_amount`; they do not invent an
exact `target_amount` at the midpoint of the range.

The initial calculated target set therefore contains at most:

- `ENERGY`: exact maintenance `target_amount` in kcal;
- `CARBOHYDRATE`: min/max grams;
- `FAT_TOTAL`: min/max grams;
- `PROTEIN`: min/max grams.

All automatically calculated values are soft planning targets
(`is_hard_limit = false`). Allergens and exclusionary dietary rules remain
separate hard constraints and must never be weakened by nutrition scoring.

P6 does not automatically derive fibre, sodium, vitamin, mineral, or
condition-specific targets. Those may be user-defined now and can receive a
separate evidence-backed calculation policy later.

## 5. Rounding policy

All calculations use `BigDecimal` and explicit rounding.

- RMR and maintenance energy retain at least 4 decimal places internally.
- Persisted `ENERGY.target_amount` is rounded to 2 decimal places using
  `HALF_UP`.
- Persisted macro min/max grams are rounded to 2 decimal places using `HALF_UP`.
- API display formatting is a presentation concern and must not alter persisted
  calculations.

The method version includes this policy. Changing formula coefficients,
conversion constants, or rounding semantics requires a new calculation-method
version.

## 6. Target lifecycle and effective periods

Target history is append-oriented. Editing today's profile must not rewrite a
past target row.

When a new target starts on date `D`:

1. lock/read the user's target rows needed to enforce the timeline;
2. reject another row already starting on `D` unless the operation is an
   explicit same-date replacement supported by the service contract;
3. close the immediately preceding open target at `D - 1 day`;
4. reject any operation that would create an overlap with a later target;
5. create the new target and all of its nutrient values in one transaction.

Example:

```text
old: 2026-09-15 -> NULL
new effectiveFrom: 2026-10-01

result:
old: 2026-09-15 -> 2026-09-30
new: 2026-10-01 -> NULL
```

The application service owns this invariant because the database cannot express
an exclusion constraint across date ranges.

## 7. Origin semantics

### `CALCULATED`

Created only by the server from a named calculation method. Clients never submit
calculated nutrient amounts for the server to trust. The server recomputes the
snapshot from authoritative profile/reference/measurement data.

### `USER_DEFINED`

Created from explicit user-supplied nutrient targets/bounds. Every nutrient code
must resolve through the reference vocabulary, every amount must satisfy the P2
range constraints, and duplicate nutrient codes are rejected before persistence.

### `ADJUSTED`

Reserved for a future workflow that starts from a known calculated/user-defined
snapshot and records a deliberate modification. P6 should not use `ADJUSTED` as
an alias for arbitrary updates without preserving provenance.

## 8. Public API contract

All user-target endpoints require a valid Bearer access token and operate on the
current authenticated subject. No user ID appears in the route or request body.

### Reference data

```text
GET /api/v1/reference/nutrients
```

Returns ordered nutrient definitions with stable nutrient code, display name,
kind, core flag, display order, and unit code/display name. Internal IDs are not
returned.

### Calculation preview

```text
POST /api/v1/me/nutrition-targets/calculate
```

Request:

```json
{
  "effectiveFrom": "2026-09-15"
}
```

The response is read-only and includes the calculation method, selected profile
inputs, selected measurement date/weight, age, RMR, maintenance energy,
calculated target values when the goal is supported, and warnings/unsupported
reasons where applicable.

This endpoint never writes nutrition-target rows.

### Persist calculated target

```text
POST /api/v1/me/nutrition-targets/calculated
```

The request supplies `effectiveFrom`. The server reruns the calculation from
current authoritative data and persists only if automatic target creation is
supported for the current goal and all inputs are valid.

### Persist user-defined target

```text
POST /api/v1/me/nutrition-targets/user-defined
```

The request supplies `effectiveFrom` and a complete set of nutrient values. The
server resolves nutrient codes and applies the same dated-target lifecycle.

### Current target

```text
GET /api/v1/me/nutrition-targets/current
```

Returns the target whose period contains the current UTC date. If no target
applies, return `404 NOT_FOUND` rather than inventing one.

### History

```text
GET /api/v1/me/nutrition-targets?page=0&size=20
```

Returns the authenticated user's target history newest-first with bounded
pagination. Each item includes effective dates, origin, calculation method,
reference codes, and nutrient values.

## 9. Error semantics

P6 should use stable Problem Details responses and avoid leaking persistence
internals.

Expected domain failures include:

- target profile missing;
- required calculation input missing;
- unsupported age/sex for automatic calculation;
- unsupported goal for automatic target persistence;
- unknown nutrient code;
- duplicate nutrient code in a request;
- invalid target/min/max relationship;
- overlapping effective period;
- no current target;
- authenticated user/account no longer active.

A calculation limitation is a domain result, not an internal server error.
Unexpected arithmetic, persistence, or contract failures remain safe `5xx`
responses with request correlation.

## 10. Module shape

Initial implementation should use a focused module:

```text
com.smartmealplanner.nutrition
├── application
│   ├── NutritionTargetCalculator
│   ├── NutritionTargetService
│   └── ReferenceNutritionService
├── persistence
│   ├── MeasurementUnit
│   ├── Nutrient
│   ├── UserNutritionTarget
│   ├── UserNutritionTargetValue
│   └── repositories...
└── web
    ├── NutritionTargetController
    ├── NutritionReferenceController
    └── request/response DTOs...
```

The exact class count may be smaller if responsibilities remain cohesive. Do not
create interfaces or abstraction layers without a real boundary/test need.

Cross-module access to P5 data should be through focused application/query
contracts where practical rather than by allowing the nutrition module to become
a grab-bag of profile repositories. P6 implementation should preserve the P1
rule that module repositories are private to their owning domain.

## 11. Testing contract

### Calculator unit tests

Use a fixed `Clock`/date and cover:

- male and female published-equation examples;
- age calculation around birthdays;
- exact selection of the latest measurement at/before `effectiveFrom`;
- activity factor multiplication;
- decimal/rounding behavior;
- adult macro min/max conversion;
- missing profile fields;
- missing measurement;
- `OTHER` and `PREFER_NOT_TO_SAY`;
- age below 19;
- weight-change goals returning a documented unsupported automatic target rather
  than a fabricated calorie deficit/surplus.

### Service tests

Cover:

- current target selection;
- target history ordering/pagination;
- closing a previous open target;
- rejecting overlapping periods;
- atomic target + values persistence;
- unknown/duplicate nutrient codes;
- user-defined value validation;
- no surrogate ID exposure.

### Integration tests

Run against MySQL 8.4 Testcontainers with the authoritative Flyway resources and
Hibernate `ddl-auto=validate`. Cover authentication, cross-user isolation,
calculation preview, calculated persistence, user-defined persistence, current
selection, and history.

P6 acceptance still requires the entire existing suite to remain green.

## 12. Security and privacy

- All `/api/v1/me/nutrition-targets/**` routes require authentication.
- User identity comes only from the verified security principal.
- Request bodies never accept a user ID.
- No internal BIGINT identifiers are exposed.
- No health/profile values are placed in URLs or logs.
- Calculation errors do not echo full profile/measurement records.
- No new secret or credential is introduced.

## 13. Dependency decision

P6 requires no new Maven dependency. Java `BigDecimal`, `LocalDate`, existing
Spring/JPA/Validation, and the current MySQL/Testcontainers stack are sufficient.

The Python AI service is intentionally not introduced. Deterministic nutrition
calculation belongs in Java; Python remains reserved for recommendation,
ranking, search/optimization, and other evaluated AI computation.

## 14. Evidence and limitations

The Mifflin-St Jeor equation was published from a cohort of adults aged 19–78:

- Mifflin MD et al. *A new predictive equation for resting energy expenditure in
  healthy individuals.* Am J Clin Nutr. 1990. PubMed:
  https://pubmed.ncbi.nlm.nih.gov/2305711/

Adult AMDR percentages used for baseline macro ranges are documented by the
National Academies:

- National Academies of Sciences, Engineering, and Medicine. *Rethinking the
  Acceptable Macronutrient Distribution Range for the 21st Century.* 2024:
  https://www.ncbi.nlm.nih.gov/books/NBK610329/

The project deliberately does not use a fixed calories-per-kilogram rule to
predict weight change. NIDDK documents that static 3500-kcal-per-pound style
rules do not account for metabolic adaptation and overpredict long-term weight
change:

- NIDDK, *NIH Body Weight Planner / Research Behind the Body Weight Planner*:
  https://www.niddk.nih.gov/research-funding/at-niddk/labs-branches/laboratory-biological-modeling/integrative-physiology-section/research/body-weight-planner

These references support the engineering baseline; they do not make the
application a clinical nutrition or medical device.

## 15. P6 implementation order

Implementation starts only after this contract is accepted.

1. Map nutrition reference and target persistence entities against existing P2
   tables; add repository integration coverage.
2. Implement deterministic `MIFFLIN_ST_JEOR_V1` calculator with pure unit tests.
3. Implement target-period lifecycle and current/history queries.
4. Add nutrient-reference and target REST DTOs/controllers.
5. Add authenticated MySQL integration tests and cross-user isolation tests.
6. Run the complete Maven verify suite, pre-commit/detect-secrets, and
   `git diff --check` before PR.

No schema migration, Python service, Food/Recipe/Pantry implementation, or
Flutter work is part of this phase.