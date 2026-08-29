# Database Documentation (Phase P2)

This directory is the Phase P2 deliverable: the relational design for the AI
Smart Meal Planner. It documents the model, the reasoning behind it, and the
conventions the team follows when the schema evolves.

P2 is a **design phase**. It contains SQL and documentation only. No Spring
Boot, Flutter, FastAPI, Maven or Gradle scaffolding is introduced here.

## Contents

| Document | Purpose |
| --- | --- |
| [Data model](data-model.md) | Domain-by-domain design, keys, normalization, lifecycle, time handling, naming conventions, and the index and constraint strategy. |
| [Data dictionary](data-dictionary.md) | Table-by-table reference: purpose, key fields, constraints, relationships, ownership and lifecycle notes. |
| [Database decisions](database-decisions.md) | Decision records (DB-ADR-001 and onwards) with context, decision, consequences and rejected alternatives. |
| [Validation report](validation-report.md) | What was executed against a real MySQL 8.4 server — including the behaviour probe that provokes each rule — and what was checked statically. |

Related artifacts:

- [Entity relationship diagram](../diagrams/database-erd.mmd) — high-level ERD.
- [Domain ERDs](../diagrams/README.md) — the three per-domain diagrams
  ([identity](../diagrams/database-erd-identity.mmd),
  [catalog](../diagrams/database-erd-catalog.mmd),
  [planning](../diagrams/database-erd-planning.mmd)) which together cover all 53
  tables at a size that stays legible in the report.
- [Schema migration `V001__initial_schema.sql`](../../database/schema/V001__initial_schema.sql)
- [Reference seed data](../../database/seed/README.md)
- [Phase P1 system architecture](../architecture/system-architecture.md)
- [Phase P1 architecture decisions](../architecture/architecture-decisions.md)

## Ownership rules inherited from P1

P2 does not change the architecture. It implements the data layer that
[ADR-004](../architecture/architecture-decisions.md) already fixed:

- MySQL is the authoritative durable store for all business state.
- The Spring Boot modular monolith is the **only** component that connects to
  MySQL, through JPA/Hibernate and controlled migrations.
- The Flutter client never reaches MySQL. It only calls the Java REST API.
- The Python AI service has **no** database connection and **no** credentials.
  It owns no authoritative business state. It returns scores, rankings and
  explanations to Java; Java validates them and decides what is persisted.

Every table in this design therefore has exactly one writer: the Java backend.

## Directory layout produced by P2

```text
database/
  schema/
    V001__initial_schema.sql      versioned, forward-only DDL
  seed/
    R001__reference_data.sql      idempotent reference vocabularies
    README.md                     what may and may not live in seed data
docs/
  database/
    README.md                     this file
    data-model.md
    data-dictionary.md
    database-decisions.md
    validation-report.md
  diagrams/
    database-erd.mmd              high-level ERD
    database-erd-identity.mmd     users, auth, profile, nutrition targets
    database-erd-catalog.mmd      foods, nutrition, ingredients, recipes
    database-erd-planning.mmd     pantry, meal plans, recommendations
```

## How to apply the schema

The schema is written so that Flyway (or any ordered migration runner) can
adopt it unchanged. Nothing in `database/` creates a database, a user, a grant,
or a credential, and nothing drops or truncates data. The target schema name and
connection details come from the deployment environment.

Illustrative only — do not commit real values, and do not run this against a
database that already holds data:

```text
flyway -url=jdbc:mysql://<host>:<port>/<schema> \
       -locations=filesystem:database/schema \
       migrate
```

Seed files are kept out of the Flyway `schema` location on purpose. They are
repeatable reference data, described in
[`database/seed/README.md`](../../database/seed/README.md).

## Scale of the design

53 tables across twelve domains: reference vocabularies, authentication,
profile and nutrition targets, foods and nutrition facts, ingredients,
ingredient substitution, recipes, recommendations and AI provenance, meal
planning, pantry, notifications, and search support with account history.
Twelve of the 53 are small reference vocabularies and 14 more are pure
association or child tables, which keeps the number of genuinely independent
aggregates low enough for a four-person team to implement incrementally.

Counts verified by loading `V001__initial_schema.sql` into MySQL 8.4.11:
53 tables, 88 foreign keys, 125 `CHECK` constraints, 40 `UNIQUE` constraints,
175 indexes. See the [validation report](validation-report.md).
