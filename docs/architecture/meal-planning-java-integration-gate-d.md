# P11-D Java–Python meal-planning integration

Gate D stops at an internal, validated application result. It does not create a
recommendation request/result or meal plan, expose a public generation route, or
mutate Pantry. Gate E owns those lifecycle operations.

## Boundary and snapshot assembly

`MealPlanGenerationIntegrationService` accepts an authenticated user's public
UUID, a request UUID, and a validated generation command. The eventual public
controller must derive the user UUID from the authenticated principal, never
from client-supplied user input. `MealPlanningSnapshotAssembler` builds the
approved contract V1 request through module-owned boundaries:

| Data | Owner's read boundary |
| --- | --- |
| Available lot-level inventory | `PantryAvailabilityQueryService.availableFor` |
| Published candidate recipes, ingredients, tags and nutrition | `RecipeRecommendationQueryService` |
| Allergens and dietary preferences | `MealPlanningPreferencesQueryService` |
| AVOID and DISLIKE strengths | `UserDislikedIngredientService` |
| Current nutrient targets | `NutritionTargetQueryService` |
| Explicit ingredient–allergen evidence | `IngredientSafetyQueryService` |
| Measurement-unit conversion graph | `PlanningUnitQueryService` |

Meal Planning imports no other module's repository, entity, persistence class,
or web DTO. Each owner performs its own short read-only queries. There is no
transaction spanning the full snapshot assembly or the HTTP call. Snapshots
are immutable records. Inventory is never aggregated: all available Pantry
lots, including their distinct expiries and locations, travel separately.
Missing allergen evidence remains missing and therefore UNKNOWN; Java does not
manufacture FREE_FROM. Missing recipe nutrition remains `null`, not zero.

Recipe preselection is bounded at 200 and deterministic: published recipes
compatible with at least one requested slot (or unassigned to any slot), ordered
by newest publication then public UUID ascending. This is an application-level
candidate cap, **not** AI ranking. It can exclude older recipes under a large
catalog; Gate C ranks only the supplied candidate set. Batch reads avoid one
database query per candidate. If a required fact, unit, or safety snapshot is
inconsistent or exceeds V1 limits, Java fails explicitly rather than sending
a partial snapshot.

Measurement units are copied from the authoritative catalog without case
normalization or Decimal rounding. Persisted base units with null base/factor
become self-referencing V1 definitions with factor 1. Derived factors retain
their full 12-decimal precision, for example `floz → ml` at
`29.573529562500` and `kj → kcal` at `0.239005736138`. Only same-dimension
conversion is valid; no mass/volume/count guessing is performed.

## HTTP sequence and trust boundary

```text
authenticated Java caller → immutable snapshots → V1 request validation
→ assert no active DB transaction → one Python POST → typed V1 parse
→ Java request-relative safety validation → internal result for Gate E
```

The Java client calls `POST /internal/v1/meal-plans/generate` with JSON,
`X-Internal-Service-Token`, and `X-Request-Id`. It never forwards an end-user
JWT. Configure `AI_BASE_URL`, `AI_INTERNAL_SERVICE_TOKEN`,
`AI_CONNECT_TIMEOUT` (default 2 seconds), `AI_RESPONSE_TIMEOUT` (default 20
seconds), and optionally `AI_MAX_RESPONSE_BYTES` (default 1 MiB) in the
environment. The V1 request remains capped at 5 MiB. The service URL and
token must remain in private deployment configuration. Expose Python only on
an internal network and ensure its token configuration matches Java; do not
place it on a public user-facing route. The client does not follow redirects
or retry. It bounds the response body, connect time, and total response time.

The orchestrator has no `@Transactional` annotation and checks for an ambient
active transaction both before reading and immediately before HTTP. Gate E
must use separate short transactions around any future persistence work.

## Java post-validation and errors

Contract parsing rejects malformed JSON, unknown fields, invalid enum codes,
and V1 shape/range violations. The Java validator then compares the response
with the **original** request: version/request ID, exact date–slot coverage,
candidate membership, supported recipe slot, serving bounds, scores and fixed
positive weights, outcome shape, and hard safety evidence. It independently
rechecks declared allergens (explicit FREE_FROM only), AVOID ingredients,
supported hard-diet tags, known cooking time under a cap, and hard daily
nutrition limits where data is present. Unknown hard-limit nutrient data is
unsafe; ordinary missing nutrition is not converted to zero. A malformed or
unsafe result is never returned for persistence.

| Java category | Source |
| --- | --- |
| `AI_SERVICE_UNAVAILABLE` | Connect/transport failure, Python 5xx, internal auth failure |
| `AI_SERVICE_TIMEOUT` | Connect/response deadline or Python 504 |
| `AI_BAD_RESPONSE` | Malformed, oversized, wrong-version/correlation, or unsafe result |
| `AI_SEARCH_BUDGET_EXHAUSTED` | Python 503 technical envelope with that exact code and matching correlation |

`AI_SEARCH_BUDGET_EXHAUSTED` is a technical failure, never `INFEASIBLE`.
Separately, invalid local commands, incomplete snapshots, and ambient
transactions have explicit local categories. Public HTTP mappings belong to
Gate E. Exceptions expose stable category names, not Python stack traces.
Operational logs contain only request ID, contract/algorithm versions, AI
duration, and outcome or error category; they omit token and personal/Pantry
snapshot bodies.

Tests cover assembly, exact unit transport, client headers/transport errors,
request-relative post-validation, and the module/transaction boundaries. The
approved V1 golden JSON fixtures remain the transport compatibility reference.
The module-boundary test is a lightweight source/import guard, not a substitute
for a full bytecode-level architecture analyzer. Java validates hard nutrient
minimums against the final sum for each requested day, never against each meal;
an entirely unfilled day cannot satisfy a positive hard minimum. An INFEASIBLE
result has no selected nutrition to validate.
