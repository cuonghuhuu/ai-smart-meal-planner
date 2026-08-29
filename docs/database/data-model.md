# Data Model (Phase P2)

## Purpose and scope

This document explains the relational design implemented by
[`database/schema/V001__initial_schema.sql`](../../database/schema/V001__initial_schema.sql):
what each domain stores, why it is shaped that way, and the conventions the team
applies when the schema evolves.

It is a design document. Per-table field reference lives in the
[data dictionary](data-dictionary.md); the reasoning behind contested choices
lives in [database decisions](database-decisions.md).

Target engine is **MySQL 8.0.19 or later** on **InnoDB**, `utf8mb4` with
`utf8mb4_0900_ai_ci`. The design was loaded and verified against MySQL 8.4.11
(see the [validation report](validation-report.md)).

## Ownership boundary

P2 changes nothing about the architecture fixed in
[ADR-004](../architecture/architecture-decisions.md). Restated because every
design choice below depends on it:

| Component | Database access |
| --- | --- |
| Flutter (Android/Web) | None. REST calls to Java only. |
| Java Spring Boot monolith | **Sole** MySQL client, via JPA/Hibernate and migrations. |
| Python AI service | None. No credentials, no connection, no authoritative state. |

Consequence for the schema: every table has exactly one writer, so invariants
that a single writer can hold in a transaction do not need defensive
denormalisation. Where an invariant is cheap to state declaratively it is still
written as a constraint, because a constraint survives a refactor of the Java
code that a comment does not.

## Domain map

53 tables in twelve sections, matching the section order of the migration file.

| # | Section | Tables | Core responsibility |
| --- | --- | --- | --- |
| 1 | Reference vocabularies | 12 | Shared controlled values: units, nutrients, roles, slots, categories, tags, score components, notification types. |
| 2 | Users and authentication | 4 | Account identity, credentials, refresh sessions, one-time tokens, role grants. |
| 3 | Profile and nutrition targets | 6 | Static profile, measurement history, dated nutrition targets, dietary preferences, allergens. |
| 4 | Foods and nutrition facts | 3 | Canonical nutrition catalog and serving definitions. |
| 5 | Ingredients | 6 | Culinary identity, food mapping, allergens, aliases, unit conversions, disliked ingredients. |
| 6 | Ingredient substitution | 3 | Groups, group membership, and typed directed substitution edges. |
| 7 | Recipes | 7 | Recipe definition, ingredient lines, steps, tags, slot suitability, nutrition snapshots. |
| 8 | Recommendations and AI provenance | 3 | Request metadata, returned shortlist, component scores. |
| 9 | Meal planning | 2 | Multi-day plans and slot entries. |
| 10 | Pantry | 2 | User inventory and quantity event history. |
| 11 | Notifications | 2 | Per-type preferences and delivery state. |
| 12 | Search support and account history | 3 | Query log, favourites, account event trail. |

A section boundary is a foreign-key dependency boundary, not a subject boundary,
because the migration has to create every referenced table before the table that
references it. That is why `user_disliked_ingredients` sits in section 5 next to
`ingredients`, `user_notification_preferences` in section 11 next to
`notification_types`, and `user_recipe_favorites` and `user_account_events` in
section 12. The [data dictionary](data-dictionary.md) groups those three
preference tables with the user's other personal data instead, because that is
where a reader looks for them.

By key shape the 53 tables divide as: **13** reference vocabularies (the 12 in
section 1 plus `ingredient_groups`), **17** whose primary key is natural rather
than surrogate — 16 composite association or child keys plus `user_profiles`
keyed by `user_id` — and **23** entity tables with a surrogate `id`. Only seven
of those 23 are independent aggregate roots that a client addresses directly:
`users`, `foods`, `ingredients`, `recipes`, `meal_plans`, `pantry_items` and
`recommendation_requests`. The rest are owned children, history rows or
provenance records reached through their parent, which is what keeps the table
count implementable incrementally by a four-person team.

## Keys and identifiers

Every entity table uses a surrogate `id BIGINT UNSIGNED NOT NULL
AUTO_INCREMENT`. Rows that users can reference from a URL or a client-held
reference additionally carry `public_id BINARY(16)`, a UUID stored as raw bytes
with a unique index. Pure association tables use a composite natural primary key
of their two foreign keys and no surrogate at all.

Why this split rather than one uniform choice:

- A monotonically increasing `BIGINT` keeps InnoDB's clustered index appending
  at the right edge. A random UUID as the clustered key scatters inserts across
  the B-tree, and every secondary index carries a 16-byte key instead of 8.
- Exposing `id` externally leaks row counts and lets a client enumerate other
  users' resources by decrementing a number. `public_id` gives the API an opaque
  handle without paying UUID cost on joins — internal foreign keys stay
  `BIGINT`.
- `public_id` exists only where it is needed: `users`, `foods`, `ingredients`,
  `recipes`, `meal_plans`, `pantry_items`, `recommendation_requests`. Adding it
  to a child row that is only ever reached through its parent would be dead
  weight.
- Association tables such as `user_roles` or `recipe_tag_assignments` have no
  identity beyond their pair. A surrogate key there would need a unique index on
  the pair anyway, so it would cost an index and buy nothing.

Reference vocabularies additionally carry a stable `code VARCHAR` with a unique
index. `code` is the value migrations, seeds, and Java enums key on;
`id` is an internal detail that must never appear in application source. This is
what makes the seed file re-runnable without touching surrogate ids that live
rows already reference.

Recipes also carry `slug`, unique, because a shareable recipe URL wants to be
readable. `slug` is a natural key for lookup, not the primary key: a title
correction should not break existing links or cascade to `recipe_ingredients`.

See [DB-ADR-001](database-decisions.md#db-adr-001--surrogate-bigint-primary-keys-with-a-public-uuid).

## Normalization

The target is third normal form for operational data, with three deliberate,
documented departures.

**What normalization buys here.** `food_nutrients` is a narrow
`(food_id, nutrient_id, amount)` table rather than 40 nutrient columns on
`foods`, so adding a nutrient is a seed row rather than a migration, and a food
with unmeasured nutrients has absent rows rather than a wall of NULLs. Same
shape for `user_nutrition_target_values` and `recipe_nutrition_values`.

Many-to-many relationships each get an explicit table with a composite key and
their own attributes where the relationship itself carries information:

| Relationship table | Extra attributes on the edge |
| --- | --- |
| `user_allergens` | `reaction_kind` (allergy vs intolerance), free-text note. |
| `user_disliked_ingredients` | `strength` (dislike vs avoid). |
| `ingredient_allergens` | `presence` (contains / may contain / free from). |
| `ingredient_foods` | `preparation_state`, `yield_factor`, `is_primary`. |
| `ingredient_substitutions` | context, exact ratio, confidence, sensory impact. |
| `user_notification_preferences` | `is_enabled`, `lead_time_days`, `preferred_time`. |
| `user_roles` | `granted_at`, `granted_by`. |

**Accepted departure 1 — generated columns.** `users.email_normalized`,
`recipes.total_minutes` and `meal_plans.day_count` are `STORED` generated
columns. They are functionally dependent on other columns in the same row, which
is technically redundant, but MySQL computes and maintains them, so they cannot
drift the way an application-maintained copy can. Each exists because something
needs to index or constrain it: a unique index on the normalized email, an index
on total cooking time for "meals under 30 minutes", and a readable day count.

**Accepted departure 2 — recipe nutrition snapshots.** Recipe nutrition is
derived from ingredient nutrition, yet `recipe_nutrition_values` stores it.
Computing 20 nutrients for a 12-ingredient recipe on every search result is not
viable, and the derivation is not stable over time: correcting a food's nutrition
facts would silently rewrite the nutrition of every historical meal. A snapshot
row records what was computed, when, against which ingredient revision, and how
complete the inputs were. See
[DB-ADR-005](database-decisions.md#db-adr-005--derived-values-are-computed-not-stored-except-as-dated-snapshots).

**Accepted departure 3 — `pantry_items.quantity_remaining`.** It could be
derived by summing `pantry_item_events.quantity_delta`. Keeping the running
value on the item makes "what is available" a single indexed read instead of an
aggregate over history, and the event trail remains the audit record. The single
writer keeps them consistent inside one transaction, and
`ck_pantry_items_quantity_remaining` bounds the value to
`[0, quantity_initial]`.

**What is deliberately not stored.** BMI is not a column: it is
`weight_kg / (height_cm/100)^2`, cheap to compute, and a stored copy goes stale
the moment either input changes. Age is not stored, only `birth_date`. Neither
is a current weight column on `user_profiles`: the current weight is the newest
`user_body_measurements` row for that user, which the unique index
`ux_user_body_measurements_user_day (user_id, measured_on)` answers with a single
backwards seek.

## JSON policy

No JSON column exists in this schema. Nutrition amounts, dietary preferences,
allergens, recipe steps, plan entries and AI score components are all queried,
filtered, joined, or referenced by foreign key, and JSON supports none of those
with integrity. A `JSON` column would be acceptable for an opaque blob that is
only ever read back whole and never used as a query predicate — for example a
future raw AI response body kept for debugging. No such need exists in P2. See
[DB-ADR-002](database-decisions.md#db-adr-002--relational-columns-not-json).

## Enumerations: lookup table or CHECK constraint

Two representations, chosen by one test: **does anything need to reference,
extend, or attach attributes to this value?**

A value becomes a **lookup table** when it is the target of a foreign key, or
carries attributes of its own, or non-developers will extend it:
`measurement_units` (conversion factor, base unit), `nutrients` (its unit),
`activity_levels` (`energy_factor`), `nutrition_goals`, `dietary_preferences`,
`allergens`, `meal_slot_types` (`typical_time`, `is_main_meal`),
`food_categories` (self-parenting hierarchy), `recipe_tags`,
`ai_score_components` (declared scale and direction), `notification_types`
(`default_enabled`, `supports_lead_time`), `roles`, `ingredient_groups`.

A value stays a **`VARCHAR` with a `CHECK` constraint plus a Java enum** when it
only constrains one column and application logic branches on it. Adding a value
means changing Java code anyway, so a table row would not avoid a deployment:
`users.account_status`, `recipes.status`, `meal_plans.status`,
`pantry_items.status`, `meal_plan_entries.provenance`,
`pantry_item_events.event_type`, `ingredient_substitutions.confidence`,
`user_account_events.event_type`, and the rest.

MySQL's native `ENUM` type is used nowhere. Its ordinal storage makes value
reordering hazardous, adding a value requires `ALTER TABLE`, and JPA maps it as a
string anyway. A `VARCHAR` + `CHECK` gives the same validation with a readable
information-schema definition. See
[DB-ADR-003](database-decisions.md#db-adr-003--lookup-tables-for-referenced-vocabularies-check-constraints-for-branch-values).

## Deletion and lifecycle semantics

### Referential actions

Three rules decide every `ON DELETE` action in the schema. All 88 foreign keys
use `ON UPDATE RESTRICT`, because a surrogate primary key never changes.

| Rule | Action | Applies to |
| --- | --- | --- |
| Child data that has no meaning without its parent | `CASCADE` | `user_profiles`, `user_auth_sessions`, `user_security_tokens`, `user_allergens`, `user_body_measurements`, `user_nutrition_targets(_values)`, `meal_plan_entries`, `pantry_item_events`, `recipe_ingredients`, `recipe_steps`, `recipe_nutrition_snapshots(_values)`, `recommendation_results(_scores)`, all `user_*` and `recipe_*` association tables. |
| Catalog rows referenced by operational or historical data | `RESTRICT` | Every foreign key into `measurement_units`, `nutrients`, `allergens`, `activity_levels`, `nutrition_goals`, `dietary_preferences`, `meal_slot_types`, `recipe_tags`, `notification_types`, `ai_score_components`, `food_categories`, plus `foods`/`ingredients`/`recipes` when referenced by a pantry item, plan entry, or recommendation result. |
| Optional provenance or attribution links | `SET NULL` | `recipes.created_by_user_id`, `meal_plans.source_request_id`, `meal_plan_entries.source_result_id`, `pantry_item_events.meal_plan_entry_id`, `notifications.pantry_item_id`, `notifications.meal_plan_id`, `user_roles.granted_by`, `user_auth_sessions.replaced_by_session_id`, `search_queries.user_id`. |

`RESTRICT` on the catalog is the load-bearing choice. If deleting a food
cascaded, removing one mistaken catalog entry would silently delete pantry
items, plan entries, and recommendation history — exactly the data the project
report depends on. Instead the delete fails, and the correct operation is
retirement.

### Retirement instead of deletion

Catalog rows are retired, not deleted:

- `foods.is_active` / `foods.retired_at` and the same pair on `ingredients`, tied
  together by `ck_foods_retirement` and `ck_ingredients_retirement` so an
  inactive row always has a retirement timestamp and an active one never does.
- `recipes.status` moves `DRAFT → PUBLISHED → ARCHIVED`, with
  `ck_recipes_archived_at` and `ck_recipes_published_at` keeping the timestamps
  consistent with the status.
- `ingredient_substitutions.is_active` withdraws a substitution edge without
  losing the record that it was once suggested.

Retired rows disappear from search because every search index leads with the
activity flag (`ix_foods_active_name`, `ix_ingredients_active_name`,
`ix_recipes_status_published_at`), while existing references stay valid.

### Account lifecycle

`account_status` runs `PENDING_VERIFICATION → ACTIVE → SUSPENDED / DEACTIVATED`.
Account closure is two-stage rather than a row delete:

1. **Deactivate** — `deactivated_at` is set, `account_status` becomes
   `DEACTIVATED`, and Java revokes every `user_auth_sessions` row. Data is
   intact; the account is reactivatable.
2. **Anonymise** — after the grace period, Java overwrites `email` with a
   non-routable placeholder, replaces `display_name`, clears
   `password_hash` to an unusable value, deletes `user_auth_sessions`,
   `user_security_tokens`, `user_profiles` and `user_body_measurements` rows,
   and stamps `anonymized_at`. `ck_users_anonymized_requires_deactivated`
   prevents anonymising an account that was never deactivated.

The `users` row itself survives, which is what makes anonymisation possible at
all: aggregate meal-plan and recommendation history stays referentially valid for
the academic evaluation while carrying no personal data. A hard `DELETE FROM
users` also works — every user-owned table cascades — and is what a full erasure
request would use.

There is no `deleted_at` soft-delete column anywhere. Soft deletion applied
uniformly forces every query in the system to remember a predicate, and forgetting
it once leaks deleted data. The states above are specific to what each table
actually needs. See
[DB-ADR-006](database-decisions.md#db-adr-006--retirement-and-anonymisation-instead-of-soft-delete-columns).

### Rows that are genuinely deleted

Expired `user_security_tokens` and `user_auth_sessions` past `expires_at` are
purged by a scheduled Java job — both have an index on `expires_at` for exactly
that sweep. Keeping consumed one-time token hashes forever has no value and
enlarges the blast radius of a database compromise.

## Time handling

Three storage types, chosen by what the value means. Mixing them is the usual
source of off-by-one-day bugs in a planner.

| Type | Meaning | Examples |
| --- | --- | --- |
| `DATETIME(6)` | An exact instant, always **UTC** | `created_at`, `updated_at`, `issued_at`, `expires_at`, `sent_at`, `requested_at`, `occurred_at`, `consumed_at` |
| `DATE` | A calendar day in the user's local reckoning, with no instant | `plan_date`, `start_date`, `end_date`, `expiry_date`, `acquired_on`, `measured_on`, `effective_from`, `birth_date` |
| `TIME` | A wall-clock time of day, no date and no zone | `meal_slot_types.typical_time`, `user_notification_preferences.preferred_time` |

Rules the application must follow:

- **`DATETIME`, not `TIMESTAMP`.** MySQL's `TIMESTAMP` converts on read and
  write using the session time zone, so the same row reads differently from two
  connections, and it caps out in 2038. `DATETIME(6)` stores what was written.
  The connection is configured with UTC, values are written as UTC, and
  conversion to local time happens in the client.
- **Microsecond precision** (`(6)`) throughout, so ordering within a request is
  not ambiguous — relevant for `pantry_item_events` and
  `user_account_events`, where two events can share a second.
- **`DATE` is never derived from a `DATETIME` in SQL.** A pantry item that
  expires "on 3 September" expires on that date in the kitchen it sits in;
  converting it to an instant requires a time zone and invents precision that
  never existed. `users.time_zone` holds an IANA zone name so Java can decide
  which local day "today" is when it filters expiry.
- **`created_at` / `updated_at`** default to `CURRENT_TIMESTAMP(6)`, and
  `updated_at` uses `ON UPDATE CURRENT_TIMESTAMP(6)`, so the database maintains
  them regardless of the write path. Immutable append-only tables
  (`pantry_item_events`, `user_account_events`, `search_queries`,
  `recipe_nutrition_snapshots`) have one timestamp and no `updated_at`, because a
  row that is never updated should not offer a column claiming otherwise.

## Naming conventions

| Object | Convention | Example |
| --- | --- | --- |
| Table | `snake_case`, plural | `pantry_items` |
| Column | `snake_case`, singular | `quantity_remaining` |
| Foreign key column | `<referenced-singular>_id`, or a role-qualified name | `recipe_id`, `original_ingredient_id` |
| Primary key | `pk_<table>` | `pk_pantry_items` |
| Foreign key | `fk_<table>_<role>` | `fk_pantry_items_ingredient` |
| Unique constraint | `ux_<table>_<meaning>` | `ux_users_email_normalized` |
| Non-unique index | `ix_<table>_<columns-or-purpose>` | `ix_pantry_items_user_expiry` |
| Full-text index | `ftx_<table>_<columns>` | `ftx_recipes_title_summary` |
| Check constraint | `ck_<table>_<rule>` | `ck_pantry_items_expiry_consistency` |
| Boolean column | `is_` / `has_` / `allow_` prefix | `is_active`, `allow_substitution` |
| Timestamp column | `_at` suffix (instant), `_on` suffix (date) | `retired_at`, `acquired_on` |
| Quantity column | includes its unit when fixed | `weight_kg`, `height_cm`, `gram_weight`, `duration_minutes` |

Constraint names are explicit everywhere rather than left to MySQL. A generated
name like `foods_chk_3` in a production error message tells nobody which rule
failed; `ck_foods_nutrition_basis` maps a constraint violation straight to a
user-facing validation message.

Unit-suffixed column names are a deliberate defence: `weight_kg` cannot be
confused for pounds by a reader, and `ck_user_body_measurements_weight`
(`> 2 AND < 700`) would reject a pound value large enough to matter.

## Domain designs

### A. Authentication and users

`users` holds one row per person: `email`, `password_hash`, `display_name`,
`account_status`, verification and lockout state, `time_zone`, `locale`, and
`version` for optimistic locking.

Security shape:

- `password_hash VARCHAR(255)` stores only the encoder's full output string
  (Argon2id or bcrypt, including algorithm id, cost parameters and salt). 255
  characters is sized for a modern Argon2id encoding. Plaintext passwords never
  reach the database, and this migration inserts no accounts.
- **JWT access tokens are never persisted.** They are short-lived bearer
  credentials; a table of live access tokens is a credential store with no
  compensating benefit, and validating them by database lookup would discard the
  reason for using JWTs.
- `user_auth_sessions` persists refresh sessions, because a refresh token must be
  revocable and rotation must detect reuse. It stores `refresh_token_hash
  CHAR(64)` — a hex SHA-256 digest, never the token. A stolen database therefore
  yields no usable refresh token. `replaced_by_session_id` records rotation
  chains so that presenting an already-rotated token identifies the whole chain
  as compromised.
- `user_security_tokens` holds email-verification and password-reset tokens, also
  hashed, single-use via `consumed_at`.
- `failed_login_count` and `locked_until` support throttling without a separate
  table.
- `email_normalized` is a `STORED` generated `LOWER(email)` column carrying the
  unique index, so `Ann@x.com` and `ann@x.com` cannot become two accounts.
  Login looks up the normalized form.

`roles` and `user_roles` implement two roles: `ROLE_USER` and `ROLE_ADMIN`.
There are no permissions, resource-scoped grants, or role hierarchies. The
product has exactly one privileged activity — curating the food and recipe
catalog — and a permission matrix built for a second privileged activity that
does not exist yet would be speculative structure. `user_roles` records
`granted_at` and `granted_by` because a privilege grant is worth attributing.
See [DB-ADR-011](database-decisions.md#db-adr-011--two-roles-not-a-permission-matrix).

### B. Profile and nutrition targets

Three separate concerns, deliberately not one wide profile table:

**`user_profiles`** (PK = `user_id`, one-to-one with `users`) holds values that
change rarely and have one current value: `birth_date`, `sex`, `height_cm`,
`activity_level_id`, `nutrition_goal_id`, `target_weight_kg`,
`weekly_change_kg`, `household_size`, `max_cook_minutes`.

**`user_body_measurements`** is the measurement history: one row per
`(user_id, measured_on)` with `weight_kg` plus optional `body_fat_percent` and
`waist_cm`. Weight is not a column on the profile. Progress tracking is a core
feature, a weight column would be destroyed on every update, and `measured_on`
being a `DATE` with a unique constraint means one measurement per day —
correcting today's reading updates the row rather than adding a duplicate.

**`user_nutrition_targets`** + `user_nutrition_target_values` are dated target
sets. The header records `effective_from`, optional `effective_to`, `origin`
(`CALCULATED` / `USER_DEFINED` / `ADJUSTED`), the `activity_level_id` and
`nutrition_goal_id` in force, and `calculation_method` (for example
`MIFFLIN_ST_JEOR`). The child rows hold per-nutrient `target_amount`,
`min_amount`, `max_amount` and `is_hard_limit`.

Why targets are versioned rather than columns on the profile: adherence over
time is only interpretable against the target that applied at the time. A user
who switches from maintenance to a deficit halfway through a month would
otherwise have last month's adherence recomputed against this month's numbers.
`ux_user_nutrition_targets_user_from` allows one target set per user per start
date; Java closes the previous set by setting its `effective_to`.

Preferences are separate tables, not columns:
`user_dietary_preferences`, `user_allergens` (with `reaction_kind`
distinguishing a medical allergy from an intolerance, which changes how strictly
the recommender must exclude), and `user_disliked_ingredients` (with `strength`
distinguishing "would rather not" from "never"). `dietary_preferences.is_exclusionary`
tells Java whether a preference is a hard filter (vegan) or a soft signal
(prefers Asian food).

### C. Foods and nutrition facts

`foods` is the nutrition catalog. Each row declares its `nutrition_basis` —
`PER_100_G` or `PER_100_ML` — and `food_nutrients` amounts are always expressed
on that basis. This is the single most important unit decision in the schema: a
nutrition amount with an unstated basis is not data. `density_g_per_ml` allows
mass/volume conversion for the specific foods where it is known and is `NULL`
otherwise, rather than assuming water density.

`food_nutrients` is `(food_id, nutrient_id)` → `amount`, with `data_quality`
recording whether the value was `ANALYTICAL`, `CALCULATED` or `ESTIMATED`. The
unit is not on this row: it comes from `nutrients.unit_id`, so protein is grams
for every food in the catalog and cannot be entered in milligrams for one row.

`food_servings` describes how people actually measure a food — "1 medium egg",
"1 cup, chopped" — as `quantity` + `unit_id` plus at least one of `gram_weight`
or `milliliters` (enforced by `ck_food_servings_measurable`). That bridge from a
household serving to the 100 g basis is what makes portion arithmetic possible.
`ux_food_servings_food_default` is a functional unique index on
`(food_id, CASE WHEN is_default THEN 1 ELSE NULL END)`, permitting at most one
default serving per food while leaving non-default rows unconstrained.

`foods.revision` increments when nutrition facts are corrected, which is what
`recipe_nutrition_snapshots.ingredient_revision` records against.

### D. Ingredients

`ingredients` is the *culinary* identity — "chicken breast", "olive oil" — while
`foods` is the *nutritional* record. They are separate because the mapping is
genuinely many-to-many in both directions: one ingredient corresponds to raw,
cooked, and canned nutrition records; one food can serve several ingredients.
Recipes, pantry items and substitutions all reason about ingredients, not foods.
See [DB-ADR-013](database-decisions.md#db-adr-013--ingredients-and-foods-are-separate-entities).

- `ingredient_foods` is the mapping edge, carrying `preparation_state`
  (`RAW` / `COOKED` / `DRIED` / `CANNED` / `FROZEN` / `UNSPECIFIED`),
  `yield_factor` (cooking mass change), and `is_primary`. A functional unique
  index allows one primary food per ingredient.
- `ingredients.default_food_id` is a denormalised shortcut to that primary food
  for the common lookup, with `RESTRICT` protecting it.
- `ingredient_aliases` gives search a home for "aubergine"/"eggplant" and
  Vietnamese names, with `alias_locale`.
- `ingredient_allergens` records `presence` per allergen — including
  `FREE_FROM`, which is a positive assertion an allergic user needs, not just an
  absent row.
- `ingredient_unit_conversions` stores conversions **per ingredient**, as an
  exact pair (`from_quantity from_unit` = `to_quantity to_unit`) with a
  `confidence` of `MEASURED` / `REFERENCE` / `ESTIMATED`.

On unit conversion honesty: mass↔mass and volume↔volume conversions are exact
and live in `measurement_units.factor_to_base_unit`. Volume↔mass is
**ingredient-specific** — a cup of flour and a cup of honey differ by more than
a factor of two — and count↔mass needs a per-ingredient piece weight
(`ingredients.piece_gram_weight`). Where no conversion row exists, the answer is
"unknown", and Java must surface that rather than guessing. The schema stores no
generic tsp→gram factor, because none exists.
See [DB-ADR-012](database-decisions.md#db-adr-012--unit-conversion-is-ingredient-specific-and-may-be-unknown).

### E. Recipes

`recipes` holds `title`, unique `slug`, `summary`, `servings`, `prep_minutes`,
`cook_minutes`, generated `total_minutes`, `difficulty`, `status`,
`created_by_user_id`, `source` and `source_reference`.

- `recipe_ingredients` is one row per ingredient line, ordered by `line_number`,
  with `quantity` + `unit_id` (both NULL together, for "salt, to taste"),
  `preparation_note`, `is_optional`, `allow_substitution`, and `section_label`
  for recipes with a sauce and a base. `food_id` may pin a specific nutrition
  record when the recipe demands one; otherwise the ingredient's default applies.
  `ux_recipe_ingredients_recipe_ingredient` prevents listing the same ingredient
  twice, which would otherwise double its nutrition contribution.
- `recipe_steps` is structured: `(recipe_id, step_number)` is the primary key,
  with `instruction` and optional `duration_minutes`. Structured steps let the
  client render a numbered walkthrough and let timing be summed. A single
  free-text blob could not do either. `instructions_note` on the recipe remains
  for a preamble.
- `recipe_tags` + `recipe_tag_assignments` carry cuisine, meal type, method and
  diet tags with `tag_kind` so the client can group filters.
  `recipe_meal_slot_types` separately marks which meal slots a recipe suits,
  which is what plan generation filters on.
- `recipe_nutrition_snapshots` + `recipe_nutrition_values` hold computed
  per-serving nutrition, with `computed_at`, `ingredient_revision`,
  `completeness_ratio` and `is_current`. `completeness_ratio` is the honest part:
  if three of twelve ingredients have no nutrition data, the total is not
  comparable to a complete one, and the ratio lets Java label it rather than
  present a confident wrong number.

Search support is covered under domain J.

### F. Ingredient substitution

Two complementary mechanisms.

`ingredient_groups` + `ingredient_group_members` express loose culinary
families ("leafy greens", "neutral cooking oils"). Membership widens candidate
search. Membership alone asserts nothing about interchangeability.

`ingredient_substitutions` is the assertion, and it is a **typed directed edge**:

| Column | Why it exists |
| --- | --- |
| `original_ingredient_id` → `substitute_ingredient_id` | Direction matters. Greek yoghurt for sour cream is fine; the reverse in a marinade is not. |
| `substitution_context` | `BAKING` / `SAUCE` / `FRYING` / `RAW` / `DAIRY_FREE` / `GLUTEN_FREE` / `VEGAN` / `GENERAL`. Butter→oil works when frying and fails in shortcrust pastry. |
| `ratio_numerator` / `ratio_denominator` | An exact rational quantity ratio, not a rounded decimal. |
| `confidence` | `HIGH` / `MODERATE` / `LOW`. |
| `preserves_allergen_profile` | Whether the swap keeps the allergen situation unchanged. |
| `sensory_impact` | `NONE` → `MAJOR`, so the client can warn. |
| `guidance` | Free text for the caveat that no enumeration captures. |
| `is_active` | Withdraw a bad edge without deleting the record. |

Safety consequences designed in: nothing in this table implies symmetry, so a
substitution is never silently reversed; `ux_ingredient_substitutions_edge` is
`(original, substitute, context)`, so the same pair can be safe in one context
and absent in another; and `preserves_allergen_profile` plus
`ingredient_allergens` mean Java re-checks the user's allergens against the
substitute before offering it — an allergen check is never delegated to a
substitution edge. See
[DB-ADR-009](database-decisions.md#db-adr-009--substitutions-are-typed-directed-edges).

### G. Meal planning

`meal_plans` is the header: `user_id`, optional `title`, `start_date`,
`end_date`, generated `day_count`, `status`, `default_servings`, optional
`meals_per_day_target`, and provenance via `source_request_id`.

`meal_plan_entries` is one row per planned item, keyed uniquely on
`(meal_plan_id, plan_date, meal_slot_type_id, position_in_slot)`.

Nothing in this design fixes the number of meals per day:

- Meal slots are rows in `meal_slot_types`, not columns. Adding a fourth snack is
  a seed row.
- A day exists because entries reference its `plan_date`, not because a
  `plan_days` table enumerates it. A day with no entries is simply unplanned.
- `position_in_slot` allows more than one item in a slot — a main plus a side.
- `meals_per_day_target` is a nullable generation hint, not a constraint on rows.
- `ck_meal_plans_window` permits 1 to 31 days, covering the 3–7 day target range
  and monthly planning without change.

`provenance` on each entry records `MANUAL`, `AI_GENERATED`, `AI_EDITED` or
`COPIED_FROM_PLAN`, and `source_result_id` links to the exact
`recommendation_results` row that produced it — `SET NULL`, so pruning old AI
history never deletes an accepted plan. That pairing is what allows the report to
measure how many AI suggestions were accepted unmodified.
`consumption_status` (`PLANNED` / `EATEN` / `SKIPPED` / `REPLACED`) with
`consumed_at` turns a plan into adherence data.
See [DB-ADR-008](database-decisions.md#db-adr-008--meal-structure-is-data-not-schema).

### H. Recommendations and AI provenance

Four tables, sized to keep what has product or academic value and nothing more.

`recommendation_requests` records that a recommendation happened: `user_id`,
`request_kind`, optional target slot and date, `status`, `correlation_id`
(matching the P1 request-id convention through to the Python call),
`constraints_hash`, `algorithm_version`, `model_identifier`, `requested_at`,
`completed_at`, `duration_ms`, `failure_reason`.

`constraints_hash CHAR(64)` is the reproducibility mechanism: a SHA-256 of the
normalised input snapshot Java sent. It proves two requests had identical inputs
without storing a copy of the user's profile, pantry and preferences on every
request — which would be both a storage problem and a privacy problem.

`recommendation_results` holds the **returned shortlist** — typically 5 to 20
rows — with `rank_position`, the recipe or food, `total_score`, `explanation`,
and `user_decision` (`PENDING` / `ACCEPTED` / `REJECTED` / `IGNORED`) with
`decided_at`. `recommendation_result_scores` breaks a result into named
components from `ai_score_components`, each with `score_value` and the `weight`
applied.

What is deliberately not stored: the full candidate set the algorithm considered,
per-iteration solver state, feature vectors, embeddings, intermediate scores. A
single meal-plan generation may evaluate thousands of candidates; persisting them
would dominate the database and answer no question worth asking.

Python's role stays exactly as ADR-003 and ADR-004 fixed it: it receives a
snapshot over HTTP, returns scores and rankings, and holds no state. Every row in
these tables is written by Java, after validating that the returned recipe ids
exist and the scores fall within each component's declared scale. An unvalidated
AI response reaches no table.
See [DB-ADR-007](database-decisions.md#db-adr-007--persist-ai-provenance-not-ai-intermediate-state).

### I. Pantry

`pantry_items` is the inventory: `user_id`, `ingredient_id`, optional pinned
`food_id`, `quantity_initial`, `quantity_remaining`, `unit_id`,
`storage_location`, `acquired_on`, `expiry_date`, `expiry_kind`,
`expiry_confidence`, `status`, `closed_at`, `version`.

**Unknown expiry is modelled explicitly.** `ck_pantry_items_expiry_consistency`
enforces a three-way equivalence: `expiry_date IS NULL` if and only if
`expiry_kind = 'UNKNOWN'` and `expiry_confidence = 'UNKNOWN'`. There is no
sentinel date. Loose vegetables bought without a label genuinely have no expiry
date, and inventing one — or defaulting to "seven days" — would produce false
urgency and, worse, false safety.

When a date is known, `expiry_kind` separates `USE_BY` (a safety limit) from
`BEST_BEFORE` (a quality limit), and `expiry_confidence` separates `LABELLED`
from `ESTIMATED`. A recommender may reasonably push a best-before item and must
not push past a use-by date, and the two are only distinguishable because the
column exists. `ingredients.typical_shelf_life_days` lets Java *offer* an
estimate at entry time — recorded as `ESTIMATED`, never as `LABELLED`.

`ix_pantry_items_user_expiry (user_id, status, expiry_date)` serves the
soon-to-expire query. A documented trap: MySQL sorts NULLs first ascending, so
an unknown expiry would appear as the most urgent item. Java must add
`expiry_date IS NOT NULL` to that query; the index still applies.

`quantity_remaining` tracks partial use, bounded to `[0, quantity_initial]`, and
`RESERVED` marks stock committed to an accepted plan but not yet cooked.
`pantry_item_events` is the append-only trail — `ADDED`, `ADJUSTED`, `CONSUMED`,
`RESERVED`, `RELEASED`, `DISCARDED`, `EXPIRED` — with `quantity_delta`,
`quantity_after`, and an optional link to the `meal_plan_entry_id` that consumed
it. Food waste measurement (`DISCARDED` and `EXPIRED` versus `CONSUMED`) is a
result the report can quote, and it exists only because the events are recorded.
See [DB-ADR-014](database-decisions.md#db-adr-014--unknown-expiry-is-represented-as-unknown).

### J. Search support

Search is served by ordinary MySQL indexes. No Elasticsearch, no vector
database, no external search service.

- Three `FULLTEXT` indexes for free-text matching:
  `ftx_foods_name_brand (display_name, brand)`,
  `ftx_ingredients_name (display_name)`,
  `ftx_recipes_title_summary (title, summary)`. InnoDB full-text supports
  `MATCH ... AGAINST` with relevance ranking and boolean mode, which covers
  "show me chicken recipes" at the catalog size this project will reach.
- Filtering and sorting use composite B-tree indexes:
  `ix_recipes_status_total_minutes` for "published, under 30 minutes",
  `ix_recipes_status_published_at` for browse-by-newest,
  `ix_recipe_tag_assignments_tag (tag_id, recipe_id)` for tag facets,
  `ix_recipe_ingredients_ingredient (ingredient_id, recipe_id)` for "recipes
  using what I have".
- `ingredient_aliases` handles synonym matching, which is the one search concern
  a plain index cannot cover.
- `search_queries` logs `search_scope`, `query_text`, `result_count`,
  `searched_at`, with `user_id` nullable and `SET NULL` on account deletion. Its
  purpose is finding zero-result queries — `ix_search_queries_scope_results`
  answers "which searches returned nothing" directly, which tells the team what
  the catalog or the alias table is missing. It is not a user-visible history
  feature and not a personalisation input in P2.

MySQL full-text has real limits — no fuzzy matching, weak stemming outside
English, no semantic similarity. Those limits are acceptable now and the
migration path is additive: a future ADR can introduce a search service fed from
MySQL, still with MySQL authoritative.
See [DB-ADR-010](database-decisions.md#db-adr-010--relational-full-text-search-not-an-external-search-engine).

### K. Notifications

`user_notification_preferences` is `(user_id, notification_type_id)` with
`is_enabled`, `lead_time_days` and `preferred_time`. An absent row means the
type's `default_enabled` applies, so registration writes nothing and a new
notification type does not require backfilling every user.

`notifications` records intent and outcome: `dedup_key`, optional links to the
triggering `pantry_item_id` or `meal_plan_id`, `title`, `body`, `status`
(`PENDING` / `SENT` / `READ` / `FAILED` / `CANCELLED`), `scheduled_for`,
`sent_at`, `read_at`, `failure_reason`.

`ux_notifications_user_dedup (user_id, dedup_key)` is the important constraint.
A daily job scanning for expiring pantry items would otherwise re-notify about
the same carton of milk every day; a `dedup_key` of the form
`PANTRY_EXPIRING:<pantry_item_id>:<expiry_date>` makes the second insert fail
harmlessly. `ix_notifications_status_scheduled_for` is the delivery worker's
query — pending rows whose time has come.

There is no queue table, retry-with-backoff schedule, delivery-attempt table or
provider-receipt table. The database records what should be sent and what
happened; transport belongs to the notification module and, later, a provider.

### L. Audit and history

History is kept where a specific question needs it, and nowhere else. There is no
generic `audit_log` table with a serialised payload, because such a table is
unqueryable in practice and becomes a dumping ground.

| Table | Question it answers |
| --- | --- |
| `user_body_measurements` | How has the user's weight moved? |
| `user_nutrition_targets` (dated) | What was the user aiming for at that time? |
| `pantry_item_events` | What happened to this item — eaten, adjusted, or wasted? |
| `meal_plan_entries.consumption_status` | Did the user actually eat the plan? |
| `recommendation_requests` / `_results` | What did the AI suggest, under which version, and was it accepted? |
| `recipe_nutrition_snapshots` | What was this recipe's nutrition when it was planned? |
| `user_account_events` | What happened to this account — logins, lockouts, password changes, role grants, deactivation, anonymisation? |
| `search_queries` | What are users looking for and not finding? |

`user_account_events` is the closest thing to a security audit log and is scoped
to 14 enumerated event types. `ip_address VARBINARY(16)` stores the packed
address form (`INET6_ATON`), which holds IPv4 and IPv6 in one column and sorts
correctly. Its retention is deliberately shorter than the account's: it exists
for incident investigation, not permanently.

Every history table is append-only with a single timestamp and no `updated_at`.
See [DB-ADR-015](database-decisions.md#db-adr-015--targeted-history-tables-not-a-generic-audit-log).

## Index strategy

175 indexes exist in total, but most are not discretionary: 53 are primary keys,
40 are unique constraints, and InnoDB requires an index on every foreign key
column. The discretionary set — the composite indexes chosen for a known query —
is what follows.

The starting position is that an index is a write cost paid on every insert and
update, plus buffer-pool space. Each one below names the query that justifies it.
Where an existing unique constraint already leads with the columns a query needs,
no second index is created: a leftmost prefix of a composite index is usable on
its own, and InnoDB can walk any index backwards, so a "descending" duplicate of
an ascending index buys nothing.

### Indexes for the required queries

**Login by normalized email.** `ux_users_email_normalized` on the generated
`email_normalized` column. Unique, so it both enforces one-account-per-mailbox
and makes login a single-row seek. Login does not query `email` directly.

**Listing a user's pantry.** The `(user_id, status)` prefix of
`ix_pantry_items_user_expiry` below. `user_id` first because it is always an
equality predicate and is the most selective column in the table; `status` second
so "available items only" filters inside the index rather than fetching discarded
rows. No dedicated two-column index exists, because that prefix is already
covered.

**Pantry expiry ordering.** `ix_pantry_items_user_expiry (user_id, status,
expiry_date)`. Equality on the first two columns and range/order on the third is
the composite ordering InnoDB can use for both the filter and the `ORDER BY`, so
the expiring-soon list needs no sort. (See the NULL-ordering trap in domain I.)

**"Do I have this ingredient?"** `ix_pantry_items_user_ingredient (user_id,
ingredient_id, status)`. This is the hot path for pantry-aware recommendation,
which asks the question once per candidate ingredient.

**Recipe search and filtering.** `ftx_recipes_title_summary` for text;
`ix_recipes_status_total_minutes (status, total_minutes)` for "published recipes
under N minutes", using the generated total; `ix_recipes_status_published_at` for
newest-first browsing; `ux_recipes_slug` for canonical URL lookup.

**Recipe ingredients, both directions.** `ux_recipe_ingredients_line (recipe_id,
line_number)` renders a recipe's lines in order from the index. The reverse
direction, "which recipes use this ingredient", is
`ix_recipe_ingredients_ingredient (ingredient_id, recipe_id)` — ingredient first
because that is the equality column, and including `recipe_id` lets the join
resolve without touching the table.

**Foods and ingredients lookup.** `ux_foods_code` and `ux_ingredients_code` for
import and seed idempotency; `ix_foods_active_name` / `ix_ingredients_active_name
(is_active, display_name)` for the browse list, which is always
active-only and always name-ordered; `ix_foods_category_active` /
`ix_ingredients_category_active` for category drill-down.

**Meal plans by user and date.** `ix_meal_plans_user_dates (user_id, start_date,
end_date)` finds the plan covering a given day; `ix_meal_plans_user_status` finds
the active plan. Within a plan, a day's entries in slot order — the query the plan
screen runs on every navigation — come from the `(meal_plan_id, plan_date,
meal_slot_type_id)` prefix of `ux_meal_plan_entries_slot`, so that read path needs
no index of its own.

**Recommendation history by user and date.**
`ix_recommendation_requests_user_requested_at (user_id, requested_at)` for the
user's history, descending on the second column.
`ix_recommendation_requests_algorithm_version (algorithm_version, requested_at)`
exists for the academic evaluation: comparing acceptance rates across algorithm
versions is a headline result of the AI course work, and without this index that
comparison is a full scan.

**Substitution traversal.** `ix_ingredient_substitutions_original
(original_ingredient_id, substitution_context, is_active)` matches exactly how
the lookup is phrased: this ingredient, in this cooking context, currently
active. `ix_ingredient_substitutions_substitute (substitute_ingredient_id,
is_active)` supports the reverse question, "what is this a substitute for",
needed when withdrawing an ingredient.

### Other non-obvious indexes

| Index | Query it serves |
| --- | --- |
| `ux_user_body_measurements_user_day` | Doubles as the access path for the latest weight per user and the progress chart range. |
| `ux_user_nutrition_targets_user_from` | Doubles as the access path for "which target set applied on date D", read backwards from the newest row. |
| `ix_user_auth_sessions_user_expiry (user_id, revoked_at, expires_at)` | A user's live sessions, and revoke-all-on-password-change. |
| `ix_user_auth_sessions_expires_at`, `ix_user_security_tokens_expires_at` | The scheduled purge of expired rows. |
| `ix_food_nutrients_nutrient_amount (nutrient_id, amount)` | "High-protein foods" — a range scan over one nutrient. |
| `ix_recipe_nutrition_values_nutrient_amount` | The same filter over recipes, per nutrient. |
| `ix_notifications_status_scheduled_for` | The delivery worker's due-now poll. |
| `ix_notifications_user_created_at` | The user's notification list, newest first. |
| `ix_pantry_item_events_item_time` | One item's full history in order. |
| `ix_pantry_item_events_type_time` | Waste analysis across all items over a period. |
| `ix_recommendation_results_decision_decided_at` | Overall acceptance rate over time. |
| `ix_recommendation_results_recipe_decision` | Which recipes get accepted when suggested. |
| `ix_recommendation_result_scores_component (score_component_id, score_value)` | Score distribution per component, for algorithm tuning. |
| `ix_user_account_events_user_time`, `_type_time` | One account's trail; all lockouts in a window. |
| `ix_search_queries_scope_results` | Zero-result queries by scope. |
| `ix_recipe_meal_slot_types_slot (meal_slot_type_id, recipe_id)` | Candidate recipes for a slot during plan generation. |
| `ix_recipe_tag_assignments_tag (tag_id, recipe_id)` | Tag facet counts and tag filtering. |
| `ix_ingredient_allergens_allergen_presence` | "All ingredients containing peanuts", for allergen filtering. |

### What is deliberately not indexed

- Free-text `note`, `guidance`, `description` and `explanation` columns. They are
  displayed, never filtered.
- `created_at` / `updated_at` on their own. Where a time query exists it is
  scoped by owner, and the composite index carries the timestamp.
- Boolean columns alone. A boolean's selectivity is at best 50%, so InnoDB will
  scan anyway; booleans appear only as leading equality columns inside composites.
- `display_order` on reference tables. They hold under 30 rows each, and MySQL
  will scan and sort them faster than it would traverse an index.
- Anything on the seed vocabularies beyond their primary and `code` unique keys.
- Any index that duplicates a leftmost prefix of one that already exists, or that
  merely reverses one. Four such indexes were written during design and removed
  once the loaded schema was introspected and every index compared against every
  other on the same table: a `(user_id, status)` index on `pantry_items` and a
  `(meal_plan_id, plan_date, meal_slot_type_id)` index on `meal_plan_entries`,
  both exact prefixes of wider indexes; a `(user_id, measured_on DESC)` index on
  `user_body_measurements` and a `(user_id, effective_from DESC, effective_to)`
  index on `user_nutrition_targets`, both shadowing a unique constraint that
  InnoDB can already read backwards. The last one is the instructive case: adding
  a third column to reach past the unique key would not have made the index
  covering, because the query needs the row's other columns regardless.

These are starting choices from expected access patterns, not measurements. Once
Java exists, `EXPLAIN` against real query plans and the slow-query log decide
what to add or drop; that revision ships as `V00x__*.sql`.

## Constraints and validation

The rule applied throughout: **the database rejects what is structurally
impossible or internally inconsistent; the application enforces policy.**

`NOT NULL` is the default. A column is nullable only where absence is a real,
distinct state — an unknown expiry date, an open-ended `effective_to`, a
never-verified email, an anonymous search — and each of those is discussed above.

125 `CHECK` constraints fall into five kinds:

**1. Enumerated values.** Every status, kind, provenance and severity column has
a `CHECK ... IN (...)` matching a Java enum. This is what keeps a typo'd status
out of the table when a future code path writes with native SQL.

**2. Structurally impossible numbers.** `quantity > 0`, `servings BETWEEN 1 AND
100`, `rank_position >= 1`, `yield_factor > 0 AND <= 10`,
`completeness_ratio BETWEEN 0 AND 1`, `scale_min < scale_max`, non-negative
nutrient amounts.

**3. Cross-column consistency.** These are the constraints that carry real
design weight, because they encode invariants that would otherwise live only in
Java:

| Constraint | Invariant |
| --- | --- |
| `ck_pantry_items_expiry_consistency` | Unknown expiry means all three expiry columns say unknown. |
| `ck_pantry_items_quantity_remaining` | `0 <= remaining <= initial`. |
| `ck_pantry_items_status_quantity` | A `CONSUMED` item has zero remaining; an `AVAILABLE` one has some. |
| `ck_meal_plan_entries_subject` | Exactly one of `recipe_id` / `food_id` is set. |
| `ck_recommendation_results_subject` | Same, for a recommended item. |
| `ck_recipe_ingredients_quantity_unit` | Quantity and unit are both present or both absent. |
| `ck_food_servings_measurable` | A serving states a gram weight or a millilitre volume. |
| `ck_measurement_units_conversion` | A derived unit has both a base unit and a factor; a base unit has neither. |
| `ck_foods_retirement`, `ck_ingredients_retirement` | `is_active = FALSE` ⟺ `retired_at IS NOT NULL`. |
| `ck_users_anonymized_requires_deactivated` | Anonymisation follows deactivation. |
| `ck_user_auth_sessions_revocation` | A revoked session has a reason. |
| `ck_meal_plans_accepted_at`, `_completed_at`, `ck_recipes_published_at`, `_archived_at`, `ck_notifications_sent_at`, `_read_at`, `_failure`, `ck_recommendation_requests_completion`, `_failure`, `ck_recommendation_results_decided_at`, `ck_meal_plan_entries_consumed_at`, `ck_pantry_items_closed_at` | A state's timestamp is present exactly when the state says it should be. |
| `ck_ingredient_substitutions_distinct`, `ck_ingredient_unit_conversions_distinct` | No self-referencing edge. |
| `ck_user_nutrition_target_values_range`, `_target_within_range`, `_present` | `min <= target <= max`, and at least one bound is given. |

**4. Date and time ordering.** `end_date >= start_date`,
`expires_at > issued_at`, `completed_at >= requested_at`,
`expiry_date >= acquired_on`, `effective_to >= effective_from`.

**5. Non-blank text.** `CHECK (CHAR_LENGTH(TRIM(col)) > 0)` on
`users.display_name`, `foods.display_name`, `ingredients.display_name`,
`recipes.title`, `recipe_steps.instruction`, `ingredient_aliases.alias`,
`search_queries.query_text`. `NOT NULL` does not exclude `''`, and a
whitespace-only recipe title is not a title.

### Health and nutrition values: bounds, not medical opinion

Health-related ranges are set to reject values that cannot be a real measurement
in the stated unit, and nothing tighter:

| Column | Range | Reasoning |
| --- | --- | --- |
| `weight_kg` | `> 2 AND < 700` | Spans documented human extremes. A pound value entered by mistake is very likely caught; a real weight never is. |
| `height_cm` | `> 30 AND < 300` | Same shape — catches metres-entered-as-centimetres. |
| `body_fat_percent` | `0 – 100` | A percentage. |
| `waist_cm` | `> 10 AND < 400` | Structural. |
| `birth_date` | `> 1900-01-01` | Rejects a mistyped year. |
| `weekly_change_kg` | `> -5 AND < 5` | A weekly *rate*; beyond this it is a data-entry error, not a plan. |
| `activity_levels.energy_factor` | `1.0 – 3.0` | Physical bounds of activity multipliers. |

Notably absent: no constraint says a calorie target must exceed some minimum, or
that a BMI must be in a healthy band, or that a deficit must not exceed a rate.
Those are clinical judgements that vary by person and belong to a qualified
professional, not to a `CHECK` constraint written by a student team. The database
is not the place to encode medical advice; nutrient targets are stored as given,
and the application layer is where any advisory warning belongs.

Nutrient amounts have no upper bound at all, only `>= 0`. Sodium in milligrams
and vitamin D in micrograms differ by orders of magnitude, and a shared ceiling
would be arbitrary for both.

## Concurrency

Seven mutable aggregate roots carry `version BIGINT UNSIGNED`: `users`,
`user_profiles`, `foods`, `ingredients`, `recipes`, `meal_plans`,
`pantry_items`. JPA's `@Version` turns a concurrent overwrite into an
`OptimisticLockException` rather than a silent last-write-wins.

`pantry_items` is the one that genuinely needs it: cooking a meal decrements
quantities while the user may be editing the same item on another device, and
losing that update means the app reports food the kitchen does not have.
Append-only and child tables have no `version`, because concurrent inserts do not
conflict.

## Migration contract

`V001__initial_schema.sql` follows Flyway's versioned naming and is treated as
**immutable once applied**. Every later change is a new `V00x__*.sql`. Forward
only; there are no `undo` scripts, because a rollback that discards user data is
not a rollback.

The file contains no `CREATE DATABASE`, `USE`, `CREATE USER` or `GRANT`, so it
carries no environment assumption and no credential. It contains no `DROP`,
`TRUNCATE` or `DELETE`, so it cannot destroy data if pointed at a populated
database. Tables are declared in foreign-key dependency order and
`FOREIGN_KEY_CHECKS` is never disabled — which is also why a clean load is a
real test of the dependency graph.

`R001__reference_data.sql` is a *repeatable* migration: re-applied when its
checksum changes, after the versioned ones, using `INSERT ... ON DUPLICATE KEY
UPDATE` keyed on each table's natural `code`. It never deletes, so removing a
line from the file does not remove the row from a database that already has it —
retiring a vocabulary value is a deliberate versioned change.

See [DB-ADR-004](database-decisions.md#db-adr-004--flyway-compatible-forward-only-migrations).

## Character set and collation

`utf8mb4` with `utf8mb4_0900_ai_ci` on every table. `utf8mb4` is the only correct
choice for Vietnamese text and emoji; MySQL's legacy `utf8` (`utf8mb3`) cannot
store a four-byte character. `utf8mb4_0900_ai_ci` is accent- and
case-insensitive, so searching "che" finds "chè" and "Pho" finds "phở" — the
behaviour a food search needs. Where exact matching matters the column is not
free text: `code` and `slug` are restricted to ASCII conventions by the
application, and hash columns are `CHAR(64)` hex.

`email` is `VARCHAR(320)` (64-character local part + `@` + 255-character
domain, the RFC maximum). Uniqueness is enforced on the generated lower-cased
copy rather than relying on collation.

## What P2 does not include

Deliberately out of scope, to be revisited with evidence:

- **Partitioning and sharding.** Wrong at this data volume.
- **Table-level encryption.** Requires key management this project does not have;
  transport security and hashed credentials are the P2 protections.
- **Materialised aggregate tables** for dashboards. Add when a query is measured
  slow, not before.
- **Database-side triggers, stored procedures and events.** Business logic stays
  in Java where it is testable and versioned with the application. The only
  database-side computation is the three generated columns and the
  `ON UPDATE CURRENT_TIMESTAMP` defaults.
- **Read replicas.** No measured read pressure.
- **A separate analytics schema.** The history tables above answer the report's
  questions directly.




