# Seed Data

Reference vocabularies only. These files populate the small lookup tables the
Java backend needs before any user-facing feature can work: units, nutrients,
roles, meal slot types, activity levels, goals, dietary preferences, allergens,
food categories, recipe tags, score components and notification types.

## What is not allowed here

- No user accounts, real or fake.
- No passwords, password hashes, tokens, API keys, secrets or connection strings.
- No real personal data of any kind.
- No `CREATE DATABASE`, `CREATE USER` or `GRANT` statements.
- No `DROP`, `TRUNCATE` or unqualified `DELETE`.
- No production data dumps and no binary database files.

Demonstration foods, ingredients and recipes are intentionally absent. Catalog
content is curated later through the backend, with the source recorded per row,
so it is not pinned into a migration.

## Files

| File | Contents |
| --- | --- |
| `R001__reference_data.sql` | All reference vocabulary rows. |

## Repeatable and idempotent

`R001` is prefixed `R` for Flyway's *repeatable* migration convention: it is
re-applied whenever its checksum changes, after the versioned migrations.

Every statement is `INSERT ... ON DUPLICATE KEY UPDATE` keyed on the table's
natural unique `code`. Re-running the file corrects drifted display text without
inserting duplicates and without touching surrogate ids that live data already
references. Rows are never deleted, so removing a value from this file does not
remove it from a database that already has it — retiring a vocabulary entry is a
deliberate change made in a versioned migration.

Ordering matters: `measurement_units` inserts its base units before the derived
units that reference them, and `nutrients` runs after the units it points at.

Applying seed data separately from the schema:

```text
flyway -url=jdbc:mysql://<host>:<port>/<schema> \
       -locations=filesystem:database/schema,filesystem:database/seed \
       migrate
```

## Why these values are rows and not Java enums

See [DB-ADR-003](../../docs/database/database-decisions.md). Values that other
tables reference by foreign key, that carry extra attributes (a unit's
conversion factor, a nutrient's unit, an activity factor), or that
non-developers will extend, live in tables. Values that only constrain a single
column and that application logic branches on — account status, plan status,
provenance, severity — stay as `CHECK`-constrained strings plus a Java enum, and
get no table.
