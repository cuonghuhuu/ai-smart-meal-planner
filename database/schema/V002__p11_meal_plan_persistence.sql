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
