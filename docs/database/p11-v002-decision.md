# P11-B V002 Decision Record

## Status

**Proposed; migration not approved or created.**

V001 remains unchanged. This record evaluates only the two representation gaps
identified for P11.

## Decision 1: first-class `INFEASIBLE` request outcome

The locked algorithm contract distinguishes `SUCCEEDED`, `DEGRADED`, and
`INFEASIBLE`. `FAILED` is reserved for Java-side technical failure.

V001 constrains `recommendation_requests.status` to `PENDING`, `SUCCEEDED`,
`FAILED`, and `DEGRADED`. Mapping `INFEASIBLE` to `FAILED` would erase the
required distinction between a valid algorithm conclusion and a broken AI call.

**Decision:** the gap is confirmed. A future V002 should add `INFEASIBLE` to the
status check. It should also tighten the existing failure-reason check so only a
technical `FAILED` row has `failure_reason`.

## Decision 2: degraded unfilled slots

An absent `meal_plan_entries` row cannot preserve which date/slot was requested
or why it was unfilled. `meals_per_day_target` is a count, not a slot schedule,
and `recommendation_results` requires a selected recipe or food subject.

**Decision:** the gap is confirmed. A future V002 should add one small child
table for the unfilled slots of a persisted degraded DRAFT plan. It does not
store Pantry state, candidate sets, or algorithm search state.

An `INFEASIBLE` request produces no plan, so only its request-level status is
persisted in the proposed P11 scope. Persisting every infeasible per-slot detail
would require a different request-owned table and is not justified yet.

## Exact minimal V002 proposal

The proposed migration would contain the following operations after explicit
approval:

```sql
ALTER TABLE recommendation_requests
    DROP CHECK ck_recommendation_requests_status,
    DROP CHECK ck_recommendation_requests_failure,
    ADD CONSTRAINT ck_recommendation_requests_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED',
                          'DEGRADED', 'INFEASIBLE')),
    ADD CONSTRAINT ck_recommendation_requests_failure
        CHECK ((status = 'FAILED' AND failure_reason IS NOT NULL)
            OR (status <> 'FAILED' AND failure_reason IS NULL));

CREATE TABLE meal_plan_unfilled_slots (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    meal_plan_id      BIGINT UNSIGNED NOT NULL,
    plan_date         DATE            NOT NULL,
    meal_slot_type_id BIGINT UNSIGNED NOT NULL,
    reason_code       VARCHAR(60)     NOT NULL,
    explanation       VARCHAR(500)    NULL,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_meal_plan_unfilled_slots PRIMARY KEY (id),
    CONSTRAINT ux_meal_plan_unfilled_slots_slot
        UNIQUE (meal_plan_id, plan_date, meal_slot_type_id),
    CONSTRAINT fk_meal_plan_unfilled_slots_plan
        FOREIGN KEY (meal_plan_id) REFERENCES meal_plans (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_meal_plan_unfilled_slots_slot
        FOREIGN KEY (meal_slot_type_id) REFERENCES meal_slot_types (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_meal_plan_unfilled_slots_reason
        CHECK (reason_code IN (
            'NO_ELIGIBLE_RECIPE',
            'HARD_CONSTRAINT_CONFLICT',
            'UNSUPPORTED_HARD_CONSTRAINT',
            'PANTRY_INFEASIBLE',
            'NUTRITION_INFEASIBLE',
            'SEARCH_LIMIT_REACHED'
        ))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
```

Java must validate that each `plan_date` lies inside its parent plan window,
because MySQL cannot enforce that cross-row rule with a CHECK constraint.

## Rejected alternatives

- Map `INFEASIBLE` to `FAILED`: violates the locked outcome semantics.
- Put gap rows into `recommendation_results`: those rows require a selected
  recipe or food.
- Infer gaps from absent entries: loses the requested schedule and reason code.
- Store the full AI request/response JSON: duplicates personal Pantry/profile
  data and is disproportionate to the requirement.
- Modify V001: forbidden because migration history is immutable.

No SQL migration is created in Gate B.
