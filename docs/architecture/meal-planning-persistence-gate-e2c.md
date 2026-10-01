# P11 Gate E2c: Gate D persistence integration

`PersistedMealPlanGenerationService` is the application entrypoint for persisted
generation. It has no transaction annotation and rejects an ambient transaction.
It resolves the authenticated user's internal ID in a short read, then calls
`MealPlanRequestWriter.begin` (`REQUIRES_NEW`) to commit a PENDING request.
The request public UUID is also Gate D's correlation/request UUID. The database
primary key stays inside the persistence boundary.

After TX1 commits, the existing Gate D service assembles its bounded snapshot,
invokes the Python client outside a transaction, and applies the unchanged
strict response validator. The coordinator resolves only selected recipe UUIDs
to published recipe IDs after validation. It then calls the E2b terminal writer
for SUCCEEDED or DEGRADED, or `markInfeasible` for a valid INFEASIBLE outcome.
The terminal writer alone owns TX2 and assigns deterministic storage ordinals.

Any runtime failure after TX1, including HTTP, validation, recipe mapping, or
terminal persistence, invokes the separate `MealPlanFailureWriter.markFailed`
`REQUIRES_NEW` operation. A terminal transaction rolls back before that call.
If TX1 itself fails, no Python call or FAILED write is attempted. No raw
snapshot, AI request body, response JSON, or token is persisted.

Because TX1 must precede snapshot assembly, `constraints_hash` is SHA-256 of
the caller's generation command fields (date, duration, requested slots,
servings, and minute cap). It is not a digest of the later profile or pantry
snapshot. The terminal duration retains Gate D's AI-call-and-validation timing;
technical failure duration remains nullable as allowed by V001.

`PersistedMealPlanGenerationIT` checks that TX1 is visible before the AI call,
that successful and degraded plans store the expected graph, and that a valid
infeasible outcome stores no plan. It also checks independent FAILED commits
after HTTP failure, strict response rejection, an archived selected recipe,
and terminal transaction rollback.

## Authenticated generation API

`POST /api/v1/me/meal-plans/generate` requires an authenticated access JWT. The
server derives the user from the JWT subject; the request has no user ID or
internal database ID. Browser clients must also satisfy the existing CSRF
protection. A successful synchronous call returns HTTP 201.

Request JSON:

```json
{
  "startDate": "2026-10-01",
  "days": 2,
  "requestedMealSlots": ["BREAKFAST", "DINNER"],
  "defaultServings": 2.50,
  "maxMinutesPerMeal": 45
}
```

`startDate` is an ISO calendar date. `days` is 1–7. `requestedMealSlots` is a
nonempty, duplicate-free list of up to six V1 slot codes. `defaultServings`
is greater than zero, at most 50, with at most two decimal places.
`maxMinutesPerMeal` is optional; when present it is 1–1440. The existing
Gate D input validator is authoritative for these constraints.

Response JSON contains `requestPublicId` (UUID), `status`, and
`mealPlanPublicId` (UUID when a plan exists). `SUCCEEDED` means all requested slots were
filled and a draft plan exists. `DEGRADED` means a draft plan exists with
explicit unfilled slots. `INFEASIBLE` means no plan exists, so
`mealPlanPublicId` is omitted. The response does not expose database IDs,
private snapshots, or internal AI payloads.

Errors use the shared `application/problem+json` `ProblemDetail` format with
`code` and request correlation ID. Invalid input is HTTP 400; missing or
invalid authentication is 401; denied access is 403. AI unavailability is 503,
timeout is 504, and a rejected AI response is 502. Persistence failures use
500. Technical failures do not return a terminal generation result; clients
receive a safe error without SQL or Python details.
