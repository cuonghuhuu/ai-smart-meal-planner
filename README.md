# AI Smart Meal Planner

AI Smart Meal Planner is a university project for Artificial Intelligence and
Java Technology. It is designed as a practical Android and Web application for
food, recipe, pantry, nutrition, recommendation, and meal-planning workflows.

The approved high-level architecture is a Flutter client, a Java Spring Boot
backend, MySQL persistence through JPA/Hibernate, and a specialized Python AI
service.

## Project documentation

- [Phase P1 system architecture](docs/architecture/README.md)
- [Phase P2 database design](docs/database/README.md)
- [Phase P3 backend foundation](backend/README.md)
- [Phase P7 food & ingredient catalog contract](docs/architecture/food-ingredient-catalog.md)
- [Source-controlled architecture and database diagrams](docs/diagrams/README.md)

## Phase status

- P1 system architecture - complete
- P2 database design - complete
- P3 backend foundation - complete
- P4 authentication and authorization - complete
- P5 user profile, preferences, and measurements - complete
- P6 deterministic nutrition targets - complete
- P7 food and ingredient catalog - complete
- P8 Flutter catalog and ingredient preferences - complete
- P9 Recipe core - current (P9A/P9B Java catalog and nutrition; P9C offline
  Vietnamese curated Recipe import)

P2 adds the relational design: documentation under `docs/database/`, the
versioned MySQL schema and reference seed data under `database/`, and the entity
relationship diagrams. The applied V001 schema remains the source of truth for
the current catalog work.
