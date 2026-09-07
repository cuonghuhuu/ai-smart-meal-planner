# Data Dictionary

Table-by-table reference for the 53 tables created by
[`database/schema/V001__initial_schema.sql`](../../database/schema/V001__initial_schema.sql).
Each entry states what the table is *for*, the fields that carry meaning, the
constraints that protect it, its relationships, and who creates and removes its
rows. It is deliberately not a restatement of the DDL — for exact types,
defaults and column order, read the migration. For the reasoning behind the
design, see the [data model](data-model.md) and the
[decision records](database-decisions.md).

## Conventions that apply to every table

These hold unless an entry says otherwise, and are not repeated below.

- **Primary key.** `id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT`, constraint
  `pk_<table>`. Pure association tables use a composite natural key instead.
  Seven user-facing aggregates additionally carry `public_id BINARY(16)` — the
  UUID exposed in REST URLs
  ([DB-ADR-001](database-decisions.md#db-adr-001--surrogate-bigint-primary-keys-with-a-public-uuid)).
- **Timestamps.** `created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`;
  mutable tables add `updated_at` with `ON UPDATE CURRENT_TIMESTAMP(6)`.
  Append-only history tables carry a single event timestamp and no `updated_at`.
- **Time semantics.** `DATETIME(6)` is always an instant in **UTC**; `DATE` is a
  local calendar date; `TIME` is local wall-clock. `TIMESTAMP` is used nowhere.
- **Referential actions.** All 88 foreign keys are `ON UPDATE RESTRICT`, so only
  the `ON DELETE` action is noted per table.
- **Storage.** Every table is InnoDB, `utf8mb4` / `utf8mb4_0900_ai_ci`.
- **Ownership.** The Spring Boot backend is the only writer of every table
  ([DB-ADR-017](database-decisions.md#db-adr-017--java-is-the-only-database-client)).
  Where an entry says "written by the AI flow", that still means Java writing
  rows after validating a Python response.
- **Enumerated text.** Branch values are `VARCHAR` guarded by a `CHECK`, never
  MySQL `ENUM` and never a lookup table
  ([DB-ADR-003](database-decisions.md#db-adr-003--lookup-tables-for-referenced-vocabularies-check-constraints-for-branch-values)).
  Each entry lists the permitted values.
- **Optimistic locking.** Seven aggregates carry `version BIGINT UNSIGNED` for
  JPA `@Version`: `users`, `user_profiles`, `foods`, `ingredients`, `recipes`,
  `meal_plans`, `pantry_items`.

## 1 · Reference vocabularies

Twelve small tables holding controlled values that other tables reference by
foreign key. All are seeded by
[`R001__reference_data.sql`](../../database/seed/R001__reference_data.sql), all
are looked up by a stable `code`, and none hold user data. Java resolves them by
`code` and must never hardcode a surrogate `id`. Rows are added or corrected by
migrations or by an administrator, and are never deleted while referenced —
every inbound foreign key is `ON DELETE RESTRICT`.

### `roles`

**Purpose.** The authorization vocabulary: `ROLE_USER` and `ROLE_ADMIN`
([DB-ADR-011](database-decisions.md#db-adr-011--two-roles-not-a-permission-matrix)).

**Key fields.** `code` — used verbatim as the Spring Security authority; `name`,
`description` for display.

**Constraints.** `ux_roles_code`.

**Relationships.** Referenced by `user_roles`.

**Lifecycle.** Two seeded rows. A third role is a migration and a decision
record, not a runtime insert.

### `measurement_units`

**Purpose.** Every unit any quantity in the database can be expressed in, plus
the exact conversion to its base unit where one universally exists.

**Key fields.** `code` (`g`, `ml`, `tbsp`, `piece`, `kcal`, …); `unit_type` one
of `MASS`, `VOLUME`, `COUNT`, `ENERGY`; `base_unit_id` + `factor_to_base_unit`
(`DECIMAL(24,12)`) for derived units.

**Constraints.** `ux_measurement_units_code`;
`ck_measurement_units_unit_type`; `ck_measurement_units_conversion` — either
*both* `base_unit_id` and `factor_to_base_unit` are NULL (this is a base unit)
or both are set and the factor is positive. Cross-type conversion is
deliberately impossible here
([DB-ADR-012](database-decisions.md#db-adr-012--unit-conversion-is-ingredient-specific-and-may-be-unknown)).

**Relationships.** Self-referencing parent (`RESTRICT`). Referenced by
`nutrients`, `food_servings`, `ingredients.default_unit_id`,
`ingredient_unit_conversions` (twice), `recipe_ingredients`, `pantry_items`.

**Lifecycle.** 16 seeded rows, base units first so the self-reference resolves.
Editing a factor rewrites the meaning of stored quantities and is a migration.

### `nutrients`

**Purpose.** The nutrient vocabulary. Holds the unit **once**, so a nutrient
cannot be grams in one food and milligrams in another — the central reason
nutrition data is normalized here rather than kept as columns on `foods`.

**Key fields.** `code` (`ENERGY`, `PROTEIN`, `FAT_TOTAL`, `SODIUM`, …);
`unit_id` → `measurement_units`; `nutrient_kind` one of `ENERGY`,
`MACRONUTRIENT`, `MINERAL`, `VITAMIN`, `OTHER`; `is_core` marks the subset shown
on every screen; `display_order` fixes presentation.

**Constraints.** `ux_nutrients_code`; `ck_nutrients_kind`; `unit_id` NOT NULL —
a nutrient without a unit is meaningless.

**Relationships.** Referenced by `food_nutrients`, `recipe_nutrition_values`,
`user_nutrition_target_values`, all `ON DELETE RESTRICT`.

**Lifecycle.** 16 seeded rows (energy, macros, fibre, sugars, saturated fat,
sodium, and the vitamins/minerals the catalog will carry). Adding a nutrient is
additive and safe; changing an existing `unit_id` invalidates every stored amount
and must be paired with a data migration.

### `activity_levels`

**Purpose.** Physical activity bands with the multiplier used in a
Mifflin-St Jeor / Harris-Benedict style energy calculation.

**Key fields.** `code` (`SEDENTARY` … `VERY_ACTIVE`); `energy_factor`
`DECIMAL(4,3)`; `display_order`.

**Constraints.** `ux_activity_levels_code`; `ck_activity_levels_energy_factor`
bounds the factor to 1.000–3.000 — a structural sanity bound, not a nutritional
claim.

**Relationships.** Referenced by `user_profiles` and `user_nutrition_targets`.

**Lifecycle.** 5 seeded rows. The factor is a reference value the team may tune;
because `user_nutrition_targets` stores the *resulting* targets with an
`effective_from` date, tuning it does not rewrite history
([DB-ADR-005](database-decisions.md#db-adr-005--derived-values-are-computed-not-stored-except-as-dated-snapshots)).

### `nutrition_goals`

**Purpose.** What the user is trying to achieve: `LOSE_WEIGHT`,
`MAINTAIN_WEIGHT`, `GAIN_WEIGHT`, `GAIN_MUSCLE`, `IMPROVE_HEALTH`,
`MANAGE_CONDITION`.

**Key fields.** `code`, `display_name`, `description`, `display_order`.

**Constraints.** `ux_nutrition_goals_code`.

**Relationships.** Referenced by `user_profiles` and `user_nutrition_targets`.

**Lifecycle.** 6 seeded rows. This is a lookup table rather than a `CHECK`
because it is FK-referenced from two tables and carries display attributes.

### `dietary_preferences`

**Purpose.** Diet patterns a user follows and recipes can be filtered by:
`VEGETARIAN`, `VEGAN`, `PESCATARIAN`, `HALAL`, `GLUTEN_FREE`, and similar.

**Key fields.** `code`; `is_exclusionary` — TRUE when the preference *forbids*
ingredients (vegan) rather than merely expressing taste (`HIGH_PROTEIN`). The
recommender treats the two differently: an exclusionary preference is a hard
filter, a non-exclusionary one is a score component.

**Constraints.** `ux_dietary_preferences_code`.

**Relationships.** Referenced by `user_dietary_preferences`.

**Lifecycle.** 11 seeded rows.

### `allergens`

**Purpose.** The allergen vocabulary shared by user avoidance
(`user_allergens`) and catalog labelling (`ingredient_allergens`). One
vocabulary is what makes safety filtering possible — a free-text allergy field
could never be matched against ingredient data.

**Key fields.** `code` (the major regulated allergen groups: `PEANUT`,
`TREE_NUT`, `MILK`, `EGG`, `FISH`, `CRUSTACEAN`, `SOY`, `WHEAT_GLUTEN`,
`SESAME`, …); `display_order`.

**Constraints.** `ux_allergens_code`.

**Relationships.** Referenced by `user_allergens` and `ingredient_allergens`,
both `RESTRICT`. Deleting an allergen row while any user avoids it is refused by
the database, which is the intended behaviour for safety data.

**Lifecycle.** 14 seeded rows.

### `meal_slot_types`

**Purpose.** The meal structure vocabulary — `BREAKFAST`, `MORNING_SNACK`,
`LUNCH`, `AFTERNOON_SNACK`, `DINNER`, `SUPPER`. Being a table rather than an
enum is what allows a configurable number of meals per day
([DB-ADR-008](database-decisions.md#db-adr-008--meal-structure-is-data-not-schema)).

**Key fields.** `code`; `display_order` for sequencing a day; `typical_time`
(`TIME`, nullable) as a local wall-clock hint for notifications;
`is_main_meal` distinguishes meals from snacks for balance scoring.

**Constraints.** `ux_meal_slot_types_code`.

**Relationships.** Referenced by `meal_plan_entries`, `recipe_meal_slot_types`,
`recommendation_requests.target_meal_slot_type_id`.

**Lifecycle.** 6 seeded rows. Nothing in the schema requires a plan to use all
of them, or exactly three.

### `food_categories`

**Purpose.** A single hierarchical taxonomy for both `foods` and `ingredients`,
used for browsing and for coarse variety scoring.

**Key fields.** `code`; `parent_category_id` (nullable — NULL is a root).

**Constraints.** `ux_food_categories_code`; `fk_food_categories_parent`
self-reference `ON DELETE RESTRICT`, so a parent cannot be removed while it has
children. The schema does not prevent a cycle; Java validates that on write, and
tree reads use a recursive CTE.

**Relationships.** Self-parent. Referenced by `foods.food_category_id` and
`ingredients.food_category_id`.

**Lifecycle.** 26 seeded rows across two levels, parents inserted before
children.

### `recipe_tags`

**Purpose.** Free-standing recipe labels — cuisine, method, diet, occasion —
kept separate from `food_categories` because a recipe legitimately carries many
tags while a food sits in one category.

**Key fields.** `code`; `tag_kind` one of `CUISINE`, `MEAL_TYPE`, `METHOD`,
`DIET`, `OCCASION`, `OTHER`, which lets the UI group filter chips.

**Constraints.** `ux_recipe_tags_code`; `ck_recipe_tags_kind`.

**Relationships.** Referenced by `recipe_tag_assignments` (`RESTRICT`).

**Lifecycle.** 23 seeded rows. Admins may add tags at runtime; this is the one
vocabulary expected to grow through normal curation.

### `ai_score_components`

**Purpose.** The declared vocabulary of score dimensions the AI service may
report, with each dimension's scale and direction. Without it, a stored score of
`0.3` would be uninterpretable
([DB-ADR-007](database-decisions.md#db-adr-007--persist-ai-provenance-not-ai-intermediate-state)).

**Key fields.** `code`; `scale_min` / `scale_max` (`DECIMAL(10,4)`, default
0–1); `higher_is_better` — FALSE for penalty components such as
`DISLIKE_PENALTY`.

**Constraints.** `ux_ai_score_components_code`;
`ck_ai_score_components_scale` requires `scale_min < scale_max`.

**Relationships.** Referenced by `recommendation_result_scores` (`RESTRICT`), so
a component cannot be dropped while historical scores cite it.

**Lifecycle.** 7 seeded rows: `NUTRITION_FIT`, `PANTRY_COVERAGE`,
`EXPIRY_URGENCY`, `PREFERENCE_MATCH`, `VARIETY`, `EFFORT_FIT` (all 0–1, higher
better) and `DISLIKE_PENALTY` (0–1, lower better). A new algorithm that reports
an unknown component is rejected by Java until the vocabulary row exists — the
mechanism that stops the score table becoming an untyped dump.

### `notification_types`

**Purpose.** The catalog of things the system may notify about, and the defaults
a new user inherits.

**Key fields.** `code`; `default_enabled`; `supports_lead_time` — TRUE only for
types where "warn me N days ahead" is meaningful, which governs whether
`user_notification_preferences.lead_time_days` may be set.

**Constraints.** `ux_notification_types_code`.

**Relationships.** Referenced by `user_notification_preferences` and
`notifications`, both `RESTRICT`.

**Lifecycle.** 6 seeded rows: `PANTRY_EXPIRING`, `PANTRY_EXPIRED`,
`MEAL_PLAN_REMINDER`, `PLAN_ENDING`, `WEEKLY_SUMMARY`,
`MEASUREMENT_REMINDER`.

The thirteenth vocabulary, `ingredient_groups`, is documented with the
substitution tables it exists for, in section 6.

## 2 · Authentication and accounts

### `users`

**Purpose.** The account record and the root of every user-owned aggregate. It
holds identity, credential state and lifecycle — nothing about nutrition or
preferences, which live in `user_profiles`.

**Key fields.**

| Field | Notes |
| --- | --- |
| `public_id BINARY(16)` | The UUID in REST paths. Surrogate `id` is never exposed. |
| `email VARCHAR(320)` | As the user typed it, preserved for display and correspondence. |
| `email_normalized` | **Generated STORED** `LOWER(TRIM(email))`. This — not `email` — carries the unique index and is what login queries match, so case variants cannot create duplicate accounts. |
| `password_hash VARCHAR(255)` | Argon2id or bcrypt encoded string (algorithm, cost, salt, digest). Never a plaintext or reversible value. Width accommodates future algorithm changes. |
| `password_updated_at` | Enables "you last changed your password on…" and forced-rotation policy. |
| `account_status` | `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED`. |
| `email_verified_at` | NULL until the verification token is consumed. |
| `failed_login_count`, `locked_until` | Brute-force throttling state, on the row the check already reads. |
| `last_login_at` | Latest successful authentication. |
| `deactivated_at`, `anonymized_at` | The two stages of account closure. |
| `time_zone VARCHAR(64)`, `locale` | IANA zone (e.g. `Asia/Ho_Chi_Minh`) and BCP-47 locale. The zone is required to turn UTC instants into the user's calendar day, and to decide when "expiring tomorrow" is true for them. |

**Notably absent.** No JWT access token, no session identifier, no API key, no
security question, no recovery answer. Access tokens are stateless and verified
by signature; refresh tokens live hashed in `user_auth_sessions`.

**Constraints.** `ux_users_email_normalized`, `ux_users_public_id`;
`ck_users_account_status`; `ck_users_email_shape` (a deliberately loose
`%_@_%._%` structural test — real validation is Java's, and the database only
rejects obvious non-addresses); `ck_users_display_name_not_blank` rejects
whitespace-only names that `NOT NULL` alone would allow;
`ck_users_verification_consistency` — a verified timestamp cannot coexist with
`PENDING_VERIFICATION`; `ck_users_anonymized_requires_deactivated` — anonymised
implies deactivated, so the two-stage order cannot be skipped.

**Index.** `ix_users_status_created_at (account_status, created_at)` for
administrative listings and unverified-account cleanup.

**Relationships.** Parent of 16 tables. Owned personal data cascades
(`user_profiles`, `user_body_measurements`, `user_nutrition_targets`,
`user_allergens`, `user_dietary_preferences`, `user_disliked_ingredients`,
`user_recipe_favorites`, `user_roles`, `user_auth_sessions`,
`user_security_tokens`, `user_account_events`, `user_notification_preferences`,
`notifications`, `pantry_items`, `meal_plans`, `recommendation_requests`).
Contributions the catalog keeps use `SET NULL`
(`recipes.created_by_user_id`, `search_queries.user_id`,
`user_roles.granted_by`).

**Lifecycle.** Created by registration only — no seed row, fake or otherwise.
Closure is two-stage: `DEACTIVATED` + `deactivated_at` first (reversible, all
data intact), then anonymisation, which overwrites `email`, `display_name` and
`password_hash` with non-identifying values and sets `anonymized_at`, keeping the
row so that recipe attributions and aggregate history survive
([DB-ADR-006](database-decisions.md#db-adr-006--retirement-and-anonymisation-instead-of-soft-delete-columns)).
A genuine `DELETE` remains available for a hard erasure request and cascades all
personal data.

### `user_roles`

**Purpose.** Role grants, with a record of who granted them.

**Key fields.** Composite PK `(user_id, role_id)`; `granted_at`; `granted_by` →
`users` (nullable).

**Constraints.** Composite primary key makes a duplicate grant impossible.

**Relationships.** `user_id` `CASCADE`; `role_id` `RESTRICT`; `granted_by`
`SET NULL` so removing an administrator's account does not erase the grant.

**Lifecycle.** `ROLE_USER` is granted at registration. `ROLE_ADMIN` is granted
deliberately and mirrored into `user_account_events` as `ROLE_GRANTED`.

### `user_auth_sessions`

**Purpose.** Refresh-token state — the only reason authentication needs
persistence. It exists so that logout, token rotation and "sign out everywhere"
can actually revoke access.

**Key fields.** `refresh_token_hash CHAR(64)` — the **SHA-256 hex digest** of the
token, never the token itself, so a database disclosure does not yield usable
credentials; `issued_at`, `expires_at`, `last_used_at`, `revoked_at`;
`revocation_reason` (`USER_LOGOUT`, `ROTATED`, `PASSWORD_CHANGE`,
`ADMIN_REVOKED`, `SUSPECTED_REUSE`, `ACCOUNT_CLOSED`);
`replaced_by_session_id` self-reference forming a rotation chain — the mechanism
for detecting reuse of an already-rotated token, which is the signature of a
stolen refresh token; `client_kind` (`ANDROID`, `WEB`, `UNKNOWN`);
`ip_address VARBINARY(16)` via `INET6_ATON`; `user_agent`.

**Constraints.** `ux_user_auth_sessions_token_hash` — a hash collision or replay
across users is impossible; `ck_user_auth_sessions_expiry`
(`expires_at > issued_at`); `ck_user_auth_sessions_revocation` — `revoked_at` and
`revocation_reason` are set together or neither is;
`ck_user_auth_sessions_revocation_reason`;
`ck_user_auth_sessions_client_kind`.

**Indexes.** `ix_user_auth_sessions_user_expiry (user_id, revoked_at,
expires_at)` for "list this user's live sessions";
`ix_user_auth_sessions_expires_at` for the expired-row sweep.

**Relationships.** `user_id` `CASCADE`; `replaced_by_session_id` `SET NULL`.

**Lifecycle.** One row per successful login, one more per rotation. Rows are
retained briefly after expiry for incident investigation, then deleted by a
scheduled job — this is transient security state, not history, and is the one
table where routine deletion is the intended behaviour.

### `user_security_tokens`

**Purpose.** Single-use, out-of-band tokens: email verification and password
reset. Separate from `user_auth_sessions` because the lifecycle differs — these
are consumed once and are not renewable.

**Key fields.** `token_kind` (`EMAIL_VERIFICATION`, `PASSWORD_RESET`);
`token_hash CHAR(64)` (SHA-256 hex, as above — the plaintext exists only in the
email that was sent); `issued_at`, `expires_at`, `consumed_at`.

**Constraints.** `ux_user_security_tokens_hash`;
`ck_user_security_tokens_kind`; `ck_user_security_tokens_expiry`.

**Indexes.** `ix_user_security_tokens_user_kind (user_id, token_kind,
expires_at)` to find or invalidate a user's outstanding tokens of one kind;
`ix_user_security_tokens_expires_at` for cleanup.

**Relationships.** `user_id` `CASCADE`.

**Lifecycle.** Created on request, marked `consumed_at` on use — never reusable,
because Java requires `consumed_at IS NULL`. Deleted after expiry by the same
sweep as sessions.

### `user_account_events`

**Purpose.** The security and lifecycle trail for an account. Deliberately
bounded to 14 event types so it stays a security log rather than becoming a
generic audit table
([DB-ADR-015](database-decisions.md#db-adr-015--targeted-history-tables-not-a-generic-audit-log)).

**Key fields.** `event_type` — `REGISTERED`, `EMAIL_VERIFIED`,
`LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `LOCKED`, `UNLOCKED`, `PASSWORD_CHANGED`,
`PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`, `ROLE_GRANTED`,
`ROLE_REVOKED`, `DEACTIVATED`, `REACTIVATED`, `ANONYMIZED`; `detail` short
context (**never** a credential, token or hash); `ip_address VARBINARY(16)`;
`occurred_at`.

**Constraints.** `ck_user_account_events_type`. Append-only: no `updated_at`, and
Java never updates a row.

**Indexes.** `ix_user_account_events_user_time (user_id, occurred_at)` for the
per-account trail; `ix_user_account_events_type_time (event_type, occurred_at)`
for cross-account patterns such as a burst of `LOGIN_FAILED`.

**Relationships.** `user_id` `CASCADE`.

**Lifecycle.** Append-only, with a retention window shorter than the account's:
it exists for incident investigation, so it is pruned on a schedule rather than
kept forever. Because it holds IP addresses, anonymisation of an account also
clears the `ip_address` and `detail` of its events.

## 3 · Profile, measurements and nutrition targets

### `user_profiles`

**Purpose.** The slowly-changing personal attributes needed to compute nutrition
targets and to filter recipes. One row per user.

**Key fields.** `user_id` is the primary key *and* the foreign key — a genuine
one-to-one. `birth_date` (not age, which would need rewriting every year);
`sex` (`FEMALE`, `MALE`, `OTHER`, `PREFER_NOT_TO_SAY`, nullable — an energy
formula needs it but the user is never forced to answer); `height_cm`;
`activity_level_id`, `nutrition_goal_id`; `target_weight_kg`;
`weekly_change_kg` (signed — negative for loss); `household_size` (drives default
servings); `max_cook_minutes` (a hard recipe filter); `notes`.

**Not stored.** No `age`, no `bmi`, no `current_weight_kg`, no `bmr` — all four
are derivable and would go stale
([DB-ADR-005](database-decisions.md#db-adr-005--derived-values-are-computed-not-stored-except-as-dated-snapshots)).
Current weight comes from the newest `user_body_measurements` row.

**Constraints.** `ck_user_profiles_height` (30–300 cm),
`ck_user_profiles_target_weight` and `ck_user_profiles_weekly_change` (±5 kg
exclusive), `ck_user_profiles_cook_minutes` (1–1440),
`ck_user_profiles_household_size` (≥ 1), `ck_user_profiles_birth_date`
(after 1900-01-01), `ck_user_profiles_sex`. These bounds reject
structurally impossible values; they encode no medical judgement, and the
narrower ranges a clinician might apply are deliberately not here.

**Indexes.** `ix_user_profiles_activity_level`, `ix_user_profiles_goal` — present
to keep the FK checks cheap and to support cohort queries for the report.

**Relationships.** `user_id` `CASCADE`; lookups `RESTRICT`.

**Lifecycle.** Created at registration or first onboarding step, mutable
thereafter, and overwritten in place — because every field here is *current
state*. What must be historical (weight, targets) lives in its own table.

### `user_body_measurements`

**Purpose.** Dated measurement history. This is the table that makes weight a
time series instead of a mutable field, which the progress chart and any
goal-tracking feature depend on.

**Key fields.** `measured_on DATE` — the user's local calendar day, not an
instant, because "I weighed 70 kg on Tuesday" has no meaningful time zone;
`weight_kg DECIMAL(5,2)`; `body_fat_percent`, `waist_cm` (both optional);
`source` (`USER_ENTERED`, `IMPORTED`, `CORRECTED`); `note`.

**Constraints.** `ux_user_body_measurements_user_day (user_id, measured_on)` —
at most one measurement per user per day, so a correction replaces rather than
accumulates; `ck_user_body_measurements_weight` (2–700 kg exclusive),
`ck_user_body_measurements_body_fat` (0–100), `ck_user_body_measurements_waist`
(10–400 cm), `ck_user_body_measurements_source`.

**Index.** None beyond the unique constraint. `ux_user_body_measurements_user_day
(user_id, measured_on)` also serves the dominant query, "current weight": InnoDB
walks that index backwards for `ORDER BY measured_on DESC LIMIT 1` at the same
cost as forwards, so a second index on the same two columns would buy nothing
and cost a write on every measurement.

**Relationships.** `user_id` `CASCADE`.

**Lifecycle.** Append-only in practice; a same-day correction updates the
existing row and sets `source = 'CORRECTED'`. Deleted only with the account.

### `user_nutrition_targets`

**Purpose.** A user's nutrition targets, versioned by the period they were in
force. Storing them dated — rather than recomputing from today's profile — is
what lets a past meal plan be evaluated against the targets that actually applied
when it was generated.

**Key fields.** `effective_from DATE`; `effective_to DATE` (NULL = still in
force); `origin` (`CALCULATED`, `USER_DEFINED`, `ADJUSTED`);
`activity_level_id` and `nutrition_goal_id` — snapshots of the inputs used, so a
later profile edit does not make the record inexplicable; `calculation_method`
(e.g. `MIFFLIN_ST_JEOR_V1`) naming the formula version.

**Constraints.** `ux_user_nutrition_targets_user_from (user_id,
effective_from)`; `ck_user_nutrition_targets_period` (`effective_to >=
effective_from`); `ck_user_nutrition_targets_origin`. Non-overlap of successive
periods is enforced by Java in one transaction — MySQL has no exclusion
constraint, and the unique key plus the check make the common errors impossible.

**Index.** None beyond the unique constraint and the two lookup foreign keys.
`ux_user_nutrition_targets_user_from (user_id, effective_from)` is itself the
access path for "which target set applied on date D": the query reads this user's
rows newest-first — InnoDB walks the index backwards — and stops at the first row
whose period contains D. Extending it with `effective_to` would not make it
covering, since the row is fetched for its target values anyway.

**Relationships.** `user_id` `CASCADE`; lookups `RESTRICT`; parent of
`user_nutrition_target_values` (`CASCADE`).

**Lifecycle.** A new target set closes the previous one by setting its
`effective_to`; nothing is overwritten. Superseded rows are retained — they are
the evidence for the academic evaluation.

### `user_nutrition_target_values`

**Purpose.** The per-nutrient numbers belonging to one target set. Separate from
the parent so any nutrient in the vocabulary can be targeted without a schema
change — a fixed column per nutrient would need a migration for every addition.

**Key fields.** Composite PK `(target_id, nutrient_id)`; `target_amount` (the
aim), `min_amount` / `max_amount` (the acceptable band), all nullable;
`is_hard_limit` — TRUE means the recommender must not exceed the bound rather
than merely penalise it (sodium for a hypertensive user, for example).

**Constraints.** `ck_user_nutrition_target_values_present` — at least one of the
three amounts must be set, so an empty row cannot exist;
`ck_user_nutrition_target_values_range` (`max >= min`);
`ck_user_nutrition_target_values_target_within_range` — the target must lie
inside its own band; `ck_user_nutrition_target_values_non_negative`. Units are
never stored here: they come from `nutrients.unit_id`, so a target cannot
disagree with the food data it is compared against.

**Index.** `ix_user_nutrition_target_values_nutrient` supports the FK check and
"all users targeting nutrient X".

**Relationships.** `target_id` `CASCADE`; `nutrient_id` `RESTRICT`.

**Lifecycle.** Written with the parent target set and immutable thereafter — a
change means a new dated target set.

### `user_allergens`

**Purpose.** What a user must avoid. Safety-relevant, and the reason `allergens`
is a shared vocabulary rather than free text.

**Key fields.** Composite PK `(user_id, allergen_id)`; `reaction_kind`
(`ALLERGY`, `INTOLERANCE`, `UNSPECIFIED`) — the distinction matters because an
allergy is an absolute exclusion while an intolerance may be a strong
preference; `note` for the user's own wording.

**Constraints.** `ck_user_allergens_reaction_kind`; composite PK prevents
duplicates.

**Index.** `ix_user_allergens_allergen` for the FK and for cohort counts.

**Relationships.** `user_id` `CASCADE`; `allergen_id` `RESTRICT`.

**Lifecycle.** User-managed. Removal is a real delete — a withdrawn allergy is a
correction, not history, and keeping a stale one would filter food the user can
eat.

### `user_dietary_preferences`

**Purpose.** Which diet patterns a user follows. Many-to-many, because
"vegetarian + gluten-free" is one user with two rows.

**Key fields.** Composite PK `(user_id, dietary_preference_id)`.

**Relationships.** `user_id` `CASCADE`; `dietary_preference_id` `RESTRICT`.
Index `ix_user_dietary_preferences_preference`.

**Lifecycle.** User-managed; plain insert/delete.

### `user_disliked_ingredients`

**Purpose.** Taste-based avoidance, kept separate from allergens because the
consequence differs: a dislike lowers a score, an allergen removes a candidate.

**Key fields.** Composite PK `(user_id, ingredient_id)`; `strength` — `DISLIKE`
(penalise) or `AVOID` (exclude); `note`.

**Constraints.** `ck_user_disliked_ingredients_strength`.

**Index.** `ix_user_disliked_ingredients_ingredient`.

**Relationships.** `user_id` `CASCADE`; `ingredient_id` `CASCADE` — the one
place an ingredient reference cascades, because a dislike of a deleted ingredient
means nothing. In normal operation ingredients are retired, not deleted.

**Lifecycle.** User-managed; plain insert/delete. Feeds the
`DISLIKE_PENALTY` score component.

### `user_recipe_favorites`

**Purpose.** Explicit positive signal — a saved recipe. The cheapest useful input
to preference learning, and directly useful in the UI.

**Key fields.** Composite PK `(user_id, recipe_id)`; `created_at` gives a
chronology without a surrogate key.

**Index.** `ix_user_recipe_favorites_recipe` for "how often is this recipe
favourited".

**Relationships.** Both sides `CASCADE`.

**Lifecycle.** Insert on favourite, delete on unfavourite.

### `user_notification_preferences`

**Purpose.** Per-user, per-type notification settings. A row exists only where
the user has departed from `notification_types.default_enabled`, so the absence
of a row is itself meaningful and no backfill is needed when a new type is added.

**Key fields.** Composite PK `(user_id, notification_type_id)`; `is_enabled`;
`lead_time_days` (how far ahead to warn — only meaningful when the type's
`supports_lead_time` is TRUE); `preferred_time TIME` in the user's local
wall-clock, resolved against `users.time_zone` at scheduling time.

**Constraints.** `ck_user_notification_preferences_lead_time` (≤ 90 days).

**Index.** `ix_user_notification_preferences_type`.

**Relationships.** `user_id` `CASCADE`; `notification_type_id` `RESTRICT`.

**Lifecycle.** Created on first change, updated in place; current state only.

## 4 · Foods and nutrition facts

### `foods`

**Purpose.** A **nutritional record**: one row is one thing whose nutrition is
known on a stated basis. "Chicken breast, raw" and "chicken breast, grilled" are
two rows, because their nutrition differs. This is not the entity users pick from
— that is `ingredients`
([DB-ADR-013](database-decisions.md#db-adr-013--ingredients-and-foods-are-separate-entities)).

**Key fields.** `public_id`; `code` (nullable, unique — a stable external
identifier such as a USDA FDC id where one exists); `display_name`; `brand`
(nullable, for packaged products); `food_category_id`;
`nutrition_basis` — `PER_100_G` or `PER_100_ML`, which is what makes every row in
`food_nutrients` unambiguous; `density_g_per_ml` (nullable, only where genuinely
known, enabling one exact volume↔mass path); `source` (`CURATED`, `IMPORTED`,
`USER_SUBMITTED`) with `source_reference` for provenance; `revision` incremented
on any nutrition change — the value recipe snapshots record so a stale snapshot
is detectable; `is_active` / `retired_at`; `version`.

**Constraints.** `ux_foods_code`, `ux_foods_public_id`;
`ck_foods_nutrition_basis`; `ck_foods_source`;
`ck_foods_display_name_not_blank`; `ck_foods_density` (0 < d < 25 — a structural
bound, since no food approaches it); `ck_foods_retirement` ties `is_active` and
`retired_at` together in both directions, so a retired row always says when.

**Indexes.** `ftx_foods_name_brand` FULLTEXT on `(display_name, brand)` for text
search; `ix_foods_active_name (is_active, display_name)` for the browse list;
`ix_foods_category_active (food_category_id, is_active)` for category browsing.

**Relationships.** `food_category_id` `RESTRICT`. Parent of `food_nutrients` and
`food_servings` (both `CASCADE` — nutrition data has no meaning without its
food). Referenced with `RESTRICT` by `ingredient_foods`,
`ingredients.default_food_id`, `recipe_ingredients.food_id`,
`pantry_items.food_id`, `meal_plan_entries.food_id`,
`recommendation_results.food_id` — a food cited by any history cannot be deleted.

**Lifecycle.** Created by curation or import; never hard-deleted once referenced.
Withdrawal is retirement (`is_active = FALSE` + `retired_at`), which removes it
from search while leaving every historical reference intact. A nutrition
correction bumps `revision` rather than creating a new row.

### `food_nutrients`

**Purpose.** The normalized nutrition facts: one row per (food, nutrient) amount,
expressed on the food's `nutrition_basis` in the nutrient's own unit. A wide
table with a column per nutrient was rejected — it needs a migration for every
new nutrient and cannot express "not measured" distinctly from zero.

**Key fields.** Composite PK `(food_id, nutrient_id)` — no surrogate key, since
the pair *is* the identity, and this also makes "all nutrients for this food" a
primary-key range scan; `amount DECIMAL(12,4)`; `data_quality` — `ANALYTICAL`
(laboratory), `CALCULATED` (derived from components), `ESTIMATED` (inferred).

**Constraints.** `ck_food_nutrients_amount` (≥ 0);
`ck_food_nutrients_quality`. An absent row means "unknown", never zero — the
distinction `NOT NULL` amounts preserve and which
`recipe_nutrition_snapshots.completeness_ratio` reports.

**Index.** `ix_food_nutrients_nutrient_amount (nutrient_id, amount)` for
"high-protein foods" style filtering, which reads the opposite direction from the
primary key.

**Relationships.** `food_id` `CASCADE`; `nutrient_id` `RESTRICT`.

**Lifecycle.** Written and corrected with the parent food; a correction bumps
`foods.revision`.

### `food_servings`

**Purpose.** Household serving descriptions with their measurable equivalent —
the bridge between "1 cup, chopped" and per-100 g nutrition.

**Key fields.** `display_name` ("1 medium", "1 cup, chopped"); `quantity` +
`unit_id`; `gram_weight` and/or `milliliters` — the actual measurable equivalent;
`is_default` marks the serving shown first.

**Constraints.** `ux_food_servings_food_name (food_id, display_name)`;
`ux_food_servings_food_default` — a **functional unique index** on
`(food_id, (CASE WHEN is_default THEN 1 ELSE NULL END))`, which permits many
non-default rows but at most one default per food, without a trigger;
`ck_food_servings_quantity` (> 0); `ck_food_servings_measurable` — at least one of
`gram_weight` or `milliliters` must be present, so a serving is always convertible
to the nutrition basis; `ck_food_servings_gram_weight` and
`ck_food_servings_milliliters` (positive when present).

**Indexes.** `ix_food_servings_unit` for the FK check.

**Relationships.** `food_id` `CASCADE`; `unit_id` `RESTRICT`. Referenced by
`meal_plan_entries.food_serving_id` (`RESTRICT`).

**Lifecycle.** Curated with the food.

## 5 · Ingredients

### `ingredients`

**Purpose.** The **culinary identity** — what a recipe line, a pantry item, a
dislike and a substitution all refer to. Users only ever see ingredients; foods
are the nutrition layer behind them.

**Key fields.** `public_id`; `code` (mandatory and unique — the canonical
identity, e.g. `CHICKEN_BREAST`); `display_name`; `food_category_id`;
`default_food_id` → the primary nutrition record, a denormalised shortcut for the
`is_primary` row in `ingredient_foods`; `default_unit_id` — the unit this
ingredient is normally measured in, which the pantry UI pre-selects;
`piece_gram_weight` (nullable — "one medium onion ≈ 150 g", the count↔mass path);
`typical_shelf_life_days` (nullable — used to *offer* an expiry estimate, never to
assert one); `is_staple` marks always-available items like salt, which pantry
coverage scoring should not treat as missing; `is_active` / `retired_at`;
`version`.

**Constraints.** `ux_ingredients_code`, `ux_ingredients_public_id`;
`ck_ingredients_display_name_not_blank`; `ck_ingredients_piece_weight` (> 0 when
set); `ck_ingredients_shelf_life` (1–3650 days);
`ck_ingredients_retirement`.

**Indexes.** `ftx_ingredients_name` FULLTEXT; `ix_ingredients_active_name`;
`ix_ingredients_category_active`; `ix_ingredients_default_food` and
`ix_ingredients_default_unit` for the FK checks.

**Relationships.** Lookups `RESTRICT`. Parent (`CASCADE`) of
`ingredient_aliases`, `ingredient_allergens`, `ingredient_foods`,
`ingredient_unit_conversions`, `ingredient_group_members`,
`ingredient_substitutions` (both ends), `user_disliked_ingredients`. Referenced
`RESTRICT` by `recipe_ingredients` and `pantry_items` — an ingredient used by any
recipe or pantry row cannot be deleted.

**Lifecycle.** Curated. Retired rather than deleted; retirement hides it from
search and new entry while every recipe that uses it keeps working. An ingredient
with no `ingredient_foods` mapping is valid and simply has no nutrition.

### `ingredient_aliases`

**Purpose.** Alternative names, so search finds "coriander" when the catalog says
"cilantro" and "hành lá" when it says "spring onion". This is what makes
relational full-text search adequate without a fuzzy-matching engine
([DB-ADR-010](database-decisions.md#db-adr-010--relational-full-text-search-not-an-external-search-engine)).

**Key fields.** `alias`; `alias_locale` (nullable BCP-47 tag) so a Vietnamese
alias can be preferred for a Vietnamese-locale user.

**Constraints.** `ux_ingredient_aliases_alias` — globally unique, deliberately:
an alias that pointed at two ingredients could not disambiguate a search hit, so
the database refuses it and curation resolves the clash;
`ck_ingredient_aliases_not_blank`.

**Index.** `ix_ingredient_aliases_ingredient` for "show this ingredient's
aliases".

**Relationships.** `ingredient_id` `CASCADE`.

**Lifecycle.** Curated; append and delete freely. No `updated_at` — an alias is
replaced, not edited.

### `ingredient_foods`

**Purpose.** The many-to-many bridge from culinary identity to nutrition records,
qualified by preparation state. This is where "raw versus cooked" lives.

**Key fields.** Composite PK `(ingredient_id, food_id)`; `preparation_state`
(`RAW`, `COOKED`, `DRIED`, `CANNED`, `FROZEN`, `UNSPECIFIED`);
`yield_factor DECIMAL(6,4)` — the mass ratio after preparation (rice roughly
2.5–3, spinach well under 1), a property that exists only on this edge and has no
home in a merged table; `is_primary`.

**Constraints.** `ux_ingredient_foods_primary` — functional unique index on
`(ingredient_id, (CASE WHEN is_primary THEN 1 ELSE NULL END))`, so exactly one
mapping can be primary; `ck_ingredient_foods_state`;
`ck_ingredient_foods_yield` (0 < factor ≤ 10).

**Index.** `ix_ingredient_foods_food` for the reverse lookup "which ingredients
use this food".

**Relationships.** `ingredient_id` `CASCADE`; `food_id` `RESTRICT`.

**Lifecycle.** Curated. `ingredients.default_food_id` should agree with the
`is_primary` row; Java maintains both in one transaction.

### `ingredient_allergens`

**Purpose.** Allergen labelling at ingredient level, which is what lets a recipe
be screened against a user's allergens by joining through
`recipe_ingredients` — no per-recipe allergen data to keep in step.

**Key fields.** Composite PK `(ingredient_id, allergen_id)`; `presence` —
`CONTAINS`, `MAY_CONTAIN` (cross-contamination risk), `FREE_FROM` (an explicit
negative assertion, useful for "gluten-free oats"); `note`.

**Constraints.** `ck_ingredient_allergens_presence`.

**Index.** `ix_ingredient_allergens_allergen_presence (allergen_id, presence)` —
the safety filter's direction of travel: "every ingredient that contains peanut".

**Relationships.** `ingredient_id` `CASCADE`; `allergen_id` `RESTRICT`.

**Lifecycle.** Curated. Absence of a row means "not asserted", **not** "safe";
Java must not present unlabelled ingredients as allergen-free, and this
distinction is why `FREE_FROM` is an explicit value.

### `ingredient_unit_conversions`

**Purpose.** Ingredient-specific conversions that no universal factor can supply
— volume↔mass and count↔mass. A cup of flour and a cup of honey differ by
nearly threefold, so this data cannot live on `measurement_units`
([DB-ADR-012](database-decisions.md#db-adr-012--unit-conversion-is-ingredient-specific-and-may-be-unknown)).

**Key fields.** `from_quantity` + `from_unit_id` equals `to_quantity` +
`to_unit_id` — an exact rational pair rather than a single rounded factor, so
composed conversions do not accumulate error; `confidence` — `MEASURED` (by the
team), `REFERENCE` (from a published table), `ESTIMATED` (inferred);
`source_note`.

**Constraints.** `ux_ingredient_unit_conversions_pair (ingredient_id,
from_unit_id, to_unit_id)`; `ck_ingredient_unit_conversions_distinct` (a unit
cannot convert to itself); `ck_ingredient_unit_conversions_quantities` (both
positive); `ck_ingredient_unit_conversions_confidence`.

**Indexes.** `ix_ingredient_unit_conversions_from_unit`,
`ix_ingredient_unit_conversions_to_unit` for the FK checks.

**Relationships.** `ingredient_id` `CASCADE`; both units `RESTRICT`.

**Lifecycle.** Curated incrementally; the catalog is useful before it is
complete. Java stores one direction and inverts arithmetically. When no path
exists the conversion result is *unknown* and every caller must handle that —
never a silent fallback factor.

### `ingredient_group_members`

**Purpose.** Membership of an ingredient in a named group, giving substitution a
set-level hint without asserting pairwise ratios.

**Key fields.** Composite PK `(group_id, ingredient_id)`.

**Index.** `ix_ingredient_group_members_ingredient` for "which groups is this
ingredient in".

**Relationships.** Both sides `CASCADE`.

**Lifecycle.** Curated. Membership alone never authorises a substitution — it
only nominates candidates that a typed edge or a human then validates.

## 6 · Ingredient substitution

### `ingredient_substitutions`

**Purpose.** Typed, **directed** substitution edges. Direction matters because
substitutability is not symmetric — yoghurt for sour cream is usually fine, the
reverse often is not
([DB-ADR-009](database-decisions.md#db-adr-009--substitutions-are-typed-directed-edges)).

**Key fields.**

| Field | Notes |
| --- | --- |
| `original_ingredient_id` → `substitute_ingredient_id` | The direction of the claim: "you may use the substitute in place of the original". |
| `substitution_context` | `GENERAL`, `BAKING`, `SAUCE`, `FRYING`, `RAW`, `DAIRY_FREE`, `GLUTEN_FREE`, `VEGAN`. The same pair can be valid in one context and wrong in another, which is why context is part of the identity. |
| `ratio_numerator` / `ratio_denominator` | Exact rational amount, e.g. 3 tsp substitute per 1 tsp original. |
| `confidence` | `HIGH`, `MODERATE`, `LOW`. |
| `preserves_allergen_profile` | FALSE means the swap changes the allergen picture — the flag that stops a substitution being applied blindly to an allergy-constrained plan. |
| `sensory_impact` | `NONE`, `MINOR`, `NOTICEABLE`, `MAJOR` — an honest expectation, not a hidden one. |
| `guidance`, `source_note` | Human instruction and provenance. |
| `is_active` | Withdrawal without deletion. |

**Constraints.** `ux_ingredient_substitutions_edge (original_ingredient_id,
substitute_ingredient_id, substitution_context)` — one claim per pair per
context; `ck_ingredient_substitutions_distinct` (no self-substitution);
`ck_ingredient_substitutions_ratio` (both parts positive);
`ck_ingredient_substitutions_confidence`, `_context`, `_sensory`.

**Indexes.** `ix_ingredient_substitutions_original (original_ingredient_id,
substitution_context, is_active)` — the forward query, "what may replace this
ingredient when baking"; `ix_ingredient_substitutions_substitute
(substitute_ingredient_id, is_active)` — the reverse, "what can this pantry item
stand in for", which is how pantry-aware recommendation uses the table.

**Relationships.** Both ends `CASCADE` to `ingredients`.

**Lifecycle.** Curated, with `is_active = FALSE` for withdrawal so that a bad
claim can be retracted without losing the record that it was made. The schema
asserts no transitivity and no symmetry: an A→B and B→C edge implies nothing
about A→C, and Java must not compose them.

## 7 · Recipes

### `recipes`

**Purpose.** The recipe header: identity, timing, difficulty, provenance and
publication state.

**Key fields.** `public_id`; `title`; `slug` (unique, URL-safe);
`summary`; `servings` — the denominator every per-serving nutrition figure and
every scaling calculation depends on, hence `NOT NULL`; `prep_minutes`,
`cook_minutes`, and `total_minutes` as a **generated STORED** column
(`COALESCE(prep,0) + COALESCE(cook,0)`) so that "under 30 minutes" filters on an
index instead of an expression; `difficulty` (`EASY`, `MEDIUM`, `HARD`);
`instructions_note` for preamble that is not a step; `image_url`;
`created_by_user_id` (nullable); `source` (`CURATED`, `IMPORTED`,
`USER_CREATED`) with `source_reference`; `status` (`DRAFT`, `PUBLISHED`,
`ARCHIVED`) with `published_at` and `archived_at`; `version`.

**Constraints.** `ux_recipes_slug`, `ux_recipes_public_id`;
`ck_recipes_servings` (1–100); `ck_recipes_prep_minutes` and
`ck_recipes_cook_minutes` (≤ 10080, i.e. one week — a structural bound that still
allows long ferments); `ck_recipes_difficulty`; `ck_recipes_source`;
`ck_recipes_status`; `ck_recipes_published_at` — a non-draft recipe must have a
publication timestamp and a draft must not; `ck_recipes_archived_at` — the same
in both directions for archival. Together these make the status column and its
timestamps unable to disagree.

**Indexes.** `ftx_recipes_title_summary` FULLTEXT; `ix_recipes_status_published_at
(status, published_at)` for the published feed; `ix_recipes_status_total_minutes
(status, total_minutes)` for the quick-recipe filter; `ix_recipes_created_by_status
(created_by_user_id, status)` for "my recipes" and the FK check.

**Relationships.** `created_by_user_id` `SET NULL` — a deleted or anonymised
author does not remove the recipe from the catalog, which other users' plans
depend on. Parent (`CASCADE`) of `recipe_ingredients`, `recipe_steps`,
`recipe_tag_assignments`, `recipe_meal_slot_types`,
`recipe_nutrition_snapshots`, `user_recipe_favorites`. Referenced `RESTRICT` by
`meal_plan_entries.recipe_id` and `recommendation_results.recipe_id`.

**Lifecycle.** `DRAFT` → `PUBLISHED` → `ARCHIVED`. Archival, not deletion, is how
a recipe leaves circulation: it disappears from search while existing plans and
recommendation history stay coherent. A hard delete is possible only for a recipe
never referenced by a plan or a result.

### `recipe_ingredients`

**Purpose.** The ingredient lines of a recipe. The join table that makes
"recipes I can cook from my pantry" and allergen screening possible at all.

**Key fields.** `line_number` (presentation order); `ingredient_id` (mandatory —
culinary identity); `food_id` (nullable — pins a specific nutrition record when
the recipe genuinely means the canned or cooked variant, otherwise nutrition
resolves through `ingredients.default_food_id`); `quantity` + `unit_id` (both
nullable, together — "salt to taste" has neither); `preparation_note` ("finely
chopped"); `is_optional`; `allow_substitution` — a per-line veto, because some
lines are structural (yeast in bread) even where a general substitution edge
exists; `section_label` for grouped recipes ("For the sauce").

**Constraints.** `ux_recipe_ingredients_line (recipe_id, line_number)`;
`ux_recipe_ingredients_recipe_ingredient (recipe_id, ingredient_id)` — an
ingredient appears at most once per recipe, so pantry matching and allergen
screening cannot double-count; `ck_recipe_ingredients_quantity_unit` — quantity
and unit are both present or both absent, which is what prevents the meaningless
state "200 of something"; `ck_recipe_ingredients_quantity` (> 0 when set);
`ck_recipe_ingredients_line_number` (≥ 1).

**Indexes.** `ix_recipe_ingredients_ingredient (ingredient_id, recipe_id)` — the
reverse lookup that drives pantry-based recipe discovery;
`ix_recipe_ingredients_food`, `ix_recipe_ingredients_unit` for FK checks.

**Relationships.** `recipe_id` `CASCADE`; `ingredient_id`, `food_id`, `unit_id`
all `RESTRICT`.

**Lifecycle.** Edited with the recipe. Any change should trigger a fresh
`recipe_nutrition_snapshots` row.

### `recipe_steps`

**Purpose.** Structured preparation instructions — one row per step rather than a
single text blob, so the UI can present a step-by-step cooking mode, attach a
timer, and track progress.

**Key fields.** Composite PK `(recipe_id, step_number)` — the natural key, which
also makes reading a recipe's steps in order a primary-key range scan;
`instruction VARCHAR(2000)`; `duration_minutes` (nullable) for timers.

**Constraints.** `ck_recipe_steps_number` (≥ 1);
`ck_recipe_steps_instruction_not_blank`; `ck_recipe_steps_duration` (≤ 10080).
Contiguity of step numbers is Java's responsibility.

**Relationships.** `recipe_id` `CASCADE`.

**Lifecycle.** Rewritten with the recipe. Steps deliberately do not reference
ingredient lines — that coupling would make editing either side fragile for no
current feature.

### `recipe_tag_assignments`

**Purpose.** Many-to-many recipe tagging.

**Key fields.** Composite PK `(recipe_id, tag_id)`.

**Index.** `ix_recipe_tag_assignments_tag (tag_id, recipe_id)` — the filter
direction, "all recipes tagged Vietnamese", which the primary key cannot serve.

**Relationships.** `recipe_id` `CASCADE`; `tag_id` `RESTRICT`.

**Lifecycle.** Curated with the recipe.

### `recipe_meal_slot_types`

**Purpose.** Which meals a recipe suits. The table has no timestamps at all — it
is pure structural association, and a "when was this tagged" record would serve
nothing.

**Key fields.** Composite PK `(recipe_id, meal_slot_type_id)`.

**Index.** `ix_recipe_meal_slot_types_slot (meal_slot_type_id, recipe_id)` — the
generator's direction: "candidate recipes for breakfast".

**Relationships.** `recipe_id` `CASCADE`; `meal_slot_type_id` `RESTRICT`.

**Lifecycle.** Curated. A recipe with no rows is treated as suitable for any
slot, so this is a narrowing hint rather than a requirement.

### `recipe_nutrition_snapshots`

**Purpose.** A dated computation of a recipe's per-serving nutrition. This is one
of the three accepted departures from strict normalization: nutrition is
derivable, but recomputing it across every ingredient on every search result
would be prohibitive, and a plan generated last month should still show the
figures it was generated from
([DB-ADR-005](database-decisions.md#db-adr-005--derived-values-are-computed-not-stored-except-as-dated-snapshots)).

**Key fields.** `computed_at`; `ingredient_revision` — the maximum
`foods.revision` across the recipe's ingredients at computation time, so a stale
snapshot is *detectable* rather than merely suspected; `completeness_ratio
DECIMAL(5,4)` — the fraction of ingredient lines that resolved to nutrition data,
which is how missing data or a failed unit conversion becomes visible instead of
silently producing a low total; `computation_note`; `is_current`.

**Constraints.** `ck_recipe_nutrition_snapshots_completeness` (0–1);
`ux_recipe_nutrition_snapshots_current` — functional unique index on
`(recipe_id, (CASE WHEN is_current THEN 1 ELSE NULL END))`, so exactly one
snapshot per recipe can be current while all superseded ones remain. There is no
`updated_at`: a snapshot is immutable by definition.

**Relationships.** `recipe_id` `CASCADE`; parent of `recipe_nutrition_values`
(`CASCADE`).

**Lifecycle.** Insert-only. Recomputation inserts a new row and flips
`is_current` in one transaction. Old snapshots are retained — they are the record
of what a past plan actually claimed. Displaying nutrition means reading the
current snapshot and, if `ingredient_revision` is behind, queuing a
recomputation.

### `recipe_nutrition_values`

**Purpose.** The per-nutrient numbers of one snapshot.

**Key fields.** Composite PK `(snapshot_id, nutrient_id)`;
`amount_per_serving DECIMAL(12,4)` — per serving, not per recipe, because that is
what every consumer compares against `user_nutrition_target_values`, which is also
per-day-per-serving.

**Constraints.** `ck_recipe_nutrition_values_amount` (≥ 0).

**Index.** `ix_recipe_nutrition_values_nutrient_amount (nutrient_id,
amount_per_serving)` for "recipes over 30 g protein per serving".

**Relationships.** `snapshot_id` `CASCADE`; `nutrient_id` `RESTRICT`.

**Lifecycle.** Immutable, written with the snapshot. Units come from
`nutrients.unit_id` and are never repeated here.

## 8 · Recommendations and AI provenance

These three tables are the entire database footprint of the AI service. Python
writes none of them: it receives a request payload over REST and returns a
ranked shortlist, which Java validates and persists
([DB-ADR-007](database-decisions.md#db-adr-007--persist-ai-provenance-not-ai-intermediate-state),
P1 [ADR-003](../architecture/architecture-decisions.md)). Intermediate candidate
sets, feature vectors and rejected options are not stored.

### `recommendation_requests`

**Purpose.** One row per recommendation attempt: what was asked, which algorithm
answered, whether it succeeded, and how long it took. This is the provenance
record that makes the AI evaluation in the report possible.

**Key fields.**

| Field | Notes |
| --- | --- |
| `public_id` | Exposed so the client can poll or reference an attempt. |
| `request_kind` | `RECIPE_SUGGESTION`, `MEAL_PLAN`, `PANTRY_USE_UP`, `SUBSTITUTION`. |
| `target_meal_slot_type_id`, `target_date` | Optional scope of the request. |
| `status` | `PENDING`, `SUCCEEDED`, `FAILED`, `DEGRADED` — `DEGRADED` records that the rule-based fallback answered because the AI service was unavailable, which P1 §7.5 requires be visible. |
| `correlation_id CHAR(36)` | The trace id shared with the Python call, so a database row can be tied to application logs without duplicating the log content. |
| `constraints_hash CHAR(64)` | SHA-256 over the normalised inputs (targets, allergens, preferences, pantry state). Lets two attempts be compared for identical inputs **without storing a second copy of the user's personal data** in a payload column. |
| `algorithm_version`, `model_identifier` | Which logic produced the result — the fields that make "version B outperformed version A" a query rather than a guess. |
| `requested_at`, `completed_at`, `duration_ms` | Latency measurement. |
| `failure_reason` | Short diagnostic, never a stack trace or a payload. |

**Notably absent.** No serialised request body, no feature snapshot, no prompt.
The hash plus the typed scope columns carry what is needed.

**Constraints.** `ux_recommendation_requests_public_id`;
`ck_recommendation_requests_kind`, `_status`;
`ck_recommendation_requests_completion` — `PENDING` implies no `completed_at`
and any terminal status requires one; `ck_recommendation_requests_completion_order`
(`completed_at >= requested_at`); `ck_recommendation_requests_failure` — a
`FAILED` row must say why.

**Indexes.** `ix_recommendation_requests_user_requested_at (user_id,
requested_at)` for the user's history; `ix_recommendation_requests_status_requested_at`
for operational monitoring and stuck-`PENDING` detection;
`ix_recommendation_requests_algorithm_version (algorithm_version, requested_at)`
for the version comparison above; `ix_recommendation_requests_slot` for the FK.

**Relationships.** `user_id` `CASCADE`; `target_meal_slot_type_id` `RESTRICT`.
Parent of `recommendation_results` (`CASCADE`). Referenced by
`meal_plans.source_request_id` (`SET NULL`).

**Lifecycle.** Insert on request, single update on completion, immutable after.
Retained for the life of the account as academic evidence; a retention policy
would prune old rows without touching the plans they produced, which is exactly
why that link is `SET NULL`.

### `recommendation_results`

**Purpose.** The returned shortlist — the ranked items the user was actually
shown, and what they did about each. Storing only the returned set (not every
evaluated candidate) is the boundary that keeps this table small and meaningful.

**Key fields.** `rank_position` (1 = top); `recipe_id` **xor** `food_id`;
`total_score`; `explanation` — the human-readable reason shown in the UI, stored
because an explanation that cannot be reproduced later is not accountable;
`user_decision` (`PENDING`, `ACCEPTED`, `REJECTED`, `IGNORED`) with `decided_at`.

**Constraints.** `ux_recommendation_results_rank (request_id, rank_position)` —
no two items at the same rank; `ck_recommendation_results_subject` — exactly one
of `recipe_id` / `food_id` is set; `ck_recommendation_results_rank` (≥ 1);
`ck_recommendation_results_decision`; `ck_recommendation_results_decided_at` —
a decided row must carry its timestamp and a `PENDING` one must not.

**Indexes.** `ix_recommendation_results_recipe_decision (recipe_id,
user_decision)` — acceptance rate per recipe, the core offline evaluation metric;
`ix_recommendation_results_decision_decided_at` for period-level acceptance
rates; `ix_recommendation_results_food` for the FK.

**Relationships.** `request_id` `CASCADE`; `recipe_id`, `food_id` `RESTRICT`.
Referenced by `meal_plan_entries.source_result_id` (`SET NULL`).

**Lifecycle.** Inserted as a set when the request completes; only
`user_decision` / `decided_at` are later updated. `IGNORED` is written by Java
when the request is superseded without an explicit choice, so absence of feedback
is distinguishable from an unseen result.

### `recommendation_result_scores`

**Purpose.** The component breakdown behind `total_score` — the evidence for the
explanation, and what allows "why was this suggested?" to be answered from data.

**Key fields.** Composite PK `(result_id, score_component_id)`; `score_value`;
`weight` (nullable — the weight applied in this run, so a change in weighting is
visible in history).

**Constraints.** `ck_recommendation_result_scores_weight` (≥ 0 when set). The
`score_component_id` foreign key is the real guard: a score may only be recorded
against a component declared in `ai_score_components`, with its scale and
direction, so no uninterpretable number can be stored. `score_value` is *not*
range-checked in SQL, because the valid range differs per component and lives in
the vocabulary row; Java validates it against `scale_min`/`scale_max` on write.

**Index.** `ix_recommendation_result_scores_component (score_component_id,
score_value)` for the distribution of a single dimension across runs.

**Relationships.** `result_id` `CASCADE`; `score_component_id` `RESTRICT`.

**Lifecycle.** Immutable, written with the result. Only components the algorithm
actually reported are stored — an absent row means "not part of this algorithm's
scoring", not zero.

## 9 · Meal planning

### `meal_plans`

**Purpose.** A planning window for one user — three to seven days in normal use,
with nothing in the schema fixing that number
([DB-ADR-008](database-decisions.md#db-adr-008--meal-structure-is-data-not-schema)).

**Key fields.** `public_id`; `title` (optional user label); `start_date` /
`end_date` as `DATE`, because a plan is a set of calendar days rather than an
interval of instants; `day_count` as a **generated STORED** column
(`DATEDIFF(end_date, start_date) + 1`), so plan length is filterable and sortable
without recomputation; `meals_per_day_target` (nullable hint, not a rule — the
actual structure is whatever entries exist); `default_servings`;
`status` (`DRAFT`, `ACCEPTED`, `ACTIVE`, `COMPLETED`, `ABANDONED`);
`source_request_id` — the AI attempt that generated it, or NULL if hand-built;
`accepted_at`, `completed_at`, `archived_at`; `version`.

**Constraints.** `ux_meal_plans_public_id`; `ck_meal_plans_dates` (`end_date >=
start_date`); `ck_meal_plans_window` (`TO_DAYS(end) - TO_DAYS(start) <= 30` — a
structural sanity bound, well above the 3–7 day product range);
`ck_meal_plans_default_servings` (1–50); `ck_meal_plans_meals_per_day` (1–12 when
set); `ck_meal_plans_status`; `ck_meal_plans_accepted_at` — anything past `DRAFT`
must record when it was accepted; `ck_meal_plans_completed_at` — `completed_at`
is set exactly when the status is `COMPLETED`.

**Indexes.** `ix_meal_plans_user_dates (user_id, start_date, end_date)` — "the
plan covering today", the app's most frequent read;
`ix_meal_plans_user_status (user_id, status)` for the plan list;
`ix_meal_plans_source_request` for the FK.

**Relationships.** `user_id` `CASCADE`; `source_request_id` `SET NULL` so
pruning recommendation history never deletes a user's accepted plan. Parent of
`meal_plan_entries` (`CASCADE`). Referenced by `notifications.meal_plan_id`
(`SET NULL`).

**Lifecycle.** `DRAFT` (generated or started) → `ACCEPTED` → `ACTIVE` →
`COMPLETED`, or `ABANDONED`. Accepted and completed plans are the meal-plan
history requirement L asks for and are not deleted; `archived_at` moves an old
plan out of the default list.

### `meal_plan_entries`

**Purpose.** One planned item: a recipe or a food, in one meal slot, on one day,
with a serving count. The table that carries the entire flexible meal structure.

**Key fields.** `plan_date DATE` (denormalised from the parent's range for direct
indexing — validated by Java to lie inside it, as MySQL `CHECK` cannot reference
another table); `meal_slot_type_id`; `position_in_slot` — allows several items in
one slot, which is how a two-dish dinner is represented; `recipe_id` **xor**
`food_id`; `servings DECIMAL(6,2)` (fractional servings are real);
`food_serving_id` — the household serving chosen when the entry is a food;
`provenance` (`MANUAL`, `AI_GENERATED`, `AI_EDITED`, `COPIED_FROM_PLAN`) —
requirement G's generated-versus-manual distinction, with `AI_EDITED` recording
that the user adjusted a generated entry, which is the most informative signal for
evaluation; `source_result_id` → the specific `recommendation_results` row;
`consumption_status` (`PLANNED`, `EATEN`, `SKIPPED`, `REPLACED`) with
`consumed_at`; `note`.

**Constraints.** `ux_meal_plan_entries_slot (meal_plan_id, plan_date,
meal_slot_type_id, position_in_slot)` — no two items in the same position of the
same slot on the same day; `ck_meal_plan_entries_subject` — exactly one of
`recipe_id` / `food_id`; `ck_meal_plan_entries_food_serving` — a
`food_serving_id` requires a `food_id`; `ck_meal_plan_entries_servings` (0 <
servings ≤ 50); `ck_meal_plan_entries_position` (≥ 1);
`ck_meal_plan_entries_provenance`; `ck_meal_plan_entries_consumption`;
`ck_meal_plan_entries_consumed_at` — `consumed_at` is present exactly when the
status is `EATEN`, so "eaten" can never be undated.

**Indexes.** No separate index for "render a plan day by day": the unique
constraint `ux_meal_plan_entries_slot` leads with exactly `(meal_plan_id,
plan_date, meal_slot_type_id)` and already answers it.
`ix_meal_plan_entries_recipe_date (recipe_id, plan_date)` serves variety checks
("has this recipe appeared recently"); `ix_meal_plan_entries_slot_type`, `_food`,
`_food_serving`, `_source_result` exist for FK checks and provenance lookups.

**Relationships.** `meal_plan_id` `CASCADE`; `recipe_id`, `food_id`,
`food_serving_id`, `meal_slot_type_id` `RESTRICT`; `source_result_id` `SET NULL`.
Referenced by `pantry_item_events.meal_plan_entry_id` (`SET NULL`), which is how
a pantry deduction is traced to the meal that caused it.

**Lifecycle.** Created with the plan or added manually; `consumption_status`
updated as the week proceeds. Deleted freely while the plan is a `DRAFT`; once
accepted, removal should be a `REPLACED` or `SKIPPED` status so the history stays
truthful.

## 10 · Pantry

### `pantry_items`

**Purpose.** One physical lot of an ingredient a user has. Lot-level rather than
aggregate-level, because two purchases of the same ingredient have different
expiry dates, and merging them would destroy the information the
expiring-soon feature depends on.

**Key fields.**

| Field | Notes |
| --- | --- |
| `public_id` | Exposed in the pantry API. |
| `ingredient_id` | Mandatory — what the user thinks they have. |
| `food_id` | Optional — pins a nutrition record when the specific product is known. |
| `quantity_initial` | What was added. Immutable. |
| `quantity_remaining` | What is left. The second accepted denormalisation: derivable by summing `pantry_item_events`, but every pantry read and every recommendation needs it, so it is maintained transactionally alongside the event that changes it ([DB-ADR-005](database-decisions.md#db-adr-005--derived-values-are-computed-not-stored-except-as-dated-snapshots)). |
| `unit_id` | The unit both quantities are in. |
| `storage_location` | `PANTRY`, `FRIDGE`, `FREEZER`, `OTHER` — affects realistic shelf life and helps the user find the item. |
| `acquired_on DATE` | Optional purchase/add date. |
| `expiry_date DATE` | Nullable — see below. |
| `expiry_kind` | `USE_BY` (a safety deadline), `BEST_BEFORE` (quality), `UNKNOWN`. |
| `expiry_confidence` | `LABELLED` (read off the package), `ESTIMATED` (derived from typical shelf life), `UNKNOWN`. |
| `status` | `AVAILABLE`, `RESERVED`, `CONSUMED`, `DISCARDED`, `EXPIRED`. |
| `closed_at` | When the lot left circulation. |
| `version` | Optimistic locking — concurrent deduction from the same lot must not lose an update. |

**Expiry honesty.** `ck_pantry_items_expiry_consistency` enforces a three-way
biconditional: `expiry_date IS NULL` **iff** `expiry_kind = 'UNKNOWN'` **and**
`expiry_confidence = 'UNKNOWN'`. There is no sentinel date and no fabricated
default, so the system never claims to know a deadline it does not know
([DB-ADR-014](database-decisions.md#db-adr-014--unknown-expiry-is-represented-as-unknown)).

**Other constraints.** `ux_pantry_items_public_id`;
`ck_pantry_items_quantity_initial` (> 0);
`ck_pantry_items_quantity_remaining` (0 ≤ remaining ≤ initial — bounding the
denormalised value in the database, not only in Java);
`ck_pantry_items_expiry_after_acquired`;
`ck_pantry_items_expiry_kind`, `_expiry_confidence`, `_storage_location`,
`_status`; `ck_pantry_items_status_quantity` — `CONSUMED` requires remaining = 0
and `AVAILABLE` requires remaining > 0, so the status cannot contradict the
quantity; `ck_pantry_items_closed_at` — open statuses have no `closed_at`, closed
ones must.

**Indexes.** `ix_pantry_items_user_expiry (user_id, status, expiry_date)` — the
expiring-soon query, and, through its `(user_id, status)` prefix, the plain
pantry list as well, so no separate index exists for the latter. **Trap:** MySQL
sorts NULLs first ascending, so items with unknown expiry would appear as the
most urgent; every such query must include `expiry_date IS NOT NULL`.
`ix_pantry_items_user_ingredient (user_id, ingredient_id, status)` serves "do I
have this ingredient", the lookup recipe matching performs per line;
`ix_pantry_items_ingredient`, `_food`, `_unit` exist for FK checks and reverse
lookups.

**Relationships.** `user_id` `CASCADE`; `ingredient_id`, `food_id`, `unit_id`
`RESTRICT`. Parent of `pantry_item_events` (`CASCADE`). Referenced by
`notifications.pantry_item_id` (`SET NULL`), so removing a lot does not delete
the notification that mentioned it.

**Lifecycle.** Added by the user, decremented as it is used, then closed as
`CONSUMED`, `DISCARDED` or `EXPIRED` with `closed_at`. Closed lots are retained —
they are the basis for waste measurement — and a scheduled job may prune very old
closed lots. `RESERVED` marks stock allocated to an accepted plan but not yet
cooked, which is what stops the same lot being promised to two meals.

### `pantry_item_events`

**Purpose.** The append-only ledger of every quantity change, which is what makes
`quantity_remaining` auditable rather than merely asserted, and what lets the
report quantify food waste.

**Key fields.** `event_type` — `ADDED`, `ADJUSTED`, `CONSUMED`, `RESERVED`,
`RELEASED`, `DISCARDED`, `EXPIRED`; `quantity_delta` (signed — negative for
consumption); `quantity_after` (the resulting balance, stored so the ledger can be
verified independently of replaying every prior row);
`meal_plan_entry_id` (nullable) — the meal that caused the deduction, the link
that turns "2 eggs disappeared" into "used by Tuesday's breakfast"; `note`;
`occurred_at`.

**Constraints.** `ck_pantry_item_events_type`;
`ck_pantry_item_events_quantity_after` (≥ 0). No `updated_at` and no update path:
a mistake is corrected by an `ADJUSTED` row, never by editing history.

**Indexes.** `ix_pantry_item_events_item_time (pantry_item_id, occurred_at)` for
one lot's history; `ix_pantry_item_events_type_time (event_type, occurred_at)` for
the waste report — `SUM` over `DISCARDED` and `EXPIRED` in a period;
`ix_pantry_item_events_plan_entry` for the provenance link.

**Relationships.** `pantry_item_id` `CASCADE`; `meal_plan_entry_id` `SET NULL`.

**Lifecycle.** Insert-only, always in the same transaction as the
`pantry_items.quantity_remaining` update that it explains.

## 11 · Notifications

### `notifications`

**Purpose.** The minimum durable state a notification feature needs: what was
generated, when it should go out, and what happened to it. Not a message queue —
delivery is Spring's scheduler reading this table
([DB-ADR-015](database-decisions.md#db-adr-015--targeted-history-tables-not-a-generic-audit-log)).

**Key fields.** `notification_type_id`; `dedup_key VARCHAR(190)` — a deterministic
key such as `PANTRY_EXPIRING:item:123:2026-09-02`, which is the mechanism that
stops a daily scheduler telling the user about the same milk four times;
`pantry_item_id` and `meal_plan_id` (both nullable) as typed subject references
instead of a generic `entity_type`/`entity_id` pair, so referential integrity
holds; `title`, `body` — rendered at generation time so the message the user saw
is the message that is stored; `status` (`PENDING`, `SENT`, `READ`, `FAILED`,
`CANCELLED`); `scheduled_for` (UTC instant, computed from the user's
`preferred_time` and `time_zone`); `sent_at`, `read_at`, `failure_reason`.

**Constraints.** `ux_notifications_user_dedup (user_id, dedup_key)` — the
idempotency guarantee; `ck_notifications_status`;
`ck_notifications_sent_at` — `SENT` and `READ` require `sent_at`, other statuses
forbid it; `ck_notifications_read_at`; `ck_notifications_failure` — a `FAILED` row
must say why. `dedup_key` is 190 characters so that the composite unique index
stays inside InnoDB's key-length limit under `utf8mb4`.

**Indexes.** `ix_notifications_status_scheduled_for (status, scheduled_for)` —
the dispatcher's only query, "pending and due"; `ix_notifications_user_created_at
(user_id, created_at)` for the in-app list; `ix_notifications_type`,
`_pantry_item`, `_meal_plan` for FK checks.

**Relationships.** `user_id` `CASCADE`; `notification_type_id` `RESTRICT`;
`pantry_item_id`, `meal_plan_id` `SET NULL` — a delivered notification survives
the deletion of its subject, since the user already read it.

**Lifecycle.** Generated by scheduled jobs, dispatched, then read. There is no
`updated_at` — every state change is one of the four typed timestamps. Old rows
are pruned on a retention schedule; nothing downstream depends on them.

## 12 · Search support

### `search_queries`

**Purpose.** What users search for and how often they find nothing. It exists to
answer one question with data: is relational full-text search good enough
([DB-ADR-010](database-decisions.md#db-adr-010--relational-full-text-search-not-an-external-search-engine))?
Zero-result queries are the evidence for a missing alias, a missing catalog entry,
or eventually for a different search technology.

**Key fields.** `user_id` (nullable — anonymous searches count too);
`search_scope` (`RECIPE`, `FOOD`, `INGREDIENT`, `ALL`); `query_text`;
`result_count`; `searched_at`.

**Notably not.** This is not a search index and stores no denormalised searchable
text. The searchable text lives on `foods`, `ingredients` and `recipes` with their
`FULLTEXT` indexes.

**Constraints.** `ck_search_queries_scope`;
`ck_search_queries_text_not_blank`.

**Indexes.** `ix_search_queries_scope_results (search_scope, result_count,
searched_at)` — the zero-result report, which leads with `result_count = 0`;
`ix_search_queries_user_time (user_id, searched_at)` for recent-searches in the
UI.

**Relationships.** `user_id` `SET NULL` — the aggregate remains useful after an
account closes, and the row becomes anonymous rather than being deleted.

**Lifecycle.** Append-only, no `updated_at`. Pruned on a retention schedule;
`query_text` is free user input, so it is treated as personal data and is included
in the pruning policy.

## Table index

All 53 tables, in the order this document presents them. The section numbers are
this document's own grouping, which follows the migration's twelve sections with
one deliberate difference: the three user-preference tables the migration places
beside the catalog it references (`user_disliked_ingredients`,
`user_recipe_favorites`, `user_notification_preferences`) are documented in
section 3 with the rest of the user's personal data, because that is where a
reader looks for them. The migration orders by foreign-key dependency; this
document orders by subject.

| # | Table | Doc § | Kind |
| --- | --- | --- | --- |
| 1 | `roles` | 1 | vocabulary |
| 2 | `measurement_units` | 1 | vocabulary |
| 3 | `nutrients` | 1 | vocabulary |
| 4 | `activity_levels` | 1 | vocabulary |
| 5 | `nutrition_goals` | 1 | vocabulary |
| 6 | `dietary_preferences` | 1 | vocabulary |
| 7 | `allergens` | 1 | vocabulary |
| 8 | `meal_slot_types` | 1 | vocabulary |
| 9 | `food_categories` | 1 | vocabulary |
| 10 | `recipe_tags` | 1 | vocabulary |
| 11 | `ai_score_components` | 1 | vocabulary |
| 12 | `notification_types` | 1 | vocabulary |
| 13 | `users` | 2 | aggregate root |
| 14 | `user_roles` | 2 | association |
| 15 | `user_auth_sessions` | 2 | security state |
| 16 | `user_security_tokens` | 2 | security state |
| 17 | `user_account_events` | 2 | history |
| 18 | `user_profiles` | 3 | child (1:1) |
| 19 | `user_body_measurements` | 3 | history |
| 20 | `user_nutrition_targets` | 3 | history |
| 21 | `user_nutrition_target_values` | 3 | child |
| 22 | `user_allergens` | 3 | association |
| 23 | `user_dietary_preferences` | 3 | association |
| 24 | `user_disliked_ingredients` | 3 | association |
| 25 | `user_recipe_favorites` | 3 | association |
| 26 | `user_notification_preferences` | 3 | association |
| 27 | `foods` | 4 | catalog |
| 28 | `food_nutrients` | 4 | child |
| 29 | `food_servings` | 4 | child |
| 30 | `ingredients` | 5 | catalog |
| 31 | `ingredient_aliases` | 5 | child |
| 32 | `ingredient_foods` | 5 | association |
| 33 | `ingredient_allergens` | 5 | association |
| 34 | `ingredient_unit_conversions` | 5 | child |
| 35 | `ingredient_group_members` | 5 | association |
| 36 | `ingredient_groups` | 6 | vocabulary |
| 37 | `ingredient_substitutions` | 6 | typed edge |
| 38 | `recipes` | 7 | catalog |
| 39 | `recipe_ingredients` | 7 | child |
| 40 | `recipe_steps` | 7 | child |
| 41 | `recipe_tag_assignments` | 7 | association |
| 42 | `recipe_meal_slot_types` | 7 | association |
| 43 | `recipe_nutrition_snapshots` | 7 | snapshot |
| 44 | `recipe_nutrition_values` | 7 | child |
| 45 | `recommendation_requests` | 8 | provenance |
| 46 | `recommendation_results` | 8 | provenance |
| 47 | `recommendation_result_scores` | 8 | provenance |
| 48 | `meal_plans` | 9 | aggregate root |
| 49 | `meal_plan_entries` | 9 | child |
| 50 | `pantry_items` | 10 | aggregate root |
| 51 | `pantry_item_events` | 10 | history |
| 52 | `notifications` | 11 | delivery state |
| 53 | `search_queries` | 12 | history |

By key shape: **13** reference vocabularies, **17** tables whose primary key is
natural rather than surrogate (16 composite association or child keys, plus
`user_profiles` keyed by `user_id`), and **23** entity tables with a surrogate
`id`. Counted directly, `information_schema.tables` reports 53 tables for the
schema; see the [validation report](validation-report.md).

