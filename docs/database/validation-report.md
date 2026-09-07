# P2 Validation Report

What was actually executed against a real MySQL server, what was checked by
tooling, and what could only be reviewed by reading. Everything below is a
recorded result, not an expectation.

Run on 2026-08-30 against branch `docs/database-design`.

## 1 · How MySQL validation was done without touching a server

The requirement was to validate against MySQL 8.x **without installing or
modifying a global database server**. The approach was a throwaway instance:

| Aspect | Value |
| --- | --- |
| Binary | The already-installed `mysqld.exe` from MySQL Server 8.4, invoked directly |
| Data directory | `D:\tmp\p2-mysql\data`, created with `--no-defaults --initialize-insecure`, deleted afterwards |
| Port | 13306, so it cannot collide with 3306 |
| Flags | `--no-defaults --mysqlx=OFF --shared-memory=OFF` |
| Scratch schema | `p2check`, created and dropped by the check itself |
| Windows service | `MySQL84` was Stopped/Disabled before and after; never started, never reconfigured |
| Other servers | A pre-existing local XAMPP MySQL instance was left running and untouched throughout |

Nothing outside `D:\tmp` was written. No global configuration file, service
definition, user, or grant was created or altered. The temporary datadir was
removed after the run, so the check leaves no trace.

```text
mysql> SELECT VERSION(), @@version_comment;
8.4.11    MySQL Community Server - GPL
```

## 2 · Migration load

The full sequence, from an empty server, each step's exit code recorded:

| Step | Command | Exit |
| --- | --- | --- |
| 1 | `DROP DATABASE IF EXISTS p2check; CREATE DATABASE p2check ... utf8mb4_0900_ai_ci` | 0 |
| 2 | `source database/schema/V001__initial_schema.sql` | 0 |
| 3 | `source database/seed/R001__reference_data.sql` | 0 |
| 4 | `source database/seed/R001__reference_data.sql` (again, unchanged) | 0 |

The schema loads in one pass with no reordering. That is the real test of
foreign-key dependency order: MySQL creates a `FOREIGN KEY` only if the
referenced table already exists, so a single successful `source` proves all 88
references resolve in file order without forward declarations or a deferred
constraint pass.

One warning is emitted, three times:

```text
Warning (Code 124): InnoDB rebuilding table to add column FTS_DOC_ID
```

This is benign and expected. Adding a `FULLTEXT` index to an InnoDB table
requires a hidden `FTS_DOC_ID` column, and InnoDB rebuilds the table to add it.
It fires once for each of the three full-text indexes (`foods`, `ingredients`,
`recipes`). No other warnings, notes, or errors were produced.

This sequence was run four times over the course of the work — after each schema
correction — with identical exit codes, identical warnings, and identical object
counts each time.

## 3 · Object counts, read back from `information_schema`

Counted after the load above, not asserted from the source file:

| Object | Count |
| --- | --- |
| Base tables | 53 |
| Primary keys | 53 |
| Foreign keys | 88 |
| `CHECK` constraints | 125 |
| `UNIQUE` constraints | 40 |
| Indexes (all kinds, including primary) | 175 |
| `FULLTEXT` indexes | 3 |
| Generated (`STORED`) columns | 3 |
| Columns, in total | 464 |
| Views | 0 |
| Stored procedures and functions | 0 |
| Triggers | 0 |
| Scheduled events | 0 |

The four zeros are a deliberate architectural result, not an omission: no
business logic lives in the database, because Java owns it
([DB-ADR-017](database-decisions.md#db-adr-017--java-is-the-only-database-client)).

Storage and encoding, also read back:

| Check | Result |
| --- | --- |
| Tables not using InnoDB | 0 |
| Tables not using `utf8mb4_0900_ai_ci` | 0 |
| Columns of type `TIMESTAMP` | 0 — every instant is `DATETIME(6)` in UTC |
| Foreign keys whose `UPDATE_RULE` is not `RESTRICT` | 0 |

## 4 · Referential actions

All 88 foreign keys were listed with their `DELETE_RULE`:

| `ON DELETE` | Count | Where it is used |
| --- | --- | --- |
| `RESTRICT` | 40 | Every reference from user or history data into the shared catalog and the vocabularies. A food cannot vanish out from under a meal plan that recorded it. |
| `CASCADE` | 39 | Owned children only: rows that have no meaning without their parent (`recipe_ingredients` under `recipes`, `pantry_item_events` under `pantry_items`, everything keyed by `user_id`). |
| `SET NULL` | 9 | Optional provenance and authorship links, where losing the link must not lose the row. |

The nine `SET NULL` edges, each verified nullable:

`meal_plan_entries.source_result_id`, `meal_plans.source_request_id`,
`notifications.meal_plan_id`, `notifications.pantry_item_id`,
`pantry_item_events.meal_plan_entry_id`, `recipes.created_by_user_id`,
`search_queries.user_id`, `user_auth_sessions.replaced_by_session_id`,
`user_roles.granted_by`.

Every `SET NULL` column is declared `NULL` — a `SET NULL` action on a `NOT NULL`
column is a runtime failure MySQL does not catch at `CREATE TABLE` time, so this
was checked explicitly rather than assumed.

All three actions were then observed on throwaway rows rather than left as
declarations; see [section 13](#13--behaviour-probe-on-throwaway-rows).

## 5 · Seed idempotency

`R001__reference_data.sql` was run twice against the same schema. For a
repeatable seed to be safe, the second run must change nothing — including not
consuming auto-increment values, since a drifting surrogate id would break any
later data that referenced it.

The test is `COUNT(*) = MAX(id)` after two runs. If the second run had inserted
duplicates, the count would exceed the intended row count; if it had inserted and
rolled back, `MAX(id)` would exceed the count.

| Vocabulary | Rows | `MAX(id)` |
| --- | --- | --- |
| `roles` | 2 | 2 |
| `measurement_units` | 16 | 16 |
| `nutrients` | 16 | 16 |
| `activity_levels` | 5 | 5 |
| `nutrition_goals` | 6 | 6 |
| `dietary_preferences` | 11 | 11 |
| `allergens` | 14 | 14 |
| `meal_slot_types` | 6 | 6 |
| `food_categories` | 26 | 26 |
| `recipe_tags` | 23 | 23 |
| `ai_score_components` | 7 | 7 |
| `notification_types` | 6 | 6 |
| `ingredient_groups` | 8 | 8 |

**146 reference rows in 13 vocabularies. Zero user rows, in either run.**

## 6 · Index review from the loaded schema

All 175 indexes were listed with their resolved column lists, then every index was
compared against every other index on the same table by script. Any index whose
columns are a leftmost prefix of another is unnecessary, because MySQL can use a
prefix of a composite index on its own, and InnoDB can read any index backwards,
so a descending copy of an ascending index is also unnecessary.

The first run of that comparison found **four** redundant indexes, all of which
were **removed** from `V001`:

| Removed index | Redundant with | Why it was unnecessary |
| --- | --- | --- |
| `ix_pantry_items_user_status (user_id, status)` | `ix_pantry_items_user_expiry (user_id, status, expiry_date)` | Exact leftmost prefix. |
| `ix_meal_plan_entries_plan_date (meal_plan_id, plan_date, meal_slot_type_id)` | `ux_meal_plan_entries_slot (…, position_in_slot)` | Exact leftmost prefix of the unique constraint. |
| `ix_user_body_measurements_user_date (user_id, measured_on DESC)` | `ux_user_body_measurements_user_day (user_id, measured_on)` | Same two columns. InnoDB reads any index backwards, so a descending copy adds write cost and no read benefit. |
| `ix_user_nutrition_targets_user_period (user_id, effective_from DESC, effective_to)` | `ux_user_nutrition_targets_user_from (user_id, effective_from)` | Shadows the unique constraint. The extra `effective_to` column does not make the index covering, because "which target applied on date D" fetches the row's target values anyway. |

The last one is the least obvious, and it is the reason the check was run
mechanically rather than by eye: two of the four were written deliberately, in the
belief that a `DESC` index or a wider trailing column would help.

After removal the schema was reloaded from scratch — exit 0, same warnings, same
counts — and the comparison re-run:

```text
indexes analysed: 175
prefix-redundant index pairs: 0
foreign keys whose leading column has no index: 0
```

The counts in section 3 are from that final reload. The
[index strategy](data-model.md#index-strategy) and the affected
[data dictionary](data-dictionary.md) entries were updated to match, and the
"deliberately not indexed" list now records the rule that caught all four.

The FK line above is a second check on the same data: InnoDB silently creates an
index for any foreign key that lacks one, which would make the real index count
higher than the design intends. Zero means every FK's index was written
deliberately in `V001` rather than conjured by the server.

Eight indexes intentionally declare a `DESC` column, which MySQL 8 implements as a
genuine descending index rather than ignoring the keyword as MySQL 5.7 did. Each
one backs a newest-first read: `recipes`, `recommendation_requests` (two),
`meal_plans`, `pantry_item_events`, `notifications`, `search_queries`,
`user_account_events`.

The three functional unique indexes were confirmed to have been accepted as
expressions, not silently as columns — `information_schema.statistics` reports
their `EXPRESSION` as `(case when \`is_default\` then 1 else NULL end)` and
equivalents for `ux_ingredient_foods_primary` and
`ux_recipe_nutrition_snapshots_current`. This is the "at most one default per
parent" pattern, and it only works because MySQL 8.0.13+ indexes expressions and
ignores NULLs in unique indexes.

## 7 · Constraint review

All 125 `CHECK` clauses were read back as MySQL normalised them, which confirms
each one parsed and was **enforced** rather than accepted-and-ignored (MySQL
before 8.0.16 parsed `CHECK` and discarded it; the fact that the server returns
normalised clause text proves this server stores them).

Reviewed by hand against the design intent, in five groups:

- **Enumerated values** — every status, kind, provenance and severity column is
  constrained to a list matching a Java enum.
- **Structurally impossible numbers** — quantities, servings, ranks, ratios and
  ratio denominators are constrained away from zero and from negatives.
- **Cross-column consistency** — the ones that carry real design meaning:
  `expiry_date IS NULL` if and only if `expiry_kind = 'UNKNOWN'`;
  `0 <= quantity_remaining <= quantity_initial`; exactly one of
  `recipe_id`/`food_id` on `meal_plan_entries` and `recommendation_results`, and
  `food_serving_id` only alongside a `food_id`; `closed_at` present exactly for
  terminal pantry statuses; `consumed_at` present exactly when consumption is
  `EATEN`; `decided_at` present exactly when a result is no longer `PENDING`.
- **Date and range sanity** — `end_date >= start_date` on `meal_plans`,
  `effective_to >= effective_from` on `user_nutrition_targets`,
  `scale_min < scale_max` on `ai_score_components`.
- **Physiological ranges** — deliberately wide: weight strictly between 2 and
  700 kg, height strictly between 30 and 300 cm, body fat 0–100% inclusive. These
  reject structurally impossible storage, not medically unusual values; the
  database is not the place to encode clinical judgement.

One rule that is deliberately **not** a `CHECK`: that a `meal_plan_entries.plan_date`
falls inside its parent plan's `start_date`–`end_date` window. That compares
columns across two rows in two tables, which a MySQL `CHECK` cannot do — a
subquery is not allowed in one. Java validates it, and
`database/schema/V001__initial_schema.sql:1583` says so at the column. Two
tempting alternatives were rejected: a trigger would put business logic in the
database, contradicting
[DB-ADR-017](database-decisions.md#db-adr-017--java-is-the-only-database-client),
and denormalising the plan window onto every entry would duplicate data to
enforce a rule the single writer already enforces. Probe 30 in
[section 13](#13--behaviour-probe-on-throwaway-rows) demonstrates the server
accepting such a row, so the gap is recorded as an observed fact rather than an
assumption.

Fifteen of the 125 were then provoked individually to confirm they reject rather
than warn; see [section 13](#13--behaviour-probe-on-throwaway-rows).

Generated columns behaved as intended: `users.email_normalized`,
`recipes.total_minutes` and `meal_plans.day_count` are all `STORED` and all
appear in `information_schema.columns` with a non-empty
`GENERATION_EXPRESSION`. `email_normalized` being generated is what makes
one-account-per-mailbox enforceable — the unique constraint sits on the
normalised form, so `A@x.com` and `a@x.com ` cannot both exist. That claim was
not left to reasoning: [section 13](#13--behaviour-probe-on-throwaway-rows) shows
the server rejecting the second address.

## 8 · ERD validation

All four Mermaid diagrams were rendered with `@mermaid-js/mermaid-cli` 11.4.2,
installed into a temporary directory outside the repository. Rendering is the only
real syntax test for Mermaid; a diagram that renders is parseable.

| Diagram | `mmdc` exit | Rendered SVG |
| --- | --- | --- |
| `database-erd.mmd` | 0 | 639,525 bytes |
| `database-erd-identity.mmd` | 0 | 452,315 bytes |
| `database-erd-catalog.mmd` | 0 | 555,870 bytes |
| `database-erd-planning.mmd` | 0 | 483,634 bytes |

The SVGs were not committed — the `.mmd` source is the artifact, so that review
stays diffable.

Entity names were then cross-checked against the loaded schema by script:

| Check | Result |
| --- | --- |
| ERD entity names that are not real table names | 0 across all four diagrams |
| Entities referenced in a relationship but never declared | 0 across all four diagrams |
| Duplicate entity declarations within one diagram | 0 |
| Tables covered by the three domain ERDs together | 53 of 53, none missing |
| Tables shown in the high-level ERD | 28, with the remaining 25 (vocabularies, auth state, association tables) deliberately deferred to the domain ERDs |

Attribute names were checked the same way, against the 464 columns
`information_schema.columns` reports for the loaded schema:

| Diagram | Attributes shown | Not a real column |
| --- | --- | --- |
| `database-erd.mmd` | 160 | 0 |
| `database-erd-identity.mmd` | 117 | 0 |
| `database-erd-catalog.mmd` | 143 | 0 |
| `database-erd-planning.mmd` | 134 | 0 |

**554 attribute references, all resolving to a real column.** This matters more
than it sounds: Mermaid will happily render a misspelled column, so an ERD is the
easiest artifact in the deliverable to let drift out of agreement with the schema.
The names were generated from an `information_schema.columns` dump rather than
retyped from the migration.

## 9 · Documentation validation

A script walked all 36 Markdown files in the repository, extracted every
relative link, and resolved both the file path and, where present, the `#anchor`
against the target file's actual headings using GitHub's slug rules.

| Check | Result |
| --- | --- |
| Relative links checked | 271 |
| Links to missing files | 0 |
| Links to missing anchors | 0 |

That includes the 18 DB-ADR anchors in `database-decisions.md` and every
cross-file citation of them from `data-model.md` and `data-dictionary.md`, which
is the class of link most likely to rot as a heading is reworded.

One caveat about how this was measured, because it produced a false result first
time: reading the Markdown with PowerShell's default console encoding mangles the
em dash in every `## DB-ADR-0NN — Title` heading, so the generated slug never
matches the link and roughly 55 anchors are reported missing. The check is only
meaningful when the files are read as UTF-8 explicitly. The numbers above are
from the UTF-8 run.

Consistency between documents and schema:

- The twelve-section table count in [`data-model.md`](data-model.md) was
  corrected to match the migration's own `-- Section N` boundaries
  (12/4/6/3/6/3/7/3/2/2/2/3 = 53). It had previously summed to 54.
- The table index in [`data-dictionary.md`](data-dictionary.md) was corrected:
  `roles` had been omitted and a cross-reference row had been counted as a table.
  It now lists exactly 53 tables, each once.
- All object counts quoted in [`README.md`](README.md), `data-model.md` and this
  report come from the introspection queries in sections 3–7, not from counting
  `CREATE TABLE` statements.

## 10 · Security review

| Check | Result |
| --- | --- |
| `pre-commit run --all-files` | All hooks pass |
| detect-secrets against the new files | No findings; `.secrets.baseline` unchanged |
| Plaintext passwords anywhere in schema or seed | None. `users.password_hash` stores an encoded Argon2id/bcrypt string only |
| JWT access tokens in a persistent table | None. Access tokens are stateless and never stored |
| Refresh and one-time tokens | Stored only as `CHAR(64)` hex SHA-256 digests, in `user_auth_sessions.refresh_token_hash` and `user_security_tokens.token_hash` |
| Connection strings, hosts, ports, credentials in committed files | None. The Flyway example in `README.md` uses `<host>:<port>/<schema>` placeholders |
| `.env` files added | None |
| `CREATE DATABASE` / `CREATE USER` / `GRANT` in migrations | None. Schema creation and privileges belong to the deployment environment |
| `DROP` / `TRUNCATE` / unqualified `DELETE` in migrations or seed | None. The seed is `INSERT ... ON DUPLICATE KEY UPDATE` only |
| Binary database files, dumps, IDE artifacts | None |
| Unrelated configuration modified | None |
| Real or fake personal data in seed | None. 146 rows, all reference vocabulary |
| Throwaway rows used by the behaviour probe | Two `example.com` addresses and literal placeholder hash strings, in `D:\tmp` outside the repository, in a scratch schema that was dropped |

The one place personal data is deliberately made *unrecoverable* rather than
protected is account closure: `users.anonymized_at` marks the irreversible second
stage, where identifying columns are overwritten and the row is kept so that
history rows referencing it stay valid
([DB-ADR-006](database-decisions.md#db-adr-006--retirement-and-anonymisation-instead-of-soft-delete-columns)).

## 11 · Architecture consistency with P1

Checked clause by clause against
[`architecture-decisions.md`](../architecture/architecture-decisions.md) ADR-003
and ADR-004 and [`system-architecture.md`](../architecture/system-architecture.md)
§8 "Data ownership":

| P1 rule | P2 status | Evidence |
| --- | --- | --- |
| Flutter → Java REST only | Preserved | P2 adds no client-facing artifact of any kind |
| Java → MySQL, and only Java | Preserved | Zero views, procedures, functions, triggers and events (section 3); no integration table; no `GRANT` |
| Java → Python AI over REST | Preserved | `recommendation_requests` stores the request metadata around the call; the call itself is unchanged |
| Flutter ⇸ MySQL | Preserved | No credential, connection string or client artifact exists in the repository |
| Python ⇸ MySQL, no credentials (ADR-003, ADR-004) | Preserved | No schema, user, grant or connection detail for the AI service. Provenance rows are written by Java from the response Python returned |
| Java owns all persisted domain state | Preserved | Every one of the 53 tables has exactly one writer |
| MySQL is the authoritative store | Preserved | No second store, cache, search engine or vector database is introduced |

§8's table names an authoritative owner per data category. Each row has a
corresponding home in the schema, and three of its explicit policies were checked
against the DDL rather than assumed:

- *"Password hashes, sessions/refresh records, roles, and revocation state in
  MySQL as required; never Python."* → `users.password_hash`,
  `user_auth_sessions` (with `revoked_at` and `replaced_by_session_id`),
  `user_security_tokens`, `roles`, `user_roles`. Nothing in the AI-provenance
  tables touches credentials.
- *"Store minimal request metadata, constraints, … algorithm version, and status …
  do not persist every intermediate candidate by default."* →
  `recommendation_requests` holds the metadata, `constraints_hash`,
  `algorithm_version` and `status`; `recommendation_results` holds the returned
  shortlist only; `recommendation_result_scores` holds component scores validated
  against `ai_score_components`. No table stores a candidate set, a feature
  matrix, or a solver state.
- *"Store snapshots with component definition, scale, algorithm/model version, and
  timestamp only when needed; never overwrite source facts."* →
  `ai_score_components` carries `scale_min`, `scale_max` and `higher_is_better`,
  so a stored score stays interpretable after the scale changes;
  `recipe_nutrition_snapshots` is versioned rather than updated in place, which is
  what "never overwrite source facts" requires.

`public_id BINARY(16)` supports this boundary: it lets the REST API expose an
opaque UUID while joins stay on `BIGINT`, so the client never learns a primary key
and never has a reason to address the database directly.

§8 also anticipates that "reproducible offline evaluation datasets and model
artifacts may later use a dedicated controlled store". P2 introduces no such
store, which is consistent — that is future work, not a P2 omission.

No blocking architecture conflict was found. P2 required no change to any P1
document.

## 12 · Scope review

`git status` and `git diff` were inspected before committing:

| Check | Result |
| --- | --- |
| Spring Boot, Flutter, FastAPI, Maven, Gradle scaffolding | None added |
| `pom.xml`, `build.gradle`, `pubspec.yaml`, `requirements.txt`, `package.json` | None added |
| Source files in any programming language | None. The deliverable is `.sql`, `.md` and `.mmd` only |
| P1 documents modified | None, except `docs/diagrams/README.md`, which gained four rows for the new ERDs, and root `README.md`, which gained one link |
| Files changed outside `database/`, `docs/database/`, `docs/diagrams/` and root `README.md` | None |

## 13 · Behaviour probe on throwaway rows

Sections 4 and 7 verify the *declarations*. This section verifies what the server
actually does when a statement breaks one. Thirty-one numbered probes were run
against `p2check` after a clean reload, in one script, with `--force` so that each
deliberately-provoked error is reported and the run continues; a short second
script covered the planning-window boundaries and the non-strict-mode question,
and is marked below where its results appear.

The rows are throwaway: two `example.com` addresses, literal placeholder strings
in `password_hash`, and objects named "Probe …". Nothing resembles a real person,
no real hash or token is used, and the script lives in `D:\tmp` outside the
repository. It is not part of the deliverable — this section is the artifact.

**Uniqueness and generated columns**

| Probe | Statement | Observed |
| --- | --- | --- |
| One account per mailbox | insert `Ada@Example.com`, then `'  ada@EXAMPLE.COM '` | `ERROR 1062 … Duplicate entry 'ada@example.com' for key 'users.ux_users_email_normalized'` |
| Normalisation is real | read the generated column back | `Ada@Example.com` → `ada@example.com` |
| One default serving per food | insert a second `is_default = TRUE` row | `ERROR 1062 … Duplicate entry '1-1' for key 'food_servings.ux_food_servings_food_default'` |
| One entry per plan slot position | re-insert `(plan, 2026-09-01, BREAKFAST, 1)` | `ERROR 1062 … Duplicate entry '2-2026-09-01-1-1' for key 'meal_plan_entries.ux_meal_plan_entries_slot'` |
| Generated columns are read-only | supply `day_count` explicitly | `ERROR 3105 … The value specified for generated column 'day_count' in table 'meal_plans' is not allowed` |
| `day_count` is correct | `2026-09-01` → `2026-09-07` | `7`; a 3-day window gives `3` |

The duplicate-mailbox result is the single most important line in this report.
It is the one place where a wrong design decision — normalising in Java only —
would have produced two accounts for one mailbox, and no amount of reading the
DDL proves the generated column is actually consulted by the unique index.

**`CHECK` constraints, provoked one at a time**

Each of these returned `ERROR 3819 (HY000) … Check constraint '<name>' is violated`:

| Attempted | Constraint that refused it |
| --- | --- |
| `email` = `not-an-email` | `ck_users_email_shape` |
| `anonymized_at` set while `deactivated_at` is null | `ck_users_anonymized_requires_deactivated` |
| `expiry_kind = 'UNKNOWN'` with a date | `ck_pantry_items_expiry_consistency` |
| `expiry_kind = 'USE_BY'` with a date but `expiry_confidence = 'UNKNOWN'` | `ck_pantry_items_expiry_consistency` |
| `quantity_remaining` raised above `quantity_initial` | `ck_pantry_items_quantity_remaining` |
| `closed_at` set on an `AVAILABLE` item | `ck_pantry_items_closed_at` |
| `AVAILABLE` with `quantity_remaining = 0` | `ck_pantry_items_status_quantity` |
| a serving with neither grams nor millilitres | `ck_food_servings_measurable` |
| `end_date` before `start_date` | `ck_meal_plans_dates` |
| a 41-day planning window | `ck_meal_plans_window` |
| `accepted_at` set while `status = 'DRAFT'` | `ck_meal_plans_accepted_at` |
| substitution `ratio_denominator = 0` | `ck_ingredient_substitutions_ratio` |
| an ingredient substituting for itself | `ck_ingredient_substitutions_distinct` |
| an entry naming both a recipe and a food | `ck_meal_plan_entries_subject` |
| an entry naming neither | `ck_meal_plan_entries_subject` |

Both directions of the pantry expiry rule fire, which is the point of writing it
as an "if and only if" rather than two independent conditions: a row may say "I
do not know when this expires", or it may carry a date, a kind and a confidence,
and there is no third state where a date exists but the label is still `UNKNOWN`.

Two further observations, recorded because they are easy to get wrong, both from
the second script:

- The `CHECK` fires *before* the range error on a generated column. Inserting
  `start_date = '2026-09-02'`, `end_date = '2026-09-01'` returns
  `ck_meal_plans_dates`; a five-day backwards window returns
  `ERROR 1264 … Out of range value for column 'day_count'` instead, because
  `DATEDIFF` goes negative and `SMALLINT UNSIGNED` cannot hold it. Both reject the
  row. The constraint is the meaningful guard; the range error is a second net.
- The `CHECK` still refuses the row in a non-strict session. Setting
  `sql_mode = ''` and repeating the backwards window returned
  `ck_meal_plans_dates` again, and `SELECT COUNT(*) … WHERE end_date < start_date`
  returned `0`. `CHECK` enforcement is not a strict-mode courtesy, unlike range
  and truncation errors, which non-strict mode downgrades to warnings. The server
  default was recorded as
  `ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION`,
  so the schema does not depend on a session setting the application might change.

**Referential actions, observed rather than declared**

A realistic row graph was built for one user — profile-adjacent rows, two auth
sessions (one rotated into the other), an account event, a pantry item with two
events, a meal plan, a notification pointing at both the pantry item and the plan,
a recipe they authored, and a search they ran — and then the user was deleted.

| Probe | Observed |
| --- | --- |
| Delete a `measurement_units` row that ingredients reference | `ERROR 1451 … CONSTRAINT 'fk_ingredients_default_unit'` |
| Delete an ingredient held in a pantry | `ERROR 1451 … CONSTRAINT 'fk_pantry_items_ingredient'` |
| Delete a food a pantry item points at | `ERROR 1451 … CONSTRAINT 'fk_pantry_items_food'` |
| `DELETE FROM users` | 1 statement; `users`, `pantry_items`, `pantry_item_events`, `meal_plans`, `notifications`, `user_auth_sessions`, `user_account_events` all → 0 |
| Same delete, catalog side | `ingredients` 2, `ingredient_substitutions` 1, `measurement_units` 16, `food_categories` 26, `notification_types` 6 — all unchanged |
| Same delete, `SET NULL` side | `recipes` 1 row kept, `created_by_user_id` now NULL; `search_queries` 1 row kept, `user_id` now NULL |
| Delete the ingredient once no pantry references it | succeeds; its substitution edge cascades away with it |
| Delete a meal plan | its 9 entries cascade; `recipes` and `meal_slot_types` untouched |

That is the whole deletion policy of the design, demonstrated in one transaction
sequence: **owned rows follow their owner, shared catalog rows refuse to be
pulled out from under history, and authorship links go null so the row survives
its author.** `pantry_item_events` reaching 0 is a two-level cascade — user →
pantry item → event — which is worth confirming rather than inferring, since a
single `RESTRICT` anywhere on that path would have made account closure fail
instead.

The `notifications` row is the case that motivates the mixed policy on one table:
its `user_id` is `CASCADE` and its `pantry_item_id` and `meal_plan_id` are
`SET NULL`. Deleting the user removed the notification outright, as intended,
rather than leaving an orphan addressed to nobody.

**Substitution is directed**

Inserting "ingredient 1 may be replaced by ingredient 2, ratio 3:4, in baking"
produced exactly one edge: `forward_edges = 1`, `reverse_edges = 0`. Nothing in
the schema creates the mirror row. That is deliberate and is the point of
[DB-ADR-009](database-decisions.md#db-adr-009--substitutions-are-typed-directed-edges) —
"honey instead of sugar" is not the same claim as "sugar instead of honey", and a
symmetric table would assert both from one entry.

**Meals per day are not fixed**

A three-day plan was filled with two meals on day 1, five on day 2 and one on
day 3, using four different slot types, and all eight entries were accepted:

```text
plan_date    meals_that_day
2026-09-01                2
2026-09-02                5
2026-09-03                1
```

Requirement (G) says the schema must not hardcode one number of meals per day.
This is that requirement executed: the day's shape is whatever entries exist for
the date, and `meals_per_day_target` on the parent plan stayed `3` throughout
without constraining anything.

**The one rule the database does not enforce**

Probe 30 inserted an entry dated `2027-01-01` into a plan running
`2026-09-01`–`2026-09-03`, and **the server accepted it**:

```text
entries_outside_window
                     1
```

This is recorded as an accepted result, not a defect, and it is the honest form of
the note in section 7. A MySQL `CHECK` cannot express the rule, because it would
have to compare a column in one row against columns in a row of another table, and
a `CHECK` may not contain a subquery. Java validates it; the alternatives were a
trigger, which would move business logic into the database against
[DB-ADR-017](database-decisions.md#db-adr-017--java-is-the-only-database-client),
or copying the plan window onto every entry, which would duplicate data to
re-derive a rule the single writer already applies. Whoever writes the meal-plan
service should treat this probe as the specification of a test they owe.

## 14 · What was not executed

Stated precisely, so nothing here is overclaimed:

- **No `EXPLAIN` plans.** The index strategy is reasoned from expected access
  patterns against an empty schema. With no data and no application, `EXPLAIN`
  output would be misleading — InnoDB's optimiser choices depend on cardinality
  statistics that do not exist yet. Measuring belongs in a later phase, and
  [`data-model.md`](data-model.md#index-strategy) says so.
- **No Flyway run.** Flyway itself was not installed. What was verified is that
  the files satisfy its contract: `V001__` and `R001__` naming, one-pass ordered
  execution, no statement that a checksum-tracked immutable migration may not
  contain. Adopting Flyway is a P3 task.
- **No JPA or Hibernate validation.** There is no Java code in P2, so
  `ddl-auto=validate` cannot be run. The mapping obligations the schema imposes
  (`BINARY(16)` UUIDs, `DATETIME(6)`, generated columns as read-only, `version`
  on exactly seven tables) are documented for whoever writes the entities.
- **No load or volume testing.** No performance claim is made anywhere in P2. The
  behaviour probe in section 13 uses a handful of rows and says nothing about
  performance at any scale.
- **No concurrency test of the `version` columns.** Optimistic locking is a
  contract between the schema and JPA's `@Version`; with no application there is
  no second writer to conflict with. What was verified is that `version` exists,
  is `NOT NULL DEFAULT 0`, and sits on exactly the seven tables intended.

## 15 · Cleanup

The temporary MySQL instance was stopped by exact process id and its data
directory deleted; the temporary `mermaid-cli` installation was deleted after
rendering. Both lived under `D:\tmp`, outside the repository, so neither could
reach a commit. The unrelated local MySQL server that was already running was
never signalled — processes were stopped individually by id, never by name or
pattern.
