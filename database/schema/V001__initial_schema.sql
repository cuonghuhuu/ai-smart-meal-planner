-- =============================================================================
-- AI Smart Meal Planner -- V001 initial schema
-- Phase P2 (Database Design). Target engine: MySQL 8.0.19+ / InnoDB.
-- Character set: utf8mb4, collation utf8mb4_0900_ai_ci.
-- =============================================================================
-- Ownership (docs/architecture/architecture-decisions.md, ADR-004):
--   The Spring Boot modular monolith is the only component that connects to
--   MySQL. The Flutter client and the Python AI service hold no database
--   credentials and have no direct database access.
--
-- Migration contract (docs/database/database-decisions.md, DB-ADR-004):
--   * Forward-only. This file is immutable once applied; later changes ship as
--     V002__*.sql, V003__*.sql, ... using Flyway-compatible file naming.
--   * No CREATE DATABASE, USE, CREATE USER or GRANT statements. The target
--     schema and its credentials come from the deployment environment and are
--     never stored in this repository.
--   * No DROP, TRUNCATE or DELETE statements. Nothing under database/ can
--     destroy existing data.
--   * Tables are declared in foreign-key dependency order and
--     FOREIGN_KEY_CHECKS is never disabled.
--
-- Conventions (docs/database/data-model.md, section "Naming conventions"):
--   * snake_case; plural table names; singular column names.
--   * Entity tables use a surrogate `id BIGINT UNSIGNED AUTO_INCREMENT`;
--     pure association tables use composite natural primary keys.
--   * pk_ / fk_ / ux_ / ix_ / ftx_ / ck_ prefixes for constraints and indexes.
--   * DATETIME(6) holds UTC instants, DATE holds local calendar dates and
--     TIME holds local wall-clock times.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Section 1 -- Reference vocabularies (no dependencies on user data)
-- -----------------------------------------------------------------------------

-- Authorization roles. Deliberately minimal (see DB-ADR-011): end users and
-- catalog administrators, not a general-purpose RBAC engine.
CREATE TABLE roles (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30)     NOT NULL,
    name        VARCHAR(60)     NOT NULL,
    description VARCHAR(255)    NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT ux_roles_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Units of measure for nutrition amounts, recipe quantities and pantry stock.
-- Conversion is expressed only within one unit_type. Mass <-> volume and
-- count <-> mass are ingredient specific and live in
-- ingredient_unit_conversions, because no general arithmetic exists for them.
CREATE TABLE measurement_units (
    id                  BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    code                VARCHAR(20)       NOT NULL,
    display_name        VARCHAR(60)       NOT NULL,
    unit_type           VARCHAR(10)       NOT NULL,
    base_unit_id        BIGINT UNSIGNED   NULL,
    factor_to_base_unit DECIMAL(24, 12)   NULL,
    created_at          DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                          ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_measurement_units PRIMARY KEY (id),
    CONSTRAINT ux_measurement_units_code UNIQUE (code),
    CONSTRAINT fk_measurement_units_base_unit
        FOREIGN KEY (base_unit_id) REFERENCES measurement_units (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_measurement_units_unit_type
        CHECK (unit_type IN ('MASS', 'VOLUME', 'COUNT', 'ENERGY')),
    -- A unit is either its own base (no factor) or converts to a base unit
    -- with a strictly positive factor. Half-specified rows are rejected.
    CONSTRAINT ck_measurement_units_conversion
        CHECK ((base_unit_id IS NULL AND factor_to_base_unit IS NULL)
            OR (base_unit_id IS NOT NULL AND factor_to_base_unit IS NOT NULL
                AND factor_to_base_unit > 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Controlled nutrient vocabulary. Every nutrient carries exactly one unit, so
-- no amount anywhere in the schema is stored without an explicit unit.
CREATE TABLE nutrients (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    code          VARCHAR(40)       NOT NULL,
    display_name  VARCHAR(80)       NOT NULL,
    unit_id       BIGINT UNSIGNED   NOT NULL,
    nutrient_kind VARCHAR(20)       NOT NULL,
    is_core       BOOLEAN           NOT NULL DEFAULT FALSE,
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 1000,
    created_at    DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_nutrients PRIMARY KEY (id),
    CONSTRAINT ux_nutrients_code UNIQUE (code),
    CONSTRAINT fk_nutrients_unit FOREIGN KEY (unit_id)
        REFERENCES measurement_units (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_nutrients_kind
        CHECK (nutrient_kind IN ('ENERGY', 'MACRONUTRIENT', 'MINERAL',
                                 'VITAMIN', 'OTHER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Physical activity levels. `energy_factor` is the multiplier applied to basal
-- metabolic rate when Java calculates a suggested energy target.
CREATE TABLE activity_levels (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    code          VARCHAR(30)       NOT NULL,
    display_name  VARCHAR(60)       NOT NULL,
    description   VARCHAR(255)      NULL,
    energy_factor DECIMAL(4, 3)     NOT NULL,
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 1000,
    created_at    DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_activity_levels PRIMARY KEY (id),
    CONSTRAINT ux_activity_levels_code UNIQUE (code),
    -- Structural bound only: a multiplier outside this range is a data error,
    -- not a medical judgement.
    CONSTRAINT ck_activity_levels_energy_factor
        CHECK (energy_factor >= 1.000 AND energy_factor <= 3.000)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Nutrition goals a user can pursue (lose weight, maintain, gain muscle, ...).
CREATE TABLE nutrition_goals (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    code          VARCHAR(30)       NOT NULL,
    display_name  VARCHAR(60)       NOT NULL,
    description   VARCHAR(255)      NULL,
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 1000,
    created_at    DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_nutrition_goals PRIMARY KEY (id),
    CONSTRAINT ux_nutrition_goals_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dietary preferences / eating patterns (vegetarian, halal, low sodium, ...).
-- `is_exclusionary` marks patterns that forbid foods rather than merely
-- expressing taste, so the recommender can treat them as hard constraints.
CREATE TABLE dietary_preferences (
    id              BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    code            VARCHAR(40)       NOT NULL,
    display_name    VARCHAR(80)       NOT NULL,
    description     VARCHAR(255)      NULL,
    is_exclusionary BOOLEAN           NOT NULL DEFAULT FALSE,
    display_order   SMALLINT UNSIGNED NOT NULL DEFAULT 1000,
    created_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_dietary_preferences PRIMARY KEY (id),
    CONSTRAINT ux_dietary_preferences_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Allergen vocabulary. A curated list (the common regulated allergen groups
-- plus additions the team validates) rather than free text, so that pantry,
-- recipe and recommendation filtering can be exact.
CREATE TABLE allergens (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    code          VARCHAR(40)       NOT NULL,
    display_name  VARCHAR(80)       NOT NULL,
    description   VARCHAR(255)      NULL,
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 1000,
    created_at    DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_allergens PRIMARY KEY (id),
    CONSTRAINT ux_allergens_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Meal slot types (breakfast, lunch, dinner, snack, ...). A table, not a fixed
-- set of columns: the number of meals per day is configurable per plan, and
-- additional slots can be added without a schema change (see DB-ADR-008).
CREATE TABLE meal_slot_types (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    code          VARCHAR(30)       NOT NULL,
    display_name  VARCHAR(60)       NOT NULL,
    -- Default ordering within a day. Plans may override the position per item.
    display_order SMALLINT UNSIGNED NOT NULL,
    -- Advisory local wall-clock hint for reminders. Not a scheduling guarantee.
    typical_time   TIME             NULL,
    is_main_meal   BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_meal_slot_types PRIMARY KEY (id),
    CONSTRAINT ux_meal_slot_types_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Hierarchical food categories (Cereals -> Rice, Vegetables -> Leafy, ...).
-- Adjacency list: depth is small and the backend caches the tree.
CREATE TABLE food_categories (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code               VARCHAR(60)     NOT NULL,
    display_name       VARCHAR(120)    NOT NULL,
    parent_category_id BIGINT UNSIGNED NULL,
    description        VARCHAR(255)    NULL,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                       ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_food_categories PRIMARY KEY (id),
    CONSTRAINT ux_food_categories_code UNIQUE (code),
    CONSTRAINT fk_food_categories_parent FOREIGN KEY (parent_category_id)
        REFERENCES food_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Recipe tags. `tag_kind` keeps cuisines, methods and diet labels in one
-- vocabulary without losing the distinction between them.
CREATE TABLE recipe_tags (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code         VARCHAR(60)     NOT NULL,
    display_name VARCHAR(120)    NOT NULL,
    tag_kind     VARCHAR(20)     NOT NULL,
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                 ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_recipe_tags PRIMARY KEY (id),
    CONSTRAINT ux_recipe_tags_code UNIQUE (code),
    CONSTRAINT ck_recipe_tags_kind
        CHECK (tag_kind IN ('CUISINE', 'MEAL_TYPE', 'METHOD', 'DIET',
                            'OCCASION', 'OTHER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Named score dimensions the AI service may report (nutrition fit, pantry
-- coverage, variety, ...). Declaring the scale here means a stored score is
-- interpretable later without reading the algorithm's source code.
CREATE TABLE ai_score_components (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code             VARCHAR(60)     NOT NULL,
    display_name     VARCHAR(120)    NOT NULL,
    description      VARCHAR(500)    NULL,
    scale_min        DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    scale_max        DECIMAL(10, 4)  NOT NULL DEFAULT 1,
    higher_is_better BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                     ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ai_score_components PRIMARY KEY (id),
    CONSTRAINT ux_ai_score_components_code UNIQUE (code),
    CONSTRAINT ck_ai_score_components_scale CHECK (scale_min < scale_max)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Notification kinds the product can raise (expiring pantry item, plan
-- reminder, ...). Delivery transport itself is not modelled here.
CREATE TABLE notification_types (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code                 VARCHAR(60)     NOT NULL,
    display_name         VARCHAR(120)    NOT NULL,
    description          VARCHAR(500)    NULL,
    default_enabled      BOOLEAN         NOT NULL DEFAULT TRUE,
    supports_lead_time   BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_notification_types PRIMARY KEY (id),
    CONSTRAINT ux_notification_types_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- Section 2 -- Users and authentication
-- -----------------------------------------------------------------------------

-- The account identity. One row per person using the product.
--
-- Security notes:
--   * `password_hash` stores only a modern one-way hash (Argon2id or bcrypt)
--     including its algorithm identifier, cost parameters and salt, exactly as
--     produced by the encoder. Plaintext passwords are never stored, logged or
--     transmitted onward. This migration inserts no accounts at all.
--   * JWT access tokens are never stored. Only long-lived refresh session
--     records are persisted, hashed, in user_auth_sessions.
--   * `email_normalized` is a generated lower-cased, trimmed copy used for the
--     unique constraint and for login lookups, so that neither address casing
--     nor stray surrounding whitespace can create two accounts for the same
--     mailbox.
CREATE TABLE users (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    -- Opaque identifier safe to expose in API responses and URLs. Keeps the
    -- sequential internal id out of the public surface (see DB-ADR-001).
    public_id              BINARY(16)      NOT NULL,
    email                  VARCHAR(320)    NOT NULL,
    email_normalized       VARCHAR(320)    AS (LOWER(TRIM(email))) STORED NOT NULL,
    password_hash          VARCHAR(255)    NOT NULL,
    password_updated_at    DATETIME(6)     NULL,
    display_name           VARCHAR(100)    NOT NULL,
    account_status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at      DATETIME(6)     NULL,
    -- Failed-login throttling state. Counters, not credentials.
    failed_login_count     SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    locked_until           DATETIME(6)     NULL,
    last_login_at          DATETIME(6)     NULL,
    -- Set when the user requests deletion; see DB-ADR-006 for the anonymisation
    -- strategy that keeps aggregate history without keeping personal data.
    deactivated_at         DATETIME(6)     NULL,
    anonymized_at          DATETIME(6)     NULL,
    -- IANA zone name. Needed to turn plan dates and expiry dates into the
    -- user's own calendar days and to time reminders correctly.
    time_zone              VARCHAR(64)     NOT NULL DEFAULT 'UTC',
    locale                 VARCHAR(20)     NOT NULL DEFAULT 'en',
    -- Optimistic locking for concurrently edited account records.
    version                BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                           ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ux_users_public_id UNIQUE (public_id),
    -- Login and registration both resolve an address to at most one account.
    CONSTRAINT ux_users_email_normalized UNIQUE (email_normalized),
    CONSTRAINT ck_users_account_status
        CHECK (account_status IN ('PENDING_VERIFICATION', 'ACTIVE',
                                  'SUSPENDED', 'DEACTIVATED')),
    CONSTRAINT ck_users_email_shape CHECK (email LIKE '%_@_%._%'),
    CONSTRAINT ck_users_display_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    -- A verified account cannot still be pending verification.
    CONSTRAINT ck_users_verification_consistency
        CHECK (email_verified_at IS NULL
            OR account_status <> 'PENDING_VERIFICATION'),
    -- Anonymisation only happens to an account that has been deactivated.
    CONSTRAINT ck_users_anonymized_requires_deactivated
        CHECK (anonymized_at IS NULL OR deactivated_at IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Administrative listing and cleanup of accounts that never verified.
CREATE INDEX ix_users_status_created_at ON users (account_status, created_at);

-- Role assignment. Composite natural key: a role is held once or not at all.
CREATE TABLE user_roles (
    user_id     BIGINT UNSIGNED NOT NULL,
    role_id     BIGINT UNSIGNED NOT NULL,
    granted_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- Who granted it, when recorded. NULL for self-service registration.
    granted_by  BIGINT UNSIGNED NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- The grantor's account may be removed without erasing the grant itself.
    CONSTRAINT fk_user_roles_granted_by FOREIGN KEY (granted_by)
        REFERENCES users (id) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "Who holds this role?" for administration screens.
CREATE INDEX ix_user_roles_role ON user_roles (role_id);
CREATE INDEX ix_user_roles_granted_by ON user_roles (granted_by);

-- Refresh/session records supporting token renewal and revocation.
--
-- Security notes:
--   * Only a SHA-256 hash of the refresh token is stored, so a database leak
--     does not yield usable tokens. The token itself exists only in the client.
--   * Short-lived JWT access tokens are never persisted anywhere.
--   * `ip_address` and `user_agent` are optional forensic aids and are cleared
--     when the account is anonymised.
CREATE TABLE user_auth_sessions (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id              BIGINT UNSIGNED NOT NULL,
    -- Hex-encoded SHA-256 of the refresh token. Never the token itself.
    refresh_token_hash   CHAR(64)        NOT NULL,
    issued_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at           DATETIME(6)     NOT NULL,
    last_used_at         DATETIME(6)     NULL,
    revoked_at           DATETIME(6)     NULL,
    revocation_reason    VARCHAR(30)     NULL,
    -- Set when this session was replaced by a rotated successor, which lets the
    -- backend detect replay of an already-rotated refresh token.
    replaced_by_session_id BIGINT UNSIGNED NULL,
    client_kind          VARCHAR(20)     NOT NULL DEFAULT 'UNKNOWN',
    ip_address           VARBINARY(16)   NULL,
    user_agent           VARCHAR(255)    NULL,
    CONSTRAINT pk_user_auth_sessions PRIMARY KEY (id),
    CONSTRAINT ux_user_auth_sessions_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_user_auth_sessions_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_auth_sessions_replaced_by
        FOREIGN KEY (replaced_by_session_id) REFERENCES user_auth_sessions (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_user_auth_sessions_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_user_auth_sessions_client_kind
        CHECK (client_kind IN ('ANDROID', 'WEB', 'UNKNOWN')),
    CONSTRAINT ck_user_auth_sessions_revocation
        CHECK ((revoked_at IS NULL AND revocation_reason IS NULL)
            OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)),
    CONSTRAINT ck_user_auth_sessions_revocation_reason
        CHECK (revocation_reason IS NULL
           OR revocation_reason IN ('USER_LOGOUT', 'ROTATED', 'PASSWORD_CHANGE',
                                    'ADMIN_REVOKED', 'SUSPECTED_REUSE',
                                    'ACCOUNT_CLOSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "List / revoke my active sessions" and per-user revocation sweeps.
CREATE INDEX ix_user_auth_sessions_user_expiry
    ON user_auth_sessions (user_id, revoked_at, expires_at);
-- Scheduled purge of expired rows across all users.
CREATE INDEX ix_user_auth_sessions_expires_at ON user_auth_sessions (expires_at);
CREATE INDEX ix_user_auth_sessions_replaced_by
    ON user_auth_sessions (replaced_by_session_id);

-- Single-use security tokens for email verification and password reset.
--
-- Security notes: only a SHA-256 hash of the token is stored. The plaintext
-- token is delivered to the user's mailbox and never written to the database or
-- to logs. Rows are consumed by setting `consumed_at`, so a token cannot be
-- replayed, and expired rows are purged on a schedule.
CREATE TABLE user_security_tokens (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    token_kind  VARCHAR(30)     NOT NULL,
    -- Hex-encoded SHA-256 of the token. Never the token itself.
    token_hash  CHAR(64)        NOT NULL,
    issued_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at  DATETIME(6)     NOT NULL,
    consumed_at DATETIME(6)     NULL,
    CONSTRAINT pk_user_security_tokens PRIMARY KEY (id),
    CONSTRAINT ux_user_security_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_security_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_user_security_tokens_kind
        CHECK (token_kind IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_user_security_tokens_expiry CHECK (expires_at > issued_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Rate limiting ("has this user already requested a reset?") and purge sweeps.
CREATE INDEX ix_user_security_tokens_user_kind
    ON user_security_tokens (user_id, token_kind, expires_at);
CREATE INDEX ix_user_security_tokens_expires_at
    ON user_security_tokens (expires_at);

-- -----------------------------------------------------------------------------
-- Section 3 -- Profile, body measurements and nutrition targets
-- -----------------------------------------------------------------------------

-- Nutrition-relevant profile, one row per user.
--
-- Derived values are deliberately absent: BMI, basal metabolic rate and total
-- energy expenditure are computed by Java from height, current weight, birth
-- date and activity level. Storing them would create a second truth that can
-- silently disagree with its inputs (see DB-ADR-005). Current weight itself is
-- read from the latest user_body_measurements row rather than duplicated here.
CREATE TABLE user_profiles (
    user_id            BIGINT UNSIGNED   NOT NULL,
    birth_date         DATE              NULL,
    -- Self-described; free-text-free but intentionally not an exhaustive list.
    sex                VARCHAR(20)       NULL,
    height_cm          DECIMAL(5, 2)     NULL,
    activity_level_id  BIGINT UNSIGNED   NULL,
    nutrition_goal_id  BIGINT UNSIGNED   NULL,
    -- Desired weight-change rate in kg per week; negative means loss.
    target_weight_kg   DECIMAL(5, 2)     NULL,
    weekly_change_kg   DECIMAL(4, 2)     NULL,
    -- Household size the user cooks for, used as the default serving count.
    household_size     TINYINT UNSIGNED  NOT NULL DEFAULT 1,
    -- Minutes the user is typically willing to spend cooking on a weekday.
    max_cook_minutes   SMALLINT UNSIGNED NULL,
    notes              VARCHAR(500)      NULL,
    version            BIGINT UNSIGNED   NOT NULL DEFAULT 0,
    created_at         DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    -- Shared primary key: the profile cannot exist without its account and
    -- cannot be duplicated for it.
    CONSTRAINT pk_user_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_profiles_activity_level FOREIGN KEY (activity_level_id)
        REFERENCES activity_levels (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_profiles_goal FOREIGN KEY (nutrition_goal_id)
        REFERENCES nutrition_goals (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_user_profiles_sex
        CHECK (sex IS NULL
           OR sex IN ('FEMALE', 'MALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    -- Structural bounds: reject values that cannot describe a living person,
    -- without asserting any clinical threshold.
    CONSTRAINT ck_user_profiles_height
        CHECK (height_cm IS NULL OR (height_cm > 30 AND height_cm < 300)),
    CONSTRAINT ck_user_profiles_target_weight
        CHECK (target_weight_kg IS NULL
           OR (target_weight_kg > 2 AND target_weight_kg < 700)),
    CONSTRAINT ck_user_profiles_weekly_change
        CHECK (weekly_change_kg IS NULL
           OR (weekly_change_kg > -5 AND weekly_change_kg < 5)),
    CONSTRAINT ck_user_profiles_household_size CHECK (household_size >= 1),
    CONSTRAINT ck_user_profiles_cook_minutes
        CHECK (max_cook_minutes IS NULL
           OR (max_cook_minutes > 0 AND max_cook_minutes <= 1440)),
    CONSTRAINT ck_user_profiles_birth_date
        CHECK (birth_date IS NULL OR birth_date > '1900-01-01')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_user_profiles_activity_level ON user_profiles (activity_level_id);
CREATE INDEX ix_user_profiles_goal ON user_profiles (nutrition_goal_id);

-- Append-only weight (and optional body-composition) history.
--
-- This is the single source of the user's weight over time. "Current weight" is
-- the most recent row by measured_on. Editing history is a correction, not an
-- overwrite of the current value, which is what makes progress charts and the
-- academic evaluation of the recommender reproducible.
CREATE TABLE user_body_measurements (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    -- Local calendar day the user attributes the measurement to.
    measured_on      DATE            NOT NULL,
    weight_kg        DECIMAL(5, 2)   NOT NULL,
    body_fat_percent DECIMAL(4, 1)   NULL,
    waist_cm         DECIMAL(5, 1)   NULL,
    source           VARCHAR(20)     NOT NULL DEFAULT 'USER_ENTERED',
    note             VARCHAR(255)    NULL,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_body_measurements PRIMARY KEY (id),
    -- One authoritative measurement per user per day; a second entry for the
    -- same day is an update of that day's record.
    CONSTRAINT ux_user_body_measurements_user_day UNIQUE (user_id, measured_on),
    CONSTRAINT fk_user_body_measurements_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_user_body_measurements_weight
        CHECK (weight_kg > 2 AND weight_kg < 700),
    CONSTRAINT ck_user_body_measurements_body_fat
        CHECK (body_fat_percent IS NULL
           OR (body_fat_percent >= 0 AND body_fat_percent <= 100)),
    CONSTRAINT ck_user_body_measurements_waist
        CHECK (waist_cm IS NULL OR (waist_cm > 10 AND waist_cm < 400)),
    CONSTRAINT ck_user_body_measurements_source
        CHECK (source IN ('USER_ENTERED', 'IMPORTED', 'CORRECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- No separate index for "current weight" or latest-first history reads:
-- ux_user_body_measurements_user_day (user_id, measured_on) already covers
-- them. InnoDB scans that index backwards for ORDER BY measured_on DESC at
-- the same cost, so a second index on the same two columns would only add
-- write overhead.

-- Nutrition targets, versioned by validity period rather than overwritten.
--
-- `effective_from` / `effective_to` (NULL = still in force) preserve the target
-- that was actually in effect when a past plan was generated. Targets may be
-- calculated by Java or entered by the user; `origin` records which, and
-- `activity_level_id` / `nutrition_goal_id` snapshot the inputs a calculated
-- target was derived from.
CREATE TABLE user_nutrition_targets (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            BIGINT UNSIGNED NOT NULL,
    effective_from     DATE            NOT NULL,
    effective_to       DATE            NULL,
    origin             VARCHAR(20)     NOT NULL DEFAULT 'CALCULATED',
    -- Snapshot of the inputs used, so a calculated target stays explicable.
    activity_level_id  BIGINT UNSIGNED NULL,
    nutrition_goal_id  BIGINT UNSIGNED NULL,
    calculation_method VARCHAR(40)     NULL,
    note               VARCHAR(255)    NULL,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                       ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_nutrition_targets PRIMARY KEY (id),
    -- At most one target row per user per start date.
    CONSTRAINT ux_user_nutrition_targets_user_from
        UNIQUE (user_id, effective_from),
    CONSTRAINT fk_user_nutrition_targets_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_nutrition_targets_activity_level
        FOREIGN KEY (activity_level_id) REFERENCES activity_levels (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_nutrition_targets_goal FOREIGN KEY (nutrition_goal_id)
        REFERENCES nutrition_goals (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_user_nutrition_targets_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_user_nutrition_targets_origin
        CHECK (origin IN ('CALCULATED', 'USER_DEFINED', 'ADJUSTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- No index for "which target applies on date D?": the unique constraint
-- ux_user_nutrition_targets_user_from (user_id, effective_from) is already the
-- access path. The query walks this user's rows newest-first, which InnoDB does
-- by reading that index backwards, and stops at the first row whose period
-- contains D. Adding effective_to as a third column would not make the index
-- covering -- the row is fetched for its other columns anyway -- so it would only
-- cost a write on every target change.
CREATE INDEX ix_user_nutrition_targets_activity_level
    ON user_nutrition_targets (activity_level_id);
CREATE INDEX ix_user_nutrition_targets_goal
    ON user_nutrition_targets (nutrition_goal_id);

-- Per-nutrient daily targets belonging to one target version.
--
-- Normalized instead of fixed columns (calories, protein, carbs, fat): adding a
-- tracked nutrient such as sodium or fibre then needs a reference row, not a
-- schema migration. The amount's unit comes from nutrients.unit_id, so no
-- amount is stored without a unit.
CREATE TABLE user_nutrition_target_values (
    target_id     BIGINT UNSIGNED NOT NULL,
    nutrient_id   BIGINT UNSIGNED NOT NULL,
    -- Aim for this amount per day. NULL when only bounds matter.
    target_amount DECIMAL(12, 4)  NULL,
    min_amount    DECIMAL(12, 4)  NULL,
    max_amount    DECIMAL(12, 4)  NULL,
    -- Hard constraints must be satisfied by any accepted plan; soft ones are
    -- optimisation objectives the recommender may trade off.
    is_hard_limit BOOLEAN         NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_user_nutrition_target_values PRIMARY KEY (target_id, nutrient_id),
    CONSTRAINT fk_user_nutrition_target_values_target FOREIGN KEY (target_id)
        REFERENCES user_nutrition_targets (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_nutrition_target_values_nutrient FOREIGN KEY (nutrient_id)
        REFERENCES nutrients (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- A row that specifies nothing is meaningless.
    CONSTRAINT ck_user_nutrition_target_values_present
        CHECK (target_amount IS NOT NULL
            OR min_amount IS NOT NULL
            OR max_amount IS NOT NULL),
    CONSTRAINT ck_user_nutrition_target_values_non_negative
        CHECK ((target_amount IS NULL OR target_amount >= 0)
           AND (min_amount IS NULL OR min_amount >= 0)
           AND (max_amount IS NULL OR max_amount >= 0)),
    CONSTRAINT ck_user_nutrition_target_values_range
        CHECK (min_amount IS NULL OR max_amount IS NULL
            OR max_amount >= min_amount),
    -- A stated target must lie inside its own bounds.
    CONSTRAINT ck_user_nutrition_target_values_target_within_range
        CHECK (target_amount IS NULL
            OR ((min_amount IS NULL OR target_amount >= min_amount)
            AND (max_amount IS NULL OR target_amount <= max_amount)))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_user_nutrition_target_values_nutrient
    ON user_nutrition_target_values (nutrient_id);

-- User's dietary preferences (many-to-many with the reference vocabulary).
CREATE TABLE user_dietary_preferences (
    user_id               BIGINT UNSIGNED NOT NULL,
    dietary_preference_id BIGINT UNSIGNED NOT NULL,
    created_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_dietary_preferences
        PRIMARY KEY (user_id, dietary_preference_id),
    CONSTRAINT fk_user_dietary_preferences_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_dietary_preferences_preference
        FOREIGN KEY (dietary_preference_id) REFERENCES dietary_preferences (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_user_dietary_preferences_preference
    ON user_dietary_preferences (dietary_preference_id);

-- User's declared allergies and intolerances.
--
-- `reaction_kind` is the user's own description, not a clinical diagnosis. The
-- backend treats every row here as a hard exclusion regardless of the value;
-- the distinction exists for explanations shown in the UI, not to let the
-- recommender decide that a reaction is mild enough to ignore.
CREATE TABLE user_allergens (
    user_id       BIGINT UNSIGNED NOT NULL,
    allergen_id   BIGINT UNSIGNED NOT NULL,
    reaction_kind VARCHAR(20)     NOT NULL DEFAULT 'UNSPECIFIED',
    note          VARCHAR(255)    NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_allergens PRIMARY KEY (user_id, allergen_id),
    CONSTRAINT fk_user_allergens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_allergens_allergen FOREIGN KEY (allergen_id)
        REFERENCES allergens (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_user_allergens_reaction_kind
        CHECK (reaction_kind IN ('ALLERGY', 'INTOLERANCE', 'UNSPECIFIED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_user_allergens_allergen ON user_allergens (allergen_id);

-- -----------------------------------------------------------------------------
-- Section 4 -- Foods and nutrition facts
-- -----------------------------------------------------------------------------

-- A food is a nutritional fact carrier: a generic item ("raw carrot") or a
-- branded product. Nutrition lives in food_nutrients, not in columns here, so
-- the tracked nutrient set can grow without a schema change.
--
-- `source` and `source_reference` record provenance, which the university report
-- needs in order to cite where nutrition data came from. `revision` increments
-- whenever the underlying facts are corrected, so a recipe snapshot can state
-- which revision it was computed from.
CREATE TABLE foods (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id         BINARY(16)      NOT NULL,
    -- Stable machine key for curated imports; NULL for admin-entered rows.
    code              VARCHAR(80)     NULL,
    display_name      VARCHAR(200)    NOT NULL,
    brand             VARCHAR(120)    NULL,
    food_category_id  BIGINT UNSIGNED NULL,
    description       VARCHAR(500)    NULL,
    -- Basis every food_nutrients amount is expressed per: 100 g or 100 ml.
    -- Storing it explicitly prevents the classic "per what?" ambiguity.
    nutrition_basis   VARCHAR(10)     NOT NULL DEFAULT 'PER_100_G',
    -- Edible-portion density, when known, is the only sound way to convert
    -- between the mass and volume basis for this food.
    density_g_per_ml  DECIMAL(8, 4)   NULL,
    source            VARCHAR(30)     NOT NULL DEFAULT 'CURATED',
    source_reference  VARCHAR(255)    NULL,
    revision          INT UNSIGNED    NOT NULL DEFAULT 1,
    -- Catalog rows are retired, never deleted, because recipes, plans and past
    -- recommendations reference them (see DB-ADR-006).
    is_active         BOOLEAN         NOT NULL DEFAULT TRUE,
    retired_at        DATETIME(6)     NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_foods PRIMARY KEY (id),
    CONSTRAINT ux_foods_public_id UNIQUE (public_id),
    CONSTRAINT ux_foods_code UNIQUE (code),
    CONSTRAINT fk_foods_category FOREIGN KEY (food_category_id)
        REFERENCES food_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_foods_nutrition_basis
        CHECK (nutrition_basis IN ('PER_100_G', 'PER_100_ML')),
    CONSTRAINT ck_foods_density
        CHECK (density_g_per_ml IS NULL
           OR (density_g_per_ml > 0 AND density_g_per_ml < 25)),
    CONSTRAINT ck_foods_source
        CHECK (source IN ('CURATED', 'IMPORTED', 'USER_SUBMITTED')),
    CONSTRAINT ck_foods_display_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT ck_foods_retirement
        CHECK ((is_active = TRUE AND retired_at IS NULL)
            OR (is_active = FALSE AND retired_at IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Category browsing restricted to selectable rows.
CREATE INDEX ix_foods_category_active ON foods (food_category_id, is_active);
-- Alphabetical admin listing and prefix lookups on active rows.
CREATE INDEX ix_foods_active_name ON foods (is_active, display_name);
-- Relevance-ranked food search (DB-ADR-010: relational full-text, no external
-- search engine in P2).
CREATE FULLTEXT INDEX ftx_foods_name_brand ON foods (display_name, brand);

-- Nutrition facts: one row per (food, nutrient). The amount is per the food's
-- declared nutrition_basis, in the nutrient's own unit.
CREATE TABLE food_nutrients (
    food_id     BIGINT UNSIGNED NOT NULL,
    nutrient_id BIGINT UNSIGNED NOT NULL,
    amount      DECIMAL(12, 4)  NOT NULL,
    -- ANALYTICAL = measured, CALCULATED = derived from components,
    -- ESTIMATED = borrowed from a similar food. Affects how much the report can
    -- claim about accuracy.
    data_quality VARCHAR(20)    NOT NULL DEFAULT 'ANALYTICAL',
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_food_nutrients PRIMARY KEY (food_id, nutrient_id),
    CONSTRAINT fk_food_nutrients_food FOREIGN KEY (food_id)
        REFERENCES foods (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_food_nutrients_nutrient FOREIGN KEY (nutrient_id)
        REFERENCES nutrients (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- Nutrient content cannot be negative.
    CONSTRAINT ck_food_nutrients_amount CHECK (amount >= 0),
    CONSTRAINT ck_food_nutrients_quality
        CHECK (data_quality IN ('ANALYTICAL', 'CALCULATED', 'ESTIMATED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "Which foods are high/low in nutrient N?" for nutrition-aware filtering.
CREATE INDEX ix_food_nutrients_nutrient_amount
    ON food_nutrients (nutrient_id, amount);

-- Named portions for a food ("1 medium carrot = 61 g", "1 cup = 240 ml").
--
-- This is what makes household units usable: each serving states its own mass
-- or volume, so Java scales the per-100 basis by a real measured quantity
-- instead of guessing a conversion (see DB-ADR-012).
CREATE TABLE food_servings (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    food_id        BIGINT UNSIGNED NOT NULL,
    display_name   VARCHAR(80)     NOT NULL,
    -- Quantity in the stated unit, e.g. 1 "cup", 2 "tbsp".
    quantity       DECIMAL(12, 4)  NOT NULL,
    unit_id        BIGINT UNSIGNED NOT NULL,
    -- Resolved mass or volume of that portion. At least one is required, which
    -- is what allows the nutrition basis to be applied.
    gram_weight    DECIMAL(12, 4)  NULL,
    milliliters    DECIMAL(12, 4)  NULL,
    is_default     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                   ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_food_servings PRIMARY KEY (id),
    CONSTRAINT ux_food_servings_food_name UNIQUE (food_id, display_name),
    CONSTRAINT fk_food_servings_food FOREIGN KEY (food_id)
        REFERENCES foods (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_food_servings_unit FOREIGN KEY (unit_id)
        REFERENCES measurement_units (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_food_servings_quantity CHECK (quantity > 0),
    CONSTRAINT ck_food_servings_gram_weight
        CHECK (gram_weight IS NULL OR gram_weight > 0),
    CONSTRAINT ck_food_servings_milliliters
        CHECK (milliliters IS NULL OR milliliters > 0),
    -- A portion that resolves to neither mass nor volume cannot be converted.
    CONSTRAINT ck_food_servings_measurable
        CHECK (gram_weight IS NOT NULL OR milliliters IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- At most one default portion per food, enforced by a functional unique index
-- (NULLs are ignored, so non-default rows do not collide).
CREATE UNIQUE INDEX ux_food_servings_food_default
    ON food_servings (food_id, (CASE WHEN is_default THEN 1 ELSE NULL END));
CREATE INDEX ix_food_servings_unit ON food_servings (unit_id);

-- -----------------------------------------------------------------------------
-- Section 5 -- Ingredients
-- -----------------------------------------------------------------------------

-- A canonical culinary ingredient: the shopping-and-pantry identity of a thing,
-- separate from the nutritional fact carrier in `foods` (see DB-ADR-013).
-- "Chicken breast" is one ingredient; "chicken breast, raw" and "chicken breast,
-- grilled" are distinct foods with different nutrition. Modelling them
-- separately is what lets pantry stock, recipe lines and substitution reason
-- about the same ingredient while nutrition stays attached to a precise food.
CREATE TABLE ingredients (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id          BINARY(16)      NOT NULL,
    -- Canonical machine key, e.g. 'chicken_breast'. Unique and stable.
    code               VARCHAR(80)     NOT NULL,
    display_name       VARCHAR(150)    NOT NULL,
    food_category_id   BIGINT UNSIGNED NULL,
    -- Representative food used when nutrition is needed but the recipe line did
    -- not name a specific preparation. Nullable: an ingredient may exist before
    -- its nutrition reference is curated.
    default_food_id    BIGINT UNSIGNED NULL,
    -- Unit the UI offers first, and the unit pantry quantities default to.
    default_unit_id    BIGINT UNSIGNED NULL,
    -- Typical mass of one countable piece, when the ingredient is bought by
    -- count ("2 onions"). Only set where a piece has a meaningful mass.
    piece_gram_weight  DECIMAL(10, 4)  NULL,
    -- Typical shelf life once acquired, used only to offer a suggested expiry
    -- date the user can accept or override. Never treated as a fact.
    typical_shelf_life_days SMALLINT UNSIGNED NULL,
    is_staple          BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active          BOOLEAN         NOT NULL DEFAULT TRUE,
    retired_at         DATETIME(6)     NULL,
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                       ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ingredients PRIMARY KEY (id),
    CONSTRAINT ux_ingredients_public_id UNIQUE (public_id),
    CONSTRAINT ux_ingredients_code UNIQUE (code),
    CONSTRAINT fk_ingredients_category FOREIGN KEY (food_category_id)
        REFERENCES food_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ingredients_default_food FOREIGN KEY (default_food_id)
        REFERENCES foods (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ingredients_default_unit FOREIGN KEY (default_unit_id)
        REFERENCES measurement_units (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ingredients_piece_weight
        CHECK (piece_gram_weight IS NULL OR piece_gram_weight > 0),
    CONSTRAINT ck_ingredients_shelf_life
        CHECK (typical_shelf_life_days IS NULL
           OR (typical_shelf_life_days > 0 AND typical_shelf_life_days <= 3650)),
    CONSTRAINT ck_ingredients_display_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT ck_ingredients_retirement
        CHECK ((is_active = TRUE AND retired_at IS NULL)
            OR (is_active = FALSE AND retired_at IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_ingredients_active_name ON ingredients (is_active, display_name);
CREATE INDEX ix_ingredients_category_active
    ON ingredients (food_category_id, is_active);
CREATE INDEX ix_ingredients_default_food ON ingredients (default_food_id);
CREATE INDEX ix_ingredients_default_unit ON ingredients (default_unit_id);
-- Ingredient search and free-text recipe-line matching.
CREATE FULLTEXT INDEX ftx_ingredients_name ON ingredients (display_name);

-- Alternative names and spellings, so that "coriander" and "cilantro" resolve to
-- the same ingredient during search and recipe import.
CREATE TABLE ingredient_aliases (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ingredient_id BIGINT UNSIGNED NOT NULL,
    alias         VARCHAR(150)    NOT NULL,
    alias_locale  VARCHAR(20)     NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ingredient_aliases PRIMARY KEY (id),
    -- An alias points at one ingredient only, or lookups become ambiguous.
    CONSTRAINT ux_ingredient_aliases_alias UNIQUE (alias),
    CONSTRAINT fk_ingredient_aliases_ingredient FOREIGN KEY (ingredient_id)
        REFERENCES ingredients (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_ingredient_aliases_not_blank
        CHECK (CHAR_LENGTH(TRIM(alias)) > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "All aliases of this ingredient" when rendering or curating.
CREATE INDEX ix_ingredient_aliases_ingredient ON ingredient_aliases (ingredient_id);

-- Which foods may represent an ingredient nutritionally, and in what state.
-- Lets one ingredient carry both its raw and cooked nutrition without
-- pretending they are interchangeable.
CREATE TABLE ingredient_foods (
    ingredient_id     BIGINT UNSIGNED NOT NULL,
    food_id           BIGINT UNSIGNED NOT NULL,
    preparation_state VARCHAR(20)     NOT NULL DEFAULT 'UNSPECIFIED',
    -- Yield when moving from the ingredient's purchased form to this food, e.g.
    -- trimming and cooking losses. 1.0 means no change.
    yield_factor      DECIMAL(6, 4)   NOT NULL DEFAULT 1.0000,
    is_primary        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ingredient_foods PRIMARY KEY (ingredient_id, food_id),
    CONSTRAINT fk_ingredient_foods_ingredient FOREIGN KEY (ingredient_id)
        REFERENCES ingredients (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ingredient_foods_food FOREIGN KEY (food_id)
        REFERENCES foods (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ingredient_foods_state
        CHECK (preparation_state IN ('RAW', 'COOKED', 'DRIED', 'CANNED',
                                     'FROZEN', 'UNSPECIFIED')),
    CONSTRAINT ck_ingredient_foods_yield
        CHECK (yield_factor > 0 AND yield_factor <= 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_ingredient_foods_food ON ingredient_foods (food_id);
-- At most one primary food per ingredient.
CREATE UNIQUE INDEX ux_ingredient_foods_primary
    ON ingredient_foods (ingredient_id, (CASE WHEN is_primary THEN 1 ELSE NULL END));

-- Per-ingredient unit conversions that are NOT derivable arithmetically.
--
-- Within a unit_type, measurement_units.factor_to_base_unit is sufficient
-- (1 kg = 1000 g always). Across types it is not: one cup of flour and one cup
-- of honey have different masses, and "1 clove of garlic" has no general mass.
-- Those conversions are therefore facts about a specific ingredient, recorded
-- here with their source, and absent when unknown (see DB-ADR-012). The
-- backend must refuse to convert rather than invent a factor.
CREATE TABLE ingredient_unit_conversions (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ingredient_id  BIGINT UNSIGNED NOT NULL,
    from_unit_id   BIGINT UNSIGNED NOT NULL,
    -- Quantity in from_unit that the conversion is stated for, usually 1.
    from_quantity  DECIMAL(12, 4)  NOT NULL DEFAULT 1.0000,
    to_unit_id     BIGINT UNSIGNED NOT NULL,
    to_quantity    DECIMAL(12, 4)  NOT NULL,
    -- MEASURED = weighed by the team, REFERENCE = taken from a cited table,
    -- ESTIMATED = best guess, which the UI may surface as approximate.
    confidence     VARCHAR(20)     NOT NULL DEFAULT 'REFERENCE',
    source_note    VARCHAR(255)    NULL,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                   ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ingredient_unit_conversions PRIMARY KEY (id),
    -- One conversion per ordered unit pair per ingredient.
    CONSTRAINT ux_ingredient_unit_conversions_pair
        UNIQUE (ingredient_id, from_unit_id, to_unit_id),
    CONSTRAINT fk_ingredient_unit_conversions_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ingredient_unit_conversions_from_unit
        FOREIGN KEY (from_unit_id) REFERENCES measurement_units (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ingredient_unit_conversions_to_unit
        FOREIGN KEY (to_unit_id) REFERENCES measurement_units (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ingredient_unit_conversions_quantities
        CHECK (from_quantity > 0 AND to_quantity > 0),
    CONSTRAINT ck_ingredient_unit_conversions_distinct
        CHECK (from_unit_id <> to_unit_id),
    CONSTRAINT ck_ingredient_unit_conversions_confidence
        CHECK (confidence IN ('MEASURED', 'REFERENCE', 'ESTIMATED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_ingredient_unit_conversions_from_unit
    ON ingredient_unit_conversions (from_unit_id);
CREATE INDEX ix_ingredient_unit_conversions_to_unit
    ON ingredient_unit_conversions (to_unit_id);

-- Allergen content of an ingredient. `presence` distinguishes a contained
-- allergen from a may-contain warning, which matters because the recommender
-- treats CONTAINS as disqualifying and surfaces MAY_CONTAIN as a caution.
CREATE TABLE ingredient_allergens (
    ingredient_id BIGINT UNSIGNED NOT NULL,
    allergen_id   BIGINT UNSIGNED NOT NULL,
    presence      VARCHAR(20)     NOT NULL DEFAULT 'CONTAINS',
    note          VARCHAR(255)    NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ingredient_allergens PRIMARY KEY (ingredient_id, allergen_id),
    CONSTRAINT fk_ingredient_allergens_ingredient FOREIGN KEY (ingredient_id)
        REFERENCES ingredients (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ingredient_allergens_allergen FOREIGN KEY (allergen_id)
        REFERENCES allergens (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ingredient_allergens_presence
        CHECK (presence IN ('CONTAINS', 'MAY_CONTAIN', 'FREE_FROM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Allergen-based exclusion: "which ingredients carry allergen A?"
CREATE INDEX ix_ingredient_allergens_allergen_presence
    ON ingredient_allergens (allergen_id, presence);

-- Ingredients the user dislikes or refuses. Separate from allergens because the
-- consequence differs: a dislike is a soft penalty the recommender may override
-- with an explanation, an avoidance is a hard exclusion.
CREATE TABLE user_disliked_ingredients (
    user_id       BIGINT UNSIGNED NOT NULL,
    ingredient_id BIGINT UNSIGNED NOT NULL,
    strength      VARCHAR(20)     NOT NULL DEFAULT 'DISLIKE',
    note          VARCHAR(255)    NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_disliked_ingredients PRIMARY KEY (user_id, ingredient_id),
    CONSTRAINT fk_user_disliked_ingredients_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_disliked_ingredients_ingredient FOREIGN KEY (ingredient_id)
        REFERENCES ingredients (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_user_disliked_ingredients_strength
        CHECK (strength IN ('DISLIKE', 'AVOID'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_user_disliked_ingredients_ingredient
    ON user_disliked_ingredients (ingredient_id);

-- -----------------------------------------------------------------------------
-- Section 6 -- Ingredient substitution
-- -----------------------------------------------------------------------------

-- Groups of ingredients that play the same culinary role ("leafy greens",
-- "hard cheeses"). Membership is a weak hint used to widen candidate search;
-- it is never on its own a claim that two members are interchangeable.
CREATE TABLE ingredient_groups (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code         VARCHAR(60)     NOT NULL,
    display_name VARCHAR(120)    NOT NULL,
    description  VARCHAR(500)    NULL,
    group_kind   VARCHAR(20)     NOT NULL DEFAULT 'CULINARY_ROLE',
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                 ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ingredient_groups PRIMARY KEY (id),
    CONSTRAINT ux_ingredient_groups_code UNIQUE (code),
    CONSTRAINT ck_ingredient_groups_kind
        CHECK (group_kind IN ('CULINARY_ROLE', 'NUTRITION_ROLE', 'CATEGORY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ingredient_group_members (
    group_id      BIGINT UNSIGNED NOT NULL,
    ingredient_id BIGINT UNSIGNED NOT NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ingredient_group_members PRIMARY KEY (group_id, ingredient_id),
    CONSTRAINT fk_ingredient_group_members_group FOREIGN KEY (group_id)
        REFERENCES ingredient_groups (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ingredient_group_members_ingredient FOREIGN KEY (ingredient_id)
        REFERENCES ingredients (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_ingredient_group_members_ingredient
    ON ingredient_group_members (ingredient_id);

-- Typed, directed substitution edges (see DB-ADR-009).
--
-- Why directed and typed rather than a symmetric "equivalent to" table:
--   * Substitution is asymmetric. Yoghurt often replaces sour cream; the
--     reverse is not equally safe.
--   * It is context-bound. `substitution_context` records where the swap holds
--     (baking behaves differently from a sauce).
--   * It is quantitative. `ratio_numerator` / `ratio_denominator` express "use
--     3 parts of the substitute for 4 parts of the original" as an exact
--     rational value, not a lossy decimal.
--   * It must never silently break a dietary or allergen constraint. Java
--     re-checks the substitute against the user's allergens and preferences
--     after selecting an edge; `preserves_allergen_profile` is a curator's
--     assertion used for ranking, never a substitute for that check.
CREATE TABLE ingredient_substitutions (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    original_ingredient_id  BIGINT UNSIGNED NOT NULL,
    substitute_ingredient_id BIGINT UNSIGNED NOT NULL,
    substitution_context    VARCHAR(30)     NOT NULL DEFAULT 'GENERAL',
    ratio_numerator         DECIMAL(10, 4)  NOT NULL DEFAULT 1.0000,
    ratio_denominator       DECIMAL(10, 4)  NOT NULL DEFAULT 1.0000,
    -- Curator's confidence that the swap works in the stated context.
    confidence              VARCHAR(20)     NOT NULL DEFAULT 'MODERATE',
    -- Does the substitute keep the original's allergen profile? Advisory.
    preserves_allergen_profile BOOLEAN      NOT NULL DEFAULT FALSE,
    -- How much the result changes in taste/texture; used to warn the user.
    sensory_impact          VARCHAR(20)     NOT NULL DEFAULT 'MINOR',
    guidance                VARCHAR(500)    NULL,
    source_note             VARCHAR(255)    NULL,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                            ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ingredient_substitutions PRIMARY KEY (id),
    -- One edge per (original, substitute, context).
    CONSTRAINT ux_ingredient_substitutions_edge
        UNIQUE (original_ingredient_id, substitute_ingredient_id,
                substitution_context),
    CONSTRAINT fk_ingredient_substitutions_original
        FOREIGN KEY (original_ingredient_id) REFERENCES ingredients (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ingredient_substitutions_substitute
        FOREIGN KEY (substitute_ingredient_id) REFERENCES ingredients (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    -- An ingredient is not a substitute for itself.
    CONSTRAINT ck_ingredient_substitutions_distinct
        CHECK (original_ingredient_id <> substitute_ingredient_id),
    CONSTRAINT ck_ingredient_substitutions_ratio
        CHECK (ratio_numerator > 0 AND ratio_denominator > 0),
    CONSTRAINT ck_ingredient_substitutions_context
        CHECK (substitution_context IN ('GENERAL', 'BAKING', 'SAUCE', 'FRYING',
                                        'RAW', 'DAIRY_FREE', 'GLUTEN_FREE',
                                        'VEGAN')),
    CONSTRAINT ck_ingredient_substitutions_confidence
        CHECK (confidence IN ('HIGH', 'MODERATE', 'LOW')),
    CONSTRAINT ck_ingredient_substitutions_sensory
        CHECK (sensory_impact IN ('NONE', 'MINOR', 'NOTICEABLE', 'MAJOR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Forward traversal: "what can replace ingredient X here?" -- the dominant read.
CREATE INDEX ix_ingredient_substitutions_original
    ON ingredient_substitutions (original_ingredient_id, substitution_context,
                                 is_active);
-- Reverse traversal: "what could this pantry item stand in for?"
CREATE INDEX ix_ingredient_substitutions_substitute
    ON ingredient_substitutions (substitute_ingredient_id, is_active);

-- -----------------------------------------------------------------------------
-- Section 7 -- Recipes
-- -----------------------------------------------------------------------------

-- A recipe: what to cook, for how many, and how long it takes.
--
-- Nutrition is not stored here as authoritative fact. It is computed from the
-- recipe's ingredient lines and cached in recipe_nutrition_snapshots, which
-- records the inputs it was computed from (see DB-ADR-005).
CREATE TABLE recipes (
    id                 BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    public_id          BINARY(16)        NOT NULL,
    title              VARCHAR(200)      NOT NULL,
    -- URL-friendly unique key for shareable links.
    slug               VARCHAR(220)      NOT NULL,
    summary            VARCHAR(500)      NULL,
    -- Servings the quantities in recipe_ingredients are written for. Scaling to
    -- a household size is a multiplication done at read time.
    servings           SMALLINT UNSIGNED NOT NULL DEFAULT 2,
    prep_minutes       SMALLINT UNSIGNED NULL,
    cook_minutes       SMALLINT UNSIGNED NULL,
    -- Generated so search and sorting by total effort need no expression index.
    total_minutes      SMALLINT UNSIGNED
                       AS (COALESCE(prep_minutes, 0) + COALESCE(cook_minutes, 0))
                       STORED,
    difficulty         VARCHAR(20)       NOT NULL DEFAULT 'EASY',
    instructions_note  VARCHAR(1000)     NULL,
    image_url          VARCHAR(500)      NULL,
    -- NULL for curated catalog recipes; set for user-created ones. SET NULL on
    -- account deletion keeps a shared recipe alive as an orphaned catalog row.
    created_by_user_id BIGINT UNSIGNED   NULL,
    source             VARCHAR(30)       NOT NULL DEFAULT 'CURATED',
    source_reference   VARCHAR(255)      NULL,
    -- DRAFT recipes are invisible to search; PUBLISHED are selectable;
    -- ARCHIVED stay referenced by history but are no longer offered.
    status             VARCHAR(20)       NOT NULL DEFAULT 'DRAFT',
    published_at       DATETIME(6)       NULL,
    archived_at        DATETIME(6)       NULL,
    version            BIGINT UNSIGNED   NOT NULL DEFAULT 0,
    created_at         DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_recipes PRIMARY KEY (id),
    CONSTRAINT ux_recipes_public_id UNIQUE (public_id),
    CONSTRAINT ux_recipes_slug UNIQUE (slug),
    CONSTRAINT fk_recipes_created_by FOREIGN KEY (created_by_user_id)
        REFERENCES users (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_recipes_servings CHECK (servings >= 1 AND servings <= 100),
    CONSTRAINT ck_recipes_prep_minutes
        CHECK (prep_minutes IS NULL OR prep_minutes <= 10080),
    CONSTRAINT ck_recipes_cook_minutes
        CHECK (cook_minutes IS NULL OR cook_minutes <= 10080),
    CONSTRAINT ck_recipes_difficulty
        CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT ck_recipes_source
        CHECK (source IN ('CURATED', 'IMPORTED', 'USER_CREATED')),
    CONSTRAINT ck_recipes_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_recipes_title_not_blank
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    -- Status and its timestamps cannot disagree.
    CONSTRAINT ck_recipes_published_at
        CHECK ((status = 'DRAFT' AND published_at IS NULL)
            OR (status <> 'DRAFT' AND published_at IS NOT NULL)),
    CONSTRAINT ck_recipes_archived_at
        CHECK ((status = 'ARCHIVED' AND archived_at IS NOT NULL)
            OR (status <> 'ARCHIVED' AND archived_at IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Browse published recipes newest-first.
CREATE INDEX ix_recipes_status_published_at ON recipes (status, published_at DESC);
-- "Quick meals": filter published recipes by total effort.
CREATE INDEX ix_recipes_status_total_minutes ON recipes (status, total_minutes);
-- "My recipes".
CREATE INDEX ix_recipes_created_by_status ON recipes (created_by_user_id, status);
-- Relevance-ranked recipe search over title and summary.
CREATE FULLTEXT INDEX ftx_recipes_title_summary ON recipes (title, summary);

-- Ordered preparation steps. A table rather than one long text blob so the
-- client can render step-by-step cooking mode and attach per-step timers.
CREATE TABLE recipe_steps (
    recipe_id       BIGINT UNSIGNED   NOT NULL,
    step_number     SMALLINT UNSIGNED NOT NULL,
    instruction     VARCHAR(2000)     NOT NULL,
    -- Hands-off time this step needs (simmering, resting), for timers.
    duration_minutes SMALLINT UNSIGNED NULL,
    created_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    -- Composite key: steps are identified by their position in the recipe.
    CONSTRAINT pk_recipe_steps PRIMARY KEY (recipe_id, step_number),
    CONSTRAINT fk_recipe_steps_recipe FOREIGN KEY (recipe_id)
        REFERENCES recipes (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_recipe_steps_number CHECK (step_number >= 1),
    CONSTRAINT ck_recipe_steps_instruction_not_blank
        CHECK (CHAR_LENGTH(TRIM(instruction)) > 0),
    CONSTRAINT ck_recipe_steps_duration
        CHECK (duration_minutes IS NULL OR duration_minutes <= 10080)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Recipe ingredient lines.
--
-- `ingredient_id` is the canonical identity used for pantry matching and
-- substitution. `food_id` optionally pins the exact nutritional form when the
-- recipe specifies one ("cooked brown rice", not just "rice"); when it is NULL
-- the ingredient's default_food_id is used for nutrition.
CREATE TABLE recipe_ingredients (
    id              BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    recipe_id       BIGINT UNSIGNED   NOT NULL,
    -- Display position within the ingredient list.
    line_number     SMALLINT UNSIGNED NOT NULL,
    ingredient_id   BIGINT UNSIGNED   NOT NULL,
    food_id         BIGINT UNSIGNED   NULL,
    quantity        DECIMAL(12, 4)    NULL,
    unit_id         BIGINT UNSIGNED   NULL,
    -- "finely chopped", "at room temperature".
    preparation_note VARCHAR(200)     NULL,
    -- Optional garnishes may be skipped when checking pantry coverage.
    is_optional     BOOLEAN           NOT NULL DEFAULT FALSE,
    -- May this line be substituted at all? Some lines define the dish.
    allow_substitution BOOLEAN        NOT NULL DEFAULT TRUE,
    -- Groups lines under a heading ("For the sauce").
    section_label   VARCHAR(80)       NULL,
    created_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_recipe_ingredients PRIMARY KEY (id),
    CONSTRAINT ux_recipe_ingredients_line UNIQUE (recipe_id, line_number),
    -- An ingredient appears at most once per recipe, so pantry coverage and
    -- substitution do not have to reconcile duplicate lines.
    CONSTRAINT ux_recipe_ingredients_recipe_ingredient
        UNIQUE (recipe_id, ingredient_id),
    CONSTRAINT fk_recipe_ingredients_recipe FOREIGN KEY (recipe_id)
        REFERENCES recipes (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_recipe_ingredients_ingredient FOREIGN KEY (ingredient_id)
        REFERENCES ingredients (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_recipe_ingredients_food FOREIGN KEY (food_id)
        REFERENCES foods (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_recipe_ingredients_unit FOREIGN KEY (unit_id)
        REFERENCES measurement_units (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_recipe_ingredients_line_number CHECK (line_number >= 1),
    CONSTRAINT ck_recipe_ingredients_quantity
        CHECK (quantity IS NULL OR quantity > 0),
    -- A quantity without a unit is ambiguous; a unit without a quantity is
    -- incomplete. Both NULL is allowed and means "to taste".
    CONSTRAINT ck_recipe_ingredients_quantity_unit
        CHECK ((quantity IS NULL AND unit_id IS NULL)
            OR (quantity IS NOT NULL AND unit_id IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "Which recipes use this ingredient?" -- pantry-driven recommendation and the
-- reverse lookup when a pantry item is about to expire.
CREATE INDEX ix_recipe_ingredients_ingredient
    ON recipe_ingredients (ingredient_id, recipe_id);
CREATE INDEX ix_recipe_ingredients_food ON recipe_ingredients (food_id);
CREATE INDEX ix_recipe_ingredients_unit ON recipe_ingredients (unit_id);

-- Recipe tagging (many-to-many with the tag vocabulary).
CREATE TABLE recipe_tag_assignments (
    recipe_id  BIGINT UNSIGNED NOT NULL,
    tag_id     BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_recipe_tag_assignments PRIMARY KEY (recipe_id, tag_id),
    CONSTRAINT fk_recipe_tag_assignments_recipe FOREIGN KEY (recipe_id)
        REFERENCES recipes (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_recipe_tag_assignments_tag FOREIGN KEY (tag_id)
        REFERENCES recipe_tags (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Faceted search: "all VEGAN recipes". Reversed order of the PK, which is what
-- makes tag-first filtering index-only.
CREATE INDEX ix_recipe_tag_assignments_tag ON recipe_tag_assignments (tag_id, recipe_id);

-- Which meal slots a recipe suits. Lets the planner pick slot-appropriate
-- recipes without hardcoding assumptions about breakfast food.
CREATE TABLE recipe_meal_slot_types (
    recipe_id         BIGINT UNSIGNED NOT NULL,
    meal_slot_type_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT pk_recipe_meal_slot_types PRIMARY KEY (recipe_id, meal_slot_type_id),
    CONSTRAINT fk_recipe_meal_slot_types_recipe FOREIGN KEY (recipe_id)
        REFERENCES recipes (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_recipe_meal_slot_types_slot FOREIGN KEY (meal_slot_type_id)
        REFERENCES meal_slot_types (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "Which recipes fit breakfast?" -- the planner's per-slot candidate query.
CREATE INDEX ix_recipe_meal_slot_types_slot
    ON recipe_meal_slot_types (meal_slot_type_id, recipe_id);

-- Cached per-serving recipe nutrition.
--
-- Derived data, kept only as an explicitly invalidatable cache (DB-ADR-005):
--   * `computed_at` and `ingredient_revision` record what it was computed from,
--     so staleness is detectable rather than assumed.
--   * `completeness_ratio` states what fraction of the ingredient lines had
--     usable nutrition, so the UI can say "approximate" honestly instead of
--     presenting a partial sum as a fact.
-- Recomputation replaces the row; the source facts in food_nutrients are never
-- overwritten by it.
CREATE TABLE recipe_nutrition_snapshots (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    recipe_id           BIGINT UNSIGNED NOT NULL,
    computed_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- Bumped by the backend whenever the recipe's lines change, so a snapshot
    -- can be matched against the recipe state it describes.
    ingredient_revision INT UNSIGNED    NOT NULL DEFAULT 1,
    completeness_ratio  DECIMAL(5, 4)   NOT NULL DEFAULT 1.0000,
    computation_note    VARCHAR(255)    NULL,
    is_current          BOOLEAN         NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_recipe_nutrition_snapshots PRIMARY KEY (id),
    CONSTRAINT fk_recipe_nutrition_snapshots_recipe FOREIGN KEY (recipe_id)
        REFERENCES recipes (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_recipe_nutrition_snapshots_completeness
        CHECK (completeness_ratio >= 0 AND completeness_ratio <= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Exactly one current snapshot per recipe; superseded ones stay for comparison.
CREATE UNIQUE INDEX ux_recipe_nutrition_snapshots_current
    ON recipe_nutrition_snapshots
       (recipe_id, (CASE WHEN is_current THEN 1 ELSE NULL END));

-- Per-nutrient values of a snapshot, per single serving, in the nutrient's unit.
CREATE TABLE recipe_nutrition_values (
    snapshot_id        BIGINT UNSIGNED NOT NULL,
    nutrient_id        BIGINT UNSIGNED NOT NULL,
    amount_per_serving DECIMAL(12, 4)  NOT NULL,
    CONSTRAINT pk_recipe_nutrition_values PRIMARY KEY (snapshot_id, nutrient_id),
    CONSTRAINT fk_recipe_nutrition_values_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES recipe_nutrition_snapshots (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_recipe_nutrition_values_nutrient FOREIGN KEY (nutrient_id)
        REFERENCES nutrients (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_recipe_nutrition_values_amount CHECK (amount_per_serving >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Nutrition-based recipe filtering, e.g. "under 600 kcal per serving".
CREATE INDEX ix_recipe_nutrition_values_nutrient_amount
    ON recipe_nutrition_values (nutrient_id, amount_per_serving);

-- -----------------------------------------------------------------------------
-- Section 8 -- Recommendations and AI provenance
-- -----------------------------------------------------------------------------

-- One row per recommendation attempt made on a user's behalf.
--
-- Boundary rules (P1 ADR-003 and ADR-004, restated in DB-ADR-007):
--   * The Python AI service never writes here. Java creates the request, calls
--     Python over internal HTTP, validates the response, and records the result.
--   * `constraints_hash` is a digest of the profile, targets, preferences,
--     allergens and pantry snapshot Java sent. It makes a past recommendation
--     reproducible for the report without copying personal data into an
--     immutable audit row.
--   * Only request-level metadata is durable. Intermediate candidate sets,
--     feature vectors and partial scores stay transient inside the AI call.
CREATE TABLE recommendation_requests (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                BINARY(16)      NOT NULL,
    user_id                  BIGINT UNSIGNED NOT NULL,
    request_kind             VARCHAR(30)     NOT NULL,
    -- Slot this request targets, for single-meal suggestions.
    target_meal_slot_type_id BIGINT UNSIGNED NULL,
    -- Local calendar day the recommendation is for, when applicable.
    target_date              DATE            NULL,
    status                   VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    -- Correlation id shared with the AI call and the application logs.
    correlation_id           CHAR(36)        NULL,
    -- Digest of the inputs sent to the AI service. Not personal data itself.
    constraints_hash         CHAR(64)        NULL,
    -- Reported by the AI service and stored verbatim, so a stored score can
    -- always be attributed to the code that produced it.
    algorithm_version        VARCHAR(60)     NULL,
    model_identifier         VARCHAR(120)    NULL,
    requested_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at             DATETIME(6)     NULL,
    -- Latency measurement for the evaluation chapter of the report.
    duration_ms              INT UNSIGNED    NULL,
    -- Bounded, non-sensitive reason when the attempt did not succeed.
    failure_reason           VARCHAR(255)    NULL,
    CONSTRAINT pk_recommendation_requests PRIMARY KEY (id),
    CONSTRAINT ux_recommendation_requests_public_id UNIQUE (public_id),
    CONSTRAINT fk_recommendation_requests_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_recommendation_requests_slot
        FOREIGN KEY (target_meal_slot_type_id) REFERENCES meal_slot_types (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_recommendation_requests_kind
        CHECK (request_kind IN ('RECIPE_SUGGESTION', 'MEAL_PLAN',
                                'PANTRY_USE_UP', 'SUBSTITUTION')),
    CONSTRAINT ck_recommendation_requests_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'DEGRADED')),
    -- A finished attempt has a completion time; a pending one does not.
    CONSTRAINT ck_recommendation_requests_completion
        CHECK ((status = 'PENDING' AND completed_at IS NULL)
            OR (status <> 'PENDING' AND completed_at IS NOT NULL)),
    CONSTRAINT ck_recommendation_requests_completion_order
        CHECK (completed_at IS NULL OR completed_at >= requested_at),
    -- A failure must say why; a success must not carry a failure reason.
    CONSTRAINT ck_recommendation_requests_failure
        CHECK ((status = 'FAILED' AND failure_reason IS NOT NULL)
            OR (status <> 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "My recommendation history, newest first" and per-user date-range analysis.
CREATE INDEX ix_recommendation_requests_user_requested_at
    ON recommendation_requests (user_id, requested_at DESC);
-- Operational view of failures and stuck attempts.
CREATE INDEX ix_recommendation_requests_status_requested_at
    ON recommendation_requests (status, requested_at DESC);
-- Cohort comparison between algorithm versions for the evaluation chapter.
CREATE INDEX ix_recommendation_requests_algorithm_version
    ON recommendation_requests (algorithm_version, requested_at);
CREATE INDEX ix_recommendation_requests_slot
    ON recommendation_requests (target_meal_slot_type_id);

-- Recommended items that were actually presented to the user.
--
-- Only the returned shortlist is stored, not every candidate the AI considered.
-- `user_decision` closes the feedback loop: it is the label the evaluation
-- chapter needs, and the signal a future personalised ranker would learn from.
CREATE TABLE recommendation_results (
    id             BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    request_id     BIGINT UNSIGNED   NOT NULL,
    -- Position in the shortlist as shown to the user; 1 is the top suggestion.
    rank_position  SMALLINT UNSIGNED NOT NULL,
    recipe_id      BIGINT UNSIGNED   NULL,
    food_id        BIGINT UNSIGNED   NULL,
    -- Overall score as reported by the AI service, on the scale declared by the
    -- algorithm version. Component breakdown lives in the child table.
    total_score    DECIMAL(10, 4)    NULL,
    -- Short human-readable justification for the UI ("uses 3 expiring items").
    explanation    VARCHAR(500)      NULL,
    user_decision  VARCHAR(20)       NOT NULL DEFAULT 'PENDING',
    decided_at     DATETIME(6)       NULL,
    created_at     DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_recommendation_results PRIMARY KEY (id),
    CONSTRAINT ux_recommendation_results_rank UNIQUE (request_id, rank_position),
    CONSTRAINT fk_recommendation_results_request FOREIGN KEY (request_id)
        REFERENCES recommendation_requests (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    -- RESTRICT: a recipe referenced by recorded history is archived, not deleted.
    CONSTRAINT fk_recommendation_results_recipe FOREIGN KEY (recipe_id)
        REFERENCES recipes (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_recommendation_results_food FOREIGN KEY (food_id)
        REFERENCES foods (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_recommendation_results_rank CHECK (rank_position >= 1),
    -- A result recommends exactly one thing: a recipe or a food, never both,
    -- never neither.
    CONSTRAINT ck_recommendation_results_subject
        CHECK ((recipe_id IS NOT NULL AND food_id IS NULL)
            OR (recipe_id IS NULL AND food_id IS NOT NULL)),
    CONSTRAINT ck_recommendation_results_decision
        CHECK (user_decision IN ('PENDING', 'ACCEPTED', 'REJECTED', 'IGNORED')),
    CONSTRAINT ck_recommendation_results_decided_at
        CHECK ((user_decision = 'PENDING' AND decided_at IS NULL)
            OR (user_decision <> 'PENDING' AND decided_at IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Acceptance-rate analysis per recipe, and "was this ever suggested to anyone?"
CREATE INDEX ix_recommendation_results_recipe_decision
    ON recommendation_results (recipe_id, user_decision);
CREATE INDEX ix_recommendation_results_food ON recommendation_results (food_id);
-- Aggregate acceptance rates over time.
CREATE INDEX ix_recommendation_results_decision_decided_at
    ON recommendation_results (user_decision, decided_at);

-- Component scores behind a recommended item -- the evidence for "why this?".
--
-- Normalized against the declared ai_score_components vocabulary rather than
-- stored as a JSON blob, so scores are queryable and their scale is known
-- (see DB-ADR-002 on the JSON policy).
CREATE TABLE recommendation_result_scores (
    result_id          BIGINT UNSIGNED NOT NULL,
    score_component_id BIGINT UNSIGNED NOT NULL,
    score_value        DECIMAL(10, 4)  NOT NULL,
    -- Weight this component carried in the total, when the algorithm reports it.
    weight             DECIMAL(10, 4)  NULL,
    CONSTRAINT pk_recommendation_result_scores
        PRIMARY KEY (result_id, score_component_id),
    CONSTRAINT fk_recommendation_result_scores_result FOREIGN KEY (result_id)
        REFERENCES recommendation_results (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_recommendation_result_scores_component
        FOREIGN KEY (score_component_id) REFERENCES ai_score_components (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_recommendation_result_scores_weight
        CHECK (weight IS NULL OR weight >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "How did component C behave across recommendations?" for evaluation.
CREATE INDEX ix_recommendation_result_scores_component
    ON recommendation_result_scores (score_component_id, score_value);

-- -----------------------------------------------------------------------------
-- Section 9 -- Meal planning
-- -----------------------------------------------------------------------------

-- A meal plan covering a date range.
--
-- Planning window: `start_date` and `end_date` are local calendar dates, and
-- the generated `day_count` is derived rather than stored independently. Any
-- span from 1 to 31 days is representable, which covers the 3-7 day windows the
-- product targets without hardcoding one length (see DB-ADR-008).
--
-- Meals per day are NOT fixed: the number of meals is whatever
-- meal_plan_entries exist for a given date, and `meals_per_day_target` is only
-- the generator's input preference.
CREATE TABLE meal_plans (
    id                   BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    public_id            BINARY(16)        NOT NULL,
    user_id              BIGINT UNSIGNED   NOT NULL,
    title                VARCHAR(150)      NULL,
    start_date           DATE              NOT NULL,
    end_date             DATE              NOT NULL,
    -- Derived: inclusive length of the planning window.
    day_count            SMALLINT UNSIGNED
                         AS (DATEDIFF(end_date, start_date) + 1) STORED,
    -- Generator preference only; not a constraint on the entries.
    meals_per_day_target TINYINT UNSIGNED  NULL,
    -- Servings each entry defaults to, usually the household size.
    default_servings     TINYINT UNSIGNED  NOT NULL DEFAULT 1,
    -- DRAFT: proposed, editable, not committed. ACCEPTED: committed by the user
    -- (P1: only a Java-validated result may be saved). ACTIVE: currently being
    -- followed. COMPLETED / ABANDONED: historical.
    status               VARCHAR(20)       NOT NULL DEFAULT 'DRAFT',
    -- Which recommendation attempt produced this plan, when it was generated.
    -- SET NULL so purging old provenance never deletes a user's saved plan.
    source_request_id    BIGINT UNSIGNED   NULL,
    accepted_at          DATETIME(6)       NULL,
    completed_at         DATETIME(6)       NULL,
    archived_at          DATETIME(6)       NULL,
    version              BIGINT UNSIGNED   NOT NULL DEFAULT 0,
    created_at           DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                           ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_meal_plans PRIMARY KEY (id),
    CONSTRAINT ux_meal_plans_public_id UNIQUE (public_id),
    CONSTRAINT fk_meal_plans_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_meal_plans_source_request FOREIGN KEY (source_request_id)
        REFERENCES recommendation_requests (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_meal_plans_dates CHECK (end_date >= start_date),
    -- Structural bound on the window length.
    CONSTRAINT ck_meal_plans_window
        CHECK (DATEDIFF(end_date, start_date) <= 30),
    CONSTRAINT ck_meal_plans_meals_per_day
        CHECK (meals_per_day_target IS NULL
           OR (meals_per_day_target >= 1 AND meals_per_day_target <= 12)),
    CONSTRAINT ck_meal_plans_default_servings
        CHECK (default_servings >= 1 AND default_servings <= 50),
    CONSTRAINT ck_meal_plans_status
        CHECK (status IN ('DRAFT', 'ACCEPTED', 'ACTIVE', 'COMPLETED',
                          'ABANDONED')),
    -- A draft has not been accepted; anything past draft has an acceptance time.
    CONSTRAINT ck_meal_plans_accepted_at
        CHECK ((status = 'DRAFT' AND accepted_at IS NULL)
            OR (status <> 'DRAFT' AND accepted_at IS NOT NULL)),
    CONSTRAINT ck_meal_plans_completed_at
        CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR (status <> 'COMPLETED' AND completed_at IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "My plans covering date D" and the calendar view: the main planning query.
CREATE INDEX ix_meal_plans_user_dates
    ON meal_plans (user_id, start_date DESC, end_date);
-- "My current plan" -- status-filtered lookup per user.
CREATE INDEX ix_meal_plans_user_status ON meal_plans (user_id, status);
CREATE INDEX ix_meal_plans_source_request ON meal_plans (source_request_id);

-- One planned meal: a slot on a date within a plan.
--
-- No fixed meals-per-day: a day has as many entries as the user or generator
-- created, and `position_in_slot` allows more than one item in the same slot
-- (a main plus a side). `provenance` distinguishes AI-generated entries from
-- manual choices, which is what makes "how much of the accepted plan did the
-- user keep?" answerable.
CREATE TABLE meal_plan_entries (
    id                 BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    meal_plan_id       BIGINT UNSIGNED   NOT NULL,
    -- Local calendar date. Java validates it falls inside the plan window;
    -- MySQL cannot express that cross-row rule as a CHECK.
    plan_date          DATE              NOT NULL,
    meal_slot_type_id  BIGINT UNSIGNED   NOT NULL,
    position_in_slot   SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    -- Exactly one of recipe_id / food_id identifies what is eaten.
    recipe_id          BIGINT UNSIGNED   NULL,
    food_id            BIGINT UNSIGNED   NULL,
    servings           DECIMAL(6, 2)     NOT NULL DEFAULT 1.00,
    -- For a food entry measured by portion rather than servings.
    food_serving_id    BIGINT UNSIGNED   NULL,
    provenance         VARCHAR(20)       NOT NULL DEFAULT 'MANUAL',
    -- The specific suggestion this entry came from, when generated.
    source_result_id   BIGINT UNSIGNED   NULL,
    -- Did the user actually eat it? Feeds adherence reporting.
    consumption_status VARCHAR(20)       NOT NULL DEFAULT 'PLANNED',
    consumed_at        DATETIME(6)       NULL,
    note               VARCHAR(255)      NULL,
    created_at         DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_meal_plan_entries PRIMARY KEY (id),
    -- One item per (plan, date, slot, position): no accidental duplicates, but
    -- multiple items per slot remain possible.
    CONSTRAINT ux_meal_plan_entries_slot
        UNIQUE (meal_plan_id, plan_date, meal_slot_type_id, position_in_slot),
    CONSTRAINT fk_meal_plan_entries_plan FOREIGN KEY (meal_plan_id)
        REFERENCES meal_plans (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_meal_plan_entries_slot_type FOREIGN KEY (meal_slot_type_id)
        REFERENCES meal_slot_types (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- RESTRICT: a recipe inside a saved plan is archived, never deleted.
    CONSTRAINT fk_meal_plan_entries_recipe FOREIGN KEY (recipe_id)
        REFERENCES recipes (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_meal_plan_entries_food FOREIGN KEY (food_id)
        REFERENCES foods (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_meal_plan_entries_food_serving FOREIGN KEY (food_serving_id)
        REFERENCES food_servings (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_meal_plan_entries_source_result FOREIGN KEY (source_result_id)
        REFERENCES recommendation_results (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_meal_plan_entries_position CHECK (position_in_slot >= 1),
    CONSTRAINT ck_meal_plan_entries_servings
        CHECK (servings > 0 AND servings <= 50),
    CONSTRAINT ck_meal_plan_entries_subject
        CHECK ((recipe_id IS NOT NULL AND food_id IS NULL)
            OR (recipe_id IS NULL AND food_id IS NOT NULL)),
    -- A named portion only makes sense for a food entry.
    CONSTRAINT ck_meal_plan_entries_food_serving
        CHECK (food_serving_id IS NULL OR food_id IS NOT NULL),
    CONSTRAINT ck_meal_plan_entries_provenance
        CHECK (provenance IN ('MANUAL', 'AI_GENERATED', 'AI_EDITED',
                              'COPIED_FROM_PLAN')),
    CONSTRAINT ck_meal_plan_entries_consumption
        CHECK (consumption_status IN ('PLANNED', 'EATEN', 'SKIPPED',
                                      'REPLACED')),
    CONSTRAINT ck_meal_plan_entries_consumed_at
        CHECK ((consumption_status = 'EATEN' AND consumed_at IS NOT NULL)
            OR (consumption_status <> 'EATEN' AND consumed_at IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- No index for "render a plan day by day, in slot order": the primary read
-- path is served by ux_meal_plan_entries_slot, whose leading three columns are
-- exactly (meal_plan_id, plan_date, meal_slot_type_id).
-- "How often has this recipe been planned?" -- variety and repetition checks.
CREATE INDEX ix_meal_plan_entries_recipe_date
    ON meal_plan_entries (recipe_id, plan_date);
CREATE INDEX ix_meal_plan_entries_food ON meal_plan_entries (food_id);
CREATE INDEX ix_meal_plan_entries_food_serving ON meal_plan_entries (food_serving_id);
CREATE INDEX ix_meal_plan_entries_source_result ON meal_plan_entries (source_result_id);
CREATE INDEX ix_meal_plan_entries_slot_type ON meal_plan_entries (meal_slot_type_id);

-- -----------------------------------------------------------------------------
-- Section 10 -- Pantry
-- -----------------------------------------------------------------------------

-- A physical stock of one ingredient held by one user.
--
-- Ownership: `user_id` is on the row itself, so every pantry read is filtered by
-- owner and no cross-user leakage is possible through a join.
--
-- Expiry honesty (see DB-ADR-014): expiry is often unknown, and pretending
-- otherwise would make "use this before it spoils" advice wrong.
--   * `expiry_date` is NULL when the user does not know it.
--   * `expiry_kind` separates a regulatory USE_BY (safety) from a BEST_BEFORE
--     (quality) from UNKNOWN, because the two must not be treated alike.
--   * `expiry_confidence` records whether the date came from the label, from
--     the ingredient's typical shelf life, or from a guess.
--   * A NULL expiry date forces confidence UNKNOWN, so no code path can read a
--     missing date as "fresh".
--
-- Usable quantity: `quantity_remaining` is the current usable amount in
-- `unit_id`; `quantity_initial` is what was acquired. Both are needed to report
-- consumption and waste. Changes are appended to pantry_item_events.
CREATE TABLE pantry_items (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id          BINARY(16)      NOT NULL,
    user_id            BIGINT UNSIGNED NOT NULL,
    ingredient_id      BIGINT UNSIGNED NOT NULL,
    -- Optional exact product form, when the user scanned or chose one.
    food_id            BIGINT UNSIGNED NULL,
    quantity_initial   DECIMAL(12, 4)  NOT NULL,
    quantity_remaining DECIMAL(12, 4)  NOT NULL,
    unit_id            BIGINT UNSIGNED NOT NULL,
    -- Where it is kept. A single-column enumeration with no extra attributes,
    -- so a CHECK plus a Java enum, not a lookup table (DB-ADR-003).
    storage_location   VARCHAR(20)     NOT NULL DEFAULT 'PANTRY',
    -- Local calendar dates: when it entered the pantry and when it expires.
    acquired_on        DATE             NULL,
    expiry_date        DATE             NULL,
    expiry_kind        VARCHAR(20)      NOT NULL DEFAULT 'UNKNOWN',
    expiry_confidence  VARCHAR(20)      NOT NULL DEFAULT 'UNKNOWN',
    status             VARCHAR(20)      NOT NULL DEFAULT 'AVAILABLE',
    -- Set when the item leaves the pantry (consumed, discarded, expired).
    closed_at          DATETIME(6)      NULL,
    note               VARCHAR(255)     NULL,
    -- Optimistic locking: a pantry row is edited from several screens and by
    -- plan acceptance, so lost updates are a real risk (P1 section 11).
    version            BIGINT UNSIGNED  NOT NULL DEFAULT 0,
    created_at         DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_pantry_items PRIMARY KEY (id),
    CONSTRAINT ux_pantry_items_public_id UNIQUE (public_id),
    CONSTRAINT fk_pantry_items_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_pantry_items_ingredient FOREIGN KEY (ingredient_id)
        REFERENCES ingredients (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pantry_items_food FOREIGN KEY (food_id)
        REFERENCES foods (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pantry_items_unit FOREIGN KEY (unit_id)
        REFERENCES measurement_units (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_pantry_items_quantity_initial CHECK (quantity_initial > 0),
    -- Remaining stock is never negative and never exceeds what was acquired.
    CONSTRAINT ck_pantry_items_quantity_remaining
        CHECK (quantity_remaining >= 0 AND quantity_remaining <= quantity_initial),
    CONSTRAINT ck_pantry_items_storage_location
        CHECK (storage_location IN ('PANTRY', 'FRIDGE', 'FREEZER', 'OTHER')),
    CONSTRAINT ck_pantry_items_expiry_kind
        CHECK (expiry_kind IN ('USE_BY', 'BEST_BEFORE', 'UNKNOWN')),
    CONSTRAINT ck_pantry_items_expiry_confidence
        CHECK (expiry_confidence IN ('LABELLED', 'ESTIMATED', 'UNKNOWN')),
    -- No date means no confidence and no kind. A date must state which kind it
    -- is, so safety and quality deadlines are never conflated.
    CONSTRAINT ck_pantry_items_expiry_consistency
        CHECK ((expiry_date IS NULL
                AND expiry_kind = 'UNKNOWN'
                AND expiry_confidence = 'UNKNOWN')
            OR (expiry_date IS NOT NULL
                AND expiry_kind <> 'UNKNOWN'
                AND expiry_confidence <> 'UNKNOWN')),
    CONSTRAINT ck_pantry_items_expiry_after_acquired
        CHECK (expiry_date IS NULL OR acquired_on IS NULL
            OR expiry_date >= acquired_on),
    CONSTRAINT ck_pantry_items_status
        CHECK (status IN ('AVAILABLE', 'RESERVED', 'CONSUMED', 'DISCARDED',
                          'EXPIRED')),
    -- Closed statuses carry a close time; open ones do not.
    CONSTRAINT ck_pantry_items_closed_at
        CHECK ((status IN ('AVAILABLE', 'RESERVED') AND closed_at IS NULL)
            OR (status NOT IN ('AVAILABLE', 'RESERVED') AND closed_at IS NOT NULL)),
    -- A fully consumed item cannot still report stock, and an available item
    -- cannot report none.
    CONSTRAINT ck_pantry_items_status_quantity
        CHECK ((status = 'CONSUMED' AND quantity_remaining = 0)
            OR (status = 'AVAILABLE' AND quantity_remaining > 0)
            OR status IN ('RESERVED', 'DISCARDED', 'EXPIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "What expires soonest?" -- the expiry-priority query, and also every plain
-- "my pantry" read, because its leading (user_id, status) prefix answers those.
-- Java must exclude expiry_date IS NULL explicitly: in MySQL NULLs sort first
-- ascending, so an unknown expiry would otherwise masquerade as the most
-- urgent item.
CREATE INDEX ix_pantry_items_user_expiry
    ON pantry_items (user_id, status, expiry_date);
-- "Do I already have this ingredient?" and pantry-aware recipe matching.
CREATE INDEX ix_pantry_items_user_ingredient
    ON pantry_items (user_id, ingredient_id, status);
-- Reverse lookup for recommendation: holders of an ingredient across users.
CREATE INDEX ix_pantry_items_ingredient ON pantry_items (ingredient_id);
CREATE INDEX ix_pantry_items_food ON pantry_items (food_id);
CREATE INDEX ix_pantry_items_unit ON pantry_items (unit_id);

-- Append-only log of pantry quantity changes.
--
-- This is the audit trail that makes consumption and food-waste reporting
-- possible: `quantity_delta` is negative for consumption and waste, positive for
-- restocking, and `quantity_after` records the resulting stock so a
-- reconstruction never has to trust replayed arithmetic. Rows are never updated
-- or deleted while the item exists.
CREATE TABLE pantry_item_events (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pantry_item_id     BIGINT UNSIGNED NOT NULL,
    event_type         VARCHAR(20)     NOT NULL,
    quantity_delta     DECIMAL(12, 4)  NOT NULL,
    quantity_after     DECIMAL(12, 4)  NOT NULL,
    -- The planned meal that caused a consumption, when known. SET NULL keeps
    -- the waste/consumption record if the plan entry is later removed.
    meal_plan_entry_id BIGINT UNSIGNED NULL,
    note               VARCHAR(255)    NULL,
    -- UTC instant the change happened.
    occurred_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_pantry_item_events PRIMARY KEY (id),
    CONSTRAINT fk_pantry_item_events_item FOREIGN KEY (pantry_item_id)
        REFERENCES pantry_items (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_pantry_item_events_plan_entry FOREIGN KEY (meal_plan_entry_id)
        REFERENCES meal_plan_entries (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_pantry_item_events_type
        CHECK (event_type IN ('ADDED', 'ADJUSTED', 'CONSUMED', 'RESERVED',
                              'RELEASED', 'DISCARDED', 'EXPIRED')),
    CONSTRAINT ck_pantry_item_events_quantity_after
        CHECK (quantity_after >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Item history newest-first, and waste/consumption aggregation by type.
CREATE INDEX ix_pantry_item_events_item_time
    ON pantry_item_events (pantry_item_id, occurred_at DESC);
CREATE INDEX ix_pantry_item_events_type_time
    ON pantry_item_events (event_type, occurred_at);
CREATE INDEX ix_pantry_item_events_plan_entry
    ON pantry_item_events (meal_plan_entry_id);

-- -----------------------------------------------------------------------------
-- Section 11 -- Notifications
-- -----------------------------------------------------------------------------

-- Per-user, per-type notification preferences.
--
-- Only preference state and a minimal delivery record are modelled. No queues,
-- topics, retries or transport configuration: the schema records what the user
-- wants and what was sent, and the transport is an application concern.
CREATE TABLE user_notification_preferences (
    user_id              BIGINT UNSIGNED   NOT NULL,
    notification_type_id BIGINT UNSIGNED   NOT NULL,
    is_enabled           BOOLEAN           NOT NULL DEFAULT TRUE,
    -- How many days ahead to warn, for types that support a lead time
    -- (notification_types.supports_lead_time), e.g. expiring pantry items.
    lead_time_days       SMALLINT UNSIGNED NULL,
    -- Local wall-clock time the user prefers to be notified at. Combined with
    -- users.time_zone to compute the actual UTC send instant.
    preferred_time       TIME              NULL,
    created_at           DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                           ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_notification_preferences
        PRIMARY KEY (user_id, notification_type_id),
    CONSTRAINT fk_user_notification_preferences_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_notification_preferences_type
        FOREIGN KEY (notification_type_id) REFERENCES notification_types (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_user_notification_preferences_lead_time
        CHECK (lead_time_days IS NULL OR lead_time_days <= 90)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_user_notification_preferences_type
    ON user_notification_preferences (notification_type_id);

-- A notification raised for a user, and how far it got.
--
-- `dedup_key` is what keeps the scheduler idempotent: one row per logical event
-- (for example "item 42 expires on 2026-09-03"), so re-running the job cannot
-- notify twice. No message templates or provider payloads are stored.
CREATE TABLE notifications (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id              BIGINT UNSIGNED NOT NULL,
    notification_type_id BIGINT UNSIGNED NOT NULL,
    -- Stable key identifying the underlying event, unique per user.
    dedup_key            VARCHAR(190)    NOT NULL,
    -- Optional references to what the notification is about.
    pantry_item_id       BIGINT UNSIGNED NULL,
    meal_plan_id         BIGINT UNSIGNED NULL,
    title                VARCHAR(150)    NOT NULL,
    body                 VARCHAR(500)    NULL,
    status               VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    -- UTC instant the notification becomes due.
    scheduled_for        DATETIME(6)     NOT NULL,
    sent_at              DATETIME(6)     NULL,
    read_at              DATETIME(6)     NULL,
    failure_reason       VARCHAR(255)    NULL,
    created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    -- Idempotent scheduling: the same event never produces two notifications.
    CONSTRAINT ux_notifications_user_dedup UNIQUE (user_id, dedup_key),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_notifications_type FOREIGN KEY (notification_type_id)
        REFERENCES notification_types (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- The notification survives deletion of what it referred to.
    CONSTRAINT fk_notifications_pantry_item FOREIGN KEY (pantry_item_id)
        REFERENCES pantry_items (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_notifications_meal_plan FOREIGN KEY (meal_plan_id)
        REFERENCES meal_plans (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_notifications_status
        CHECK (status IN ('PENDING', 'SENT', 'READ', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_notifications_sent_at
        CHECK ((status IN ('SENT', 'READ') AND sent_at IS NOT NULL)
            OR (status NOT IN ('SENT', 'READ') AND sent_at IS NULL)),
    CONSTRAINT ck_notifications_read_at
        CHECK ((status = 'READ' AND read_at IS NOT NULL)
            OR (status <> 'READ' AND read_at IS NULL)),
    CONSTRAINT ck_notifications_failure
        CHECK ((status = 'FAILED' AND failure_reason IS NOT NULL)
            OR (status <> 'FAILED' AND failure_reason IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The dispatcher's query: due, still-pending notifications in time order.
CREATE INDEX ix_notifications_status_scheduled_for
    ON notifications (status, scheduled_for);
-- The user's in-app inbox, newest first.
CREATE INDEX ix_notifications_user_created_at
    ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_pantry_item ON notifications (pantry_item_id);
CREATE INDEX ix_notifications_meal_plan ON notifications (meal_plan_id);
CREATE INDEX ix_notifications_type ON notifications (notification_type_id);

-- -----------------------------------------------------------------------------
-- Section 12 -- Search support and account history
-- -----------------------------------------------------------------------------

-- Recorded searches, kept for two concrete purposes: offering the user their
-- recent searches, and measuring which queries return nothing so the catalog can
-- be improved. Not a general analytics pipeline.
--
-- Retention: rows carry no personal content beyond the query text the user
-- typed, and are purged on a schedule. Anonymisation clears user_id via
-- ON DELETE SET NULL, leaving aggregate query statistics intact.
CREATE TABLE search_queries (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NULL,
    search_scope  VARCHAR(20)     NOT NULL,
    query_text    VARCHAR(200)    NOT NULL,
    result_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    searched_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_search_queries PRIMARY KEY (id),
    CONSTRAINT fk_search_queries_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_search_queries_scope
        CHECK (search_scope IN ('RECIPE', 'FOOD', 'INGREDIENT', 'ALL')),
    CONSTRAINT ck_search_queries_text_not_blank
        CHECK (CHAR_LENGTH(TRIM(query_text)) > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "My recent searches", newest first.
CREATE INDEX ix_search_queries_user_time ON search_queries (user_id, searched_at DESC);
-- Zero-result and popular-query analysis per scope.
CREATE INDEX ix_search_queries_scope_results
    ON search_queries (search_scope, result_count, searched_at);

-- Saved and favourite recipes -- the explicit taste signal, distinct from the
-- implicit accept/reject signal in recommendation_results.
CREATE TABLE user_recipe_favorites (
    user_id    BIGINT UNSIGNED NOT NULL,
    recipe_id  BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_recipe_favorites PRIMARY KEY (user_id, recipe_id),
    CONSTRAINT fk_user_recipe_favorites_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_recipe_favorites_recipe FOREIGN KEY (recipe_id)
        REFERENCES recipes (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "Who favourited this recipe?" -- popularity ranking input.
CREATE INDEX ix_user_recipe_favorites_recipe ON user_recipe_favorites (recipe_id);

-- Security-relevant account events.
--
-- Deliberately narrow (DB-ADR-015): only the account lifecycle and credential
-- events a university project must be able to explain, not a generic audit
-- framework over every table. No credentials, tokens or password values are
-- ever written here -- only the fact that an event occurred.
CREATE TABLE user_account_events (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    event_type  VARCHAR(40)     NOT NULL,
    -- Non-sensitive context, e.g. 'password_reset_completed'. Never secrets.
    detail      VARCHAR(255)    NULL,
    ip_address  VARBINARY(16)   NULL,
    occurred_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_account_events PRIMARY KEY (id),
    CONSTRAINT fk_user_account_events_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_user_account_events_type
        CHECK (event_type IN ('REGISTERED', 'EMAIL_VERIFIED', 'LOGIN_SUCCEEDED',
                              'LOGIN_FAILED', 'LOCKED', 'UNLOCKED',
                              'PASSWORD_CHANGED', 'PASSWORD_RESET_REQUESTED',
                              'PASSWORD_RESET_COMPLETED', 'ROLE_GRANTED',
                              'ROLE_REVOKED', 'DEACTIVATED', 'REACTIVATED',
                              'ANONYMIZED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Account timeline newest-first, and brute-force investigation by event type.
CREATE INDEX ix_user_account_events_user_time
    ON user_account_events (user_id, occurred_at DESC);
CREATE INDEX ix_user_account_events_type_time
    ON user_account_events (event_type, occurred_at);

-- =============================================================================
-- End of V001. Reference vocabulary rows are loaded separately by
-- database/seed/R001__reference_data.sql.
-- =============================================================================

