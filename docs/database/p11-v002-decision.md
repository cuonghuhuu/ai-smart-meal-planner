# P11 Gate E - V002 Persistence Decision Record

## Status and scope

**Approved for V002 implementation; the migration file has been created but not
applied to a deployment database.** V001 is immutable. Gate E will persist
validated meal-plan generation outcomes in MySQL through Java; Python has no
database access. This record defines the schema changes and the ordering
convention needed before Java persistence work begins.

## Final decisions

1. `recommendation_requests.status` gains `INFEASIBLE`. `SUCCEEDED`,
   `DEGRADED`, and `INFEASIBLE` are validated algorithm outcomes; `FAILED` is
   reserved for a technical failure. Only `FAILED` may have `failure_reason`.
   Existing completion-time checks continue to apply to every terminal state.
2. A degraded generated plan stores each unfilled `(plan_date, meal_slot_type)`
   and its V1 reason in `meal_plan_unfilled_slots`. An absent entry cannot record
   the requested slot or its reason. An `INFEASIBLE` request has no plan or
   recommendation result; its per-slot reasons remain transient in this scope.
3. `meal_plans.default_servings` becomes `DECIMAL(5,2) NOT NULL DEFAULT 1.00`
   with `0 < default_servings <= 50`. This preserves every V1 `defaultServings`
   value. Existing `meal_plan_entries.servings DECIMAL(6,2)` also supports the
   contract range; it does not need to change.
4. `recommendation_results.total_score` and
   `recommendation_result_scores.score_value` become `DECIMAL(12,6)`. This
   preserves the six integer digits of V001 `DECIMAL(10,4)` while adding the
   six fractional places permitted by V1. The current Python algorithm
   quantizes to four places. `total_score` remains nullable and `score_value`
   remains non-null. V1 weights have two fractional places, so the existing
   `weight DECIMAL(10,4)` needs no change.
5. A request may source at most one plan. A unique index on nullable
   `meal_plans.source_request_id` enforces this for every linked request,
   including `MEAL_PLAN`; multiple manual plans with `NULL` source IDs remain
   permitted. Java must also verify the source request is of kind `MEAL_PLAN`
   and belongs to the same user. The existing `ON DELETE SET NULL` FK remains.
6. For selected V1 entries, assign `recommendation_results.rank_position`
   consecutively from 1 in `(plan_date ascending,
   meal_slot_types.display_order ascending, meal_slot_types.code ascending,
   position_in_slot ascending)` order, skipping unfilled slots. Each selection
   receives its own result row so its entry can link to that exact result. This
   is a deterministic **storage/presentation order**, not a Python quality rank
   or a sort by `total_score`. Persist the position at write time.

The generated plan starts as `DRAFT`. Java must validate each unfilled date
against the plan window and prevent an unfilled row from overlapping a filled
entry for the same plan/date/slot. MySQL cannot express these cross-row rules
with a `CHECK`. Subsequent plan edits must reconcile gap rows in the same write
transaction. Neither full AI request/response JSON nor Pantry state is stored.

## Exact V002 DDL

The approved migration is `database/schema/V002__p11_meal_plan_persistence.sql`.
It contains the following statements. Existing constraints and FKs not named
here remain unchanged.

```sql
-- -----------------------------------------------------------------------------
-- P11 Gate E persistence alignment
-- -----------------------------------------------------------------------------

-- Preserve INFEASIBLE as a first-class algorithm outcome and make
-- failure_reason exclusive to technical FAILED outcomes.
ALTER TABLE recommendation_requests
    DROP CHECK ck_recommendation_requests_status,
    DROP CHECK ck_recommendation_requests_failure,
    ADD CONSTRAINT ck_recommendation_requests_status
        CHECK (
            status IN (
                'PENDING',
                'SUCCEEDED',
                'FAILED',
                'DEGRADED',
                'INFEASIBLE'
            )
        ),
    ADD CONSTRAINT ck_recommendation_requests_failure
        CHECK (
            (status = 'FAILED' AND failure_reason IS NOT NULL)
            OR
            (status <> 'FAILED' AND failure_reason IS NULL)
        );

-- Preserve fractional V1 defaultServings.
ALTER TABLE meal_plans
    DROP CHECK ck_meal_plans_default_servings,
    MODIFY COLUMN default_servings
        DECIMAL(5, 2) NOT NULL DEFAULT 1.00,
    ADD CONSTRAINT ck_meal_plans_default_servings
        CHECK (
            default_servings > 0
            AND default_servings <= 50
        );

-- Preserve all contract-valid score precision and the previous integer range.
ALTER TABLE recommendation_results
    MODIFY COLUMN total_score DECIMAL(12, 6) NULL;

ALTER TABLE recommendation_result_scores
    MODIFY COLUMN score_value DECIMAL(12, 6) NOT NULL;

-- One persisted plan at most for each non-null source request.
ALTER TABLE meal_plans
    ADD CONSTRAINT ux_meal_plans_source_request
        UNIQUE (source_request_id);

-- The UNIQUE index also satisfies the nullable FK column's lookup requirement.
DROP INDEX ix_meal_plans_source_request ON meal_plans;

-- Persist requested date/slot gaps for DEGRADED plans.
CREATE TABLE meal_plan_unfilled_slots (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    meal_plan_id      BIGINT UNSIGNED NOT NULL,
    plan_date         DATE            NOT NULL,
    meal_slot_type_id BIGINT UNSIGNED NOT NULL,
    reason_code       VARCHAR(60)     NOT NULL,
    explanation       VARCHAR(500)    NULL,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_meal_plan_unfilled_slots
        PRIMARY KEY (id),

    CONSTRAINT ux_meal_plan_unfilled_slots_slot
        UNIQUE (meal_plan_id, plan_date, meal_slot_type_id),

    CONSTRAINT fk_meal_plan_unfilled_slots_plan
        FOREIGN KEY (meal_plan_id)
        REFERENCES meal_plans (id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,

    CONSTRAINT fk_meal_plan_unfilled_slots_slot
        FOREIGN KEY (meal_slot_type_id)
        REFERENCES meal_slot_types (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_meal_plan_unfilled_slots_reason
        CHECK (
            reason_code IN (
                'NO_ELIGIBLE_RECIPE',
                'HARD_CONSTRAINT_CONFLICT',
                'UNSUPPORTED_HARD_CONSTRAINT',
                'PANTRY_INFEASIBLE',
                'NUTRITION_INFEASIBLE',
                'SEARCH_LIMIT_REACHED'
            )
        ),

    INDEX ix_meal_plan_unfilled_slots_slot_type (meal_slot_type_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
```

`ux_meal_plan_unfilled_slots_slot` also provides the leftmost `meal_plan_id`
index for its plan FK and plan-day reads. The explicit slot-type index supports
the other FK. The new unique source-request index replaces the existing
nonunique index and remains usable for its FK lookup. No FK definition changes.

## Upgrade compatibility and pre-migration checks

- V001-valid integer default servings fit the new decimal type and range;
  widening the two score columns does not discard stored precision. Existing
  `PENDING`, `SUCCEEDED`, `FAILED`, and `DEGRADED` statuses remain valid.
- The tighter failure-reason check rejects any existing non-`FAILED` row with a
  reason. The unique source-request index rejects duplicate non-null links.
  Run these read-only queries against each target database before migration:

  ```sql
  SELECT id, status, failure_reason
  FROM recommendation_requests
  WHERE status <> 'FAILED' AND failure_reason IS NOT NULL;

  SELECT source_request_id, COUNT(*) AS plan_count
  FROM meal_plans
  WHERE source_request_id IS NOT NULL
  GROUP BY source_request_id
  HAVING COUNT(*) > 1;
  ```

  Both must return zero rows. Inspect and resolve any legacy rows explicitly;
  V002 must not silently delete or rewrite them.
- Review existing linked plans for a non-`MEAL_PLAN` source request or a
  different owner before deploying. The database uniqueness constraint cannot
  express these domain rules:

  ```sql
  SELECT mp.id AS meal_plan_id, mp.user_id AS meal_plan_user_id,
         rr.id AS request_id, rr.user_id AS request_user_id, rr.request_kind
  FROM meal_plans mp
  JOIN recommendation_requests rr ON rr.id = mp.source_request_id
  WHERE mp.source_request_id IS NOT NULL
    AND (rr.request_kind <> 'MEAL_PLAN' OR rr.user_id <> mp.user_id);
  ```

- MySQL permits multiple `NULL` values in a unique index, which preserves
  manual plans and plans whose old provenance was removed. Verify this and the
  replacement FK-supporting index on the project's MySQL 8.4 Testcontainer.
- MySQL DDL can commit between statements. A later statement failing may leave
  earlier changes applied while Flyway has not completed V002. Rehearse both a
  clean install and a V001-plus-seed upgrade in disposable MySQL before rollout;
  do not edit an already applied V001 or use Flyway repair to conceal a failure.
- The new table only stores gaps for a persisted degraded plan. `INFEASIBLE`
  slot details and the requested date/slot schedule of a failed attempt are not
  durable under this decision.

## Migration verification

`SchemaResourceTest` verifies the packaged V002 bytes. `BackendFoundationIT`
expects V001, V002, and the repeatable seed, 54 application tables, and 126
checks on a fresh database. `V002MigrationIT` covers a V001-plus-seed upgrade
with representative existing rows. The MySQL tests verify fractional
`0.01` and `50.00` defaults, rejection of `0`, six-place score round trips,
`INFEASIBLE` and failure-reason checks, exact reason codes, duplicate linked
plans, multiple `NULL` source IDs, gap uniqueness/FKs, and Flyway/Hibernate
validation. Run `mvn -B -ntp -f backend/pom.xml verify` in an environment with
Docker before deploying V002.

## Rejected alternatives

- Map `INFEASIBLE` to `FAILED`: loses the distinction between an algorithm
  conclusion and a technical failure.
- Infer gaps from absent entries or put them in `recommendation_results`:
  neither preserves a missing slot's reason without inventing a recipe/food.
- Round fractional default servings or six-place scores to fit V001: changes
  contract-valid values during persistence.
- Store full AI payloads: duplicates personal Pantry/profile data.
- Modify V001: violates immutable Flyway migration history.

The remote Python HTTP call remains outside every database transaction. Gate E
will use separate, short transactions for pending-request creation and terminal
outcome persistence. This migration does not change any Java entity,
repository, persistence service, controller, or Gate D integration code.
