# Architecture Diagrams

These Mermaid files are source-controlled P1 architecture artifacts. GitHub and
Mermaid-compatible editors can render them directly; keeping the `.mmd` source
makes review and future changes diffable.

| Diagram | Purpose |
| --- | --- |
| [System context](system-context.mmd) | Users, the product boundary, core systems, and possible future integrations. |
| [Container architecture](container-architecture.mmd) | Deployable responsibilities and allowed communication paths. |
| [Main request/data flow](main-request-data-flow.mmd) | Authentication, AI recommendation, failure/degradation, and accepted-plan persistence over time. |
| [Java module dependencies](java-module-dependencies.mmd) | High-level allowed dependency direction inside the modular monolith. |

The diagrams are intentionally architectural rather than implementation
blueprints. They do not imply that future integrations, a notification provider,
or optional ML infrastructure already exists.
