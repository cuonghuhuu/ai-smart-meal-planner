# P11 Gate E2b: meal-plan persistence transactions

The persistence boundary has three Spring beans. `MealPlanRequestWriter.begin`
creates and commits a PENDING `MEAL_PLAN` request in TX1. It stores the user ID,
request UUID, contract algorithm version, correlation UUID, and a constraints
digest. It does not store profile or pantry snapshots, tokens, or an AI payload.

`MealPlanTerminalWriter.persistGenerated` is TX2 for SUCCEEDED and DEGRADED.
It locks the pending request row, checks for an existing result graph, validates
the requested date/slot coverage, and writes results, component scores, one
DRAFT plan, entries, and DEGRADED gaps in a single transaction. Only after
those writes does it mark the request terminal. A failed insert rolls back the
whole graph and leaves the separately committed TX1 request PENDING.
`markInfeasible` records a valid algorithm conclusion without a result graph.

`MealPlanFailureWriter.markFailed` runs in its own `REQUIRES_NEW` transaction.
It can commit after TX2 rolls back or while a caller's unrelated transaction
is marked rollback-only. All three writers use `REQUIRES_NEW` and are separate
Spring beans, so no transaction boundary depends on self-invocation. Each
terminal writer takes a pessimistic lock on the request row before checking
PENDING, preventing concurrent terminal transitions from both proceeding.
V002's unique `source_request_id` remains the final one-plan backstop.

MySQL generates `requested_at` for TX1. Terminal writers set `completed_at`
with `CURRENT_TIMESTAMP(6)` in the terminal update, on the same database clock.
Reading database time into a Java `LocalDateTime` and binding it again through
Hibernate can shift the value when JDBC and JVM time zones differ, violating
V001's `completed_at >= requested_at` constraint.

The writers do not invoke Python or the Gate D HTTP client. A future caller
must finish TX1 before invoking Gate D and call one terminal writer only after
the remote response has been validated. The existing Gate D transaction guard
still rejects an HTTP call inside an active transaction.

For every selected recipe, `rank_position` is assigned consecutively from 1
after sorting by `plan_date`, `meal_slot_types.display_order`, slot code, and
`position_in_slot`. It is a storage ordinal, not a score ranking. Slot and
score-component IDs come from the existing reference tables; recipe IDs are
resolved before calling the persistence writer. The writer accepts only the
small values it must store, not HTTP request or response DTOs.
