# Architecture Diagrams

These Mermaid files are source-controlled design artifacts from Phase P1
(architecture) and Phase P2 (database). GitHub and Mermaid-compatible editors can
render them directly; keeping the `.mmd` source makes review and future changes
diffable.

| Diagram | Purpose |
| --- | --- |
| [System context](system-context.mmd) | Users, the product boundary, core systems, and possible future integrations. |
| [Container architecture](container-architecture.mmd) | Deployable responsibilities and allowed communication paths. |
| [Main request/data flow](main-request-data-flow.mmd) | Authentication, AI recommendation, failure/degradation, and accepted-plan persistence over time. |
| [Java module dependencies](java-module-dependencies.mmd) | High-level allowed dependency direction inside the modular monolith. |
| [Database ERD](database-erd.mmd) | P2 high-level entity relationship diagram: the 28 entities that carry the product's core meaning. |
| [Database ERD — identity](database-erd-identity.mmd) | P2 domain ERD: users, authentication state, profile, body measurements, nutrition targets, declared preferences. |
| [Database ERD — catalog](database-erd-catalog.mmd) | P2 domain ERD: units, nutrients, foods, nutrition facts, ingredients, substitution, recipes. |
| [Database ERD — planning](database-erd-planning.mmd) | P2 domain ERD: pantry, AI recommendation provenance, meal plans, notifications, search log. |

The four database diagrams are Phase P2 artifacts. Their source of truth is
[`database/schema/V001__initial_schema.sql`](../../database/schema/V001__initial_schema.sql),
and the tables they show are described in the
[data dictionary](../database/data-dictionary.md). The high-level ERD is the one
to read first; the three domain ERDs together cover all 53 tables, at a size
that stays legible when printed in the university report.

The diagrams are intentionally architectural rather than implementation
blueprints. They do not imply that future integrations, a notification provider,
or optional ML infrastructure already exists.
