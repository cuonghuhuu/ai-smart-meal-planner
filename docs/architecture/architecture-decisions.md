# Phase P1 Architecture Decisions

## Decision record conventions

These decisions are accepted for the P1 baseline. A later decision may supersede
one, but should preserve its history and explain the migration. The project does
not add technology solely to anticipate hypothetical scale.

## ADR-001 — Flutter for Android and Web

**Status:** Accepted

**Context:** The product needs primary Android and Web clients, a four-person team
must minimize duplicate UI work, and the project stack is constrained to
Flutter/Dart.

**Decision:** Build one Flutter application with adaptive Android/Web
presentation, shared feature logic, and platform-specific adapters only where
behavior genuinely differs.

**Rationale:** Flutter provides one typed codebase and UI system for both required
targets while still supporting platform-aware navigation, storage, input, and
responsive behavior.

**Consequences:** The team must test both platforms, avoid mobile-only assumptions,
and design Web URLs, keyboard/focus behavior, and responsive layouts deliberately.
Some authentication/storage adapters will differ by platform.

**Rejected alternatives:** Separate native Android and Web applications duplicate
features and exceed team capacity. React, Angular, Vue, React Native, and Kotlin UI
violate the approved frontend stack.

## ADR-002 — Spring Boot as the primary backend boundary

**Status:** Accepted

**Context:** The Java Technology course requires a substantial Java application,
and the system needs one authoritative boundary for clients, workflows, security,
validation, and transactions.

**Decision:** Use a Maven-built Spring Boot application with Spring Web, Spring
Data JPA/Hibernate, Jakarta Validation, and Spring Security as the only public
application API.

**Rationale:** Spring Boot supports the required layered Java architecture and
integrates mature HTTP, validation, persistence, transaction, and security
capabilities. A single public boundary avoids duplicating policy across services.

**Consequences:** Java owns public contracts and orchestrates AI calls. Controllers
remain thin; services own workflows; repositories are private to modules. The
backend must translate Python failures into safe public behavior.

**Rejected alternatives:** Exposing Python as a second public backend would split
security and business authority. Replacing Spring Boot with another framework
violates the locked stack.

## ADR-003 — Python as a specialized AI service

**Status:** Accepted

**Context:** Recommendation, heuristic reasoning, optimization/search, evaluation,
and future ML benefit from Python's scientific ecosystem, but ordinary application
logic does not.

**Decision:** Use a small internal Python service, preferably FastAPI for HTTP,
that exposes typed versioned computation endpoints and contains algorithm code
independent of its API framework.

**Rationale:** Python gives the AI course a clear, testable home for genuine and
explainable algorithms while Java remains the application authority. The boundary
supports independent algorithm evaluation and future model inference.

**Consequences:** The team owns one additional deployable and an internal contract.
Java must supply normalized snapshots and validate responses. Python remains
stateless for product traffic, has no MySQL credentials, and does not own CRUD,
users, sessions, or accepted plans.

**Rejected alternatives:** Implementing all AI in Java reduces access to suitable
optimization/ML tooling and weakens course separation. An external LLM wrapper is
not an AI algorithm and cannot replace scoring, ranking, search, optimization, or
evaluation.

## ADR-004 — MySQL is accessed through Java only

**Status:** Accepted

**Context:** Persisted data needs one owner for integrity, authorization,
transactions, migration, and audit. Multiple writers would couple schemas and
produce inconsistent enforcement.

**Decision:** Use MySQL as the durable system of record. Only Spring Boot accesses
it through JPA/Hibernate repositories and controlled migrations. Flutter and
Python have no database connection or credentials.

**Rationale:** A single persistence owner keeps authorization and transaction
boundaries enforceable and prevents client/service coupling to schema details.

**Consequences:** Python computations receive request snapshots and return results
to Java. Java persists selected validated results and provenance. Reporting or
offline evaluation access must use approved exports/read paths rather than a new
uncontrolled writer.

**Rejected alternatives:** Direct Flutter access exposes credentials and bypasses
business/security controls. Direct Python writes make AI failures transactional
and create competing data ownership. A separate AI product database is premature.

## ADR-005 — REST/JSON for initial communication

**Status:** Accepted

**Context:** Flutter-to-Java and Java-to-Python require understandable contracts,
simple debugging, and tooling appropriate for a small team. Initial recommendation
and bounded planning flows are request/response interactions.

**Decision:** Use HTTPS REST/JSON for public client APIs and internal AI APIs, with
major versions in paths, typed schemas, request IDs, explicit timeout/error
behavior, and generated/published API documentation in later phases.

**Rationale:** REST is broadly supported by Flutter, Spring Boot, and FastAPI and
keeps course demonstrations and contract testing approachable.

**Consequences:** Payload sizes and synchronous computation time must be bounded.
Breaking changes require versioning. Java owns public error mapping; internal AI
errors are not leaked.

**Rejected alternatives:** gRPC adds schema/tooling and browser considerations
without a current benefit. GraphQL is unnecessary for the initial domain flows.
Message queues add operational complexity and are deferred until measured
long-running workloads justify asynchronous jobs.

## ADR-006 — Java is a modular monolith

**Status:** Accepted

**Context:** The domain has multiple capabilities, but four people cannot justify
the deployment, observability, distributed transaction, and contract overhead of
many backend services.

**Decision:** Organize the Spring Boot application into cohesive feature modules
with private repositories/entities and explicit module APIs, all deployed as one
Java application.

**Rationale:** A modular monolith gives clear academic and maintenance boundaries
without distributed-system cost. Module rules can be tested and later extraction
remains possible if evidence appears.

**Consequences:** The team must actively prevent package/repository leakage and
cyclic dependencies. Cross-module use goes through application APIs or stable
events. One deployment scales Java modules together initially.

**Rejected alternatives:** A package-by-technical-layer monolith makes feature
ownership and dependencies unclear. Separate services for authentication, pantry,
recipe, nutrition, and notification are unnecessary microservices at this scale.

## ADR-007 — Separate only the AI compute boundary

**Status:** Accepted

**Context:** ADR-006 avoids microservice sprawl, while ADR-003 needs a Python
runtime and an independently testable algorithm lifecycle. This could appear
inconsistent without a clear extraction rule.

**Decision:** Keep all ordinary backend domains in the Java modular monolith and
separate exactly one Python AI compute service because it has a different language,
dependency ecosystem, evaluation lifecycle, and scaling profile. This is a
bounded exception, not a move to broad microservices.

**Rationale:** The split creates educational and technical value unavailable from
an internal Java package while containing distributed-system complexity to one
well-defined adapter and contract.

**Consequences:** Java remains authoritative and can provide a deterministic
degraded path. Python cannot expand into general CRUD or direct persistence. Any
new deployable requires a new ADR demonstrating independent deployment/scale or
ownership needs that outweigh operational cost.

**Rejected alternatives:** Embedding Python in the Java process complicates build,
deployment, and isolation. Splitting every business capability because one service
is separate would multiply failure modes without value.

## Decision implications summary

Together these records establish a deliberately small distributed system:

```text
One cross-platform client
        -> one public modular Java backend
        -> one Java-owned relational database
        -> one internal specialized AI compute service
```

The [system architecture](system-architecture.md) applies these decisions to
module dependencies, data ownership, product flows, security, and resilience.
