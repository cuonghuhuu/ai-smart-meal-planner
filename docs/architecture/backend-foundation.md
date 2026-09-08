# P3 backend foundation decisions and API contract

P1 and P2 remain authoritative. No P2 SQL is modified. Java is the only MySQL
client; Python remains stateless compute over a future internal HTTP contract.

## Stack selection

Spring Boot **3.5.16** is a stable release whose
[official requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
explicitly support Java 25 and Maven 3.6.3+. The actual local environment is
Oracle JDK 25.0.4 and Maven 3.9.16. This foundation chooses the compatible 3.5
line; future upgrades should verify the entire dependency set and rerun MySQL
integration tests. Maven Central successfully resolved this parent and BOM.

The Boot BOM manages Spring Framework 6.2.19, Spring Security 6.5.11,
Hibernate 6.6.53.Final, Flyway 11.7.2, Testcontainers 1.21.4 and JUnit Jupiter
5.12.2. Connector/J is explicitly **8.4.0** to preserve P2 DB-ADR-016's 8.x
driver baseline. MySQL integration tests pin **mysql:8.4.11**, the same server
release P2 validated. Compatibility at runtime is an acceptance test, not a
claim inferred from dependency resolution.

Dependencies are limited to the requested Spring Web, JPA, Security, Validation,
Actuator and testing libraries, Flyway core and its required MySQL database
module, Connector/J, and Testcontainers JUnit/MySQL modules. There is no H2,
external search engine, cache, messaging system, or architecture framework.

## Actual package structure

```text
com.smartmealplanner
  MealPlannerApplication
  auth
    SecurityConfiguration
  food
    Food, FoodCategory
    FoodRepository, FoodCategoryRepository
  mealplanning
    MealPlanDateValidator, PlanEntryDateRequest
  shared.web
    ApiProblems, ApiExceptionHandler, InvalidRequestException, RequestIdFilter
```

Food entities and repositories are package-private. Public module operations
will be services returning explicit DTOs; other features must not access food
persistence classes. No JPA entity is a REST response. Controllers must call
application services, which own transactions and contextual validation.
Shared code contains only technical HTTP/error infrastructure.

The unimplemented boundaries retain P1's names and direction:

| Future package | Responsibility and permitted dependencies |
| --- | --- |
| `user` | Profile/preferences; auth identity reference only |
| `household` | Reserved concept, no new table or persistence mapping; P2 currently has `user_profiles.household_size` |
| `recipe` | Recipe composition through food/nutrition APIs |
| `search` | Read orchestration through food/recipe APIs |
| `pantry` | User inventory through identity/food APIs |
| `nutrition` | Nutrient calculations and targets through food APIs |
| `recommendation` | Candidate orchestration through profile/food/recipe/pantry/nutrition/AI APIs |
| `mealplanning` | Plan validation/lifecycle through profile/recipe/nutrition/recommendation APIs |
| `notification` | Stable events and user identity; domains do not call notification internals |
| `aiintegration` | Typed HTTP compute adapter; technical dependencies only, no database access |

These are conventions, not empty placeholder classes or new services.

## Representative persistence scope

Only `foods` and `food_categories` are mapped. Food demonstrates P2's internal
`BIGINT UNSIGNED AUTO_INCREMENT` as Java `Long` with `IDENTITY`, independent
`BINARY(16)` public UUID bytes, a lazy category relationship without cascade,
database-maintained `DATETIME(6)` fields mapped read-only, and `@Version` on the
existing unsigned BIGINT version column. Unmapped columns retain their P2
defaults and constraints. This is intentionally a partial table mapping; future
feature work must map needed fields faithfully before exposing workflows.

The Java numeric range is the nonnegative signed-Long subset of SQL's unsigned
BIGINT range. Numeric IDs never cross the public API. Java's standard random
UUID v4 is used without a new dependency; P2 prefers v7 for locality but does not
require it. The schema and opaque identifier strategy remain unchanged. Byte
order matches MySQL `BIN_TO_UUID(public_id)` with no swap flag.

SQL is packaged without filtering from the authoritative `database/` tree.
[Flyway's repeatable prefix setting](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-repeatable-sql-migration-prefix-setting)
supports `R001` as the prefix needed for the unchanged P2 filename. See the
[backend runbook](../../backend/README.md) for migration rules.

## Two validation levels

`PlanEntryDateRequest` requires a non-null `LocalDate` through Jakarta Bean
Validation. This is a structural contract example, not a published business
endpoint. `MealPlanDateValidator` rejects dates outside the inclusive parent
window and invalid/missing parent windows, including P2's 31-day maximum.

The future entry write service must load and authorize the parent in its write
transaction, invoke this validator before insertion/update, and serialize parent
window edits with entry writes (for example, using a parent lock). Parent-window
changes must also revalidate existing entries. Clients must not supply trusted
copies of the parent range. P3 establishes and tests the validator; it introduces
no meal-plan write path, trigger, duplicate dates, or schema change.

## HTTP and security contract

Public health endpoint: `GET /actuator/health`, JSON `{"status":"UP"}` when
healthy, with no component or database details. No business endpoint is
implemented. MVC test probes exist only under test sources.

All other routes require authentication. `/api/v1/admin/**` additionally requires
ADMIN. No generated default user or credential logging is enabled. The standard
delegating encoder produces salted bcrypt hashes with an algorithm prefix. CSRF
is retained, and the browser origin allowlist is supplied at runtime.

MVC advice and security authentication/access-denied handlers return
`application/problem+json` with standard `type`, `title`, `status`, `detail`,
plus stable `code` and `requestId` extensions:

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | Structural or contextual invalid request |
| 401 | `UNAUTHORIZED` | Authentication required |
| 403 | `FORBIDDEN` | Authorization/CSRF rejection |
| 404 | `NOT_FOUND` | Missing resource |
| 409 | `CONFLICT` | Integrity or optimistic-lock conflict |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected failure with generic detail |

No exception message, rejected value, SQL, stack trace or secret is included.
The advice extends Spring's `ResponseEntityExceptionHandler`, preserving framework
status classifications and protocol headers while replacing diagnostic bodies.
Missing query parameters/headers, invalid parameter types, and method-input
constraints therefore return safe 400 responses. Unsupported methods and media
types retain 405/415 respectively, including the `Allow` header for 405.
Spring's CORS processor rejects disallowed origins before MVC with its standard
403 response and no allow-origin header. It does not use the MVC problem body.

`X-Request-ID` accepts a canonical UUID-shaped string; absent/invalid headers are
replaced with a random UUID. The ID is returned in the response header and made
available in MDC during the request, then removed. The log level pattern includes
the request ID. Request bodies, credentials and arbitrary exception messages are
not logged by this foundation. Correlation IDs are diagnostic, never proof of
identity or authorization.

## Acceptance evidence design

- `SchemaResourceTest`: both packaged SQL files equal the source bytes.
- `MealPlanDateValidatorTest`: inclusive boundaries, inside/outside, invalid
  windows, missing values and Jakarta validation.
- `ApiFoundationTest`: Spring Security, CSRF, CORS, password encoder, MVC
  DTO/parameter/header/method validation, safe problem statuses, preserved
  method/media-type errors and request IDs. Uses the `test` profile.
- `BackendFoundationIT`: mandatory real MySQL Testcontainer; assert database
  empty before Flyway; assert two migrations; snapshot DDL before JPA and compare
  after startup; assert Hibernate validate, 53 tables, 125 checks, three FULLTEXT
  indexes and three STORED columns; rerun the seed and compare all 146 IDs/codes;
  persist and read representative data; reject a stale writer from a second
  persistence context; test real Actuator health.

Surefire and Failsafe fail if no tests are found. CI checks Docker and runs
`clean verify`, without skip flags, secrets, or replacement of the existing
pre-commit job. Actual local execution limitations are recorded separately.
