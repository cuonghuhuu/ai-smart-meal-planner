# Phase P1 — System Architecture

## Purpose

This package defines the initial system architecture for AI Smart Meal Planner.
It is the architectural baseline for both the Artificial Intelligence and Java
Technology course work, while remaining realistic for a four-person team.

P1 is documentation-only. It does not create Flutter, Spring Boot, Python, or
database scaffolding.

## Architecture baseline

```text
Flutter (Android and Web)
        |
        | HTTPS REST API
        v
Java Spring Boot backend
        |
        +---- MySQL through JPA/Hibernate
        |
        +---- Python AI service through HTTP
```

The Java backend is the application boundary and authority for security,
workflows, validation, transactions, and persisted state. Python specializes in
explainable AI computation. Flutter does not access MySQL and does not normally
call Python directly.

## Documents

- [System architecture](system-architecture.md) — context, containers, component
  boundaries, communication, ownership, flows, and non-functional design.
- [Architecture decisions](architecture-decisions.md) — the major decisions,
  rationales, consequences, and rejected alternatives for P1.
- [Authentication & authorization](authentication.md) — P4 auth security, JWT/session
  lifecycle, and role-based access control.
- [User profile, preferences & measurements](user-profile.md) — P5 profile core,
  optimistic concurrency, time-series measurements, dietary preferences, and allergen safety.
- [Nutrition targets & deterministic calculation](nutrition-targets.md) — P6 nutrition
  target lifecycle, Mifflin-St Jeor baseline calculation, adult macro ranges, API
  boundaries, and implementation order.
- [Food & ingredient catalog core](food-ingredient-catalog.md) - P7 catalog
  ownership, normalized Food nutrition/servings, canonical Ingredients, search,
  authorization, and the implementation sequence.
- [Recipe catalog core](recipe-catalog.md) - P9A Recipe persistence ownership,
  published-only read API, cross-module reference boundaries, and nutrition
  snapshot read behavior.
- [Recipe nutrition calculation](recipe-nutrition.md) - P9B BigDecimal
  calculation, conservative unit conversion, completeness, and immutable
  nutrition snapshot replacement.
- [Pantry architecture](pantry.md) - P10 lot-level inventory, immutable
  availability snapshots, ownership, expiry semantics, and mutation history.
- [Meal Planning internal contract V1](meal-planning-contract-v1.md) - P11-B
  Java/Python DTOs, bounds, outcomes, allergen safety, and internal service
  security.
- [Meal Planning algorithm V1](meal-planning-algorithm-v1.md) - P11-C
- [Meal Planning Java–Python integration](meal-planning-java-integration-gate-d.md) - P11-D
  fail-closed CSP filtering, Decimal scoring, Virtual Pantry, forward checking,
  deterministic bounded Beam Search, and testable outcome semantics.
- [Vietnamese Recipe offline import](vietnam-recipe-import.md) - P9C
  project-curated starter data, canonical SMILING Ingredient mappings,
  validation, idempotency, and explicit offline execution.
- [Vietnamese Recipe data validation](vietnam-recipe-data-validation.md) - P9C
  source-controlled semantic review of the committed starter dataset and its
  diet/allergen evidence limits.

## Diagrams

- [System context](../diagrams/system-context.mmd)
- [Container architecture](../diagrams/container-architecture.mmd)
- [Main request and data flow](../diagrams/main-request-data-flow.mmd)
- [Java domain/module dependencies](../diagrams/java-module-dependencies.mmd)

See the [diagram index](../diagrams/README.md) for diagram scope and rendering
guidance.

## Status and change policy

This is the accepted P1 baseline. Later phases may refine endpoint schemas,
storage models, algorithms, and deployment details through new architecture
decisions. Changes must preserve the locked technology boundaries unless the
project owners explicitly approve an architectural replacement.
