# AI Smart Meal Planner — System Architecture

- **Phase:** P1
- **Status:** Accepted baseline
- **Scope:** Architecture and boundaries; no application implementation

## 1. Goals and constraints

AI Smart Meal Planner supports food and recipe discovery, pantry inventory,
nutrition goals, personalized recommendations, and meal-plan generation. The
architecture must demonstrate genuine AI techniques for the Artificial
Intelligence course and maintainable Java application design for the Java
Technology course.

The design follows these constraints:

- Flutter/Dart serves Android and Web from one client codebase.
- Spring Boot is the public application/backend boundary.
- MySQL is accessed only by Java through JPA/Hibernate and controlled migrations.
- Python exposes a clean internal HTTP API for AI computation.
- Python contains explainable scoring, ranking, reasoning, search, optimization,
  or future evaluated ML models; it is not an LLM wrapper.
- Flutter never accesses MySQL directly and normally never calls Python directly.
- The Java application begins as a modular monolith. The Python AI service is the
  only intentional service split.
- The design favors explicit boundaries a four-person team can build and test.

The system, container, request-flow, and module views are maintained as Mermaid
sources in the [diagram index](../diagrams/README.md).

## 2. System context

### Actors and systems

| Element | Role | Trust/boundary notes |
| --- | --- | --- |
| End user | Manages identity, profile, goals, pantry, recipes, recommendations, and meal plans from Android or a browser. | Untrusted input source; every request is authenticated and/or validated as appropriate. |
| Flutter client | Presents responsive Android/Web experiences and calls the public Java REST API. | No database credentials, direct SQL, or direct AI-service dependency. |
| Spring Boot backend | Enforces the public API, authentication, authorization, business workflows, validation, transactions, and persistence. | Primary application trust boundary and system of record. |
| MySQL | Stores authoritative application and audit state. | Private data tier; reachable by the Java backend, not clients or Python. |
| Python AI service | Computes recommendations, scores, rankings, substitutions, and optimized candidate plans. | Internal compute boundary; does not own user accounts or primary business records. |
| Future external integrations | Optional nutrition catalogs, identity providers, notification delivery services, or food-data providers. | Accessed through Java-owned adapters; data is validated and provider failures are isolated. |

Optional integrations are not required for P1 and do not bypass Java. A future
model registry or offline evaluation store may support the AI lifecycle, but it
must not become a second source of truth for application domain state.

## 3. Container architecture

| Container | Responsibilities | Owns/does | Must not do |
| --- | --- | --- | --- |
| Flutter application | UI composition, local interaction state, navigation, responsive layouts, input formatting, and calls to versioned Java APIs. | User-facing presentation and short-lived client state. | Access MySQL; embed server secrets; make authoritative authorization decisions; normally call Python. |
| Spring Boot application | Public REST API, identity/session lifecycle, authorization, orchestration, business rules, DTO validation, domain persistence, transactions, and AI/external adapters. | All authoritative workflows and persisted domain state. | Put business logic in controllers; expose entities as public contracts; treat AI output as trusted without validation. |
| MySQL database | Durable relational storage with integrity constraints, indexes, and transaction support. | Authoritative application records and selected auditable AI artifacts. | Accept direct client or AI-service connections. |
| Python AI service | Stateless or reproducible AI computation behind an internal versioned API; algorithm evaluation and explainability metadata. | Candidate generation, scoring, ranking, constraint solving, search/optimization, and future ML inference. | Own authentication, CRUD workflows, user records, transactions, or primary persistence. |

### Deployment posture

Initially, each container has one deployable instance per environment. The
Spring Boot application and Python service are designed to be horizontally
replicable after they are made stateless; MySQL remains the durable state tier.
No queue, cache, API gateway, or service mesh is introduced until measured need
justifies its operational cost.

## 4. Java backend architecture

### 4.1 Structure

Java uses a modular monolith organized by business feature. Within a feature,
the normal call direction is:

```text
Controller -> Application/Service -> Repository port -> JPA adapter -> Entity/Domain
```

For simple CRUD, a service may coordinate validation and a repository without
extra domain abstractions. Complex planning or authorization rules belong in
focused domain/application services, not controllers or JPA entities by default.

### 4.2 Layer roles

| Concern | Role |
| --- | --- |
| Controllers | Translate HTTP into application calls and response DTOs. They contain no database access or complex business logic. |
| Services/application use cases | Enforce workflows, authorization-sensitive business checks, transaction boundaries, and coordination across module APIs. |
| Repositories | Provide persistence operations for the owning module. Other modules do not query another module's tables through its repository. |
| Entities/domain objects | Represent persisted identity and domain invariants. Public API clients never depend directly on their shape. |
| DTOs | Define versioned request/response contracts and isolate API evolution from persistence. Separate internal AI DTOs prevent contract leakage. |
| Validation | Jakarta Validation handles structural constraints at the boundary; services enforce contextual and cross-record invariants. Database constraints provide the final integrity layer. |
| Mapping | Small explicit mappers convert DTOs, domain objects, entities, and AI contracts. Mapping logic does not make business decisions. |
| Configuration | Typed configuration binds environment-provided settings, validates required values at startup, and supplies client/persistence settings. |
| Security | Central Spring Security configuration authenticates requests, enforces route/method authorization, and standardizes CORS/CSRF/token behavior. |
| Exception handling | Central advice maps known exceptions to stable `application/problem+json` responses; unexpected errors receive a safe generic message and correlation ID. |
| Integration clients | Dedicated adapters call Python and future providers with typed contracts, bounded timeouts, observability, and error mapping. |

### 4.3 API versioning

Public endpoints begin under `/api/v1`. The internal AI contract begins under
`/internal/ai/v1`. A breaking contract change creates a new major path while the
old version is supported for an announced transition. Additive fields remain
backward compatible. API documentation must describe status codes, validation
errors, authentication, pagination, and examples.

### 4.4 Transaction boundaries

Transactions are declared at application/service operations that update a
consistent business aggregate. Remote Python calls do not occur while holding a
database transaction open. Java loads and validates a snapshot, ends the read
transaction, calls Python, validates the response, then starts a short write
transaction only if an auditable result or accepted plan must be persisted.

## 5. Flutter architecture

Flutter uses a feature-oriented structure that scales across Android and Web
without creating a framework inside the application.

### 5.1 Boundaries

| Boundary | Responsibility |
| --- | --- |
| Presentation/UI | Screens, view composition, accessibility, formatting, loading/empty/error states, and user events. Widgets do not make raw HTTP calls. |
| State management | Owns screen/feature state and invokes use cases or repositories. A single project-wide approach should be selected during Flutter foundation work. |
| Feature modules | Group presentation, application/domain logic where useful, data contracts, and tests by capability such as pantry or meal planning. |
| Domain/application logic | Holds client-side rules that improve interaction, such as form state or composing a use case. Server-authoritative rules are never duplicated as security controls. |
| Repositories/data layer | Offers typed feature operations and maps API DTOs/errors to client models. It is the boundary between feature logic and remote/local data sources. |
| REST API clients | Apply the base URL, API version, JSON serialization, access token, request ID, timeouts, and common error decoding. |
| Routing/navigation | Central route definitions support authenticated guards, Android navigation, Web URLs, deep links, and unknown-route handling. |
| Configuration | Compile/deploy-time non-secret values select API origins and environment labels. Secrets are never bundled in Flutter. |
| Shared UI/design system | Reusable tokens, themes, accessibility conventions, and truly cross-feature widgets. Feature-specific widgets remain with their feature. |

### 5.2 Feature shape

A feature may contain `presentation`, `application`, `domain`, and `data`
boundaries, but only when their behavior warrants separation. Features depend on
small shared platform/UI foundations, not on one another's internal widgets or
transport DTOs. Cross-feature journeys are composed at the application and
routing level.

### 5.3 Responsive Android and Web behavior

- Define width-based layout breakpoints from observed product needs rather than
  device-name checks.
- Use constrained content widths, flexible grids, and adaptive navigation so the
  same workflow works with touch, keyboard, mouse, browser resizing, and text
  scaling.
- Preserve focus order, semantic labels, contrast, and minimum interactive target
  sizes.
- Avoid assuming mobile-only lifecycle or storage behavior in shared code.
- Test representative narrow phone, wide phone/tablet, and desktop browser sizes.

## 6. Python AI architecture

### 6.1 Module boundaries

| Module/boundary | Responsibility |
| --- | --- |
| API | FastAPI endpoints, typed request/response schemas, size limits, validation, health/readiness, and translation to application calls. No algorithm logic. |
| Recommendation | Coordinates candidate eligibility, feature construction, scoring, ranking, diversity, and explanations. |
| Scoring | Calculates interpretable component scores such as preference fit, nutrition fit, pantry coverage, expiry benefit, cost, and preparation fit. |
| Ranking | Orders eligible candidates with deterministic tie-breaking and optional diversity re-ranking. |
| Meal-plan optimization | Searches combinations across days/meals under nutrition, preference, repetition, pantry, time, and budget constraints. |
| Pantry reasoning | Measures ingredient availability, missing quantities, expected consumption, and pantry utilization. |
| Expiry prioritization | Increases utility for safe, soon-to-expire ingredients using explicit urgency functions and expiry confidence. It never overrides food-safety exclusions. |
| Ingredient substitution | Searches a typed substitution graph and filters alternatives by allergens, diet, recipe role, availability, and nutritional effects. |
| Nutrition/constraint evaluation | Applies hard eligibility constraints and calculates soft deviations/penalties with traceable units. |
| Evaluation | Runs reproducible datasets and reports ranking, constraint, nutrition, waste, diversity, and latency metrics by algorithm version. |
| Tests | Unit tests for calculations and edge cases; property/constraint tests; API contract tests; golden deterministic datasets; performance checks where useful. |

Shared Python types and utilities remain small. Modules exchange typed, plain
data models and do not import FastAPI objects into algorithm code.

### 6.2 Technique classification

| Technique | Appropriate use | Examples |
| --- | --- | --- |
| Deterministic rules | Non-negotiable eligibility and safety/business constraints. | Allergen exclusion, diet incompatibility, invalid quantity, expired/unsafe ingredient exclusion. |
| Heuristics | Explainable approximations where an exact solution is unnecessary or costly. | Expiry urgency, pantry coverage, preparation-time fit, weighted nutrition deviation. |
| Scoring/ranking | Personalized ordering of eligible candidates. | Weighted multi-criteria score, normalized components, deterministic tie-break, diversity re-ranking. |
| Optimization/search | Multi-meal decisions with interacting constraints. | Backtracking/beam search initially; constraint programming, integer optimization, or local search when evaluation justifies it. |
| Optional ML models | Learned ranking or preference prediction after sufficient consented data and a measurable baseline exist. | Learning-to-rank or preference probability, versioned and compared with heuristic baselines. |
| Optional LLM assistance | Non-authoritative language assistance only, isolated from core decisions. | Drafting an explanation or normalizing free text, followed by schema/rule validation and clear labeling. |

An LLM is never the meal-planning algorithm, the source of nutrition truth, or a
bypass around constraints. Every AI result includes algorithm/model version,
component scores or constraint evidence, and warnings needed for Java to validate
and explain it.

### 6.3 Academic evaluation

Each implemented algorithm must document its problem, inputs, outputs,
assumptions, choice, complexity/trade-offs, and evaluation. Candidate measures
include constraint-satisfaction rate, nutrition-target deviation, pantry
utilization, projected expiry waste reduction, diversity/repetition, Precision@K
or nDCG when relevance labels exist, coverage, deterministic repeatability, and
latency. A simple rules/weighted-score baseline is retained for comparison with
later algorithms or ML.

## 7. Inter-service communication

### 7.1 Flutter to Java

- HTTPS JSON REST is the only normal client entry point.
- Flutter calls versioned `/api/v1` resources and supplies an access token for
  protected operations.
- Java performs schema validation, authentication, authorization, and contextual
  validation even if Flutter already checked the form.
- Responses use documented DTOs and `application/problem+json` for errors.
- Flutter maps errors into actionable field, authentication, conflict, degraded,
  or retry states without displaying internal details.

### 7.2 Java to Python

- Java calls versioned `/internal/ai/v1` JSON endpoints through one AI integration
  adapter.
- Contracts use stable domain identifiers and normalized values/units. Python
  receives only data needed for the computation.
- Java never forwards an end-user access/refresh token. Service authentication
  uses environment-provided credentials or workload identity when deployment
  supports it, plus network restriction and TLS.
- Python returns candidates and evidence; Java revalidates identifiers,
  authorization scope, hard constraints, numeric bounds, and algorithm version.
- The contract is idempotent for a supplied request ID and deterministic seed
  where the selected algorithm permits reproducibility.

### 7.3 Java to MySQL

- Spring Data JPA/Hibernate provides parameterized access through module-owned
  repositories.
- Migrations are version controlled and applied in a controlled deployment step.
- Constraints, transaction isolation, and indexes protect integrity and expected
  access patterns.
- Database credentials exist only in backend runtime configuration.

### 7.4 Authentication and token flow

1. Flutter sends credentials or a future identity-provider proof to Java over
   HTTPS.
2. Java validates the identity, records the session as needed, and issues a
   short-lived access token plus a renewable session/refresh mechanism.
3. Android stores renewable credentials in OS-backed secure storage. On Web, the
   preferred renewable credential is a `Secure`, `HttpOnly`, appropriately
   `SameSite` cookie; access tokens remain short-lived and preferably in memory.
4. Flutter sends the access token to Java. Java validates signature/session,
   expiry, audience, and authorization for every protected operation.
5. Cookie-authenticated mutation/refresh endpoints apply CSRF protection and an
   explicit CORS allowlist. Logout/revocation invalidates the renewable session.
6. Java calls Python using service identity, not the user's token. A pseudonymous
   subject identifier is sent only when an algorithm genuinely needs continuity.

The concrete token format and identity provider are deferred to a security ADR,
but these boundaries are mandatory.

### 7.5 Timeouts, retries, fallback, and errors

- All network calls have configurable connect and total timeouts. Initial budgets
  should target at most 1 second to connect, about 3 seconds for recommendation,
  and about 10 seconds for bounded plan optimization, always within the client
  request budget and refined with measurements.
- Java may retry an idempotent AI request once for a transient connection or 5xx
  failure with short jitter. It does not retry validation failures or exhaust the
  user-facing latency budget.
- A circuit breaker is added only when failure testing or deployment experience
  justifies it; timeout/fallback behavior is required from the first integration.
- If advanced ranking fails, Java may request/use a documented deterministic
  baseline or return a clearly marked degraded result. If no validated safe plan
  exists, Java returns a recoverable service-unavailable problem rather than
  inventing or silently accepting a plan.
- Python validation failures map to an internal contract error; expected
  infeasible constraints map to a domain result, not a 500. Java owns the public
  status and safe message.
- Flutter may retry only safe/idempotent actions automatically and preserves user
  input when recovery is manual.

### 7.6 Correlation IDs and logs

Java accepts a well-formed client request ID or generates one, returns it in
`X-Request-ID`, and passes it to Python. Both services include it in structured
logs and error metadata. IDs are opaque and never contain user data. Logs avoid
credentials, tokens, raw health details, and unnecessary personal data.

## 8. Data ownership

MySQL through Java is the system of record. Python receives a computation
snapshot and returns a result; its memory, caches, temporary solver state, and
evaluation artifacts are not authoritative application persistence.

| Data | Authoritative owner | Persistence policy |
| --- | --- | --- |
| Users | Java authentication/user modules | Permanent MySQL record subject to lifecycle and privacy policy. |
| Authentication data | Java authentication module | Password hashes, sessions/refresh records, roles, and revocation state in MySQL as required; never Python. |
| Profiles | Java user/profile module | MySQL; includes validated demographics/settings needed by the product. |
| Foods and ingredients | Java food module | MySQL canonical catalog and source/provenance metadata. |
| Recipes | Java recipe module | MySQL recipe definitions, ingredients, instructions, ownership, and visibility. |
| Pantry inventory | Java pantry module | MySQL quantities, units, acquisition/expiry metadata, and optimistic-lock/version information. |
| Preferences and goals | Java user/profile and nutrition modules | MySQL; explicit user inputs are distinguished from inferred preferences. |
| Nutrition data | Java nutrition/food modules | MySQL canonical values, units, serving basis, source, and revision. Python receives normalized snapshots. |
| Meal plans | Java meal-planning module | Draft/accepted plans and plan items in MySQL. Only a Java-validated result may be saved. |
| Recommendation requests | Java recommendation module | Store minimal request metadata, constraints, consented feature snapshot reference/hash, algorithm version, and status when audit/reproducibility needs justify it. |
| Recommendation results | Java recommendation module | Persist accepted/displayed result summaries and evidence when needed for history/evaluation; do not persist every intermediate candidate by default. |
| AI-derived scores | Java recommendation module after validation | Store snapshots with component definition, scale, algorithm/model version, and timestamp only when needed; never overwrite source facts. |
| History/audit data | Owning Java module with audit conventions | Append-oriented MySQL records for security or important domain changes, with retention and access controls. |
| Notification state | Java notification module | Preferences, scheduled intents, delivery status, and read/dismiss state in MySQL; external provider receipts are normalized. |

Transient Python data includes candidate feature matrices, search frontiers,
solver variables, temporary embeddings, intermediate scores, and request-local
caches. Reproducible offline evaluation datasets and model artifacts may later use
a dedicated controlled store, but Java/MySQL remains authoritative for product
records.

## 9. Domain and module boundaries

### 9.1 Java modules

| Module | Owns | Permitted dependencies |
| --- | --- | --- |
| Authentication | Credentials, sessions, roles, authentication policies. | Shared technical foundation only. |
| User/profile | Profile, preferences, allergies, dietary settings, goals. | Authentication identity reference; no credential internals. |
| Food | Food/ingredient catalog and provenance. | Shared foundation. |
| Nutrition | Nutrient definitions, normalized calculations, goal evaluation contracts. | Food public API/identifiers. |
| Recipe | Recipe lifecycle and ingredient composition. | Food and nutrition public APIs. |
| Search | Read-oriented food/recipe query orchestration and result projection. | Food and recipe public APIs. |
| Pantry | User inventory, quantities, expiry, and pantry history. | User identity and food public APIs. |
| Recommendation | Request lifecycle, candidate orchestration, validated result/evidence persistence. | User/profile, food, recipe, pantry, nutrition, and AI integration ports. |
| Meal planning | Plan lifecycle, constraints, generation orchestration, acceptance, and plan persistence. | User/profile, recipe, nutrition, recommendation contracts, and AI integration ports where optimization is direct. |
| Notification | Notification preferences, scheduling intents, and delivery status. | Stable application events/contracts and user identity reference. |
| AI integration | Typed internal client, resiliency, AI contract mapping, and service health. | Technical foundation only; it owns no business records. |

### 9.2 Dependency rules

- Dependencies point from orchestration modules toward stable capability APIs;
  repositories and entities are private to the owning module.
- Search is read-only and cannot become a back door for updating food or recipes.
- Recommendation produces candidate/result contracts; meal planning may consume
  them. Recommendation never depends on meal planning, preventing a cycle.
- Pantry and meal-planning actions publish stable in-process application events;
  notification observes them. Core modules do not call notification internals.
- AI integration is an adapter, not a domain. Recommendation and meal planning
  decide why/when to call it and validate the returned domain meaning.
- Shared code contains technical primitives and cross-cutting contracts, not a
  miscellaneous collection of business logic.
- Cross-module writes go through the owning module's service/API. Compile-time
  architecture tests should enforce the intended direction when Java starts.

The [module dependency diagram](../diagrams/java-module-dependencies.mmd) shows the
allowed high-level direction.

## 10. Main product flows

### 10.1 User authentication

1. Flutter validates form completeness and sends credentials to Java.
2. Java validates the request, authenticates against Java-owned records or a
   configured provider, applies throttling/audit policy, and issues session tokens.
3. Flutter stores/uses tokens according to the platform policy in section 7.4.
4. Java authorizes each later request; neither MySQL nor Python is exposed.

### 10.2 Food or recipe search

1. Flutter sends a sanitized query, filters, sort, and page request to Java.
2. Java validates limits and visibility, then the search module queries food and
   recipe capabilities using indexed, parameterized persistence.
3. Java maps results to a stable DTO and Flutter presents responsive result states.
4. AI ranking is optional for later personalized search; base search remains
   available if Python is unavailable.

### 10.3 Pantry update

1. Flutter submits a create/update/consume request with quantity, unit, dates, and
   the last-known version where concurrent editing matters.
2. Java authenticates ownership and validates food, unit, quantity, and date
   invariants.
3. The pantry service updates MySQL in one transaction and records relevant
   history.
4. Java returns the new representation/version. A notification intent may be
   derived after commit without coupling pantry persistence to delivery.

### 10.4 Personalized recommendation

1. Flutter asks Java for recommendations and supplies user-selected constraints.
2. Java authorizes the subject and loads profile, preferences, nutrition goals,
   eligible recipes/foods, and pantry state.
3. Java applies authoritative validation and builds a minimal normalized AI
   request snapshot.
4. Python filters hard constraints, constructs features, scores/ranks candidates,
   and returns explanations and algorithm version.
5. Java verifies returned IDs and constraints, persists only required audit/history
   data, and returns public DTOs with any degraded-status indicator.

### 10.5 Meal-plan generation

1. Flutter posts horizon, meal slots, nutrition targets, exclusions, time/budget,
   and pantry preferences to Java.
2. Java validates limits and constructs a consistent candidate/constraint snapshot.
3. Python performs bounded optimization/search and returns a feasible candidate,
   objective components, unmet soft constraints, and algorithm metadata.
4. Java rechecks hard constraints and current entity availability. It returns a
   preview; generation alone does not mutate pantry or create an accepted plan.
5. If infeasible, Java returns structured constraint feedback the user can relax.

### 10.6 Java-to-AI recommendation request

The end-to-end request sequence is captured in the
[main request/data-flow diagram](../diagrams/main-request-data-flow.mmd). The call
occurs outside a database transaction, carries a request ID and service identity,
and contains no end-user token or unnecessary personal data.

### 10.7 Saving an accepted meal plan

1. Flutter submits the preview/result identifier plus any user edits to Java.
2. Java reloads referenced recipes/foods and current user constraints, verifies
   ownership, expiry/version, and hard invariants, and recalculates authoritative
   summary values where necessary.
3. The meal-planning service saves the plan and items in one MySQL transaction,
   linking algorithm/version provenance if the plan originated from AI.
4. After commit, Java may create notification intents. Pantry quantities are not
   silently consumed merely because a future plan was accepted.

## 11. Non-functional architecture

### Security

- Deny by default for protected APIs; enforce ownership and roles in Java.
- Use TLS, strong password hashing where local credentials exist, short-lived
  access tokens, rotation/revocation, CSRF protection for cookie flows, and a
  strict environment-specific CORS allowlist.
- Validate all inputs, bound collection/page sizes and algorithm horizons, and
  protect authentication/expensive AI endpoints with rate limits.
- Apply least-privilege database and service identities. Python has no database
  account. Flutter contains no server credentials.
- Treat AI output and external provider data as untrusted input. Record algorithm
  provenance and prevent unsafe/allergen constraints from becoming soft penalties.
- Avoid sensitive data in URLs, logs, error bodies, or AI requests.

### Configuration and secrets

Non-secret defaults may be version controlled. Secrets and environment-specific
credentials come from environment variables or a deployment secret manager;
local examples contain placeholders only. Typed startup validation fails safely
when required configuration is absent. GitHub credentials remain in GitHub
Secrets. Existing repository secret-deny rules and scanners remain enabled.

### Validation and error handling

Validation is layered across Flutter usability checks, Java API schema and
business rules, MySQL constraints, Python schema/algorithm bounds, and Java
post-validation of AI results. Public errors use stable codes, safe detail,
field violations where appropriate, and a correlation ID. Stack traces and
provider/internal payloads are not returned.

### Logging and observability

Use structured logs with timestamp, severity, service, environment, request ID,
route/template, outcome, latency, and safe algorithm version. Track request/error
rates, Java/MySQL latency, AI latency/timeouts/infeasibility, and algorithm quality
metrics. Health endpoints separate liveness from readiness and do not disclose
secrets or internals. Distributed tracing is optional until multiple call paths
justify it; correlation is required.

### Performance and scalability

- Paginate and index catalog/history queries; avoid N+1 ORM access.
- Bound recommendation candidates, plan horizon, solver time, and payload size.
- Keep Java/Python stateless so instances can scale horizontally later.
- Cache only measured read hotspots with explicit staleness/invalidation rules;
  do not add a cache in P1.
- Run expensive offline model training/evaluation outside synchronous API paths.
- Consider asynchronous plan generation only if bounded synchronous requests fail
  measured user-experience targets.

### Resilience

Java-to-Python calls use timeouts, cancellation, bounded retry, idempotency, safe
fallback, and explicit degraded responses. Failure tests cover unavailable,
slow, malformed, and infeasible AI responses. Bulkheads/circuit breakers are
incremental additions based on evidence, not prerequisites for the first build.

### Testability

- Flutter: unit tests for feature state/mapping, widget tests for responsive and
  interaction behavior, and integration tests for critical journeys.
- Java: unit tests for rules/services, repository tests, security/controller
  tests, architecture dependency tests, and AI-client contract/integration tests.
- Python: deterministic unit/property tests, API schema tests, solver edge cases,
  golden datasets, and evaluation regression thresholds.
- Cross-system: contract tests and a small end-to-end suite exercise authentication,
  recommendation, degradation, and accepted-plan persistence.

### Maintainability

Use feature ownership, documented public module APIs, explicit DTOs, migrations,
consistent units/time zones, small functions, and automated formatting/static
analysis. Add dependencies only after existing tools or the standard library are
shown insufficient. Architecture decisions and API documentation change with the
implementation they govern.

### Database consistency

Use database constraints for uniqueness/references/ranges where appropriate,
optimistic locking for concurrent user-edited records, and short service-owned
transactions. Use UTC instants for events and explicit local date/time semantics
for meal schedules and expiry. Plan acceptance is atomic; external calls and
notification delivery occur outside its transaction.

## 12. Java versus Python responsibility boundary

| Java remains authoritative for | Python specializes in |
| --- | --- |
| Authentication and token/session lifecycle | Candidate feature calculation |
| Authorization and ownership | Interpretable nutritional/preference scoring |
| Public and internal contract orchestration | Candidate ranking and diversity |
| Business workflows and CRUD | Pantry/expiry heuristic reasoning |
| Input and AI-output validation | Ingredient substitution search |
| Persisted domain state and history | Constraint evaluation for computation |
| Database transactions and concurrency | Meal-plan optimization/search |
| Notification state and external integrations | Future evaluated ML inference |
| Final acceptance of recommendations/plans | Offline algorithm/model evaluation |

Java decides whether a computation is permitted, supplies a validated snapshot,
checks the result, and commits accepted state. Python decides how to compute the
best candidate under the supplied contract. Ordinary CRUD, sessions, recipe
ownership, pantry updates, and plan persistence never move to Python merely
because the product includes AI.

## 13. Expected repository alignment

Later implementation phases may create the following structure when work exists:

```text
AI-Smart-Meal-Planner/
├── mobile_app/
├── backend/
├── ai_service/
├── database/
│   ├── schema/
│   └── seed/
├── docs/
│   ├── requirements/
│   ├── architecture/
│   ├── api/
│   └── diagrams/
├── tests/
├── AGENTS.md
├── README.md
└── .gitignore
```

P1 creates only `docs/architecture`, `docs/diagrams`, and the README links.
Empty application directories are intentionally not created. Each later module
must arrive with its build configuration, tests, and relevant documentation in a
focused phase.

## 14. Evolution guardrails

- Do not split Java business modules into separate services without measured
  scaling, ownership, or deployment pressure and a new ADR.
- Do not allow Python to become a parallel backend or database owner.
- Do not introduce direct Flutter-to-Python or Flutter-to-MySQL paths.
- Preserve a deterministic, explainable AI baseline when adding ML.
- Version algorithms/models and the internal/public API contracts independently.
- Prefer synchronous REST for initial calls; add asynchronous infrastructure only
  for demonstrated long-running or throughput needs.

The rationale behind these guardrails is recorded in
[Architecture Decisions](architecture-decisions.md).
