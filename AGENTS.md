\# AI Smart Meal Planner — Agent Instructions



\## 1. Project Mission



AI Smart Meal Planner is a university project designed for two courses:



\* Artificial Intelligence

\* Java Technology



The system must also be designed as a practical real-world application, not only as a classroom demo.



The application helps users manage food, ingredients, nutrition goals, pantry inventory, recipes, and personalized meal plans.



\---



\## 2. Core Architecture



This repository is a monorepo.



Main components:



\* `mobile\_app/`



&#x20; \* Flutter application

&#x20; \* Android and Web are primary targets



\* `backend/`



&#x20; \* Java

&#x20; \* Spring Boot

&#x20; \* REST API

&#x20; \* JPA / Hibernate

&#x20; \* Maven



\* `ai\_service/`



&#x20; \* Python

&#x20; \* AI recommendation and optimization algorithms



\* `database/`



&#x20; \* Database schema

&#x20; \* Seed data

&#x20; \* Migration-related documentation



\* `docs/`



&#x20; \* Requirements

&#x20; \* Architecture

&#x20; \* API documentation

&#x20; \* Diagrams



\* `tests/`



&#x20; \* Cross-system or integration test assets when appropriate



Do not change the core technology stack without explicit approval.



\---



\## 3. Technology Stack



\### Frontend



Use:



\* Flutter

\* Dart



Primary targets:



\* Android

\* Web



Do not introduce React, Angular, Vue, React Native, Kotlin UI, or another frontend framework unless explicitly requested.



\### Backend



Use:



\* Java

\* Spring Boot

\* Maven

\* Spring Web

\* Spring Data JPA

\* Jakarta Validation

\* MySQL



Do not replace Spring Boot with another Java backend framework unless explicitly requested.



\### AI Service



Use:



\* Python

\* FastAPI when an HTTP AI service is required

\* pytest

\* Ruff

\* type hints



AI functionality must contain explainable algorithms or models where academically relevant.



Do not label a feature as Artificial Intelligence merely because it calls an external LLM API.



\---



\## 4. Architecture Rules



Use clear separation of concerns.



Backend should generally follow:



Controller

→ Service

→ Repository

→ Entity / Domain



Controllers must not contain complex business logic.



Database access must not be written directly inside controllers.



AI algorithms must be isolated from UI code.



Flutter UI must not directly access the database.



Communication between Flutter and backend must occur through defined APIs.



Communication with the AI service must use clearly defined interfaces or APIs.



Avoid unnecessary microservices.



Prefer maintainable modular architecture over excessive abstraction.



\---



\## 5. AI Requirements



The AI portion of this project should progressively support features such as:



\* meal recommendation

\* personalized ranking

\* nutritional scoring

\* ingredient-based search

\* pantry-aware recommendations

\* expiry-aware food prioritization

\* constraint-based meal planning

\* heuristic search or optimization

\* alternative ingredient suggestions



When implementing an AI algorithm:



1\. Document the problem.

2\. Document inputs.

3\. Document outputs.

4\. Explain the algorithm.

5\. Explain why the algorithm was selected.

6\. Include measurable evaluation when possible.

7\. Add automated tests.



Never silently replace an academic AI algorithm with an LLM API call.



\---



\## 6. Security Rules



Never commit:



\* passwords

\* API keys

\* access tokens

\* private keys

\* JWT secrets

\* database credentials

\* production credentials

\* `.env` files containing secrets



Use:



\* environment variables

\* `.env.example`

\* GitHub Secrets for CI/CD credentials



Never print secrets in application logs.



Never hard-code credentials.



Validate all external input.



Use parameterized database access through JPA or safe query mechanisms.



Do not disable security mechanisms merely to make tests pass.



\---



\## 7. Git Rules



Never work directly on `main` for feature development.



Use branches such as:



\* `feat/...`

\* `fix/...`

\* `refactor/...`

\* `docs/...`

\* `test/...`

\* `chore/...`



Examples:



\* `feat/flutter-foundation`

\* `feat/backend-auth`

\* `feat/meal-recommendation`

\* `fix/login-validation`



Do not force push to `main`.



Do not rewrite shared Git history without explicit approval.



Do not merge a pull request while required tests are failing.



Keep commits focused and understandable.



Preferred commit style:



\* `feat: ...`

\* `fix: ...`

\* `docs: ...`

\* `test: ...`

\* `refactor: ...`

\* `chore: ...`



\---



\## 8. Multi-Agent Development Rules



This project may be modified using multiple AI coding agents, including Claude Code, Codex, and Antigravity CLI.



Agents must avoid editing the same working directory simultaneously.



Prefer isolated Git branches and Git worktrees for concurrent work.



Each agent should:



1\. inspect the repository before modifying files

2\. identify the relevant module

3\. make the minimum necessary changes

4\. run relevant tests

5\. report files changed

6\. report commands executed

7\. report test results

8\. report unresolved issues



Never assume another agent's uncommitted changes are safe to overwrite.



Do not delete another agent's work unless explicitly instructed.



\---



\## 9. Testing Requirements



Every significant feature must include appropriate testing.



Flutter:



\* `dart format`

\* `flutter analyze`

\* `flutter test`



Java:



\* Maven test suite

\* JUnit

\* integration testing where appropriate



Python:



\* Ruff

\* pytest

\* type checking where configured



A feature is not considered complete only because it runs manually.



\---



\## 10. Code Quality



Prefer:



\* readable code

\* clear names

\* small focused functions

\* explicit validation

\* documented public APIs

\* predictable error handling



Avoid:



\* duplicated business logic

\* giant classes

\* giant functions

\* magic constants

\* unnecessary dependencies

\* premature optimization

\* excessive abstraction



Do not modify unrelated code during a feature task unless necessary.



\---



\## 11. Dependency Rules



Before introducing a new dependency:



1\. confirm that the standard library or existing dependency cannot reasonably solve the problem

2\. verify that the dependency is actively maintained

3\. prefer widely adopted libraries

4\. avoid unnecessary packages

5\. explain why the dependency is required



Never introduce a dependency solely because it makes a few lines of code shorter.



\---



\## 12. Documentation



Important architecture decisions must be documented under `docs/`.



API contracts must be documented.



Database changes must be documented.



AI algorithms must include academic explanation suitable for university assessment.



Documentation must remain consistent with actual implementation.



\---



\## 13. Definition of Done



A task is DONE only when:



\* implementation is complete

\* relevant tests pass

\* static analysis passes where configured

\* no secret is committed

\* no unrelated regression is introduced

\* documentation is updated when required

\* changed files are reported

\* test evidence is reported

\* known limitations are reported



Do not claim a task is complete when tests have not been run.



\---



\## 14. Agent Completion Report



At the end of a substantial task, return:



\### Status



DONE / PARTIAL / BLOCKED



\### Files Created



List files.



\### Files Modified



List files.



\### Implementation



Summarize what was implemented.



\### Commands Executed



List important commands.



\### Tests



Include exact test results.



\### Security



Describe relevant security checks.



\### Known Issues



List remaining problems.



\### Recommended Next Step



State the most logical next action.



