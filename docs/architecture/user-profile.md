# P5 — User Profile, Preferences & Measurements Domain Design

## 1. Objective & Scope

Phase 5 (P5) introduces comprehensive user profile, body measurement tracking, dietary preference management, and allergen safety profiles into the Spring Boot backend of the **AI Smart Meal Planner** platform.

All P5 capabilities operate within the authenticated `/api/v1/me/**` and `/api/v1/reference/**` namespaces, backed by the MySQL schema established in P2 without modifying `V001__initial_schema.sql` or altering the existing database baseline.

### In-Scope Domain Areas:
1. **User Profile Management**:
   - Demographic baseline (`birth_date`, `sex`, `height_cm`, `household_size`, `max_cook_minutes`, `notes`).
   - Goal and lifestyle reference mapping (`activity_level_code`, `nutrition_goal_code`).
   - Target weight and target weekly weight change rate.
   - Optimistic concurrency control via entity versioning.
2. **Body Measurements Tracking**:
   - Historical time-series tracking (`measured_on`, `weight_kg`, `body_fat_percent`, `waist_cm`, `source`, `notes`).
   - Idempotent record/update semantics per user date.
   - Paginated historical logs and latest measurement lookup.
3. **Dietary Preferences**:
   - Multi-selection of dietary lifestyle choices (e.g., `VEGETARIAN`, `VEGAN`, `LOW_CARB`, `KETO`).
   - Exclusionary flag indicators informing downstream meal plan filtering.
   - Atomic replacement with duplicate payload tolerance.
4. **Allergens & Intolerances**:
   - Medical allergy and intolerance registration against standard allergen references (`PEANUT`, `GLUTEN`, `CRUSTACEAN`, etc.).
   - Reaction severity distinction (`ALLERGY` vs `INTOLERANCE`) and custom user reaction notes.
   - Atomic replacement supporting safety-first dietary exclusion.
5. **Reference Data Lookups**:
   - Deterministic, read-only listings for `activity-levels`, `nutrition-goals`, `dietary-preferences`, and `allergens`.

### Strict Out-of-Scope (Deferred to Downstream Phases):
- **Dynamic Nutrition Targets & Caloric Calculations**: BMR, TDEE, macronutrient distribution calculations, and target calorie derivation are deferred to Phase 6 / AI planning service.
- **BMI Persistence**: BMI is a derived calculation and is intentionally NOT stored in the database schema.
- **Food Catalog, Recipes, Pantry, & Meal Plans**: Maintained as distinct modules in future phases.
- **AI Service & Flutter Client**: Backend API foundation only; no Flutter or Python AI changes in P5.

---

## 2. Architecture & Identity Bridge

### Separation of Concerns & Modular Boundary
The `profile` package (`com.smartmealplanner.profile`) is strictly separated from `com.smartmealplanner.auth`:
- The profile domain does **not** import or query `UserAccountRepository`, `RoleRepository`, or any internal authentication entities.
- Password hashes, verification tokens, refresh tokens, and session states remain encapsulated within the `auth` module.

```text
+-----------------------+           +-----------------------------+
|    Client Request     |           |     Spring Security         |
| (Bearer Access Token) | --------> |  (Validates JWT & Extracts) |
+-----------------------+           +-----------------------------+
                                                   |
                                                   v
+-----------------------------------------------------------------+
|                       CurrentUserService                        |
|  Bridges public UUID (from JWT subject) to internal surrogate   |
|  ID via CurrentUserIdentity(Long internalId, UUID publicId)     |
+-----------------------------------------------------------------+
                                                   |
                    +------------------------------+------------------------------+
                    |                                                             |
                    v                                                             v
+---------------------------------------+                     +---------------------------------------+
|          UserProfileService           |                     |      UserBodyMeasurementService       |
| Operates strictly on internal user ID |                     | Operates strictly on internal user ID |
+---------------------------------------+                     +---------------------------------------+
```

### Information Hiding & Zero Surrogate Key Exposure
- The internal primary keys (`user_profiles.user_id`, `user_body_measurements.id`, `user_allergens.id`) are internal surrogate identifiers.
- REST DTOs (`ProfileResponse`, `MeasurementResponse`, `UserDietaryPreferenceResponse`, `UserAllergenResponse`) **never** expose internal BIGINT database keys.
- Entities are addressed by natural keys:
  - User identity is addressed by authenticated context.
  - Measurements are addressed by ISO-8601 date (`measuredOn`).
  - Reference items are addressed by unique alphanumeric codes (`code`).

---

## 3. Concurrency Control & Optimistic Locking

The `user_profiles` table includes a `version BIGINT NOT NULL DEFAULT 0` column.

```java
@Version
@Column(nullable = false)
private Long version;
```

### Concurrency Rules:
1. When retrieving a profile (`GET /api/v1/me/profile`), the current `version` integer is included in `ProfileResponse`.
2. When submitting updates (`PUT /api/v1/me/profile`), clients provide the known `version` in `UpdateProfileRequest`.
3. If two concurrent requests attempt to update the same profile, the second transaction detects a version mismatch and raises `OptimisticLockingFailureException`.
4. `ApiExceptionHandler` intercepts the exception and maps it to HTTP `409 CONFLICT` with Problem Details (`{"code": "CONFLICT", "detail": "Profile version conflict"}`).
5. If creating a profile for the first time, `version` is expected to be null or 0.

---

## 4. Body Measurement Semantics

### Time-Series Representation
Measurements are captured in `user_body_measurements`:
- Composite uniqueness constraint: `UNIQUE KEY uq_user_body_measurements_user_date (user_id, measured_on)`.
- Temporal constraint: `measured_on` must not be in the future (validated against UTC clock).
- Range constraints:
  - Weight: 2.00 kg – 700.00 kg.
  - Body Fat: 0.00% – 100.00%.
  - Waist: 10.00 cm – 400.00 cm.

### Idempotent UPSERT vs Same-Date Correction
- `POST /api/v1/me/measurements`: Records a measurement for a specific date. If a record already exists for the user on that date, it performs a same-date correction (updates existing row) rather than failing or duplicating.
- `PUT /api/v1/me/measurements/{measuredOn}`: Explicitly updates the measurement for a given date. If absent, returns HTTP `404 NOT_FOUND`.
- `DELETE /api/v1/me/measurements/{measuredOn}`: Deletes the measurement for that date.
- `GET /api/v1/me/measurements/latest`: Returns the most recently measured entry (highest `measured_on`), or HTTP `404 NOT_FOUND` if no records exist.

---

## 5. Dietary Preferences & Allergen Exclusion Semantics

### Dietary Preferences
- Preferences are modeled as M:N associations between users and the `dietary_preferences` reference table (`user_dietary_preferences`).
- `PUT /api/v1/me/dietary-preferences` accepts a complete list of preference codes (`List<String> codes`).
- The operation is atomic: existing preferences for the user are cleared and replaced with the new set.
- Duplicate entries in the request body are automatically deduplicated.
- If any requested code does not exist in `dietary_preferences`, the entire request is rejected with HTTP `400 BAD_REQUEST`.

### Allergens & Safety-First Exclusion
- Allergens represent critical health constraints.
- Associations are stored in `user_allergens`, capturing:
  - `allergen_code` (FK to `allergens`).
  - `reaction_kind` (`ALLERGY` or `INTOLERANCE`).
  - `notes` (optional specifics, e.g., "Anaphylactic shock risk", "Mild hives").
- `PUT /api/v1/me/allergens` replaces user allergens atomically.
- **Academic AI Planning Contract**: Downstream recommendation algorithms must treat `ReactionKind.ALLERGY` as a **hard constraint** (strict filter: recipes containing this allergen MUST NEVER be recommended), whereas exclusionary preferences or intolerances can be weighted or parameterized based on user mode.

---

## 6. Reference Data API Contracts

Reference data endpoints are available under `/api/v1/reference/**` (and aliased under `/api/v1/**` for consumer convenience):

| Method | URI | Description | Order by |
|---|---|---|---|
| GET | `/api/v1/reference/activity-levels` | List physical activity levels | `display_order ASC, code ASC` |
| GET | `/api/v1/reference/nutrition-goals` | List high-level nutrition goals | `display_order ASC, code ASC` |
| GET | `/api/v1/reference/dietary-preferences` | List dietary preferences | `display_order ASC, code ASC` |
| GET | `/api/v1/reference/allergens` | List standard recognized allergens | `display_order ASC, code ASC` |

All reference responses return stable natural identifiers (`code`, `name`, `description`, `displayOrder`) and omit internal primary keys.

---

## 7. Security & Multi-Tenancy Ownership

1. **Authentication Enforcement**:
   - Every profile, measurement, preference, and allergen endpoint requires a valid Bearer JWT.
   - Unauthenticated requests are rejected with HTTP `401 UNAUTHORIZED`.
2. **Contextual Identity Resolution**:
   - The user ID is never passed in request URLs or bodies (e.g., `/api/v1/users/{id}/profile` is intentionally rejected in favor of `/api/v1/me/profile`).
   - The user's internal ID is derived strictly from the verified JWT `sub` (UUID) claim via `CurrentUserService.getIdentity()`.
3. **Multi-Tenancy Isolation**:
   - User A can never inspect, alter, or delete measurements or preferences belonging to User B.
   - Repository queries include `WHERE user_id = :userId` in all data access operations.

---

## 8. Automated Test Coverage Evidence

The P5 implementation is fully covered by automated unit and integration tests:

1. **Unit Tests (Surefire - 56 tests passing)**:
   - `UserProfileServiceTest`: Profile retrieval, creation, updates, constraint validation, optimistic locking, and unknown reference handling.
   - `UserBodyMeasurementServiceTest`: Measurement logging, upsert correction, date bounds, physical value validation, and pagination.
   - `UserDietaryPreferenceServiceTest`: Preference mapping, duplicate deduplication, and atomic replacement.
   - `UserAllergenServiceTest`: Allergen mapping, reaction kind distinction, and atomic replacement.
   - `ReferenceDataServiceTest`: Deterministic ordering and payload mapping.
2. **Integration Tests (Failsafe - 58 tests passing)**:
   - `ProfileIT`: End-to-end testing against real MySQL 8.4 via Testcontainers.
   - Full Flyway migration execution (`V001__initial_schema.sql` + `R__001_reference_data.sql`).
   - Strict Hibernate validation (`ddl-auto=validate`).
   - Cross-user ownership security verification.
