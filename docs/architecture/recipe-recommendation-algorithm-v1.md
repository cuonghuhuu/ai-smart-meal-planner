# P16 Deterministic Personalized Recipe Ranking Algorithm V1

## Problem and strategy

HEURISTIC_RECIPE_RANK_V1 ranks a bounded set of published Recipe candidates for
one authenticated user's requested meal slot and servings. It is a deterministic
filter-and-score algorithm, not a generative model and not a Beam Search.

The algorithm reuses the safety boundaries and explainable scoring foundation
from P11 while changing the state model from multi-slot plan search to
independent single-Recipe evaluation.

Java owns identity, data access, authoritative snapshot assembly, persistence,
and final response validation. Python owns deterministic eligibility, feature
computation, scoring, ordering, and bounded explanations.

## Input normalization

Java sends at most 200 candidates selected through the Recipe-owned
recommendation query boundary. Python performs no database lookup.

Each candidate is evaluated from the same immutable request snapshot:

- Ingredient requirements scale by context.servings divided by Recipe base
  servings.
- Nutrition amount for the ranked choice is amountPerServing multiplied by
  context.servings.
- Unit conversion uses the supplied exact Decimal unit catalog.
- Pantry lots are reset to the same initial availability snapshot for each
  candidate. Ranking one Recipe never consumes inventory for another.
- Recent history is the Java-supplied 30-day count for that Recipe.

No randomness, wall-clock lookup, external model call, or hidden process state
may influence ordering.

## Hard filtering

Hard filtering runs before heuristic scoring.

### Meal-slot compatibility

The candidate must be valid for context.mealSlotCode in the normalized Recipe
candidate snapshot supplied by Java.

### Cooking-time cap

When context.maxMinutes is non-null, totalMinutes must be known and at most the
cap. Unknown time under a requested hard cap fails closed.

### Dietary constraints

Every exclusionaryDietaryCode must be explicitly present in candidate
dietaryCodes.

The supported V1 vocabulary is VEGETARIAN, VEGAN, PESCATARIAN, HALAL, KOSHER,
GLUTEN_FREE, and DAIRY_FREE.

No implication is inferred. VEGAN, for example, does not automatically prove a
VEGETARIAN requirement unless the authoritative candidate evidence contains the
required code.

An exclusionary code outside the V1 supported vocabulary makes the request
INFEASIBLE with UNSUPPORTED_HARD_CONSTRAINT.

### AVOID ingredients

Any candidate containing an Ingredient UUID in avoidIngredientPublicIds is
rejected. Optional Ingredients are included because P16 does not implement
substitution or conditional ingredient removal.

### Allergen safety

For every user allergen and every listed candidate Ingredient, evidence must be
explicitly FREE_FROM.

CONTAINS, MAY_CONTAIN, UNKNOWN, a missing IngredientFact, or a missing allergen
entry rejects the candidate. The rule is deliberately fail-closed.

### Mandatory quantity knowledge

Every non-optional Ingredient requirement must contain both quantity and
unitCode. Unknown mandatory quantity or unit rejects the candidate rather than
guessing Pantry coverage.

### Hard nutrition maximum

When a NutritionTarget has hardLimit true and maxValue, candidate nutrition must
be complete, contain a compatible explicit nutrient value, and the requested
servings must not exceed that full daily maximum.

A daily minValue is not enforced as a single-meal minimum. This prevents a safe
individual Recipe from being rejected merely because one meal cannot satisfy an
entire day's minimum.

If an applicable hard maximum cannot be evaluated because required nutrition
evidence is absent or incompatible, that candidate is rejected with
NUTRITION_INFEASIBLE semantics.

## Virtual Pantry evaluation

Each surviving candidate is simulated independently against an immutable copy
of the same available-lot snapshot.

The algorithm preserves P11 lot rules:

- same-dimension exact Decimal conversion only;
- expired lots are unusable;
- earliest expiry is consumed first;
- USE_BY precedes BEST_BEFORE on an equal date;
- public UUID is the final lot tie-breaker;
- unknown expiry sorts last and receives no urgency credit;
- no negative balances;
- no mass-volume, count-mass, density, or guessed serving conversion.

Pantry shortage is not a hard rejection. It lowers PANTRY_COVERAGE and can be
reported in the explanation. P16 does not create a Shopping List and does not
mutate Pantry inventory.

## Score formula

Each component is clamped to 0 through 1 and quantized to four decimal places
with ROUND_HALF_UP before total calculation.

    total =
      0.40 * PANTRY_COVERAGE
    + 0.25 * NUTRITION_FIT
    + 0.10 * EXPIRY_URGENCY
    + 0.10 * PREFERENCE_MATCH
    + 0.10 * VARIETY
    + 0.05 * EFFORT_FIT
    - 0.20 * DISLIKE_PENALTY

The total is then quantized to four decimal places with ROUND_HALF_UP and must
remain between -0.20 and 1.00.

### PANTRY_COVERAGE

Reuse the P11 definition: the mean fraction of each mandatory known Ingredient
requirement supplied by usable compatible Pantry lots. If a Recipe has no
mandatory requirements, the value is 1. Optional Ingredients do not reduce this
component.

### NUTRITION_FIT

If Recipe nutrition is absent or no compatible target dimension can be
evaluated, return neutral 0.5.

For the selected meal slot, multiply each daily target, minimum, or maximum used
for heuristic fit by the fixed slot share:

- BREAKFAST: 0.25
- MORNING_SNACK: 0.05
- LUNCH: 0.30
- AFTERNOON_SNACK: 0.05
- DINNER: 0.30
- EVENING_SNACK: 0.05

For targetValue, desired equals daily target multiplied by slot share. Fit is:

    1 - absolute(actual - desired) / desired

clamped at zero. A zero desired value scores 1 only when actual is also zero.

When targetValue is absent, scale minValue and maxValue by the slot share.
Values inside the resulting interval score 1. Below a positive minimum, fit is:

    1 - shortfall / minimum

Above a positive maximum, fit is:

    1 - excess / maximum

Both are clamped at zero.

Unknown or unit-incompatible dimensions are skipped. The remaining dimensions
are averaged. Incomplete Recipe nutrition blends known fit with neutral
evidence:

    completenessRatio * meanFit + (1 - completenessRatio) * 0.5

The slot shares are ranking heuristics only and never replace the Java-owned
daily nutrition target.

### EXPIRY_URGENCY

Reuse P11 Virtual Pantry urgency. For required Ingredients actually supplied
from Pantry, credit the fraction consumed from lots expiring within seven days.
Urgency declines linearly from 1 on targetDate to 0 seven days later. Unknown
expiry receives no urgency credit.

### PREFERENCE_MATCH

If preferenceCodes is empty, return neutral 0.5. Otherwise return the fraction
of requested soft codes explicitly present in the union of candidate
dietaryCodes and tagCodes. No semantic implication is inferred.

### VARIETY

Let recentCount be the Java-supplied 30-day count for the candidate, or zero
when the candidate has no RecentRecipeCount entry.

    VARIETY = 1 / (1 + recentCount)

This is recent-use diversification only. P16 does not infer a learned
preference from ACCEPTED, REJECTED, or IGNORED decisions.

### EFFORT_FIT

If totalMinutes is unknown and no hard cap exists, return neutral 0.5.

Otherwise:

    EFFORT_FIT = 1 - totalMinutes / referenceMinutes

clamped at zero. referenceMinutes is context.maxMinutes when supplied and 60
otherwise.

### DISLIKE_PENALTY

Return the fraction of all listed Recipe Ingredients whose public UUID appears
in dislikeIngredientPublicIds. Optional Ingredients are included.

## Deterministic ranking

After filtering and scoring:

1. sort by totalScore descending;
2. break ties by recipePublicId lexicographically ascending;
3. keep the first context.resultLimit items;
4. assign contiguous ranks beginning at 1.

Sorting uses the visible four-place totalScore. Hidden extra precision must not
affect ordering.

If at least one candidate survives, status is SUCCEEDED. Fewer than resultLimit
results are allowed.

If none survive, status is INFEASIBLE. Reason precedence is:

1. UNSUPPORTED_HARD_CONSTRAINT when the request contains an unsupported
   exclusionary hard rule;
2. NUTRITION_INFEASIBLE when at least one candidate survives non-nutrition hard
   checks but all such candidates fail applicable hard nutrition evidence or
   daily maximum;
3. NO_ELIGIBLE_RECIPE otherwise.

Search-budget exhaustion is not an algorithm concept in this V1 ranker because
work is directly bounded by the contract limits. Unexpected execution failures
remain technical HTTP 5xx failures rather than false infeasibility.

## Explanations

Each ranked result returns one deterministic explanation of at most 500
characters. It may summarize:

- Pantry coverage percentage;
- soon-expiring Pantry use;
- nutrition fit;
- explicit preference matches;
- recent repetition;
- cooking-time fit;
- disliked Ingredient presence;
- known Pantry shortfall count.

Explanations must not contain user IDs, JWTs, internal service tokens, numeric
database IDs, raw profile snapshots, or unbounded user text.

## Complexity and academic rationale

For C candidates, R Ingredient requirements per candidate, and indexed Pantry
lots for those Ingredients, evaluation is bounded and approximately linear in
the transported candidate data. V1 caps C at 200 and R at 50.

A deterministic weighted heuristic is appropriate for P16 because the project
already has explainable domain signals but does not yet have enough high-quality
user feedback for a credible trained collaborative or learning-to-rank model.
The approach exposes feature contributions, preserves hard safety constraints,
and produces persisted provenance for later evaluation.

P16 evaluation should cover:

- hard-constraint safety;
- deterministic repeatability under candidate input reorder;
- score-component arithmetic;
- top-K ordering and UUID tie-breaks;
- Pantry and expiry sensitivity;
- preference and dislike sensitivity;
- 30-day variety sensitivity;
- nutrition slot-share behavior;
- behavior when nutrition targets are absent;
- Java/Python contract drift.
