# P11-C Deterministic Meal Planning Algorithm V1

## Problem, boundary, and result

Given one validated V1 snapshot from Java, select recipes for up to seven days
and six requested slots per day. Python has no database or Pantry mutation
capability. It returns `SUCCEEDED` for all slots filled, `DEGRADED` for a
nonempty partial plan, or `INFEASIBLE` when no entry can be safely selected
under the supplied hard constraints. Technical failures and search-budget
exhaustion before any safe entry are not algorithm outcomes.

Java remains responsible for candidate publication, user ownership, snapshot
provenance, and final response validation. The Python service accepts only
`POST /internal/v1/meal-plans/generate` with the existing V1 contract and
internal service token. Neither this algorithm nor the endpoint accesses MySQL.

## State-space model and strategy

A search state contains selected entries, unfilled slots, next slot position
(implicit in the search loop), cumulative Decimal score, immutable Virtual
Pantry balances, recipe-use history, current date, and accumulated daily
nutrition. An action chooses an eligible recipe for the next slot; when no
choice survives, the slot is marked unfilled with a stable reason. The
transition scales ingredient quantities by `defaultServings / recipe.servings`,
uses compatible Pantry lots virtually, updates daily nutrition and variety,
and appends a seven-component score and explanation.

Static CSP filtering happens before search: slot compatibility, known cooking
time under a hard cap, exact explicit dietary certification, AVOID ingredients,
allergen evidence, known mandatory quantities, and known complete nutrition for
hard limits. The seed-data exclusionary vocabulary is `VEGETARIAN`, `VEGAN`,
`PESCATARIAN`, `HALAL`, `KOSHER`, `GLUTEN_FREE`, and `DAIRY_FREE`. An unknown
exclusionary code invalidates the entire request: `INFEASIBLE`, zero entries,
and `UNSUPPORTED_HARD_CONSTRAINT` for every requested slot. A
recipe must carry each requested dietary code explicitly in `dietaryCodes`;
tags and implications such as VEGAN implying VEGETARIAN are not inferred.
Java must certify these snapshot facts when it constructs candidates.

Every declared allergen requires explicit `FREE_FROM` for every listed recipe
ingredient, including optional ingredients. `CONTAINS`, `MAY_CONTAIN`,
`UNKNOWN`, and absent facts all reject the recipe. DISLIKE remains a soft
penalty; AVOID is a hard exclusion. If a mandatory ingredient has unknown
quantity/unit, the recipe cannot be simulated and is rejected. A hard nutrition
target requires complete recipe nutrition and a compatible explicit nutrient
value. Missing hard-limit nutrient data is unknown, never zero; that
candidate transition is rejected. Soft heuristic nutrition alone may use the
neutral `0.5000` when no useful dimension is known. A hard `targetValue`
without a min or max is unsupported because V1 has
no tolerance semantics for treating an approximate target as an exact limit.

For hard daily nutrient limits, accumulated values must not exceed maxima.
Forward checking computes an optimistic maximum from each remaining slot's
eligible domain; if even those best independent values cannot reach a minimum,
the branch is abandoned. This is bounded CSP pruning, not a separate exhaustive
backtracking solver. It cannot incorrectly remove a branch that could meet a
minimum, because Pantry availability and conflicts are ignored in that upper
bound. At a day boundary, the same check enforces the minimum exactly.

Beam Search retains the best `K` states after each slot. Default `K=5`, allowed
`1..50`. Ranking is deterministic: more filled slots, then larger cumulative
score (the sum of already four-place persisted entry scores), then
lexicographic recipe UUID sequence, then unfilled reason sequence. A fillable
slot is not abandoned merely to preserve a higher numerical score: filled-slot
count outranks score, even when the added entry has a negative score.
Candidates are sorted by public UUID before expansion. No randomness or
unbounded retry exists. If the expansion budget would be exceeded, the search
returns a nonempty safe partial plan as `DEGRADED` and marks remaining slots
`SEARCH_LIMIT_REACHED`. A partial current day is retained only if its hard
daily minima already hold; otherwise search rewinds to the best completed-day
prefix. If no safe entry remains, it raises `SearchBudgetExhausted`, mapped to
HTTP 503 `AI_SEARCH_BUDGET_EXHAUSTED` at the Python boundary. This is a
technical execution failure for Java to persist as `FAILED` in Gate D, not
`INFEASIBLE`. The service does not claim infeasibility when the search budget
prevented a conclusion.

## Virtual Pantry semantics

Each availability lot remains independent, including expiry, storage location,
unit, and public ID. Input snapshots and search branches are never mutated.
Balances are stored in each dimension's declared base unit; conversion is
permitted only when both units have the same dimension and base unit. No
mass-volume, count-mass, density, serving-size, or guessed conversion occurs.
Unit codes are the case-sensitive lowercase identifiers from Java's
measurement-unit catalog, never normalized aliases. The algorithm uses the
contract-supplied exact Decimal `toBaseFactor` (up to 12 fractional digits),
including `floz` to `ml` and `kj` to `kcal`; these factors are not rounded to
the four-place score precision.
Compatible, usable lots are consumed in earliest-expiry order, with USE_BY
before BEST_BEFORE on a tie and public UUID as the final tie-breaker. A lot
past its expiry date is excluded; unknown expiry sorts last and receives no
urgency credit. Reserved stock must already be absent from Java's
`PantryAvailabilityQueryService` snapshot.

The V1 request has no `pantryOnly` hard flag. A recipe with a known ingredient
shortfall may still be recommended: the algorithm consumes only what exists,
never creates a negative balance, reduces `PANTRY_COVERAGE`, and states the
number of shortfalls in the explanation. It does not create a shopping list or
claim the meal can be prepared entirely from current stock. Unknown mandatory
quantities are different: those are rejected rather than guessed.

## Heuristic and explanations

All arithmetic uses `Decimal`. Each externally visible component is clamped to
`[0,1]` and quantized with `Decimal("0.0001")` and `ROUND_HALF_UP`. The
weighted sum uses only those quantized component values and is itself
quantized to four places with the same policy. Beam ranking sums these visible
four-place entry totals; it does not rank on hidden extra precision that would
be lost on persistence:

```text
0.40 PANTRY_COVERAGE + 0.25 NUTRITION_FIT + 0.10 EXPIRY_URGENCY
+ 0.10 PREFERENCE_MATCH + 0.10 VARIETY + 0.05 EFFORT_FIT
- 0.20 DISLIKE_PENALTY
```

The reported penalty weight is positive `0.20`; subtraction is only an
algorithm operation. Total score remains in `[-0.20,1.00]`. For example,
`0.12344` becomes `0.1234`, `0.12345` becomes `0.1235`, and `-0.12345`
becomes `-0.1235`.

| Component | Normalized V1 rule |
|---|---|
| `PANTRY_COVERAGE` | Mean fraction of each mandatory known requirement supplied by usable compatible lots; `1` if none are mandatory. |
| `NUTRITION_FIT` | Mean fit of known comparable recipe nutrients to target or per-slot range (`daily target / slots per day`); absent dimensions are skipped, and no useful dimension yields neutral `0.5`. Incomplete nutrition blends known fit with neutral `0.5` using `completenessRatio`. |
| `EXPIRY_URGENCY` | Mean per-required-ingredient fraction consumed from lots expiring within seven days, weighted linearly from `1` on the expiry date to `0` seven days ahead. |
| `PREFERENCE_MATCH` | Fraction of requested soft codes explicitly present in recipe dietary or tag codes; no soft codes yields neutral `0.5`. |
| `VARIETY` | `1 / (1 + previous uses of this recipe in this plan)`. |
| `EFFORT_FIT` | `1 - minutes / maxMinutesPerMeal` when capped, otherwise against a 60-minute reference; unknown minutes without a cap yields neutral `0.5`. |
| `DISLIKE_PENALTY` | Fraction of listed recipe ingredients marked DISLIKE, including optional ones. |

Explanations report bounded percentages, shortfall count, expiry use, recipe
repetition, and dislike presence. They use no food/profile names, user IDs,
tokens, or raw snapshot data. They are deterministic for the same normalized
request and search configuration.

## Bounds and evaluation

The contract caps input at 42 slots, 200 candidates, 2,000 lots, and 50
ingredients per recipe. Search additionally caps expansions at 50,000 by
default (configurable `1..200000`). A deployment configures `AI_BEAM_WIDTH`
and `AI_MAX_SEARCH_EXPANSIONS`; invalid settings fail on startup. The beam
stores at most 50 partial states. The worst-case expansion count is bounded by
`min(42 × beam_width × eligible_candidates, max_expansions)`; each expansion
visits only indexed lots of its ingredients. This is heuristic search, not
brute-force enumeration of the full plan space.

Automated evaluation covers each hard rule and score component, quantization,
lot isolation and expiry order, safe units and overdraw, forward checking,
beam width 1 versus 2, repeatability under candidate reorder, search-budget
degradation, and authenticated HTTP outcomes. An explicit quality tradeoff is
that a narrow beam can miss a later feasible sequence; the width-divergence
test demonstrates this. No trained model or stochastic claim is made.
