# Spring Boot backend foundation (P3)

Java is the sole owner of persisted business state. This Maven module is a
foundation, with no registration, login, business CRUD, or AI algorithms.

## Prerequisites and commands

- JDK 25 (local audit: Oracle 25.0.4).
- Maven 3.6.3+ (local audit: 3.9.16).
- A running Docker engine with Linux containers for tests, able to pull
  `mysql:8.4.11` and Testcontainers support images.
- For normal application startup: an **empty, already provisioned MySQL 8.x
  database**, with runtime credentials. Do not point the initial migration at an
  existing P2 schema; automatic baselining is deliberately disabled.

From the repository root:

```text
mvn -B -ntp -f backend/pom.xml clean verify
```

Surefire runs unit/MVC tests; Failsafe runs `*IT` against a disposable MySQL
Testcontainer. Docker absence is a failure, not a skip. No H2 dependency or test
profile substitutes another database. `mvn test` alone does not run integration
tests and is insufficient for acceptance.

For application startup, set these variables through the IDE run configuration
or your runtime secret mechanism, then run:

```text
mvn -f backend/pom.xml spring-boot:run
```

For local development, add `-Dspring-boot.run.profiles=local`. Non-secret `local`
and `test` profile documents live in `application.yml`: local uses a five-connection
pool and test uses up to two connections with no idle minimum. Both inherit the
same Flyway, Hibernate validation, security, and environment credential settings.
Integration and MVC tests activate `test`; only the MySQL integration test supplies
database credentials, through Testcontainers at runtime.

| Variable | Value |
| --- | --- |
| `DB_URL` | MySQL JDBC URL for the provisioned database; include `connectionTimeZone=UTC` |
| `DB_USERNAME` | Runtime database principal |
| `DB_PASSWORD` | Runtime password, never committed |
| `CORS_ALLOWED_ORIGINS` | Optional comma-separated exact browser origins; empty means no cross-origin access |

The pool initializes each connection's MySQL time zone to UTC. Hibernate also
uses UTC. `DATE` values remain `LocalDate`; `DATETIME(6)` fields represent UTC
using `LocalDateTime`. No local secrets file is needed. Integration tests supply
their own generated disposable password and database properties.

`GET /actuator/health` is public and exposes status only. Everything else is
protected; `/api/v1/admin/**` requires ADMIN. P3 deliberately installs no login
mechanism, test user, Basic authentication or JWT workflow. Protected routes
therefore have no real-user authentication path yet. Tests inject a Spring
Security test principal. CSRF remains enabled. CORS credentials are disabled
until a later authentication ADR defines cookie handling.

## Migration source of truth

`database/schema/` and `database/seed/` remain authoritative. Maven copies their
SQL resources directly into `target/classes/db/migration/` with **filtering
disabled**. The packaged JAR is self-contained; there is no second maintained SQL
copy in `backend/src/main/resources`. `SchemaResourceTest` checks byte identity.

P2's filenames are retained exactly:

- `V001__initial_schema.sql`
- `R001__reference_data.sql`

Flyway's default repeatable prefix would expect `R__description.sql`. P3 sets
`spring.flyway.repeatable-sql-migration-prefix=R001` to adopt the actual P2 seed
without renaming or editing it. `001` is part of this configured prefix, **not a
repeatable migration version**. Future repeatables must use `R001__description.sql`
under this configuration. Future schema migrations use `V002__...`, etc.

Naming validation is enabled, automatic baselining is disabled, and Flyway clean
is disabled. Hibernate uses `ddl-auto=validate`; SQL initialization is disabled.
Applied versioned SQL must remain immutable. Changing reference SQL causes its
repeatable migration to run after versioned migrations. Do not repair migration
history to conceal a changed versioned file.

## Boundaries and verification

See [P3 architecture and API foundation](../docs/architecture/backend-foundation.md)
and [P3 execution report](../docs/backend/p3-execution-report.md). The report
distinguishes implemented tests from tests actually executed in this environment.
