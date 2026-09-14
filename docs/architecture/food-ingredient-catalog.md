# P7 - Food & Ingredient Catalog Core

- **Phase:** P7
- **P7 status:** Implementation complete through P7.8; final P7.9 verification is
  required before the phase is accepted as complete.
- **Scope:** Java/MySQL catalog foundations for foods, ingredients, catalog facts,
  and authenticated catalog reads.

## 1. Objective

P7 establishes the authoritative Food and Ingredient catalog that later recipe,
pantry, recommendation, substitution, and meal-planning work will consume. It
uses the normalized P2 schema already applied through Flyway V001. P7 does not
redesign that schema and does not make Food and Ingredient interchangeable.

The catalog provides reproducible nutrition facts, canonical culinary identity,
serving facts, aliases, allergen facts, and honest unit-conversion facts. Java
remains the public application boundary and MySQL remains the authoritative store.

## 2. Dependency boundary

The Food module owns catalog workflows and exposes small application/API
contracts. Its normal direction is:

    web controller -> food application service -> food repository -> food persistence

Controllers remain thin and do not query JPA repositories, calculate nutrition,
or infer conversions. Repositories and JPA entities remain private to the Food
module.

The Food module maps the V001 catalog contract directly. Its persistence
entities remain module-private; public application and HTTP projections expose
UUIDs and stable codes rather than JPA entities or surrogate identifiers.

Cross-module rules are deliberately narrow:

- Nutrition owns the nutrient and measurement-unit reference vocabulary. Food
  composition uses narrow lazy persistence references only; Food application
  code does not use Nutrition repositories as a shortcut.
- The profile module owns the allergen vocabulary used for user allergy records.
  Ingredient allergen facts store only the V001 scalar reference and resolve it
  through a focused Profile application query contract; Food has no Profile
  persistence dependency.
- CurrentUserService and CurrentUserIdentity are the allowed Auth application
  boundary for user-disliked-ingredient workflows. Food must not import Auth
  persistence merely to resolve a user.
- Recipe, pantry, substitution, recommendation, and meal-planning modules later
  consume Food application contracts and public identifiers. They must not
  directly access Food repositories or entities.

No Python service, external catalog provider, AI client, Elasticsearch, or vector
store participates in P7.

## 3. Existing database contract

V001 is the source of truth. P7 uses these existing tables and constraints:

| Concern | Existing tables | Important contract |
| --- | --- | --- |
| Food catalog | foods, food_categories | foods.public_id is unique; foods.code is a nullable import/curation key; categories use stable unique code. |
| Food composition | food_nutrients, nutrients, measurement_units | One normalized row per (food_id, nutrient_id); nutrient units are reference-owned. |
| Food portions | food_servings | A named serving has a positive quantity/unit and at least a gram weight or millilitres. |
| Ingredient catalog | ingredients | ingredients.public_id and stable code are unique; default food/unit references are nullable. |
| Ingredient names | ingredient_aliases | An alias is globally unique and resolves to one canonical ingredient. |
| Ingredient nutrition choices | ingredient_foods | The many-to-many edge has preparation state, yield factor, and at most one primary food per ingredient. |
| Ingredient conversion facts | ingredient_unit_conversions | Ordered unit pairs are per ingredient and must have positive quantities. |
| Ingredient allergen facts | ingredient_allergens | Presence is CONTAINS, MAY_CONTAIN, or FREE_FROM. |
| User avoidance state | user_disliked_ingredients | One row per user/ingredient with DISLIKE or AVOID. |

The database already supplies active-name/category indexes and MySQL FULLTEXT
indexes for foods and ingredients. No P7.0 migration is necessary. If a future
implementation proves V001 cannot express a required invariant, it must propose
a new V002+ migration and an architecture decision; V001 is never edited.

## 4. Food domain ownership

Food is the nutrition fact carrier. The Food module owns the foods,
food_nutrients, food_servings, and food-category catalog workflows, including
source/provenance preservation and public projections.

A Food can represent a generic item or a branded product. Its V001 fields carry:

- a server-generated public UUID and an optional curated/import code;
- display name, optional brand, optional category, and description;
- nutrition basis of PER_100_G or PER_100_ML;
- optional density_g_per_ml, only when known for that food;
- source (CURATED, IMPORTED, or USER_SUBMITTED) and optional source_reference;
- revision for nutrition-fact corrections, optimistic-lock version, and
  active/retirement state.

No Food API represents nutrition as hard-coded fields such as protein, carbs,
fat, or vitaminC.

## 5. Food nutrition model

The normalized composition relationship is mandatory:

    Food -> FoodNutrient -> Nutrient -> MeasurementUnit

Each food_nutrients.amount is non-negative, expressed per the parent Food's
declared nutrition basis, and uses the unit attached to the referenced Nutrient.
data_quality remains explicit as ANALYTICAL, CALCULATED, or ESTIMATED. The
application must not duplicate nutrient units on a food-nutrient row or invent
an amount for a nutrient that has no curated fact.

Food detail projections may return an ordered list of nutrient facts with stable
nutrient code, display data where needed, unit code, amount, and data quality.
They never expose food_id, nutrient_id, or unit_id.

Correcting nutrition-relevant Food facts must preserve source/provenance and
advance the Food revision according to the existing revision semantics. Later
recipe snapshots rely on that revision rather than silently rewritten facts.

## 6. Food serving model

food_servings stores real catalog serving facts, not inferred household-unit
rules. A serving has a display name, positive quantity, stable unit reference,
and at least one resolved gram_weight or milliliters; at most one serving per
Food is the default.

Portion and nutrition calculations may use a serving only when it provides the
needed measured bridge to the Food's per-100 basis. The backend must not guess an
unknown mass/volume conversion from a serving label, assume water density, or
manufacture a household conversion.

## 7. Ingredient domain ownership

Ingredient is the canonical culinary concept used by future recipe lines,
pantry inventory, recommendation, substitution, and search. The Food module owns
the catalog workflows for ingredients, aliases, ingredient-to-food mappings,
ingredient allergen facts, and ingredient-specific conversion facts.

An Ingredient carries a public UUID, stable code, display name, optional food
category, optional default Food and default unit, optional piece gram weight,
advisory shelf-life metadata, staple flag, optimistic-lock version, and
retirement state. It is not a nutrition row and does not duplicate Food nutrient
facts.

## 8. Invariant: Ingredient != Food

This distinction is explicit and non-negotiable:

    Ingredient: Chicken Breast

    Foods that can represent its nutrition:
      - chicken breast, raw
      - chicken breast, grilled

Nutrition belongs to Food. Recipe/pantry/substitution identity belongs to
Ingredient. An Ingredient may have multiple Food representations, and one Food
may serve multiple Ingredients. P7 must not collapse the two concepts, copy
nutrition columns onto Ingredient, or use a Food ID as a substitute for a
canonical Ingredient identity.

## 9. Ingredient aliases

ingredient_aliases holds alternate names and spellings for search and import
resolution, including locale-specific names. An alias resolves to exactly one
canonical Ingredient because alias is unique.

Alias resolution is deterministic catalog matching. It may normalize ordinary
input according to the database collation and use exact/prefix or relational
FULLTEXT query paths where appropriate, but P7 does not introduce LLM matching,
semantic similarity, or AI fuzzy matching. An unresolved alias/search term stays
unresolved; the backend does not create a new Ingredient or choose an arbitrary
near match.

## 10. Ingredient-to-Food nutritional mappings

ingredient_foods links an Ingredient to candidate Food representations. Its
facts are:

- preparation_state: RAW, COOKED, DRIED, CANNED, FROZEN, or UNSPECIFIED;
- a positive yield_factor describing preparation mass change;
- is_primary, with at most one primary Food per Ingredient.

ingredients.default_food_id is the denormalized default lookup. When P7 writes
or changes it, the application must validate that the selected Food is a valid
mapping for that Ingredient and is consistent with the primary-mapping policy.
This is an application invariant because V001 cannot express the cross-table
relationship directly.

The mapping makes a nutrition choice explicit. It must not imply that raw and
cooked foods are nutritionally interchangeable.

## 11. Ingredient allergen facts

ingredient_allergens records catalog facts against the stable allergen
vocabulary. It preserves the distinction between CONTAINS and MAY_CONTAIN; V001
also supports the explicit positive assertion FREE_FROM.

P7 stores and returns facts only. It does not implement user-allergen filtering,
recommendation scoring, medical advice, or substitution safety decisions. Future
consumers must interpret the recorded facts through their own approved workflow
while retaining the presence distinction.

## 12. Ingredient-specific unit conversions

Universal conversions within a measurement dimension remain generic reference
math, for example kg -> g or volume-unit conversions defined by the reference
vocabulary. Cross-dimension and household conversions are facts about an
Ingredient:

- cup flour -> grams;
- cup honey -> grams;
- clove garlic -> grams.

ingredient_unit_conversions records an ordered unit pair, positive source and
target quantities, confidence (MEASURED, REFERENCE, or ESTIMATED), and an
optional source note. ingredients.piece_gram_weight is also a per-Ingredient
fact when a countable piece has a meaningful typical mass.

If a required ingredient-specific conversion is absent, the backend must refuse
the conversion with a structured domain outcome. It must never invent a factor,
assume density, or use another Ingredient's conversion.

## 13. User disliked/avoided ingredients

user_disliked_ingredients is in P7 because it relies on canonical Ingredients,
but it is private per-user state rather than shared catalog data. It stores an
optional note and a distinct strength:

- DISLIKE: a stated preference;
- AVOID: a stated avoidance.

It is separate from user_allergens and from ingredient_allergens. P7 does not
rank, filter, score, or otherwise make recommendations from these records.
Future recommendation code must receive this information through a stable
application contract rather than by reaching into Food persistence.

## 14. Lifecycle and retirement semantics

Foods and Ingredients are retired rather than physically deleted. V001 enforces
the paired state invariant:

    is_active = TRUE  <=> retired_at is NULL
    is_active = FALSE <=> retired_at is non-NULL

Authenticated public reads list active catalog rows only. A retired row remains
referencable for historical/operational data and may be visible to authorized
catalog administration, but it must not be presented as an active choice.

Food revision protects reproducibility of nutrition facts; Food and Ingredient
version support optimistic concurrency. P7 catalog curation must not delete
historical rows or overwrite provenance without an explicit correction workflow.
Retirement/reactivation and aggregate updates belong to authorized application
services, not direct repository calls from controllers.

## 15. Search and pagination contract

Catalog reads are deterministic and relational:

- GET /api/v1/foods and GET /api/v1/ingredients default to page=0 and size=20;
  size must be bounded to 1..100.
- Browse results default to active rows and use stable ordering
  displayName ASC, publicId ASC.
- categoryCode filters by the stable food-category code. A category identifier
  is never a surrogate ID.
- q is optional, trimmed, and bounded. Food search uses the existing MySQL
  ftx_foods_name_brand; Ingredient search uses ftx_ingredients_name plus
  deterministic alias resolution through ingredient_aliases.
- Relevance-ranked search has a stable tie break: relevance descending, then
  display name and public UUID. Responses return actual page, size, total
  elements, total pages, and ordered content.

P7 uses existing MySQL indexes/FULLTEXT only. It does not introduce
Elasticsearch, a vector database, a semantic-search service, or an LLM search
layer.

## 16. Public identifiers vs internal surrogate IDs

Foods and Ingredients expose public_id as UUID publicId values. Java generates
these values; a client never chooses an internal key. Stable reference
vocabularies use existing codes such as food-category code, nutrient code, unit
code, and allergen code.

Public routes, request fields, and response fields must never expose or accept
database BIGINT values including:

- food_id, ingredient_id, food_category_id, nutrient_id, or unit_id;
- association-table composite keys or serving/conversion/alias surrogate IDs;
- user_id in any /api/v1/me workflow.

Admin write commands refer to existing catalog records by public UUID and
reference data by stable code. Detail responses use public-safe nested values,
not JPA entities.

## 17. Authorization boundary

The existing P4 security contract applies unchanged:

- anyRequest().authenticated() protects non-public API routes, so catalog reads
  are authenticated reads under the current implementation.
- /api/v1/admin/** requires hasRole(\"ADMIN\"), which corresponds to the existing
  ROLE_ADMIN catalog-administrator role.
- ROLE_ADMIN is limited to shared catalog curation. It does not bypass ownership
  of user profile, pantry, plan, or other private resources.
- User-disliked-ingredient operations resolve ownership only from the verified
  JWT subject through SecurityPrincipals and the Auth application boundary;
  request bodies never choose a user.

P7 does not alter SecurityConfiguration, role seeds, JWT claims, CSRF policy, or
the shared Problem Details infrastructure.

## 18. Proposed REST API contract

The following routes define the intended P7 boundary. Their controllers are not
implemented by P7.0.

### Authenticated catalog reads

| Method | Route | Contract |
| --- | --- | --- |
| GET | /api/v1/foods | Active Food browse/search with q, categoryCode, page, and size. |
| GET | /api/v1/foods/{publicId} | One active Food by public UUID, including normalized nutrients and servings when implemented. |
| GET | /api/v1/ingredients | Active Ingredient browse/search with q, categoryCode, page, and size. |
| GET | /api/v1/ingredients/{publicId} | One active Ingredient by public UUID, including aliases/mappings/facts when implemented. |
| GET | /api/v1/reference/food-categories | Ordered category reference data with code, display name, parent code when present, and description. |

Food results expose public UUID, public-safe catalog/provenance fields, category
code, nutrition basis, revision, and active-state-safe projections. Ingredient
results expose public UUID, code, display name, category/default reference codes
or public UUIDs where present, and public-safe related facts.
Food-category reference rows use deterministic display-name/code ordering.

### Authenticated user avoidance state

P7.8 is expected to expose a focused /me contract, following the existing P5
complete-replacement convention:

| Method | Route | Contract |
| --- | --- | --- |
| GET | /api/v1/me/disliked-ingredients | The authenticated user's canonical Ingredient preferences. |
| PUT | /api/v1/me/disliked-ingredients | Atomically replaces the authenticated user's supplied Ingredient public UUID/strength/note list. |

The exact request/response DTOs remain implementation work, but neither route
accepts a user ID or an internal Ingredient ID.

### Admin catalog mutation boundary

All catalog curation is confined to these role-protected route families:

    /api/v1/admin/foods/**
    /api/v1/admin/ingredients/**

P7 implements public-safe catalog reads and the private current-user avoidance
workflow. Catalog curation/admin mutation remains a documented boundary for a
later phase; no P7 controller writes shared Food or Ingredient catalog facts.
Any future admin request uses public UUIDs and reference codes, not surrogate
IDs, and must preserve Food provenance/revision and retirement semantics.

## 19. Error and validation semantics

P7 uses the existing safe application/problem+json infrastructure, including its
request correlation ID. It must not expose JPA exception messages, SQL constraint
text, internal IDs, raw source credentials, or a caller's full catalog input in
an error detail.

Expected semantics are:

| Situation | Proposed result |
| --- | --- |
| Malformed UUID/date/body, blank required text, invalid page/size/filter, unknown reference code | 400 BAD_REQUEST with a stable safe code. |
| No valid access token | Existing 401 UNAUTHORIZED security response. |
| Non-admin attempts catalog mutation | Existing 403 FORBIDDEN security response. |
| Unknown or retired public catalog item not visible to the caller | 404 NOT_FOUND; do not leak an internal ID. |
| Duplicate food/ingredient code, duplicate alias, stale optimistic version, or incompatible aggregate change | 409 CONFLICT where applicable. |
| Requested ingredient-specific conversion has no fact | A structured refusal, such as 422 UNPROCESSABLE_ENTITY with CONVERSION_NOT_AVAILABLE; never a fabricated value. |

Application services enforce cross-row rules such as a valid default-food
mapping, one canonical alias target, valid nutrient/unit reference codes, and a
coherent retirement state. Database constraints remain the final integrity
defense.

## 20. Transaction boundaries

Read operations use focused read-only application transactions and map all lazy
relations to immutable application views before returning to web code.

Each catalog aggregate write is short and atomic:

- a Food header with its nutrition rows and serving rows commits or rolls back as
  one operation;
- an Ingredient header with aliases, Food mappings, allergen facts, and
  ingredient-specific conversion facts commits or rolls back as one operation;
- a user-disliked-ingredient complete replacement commits atomically for the
  authenticated user.

Optimistic locking uses the existing version columns. No controller has a
transaction boundary, and P7 opens no remote provider, Python, or AI call while
a database transaction is held.

## 21. Explicit P7 non-goals

P7 excludes all of the following:

- recipe CRUD and recipe nutrition snapshots;
- pantry inventory workflows;
- ingredient substitution algorithms or workflows;
- recommendation or ranking;
- Python AI service work;
- meal-plan generation or optimization;
- Flutter UI;
- notifications;
- Elasticsearch;
- vector databases;
- LLM ingredient matching.

P7 may define contracts that these later phases consume, but it does not
implement their workflows.

## 22. Implementation slices

The intended order is:

1. **P7.0 - architecture/API contract** — complete.
2. **P7.1 - Food persistence foundation** — complete.
3. **P7.2 - Food nutrients and servings** — complete.
4. **P7.3 - Food query/search REST API** — implemented; final verification pending.
5. **P7.4 - Ingredient persistence foundation** — implemented; final verification pending.
6. **P7.5 - Aliases and ingredient-Food mappings** — implemented; final verification pending.
7. **P7.6 - Allergens and ingredient-specific unit conversions** — implemented; final verification pending.
8. **P7.7 - Ingredient query/search REST API** — implemented; final verification pending.
9. **P7.8 - User disliked/avoided ingredients** — implemented; final verification pending.
10. **P7.9 - integration coverage, documentation, and full verification** — in progress.

## 23. Testing strategy

P7 retains focused unit tests and MySQL 8.4 Testcontainers coverage for:

- pure unit tests for catalog validation, stable mapping, alias resolution, and
  refusal of unknown ingredient-specific conversions;
- MySQL 8.4 Testcontainers integration tests against the authoritative V001 and
  seed resources for JPA mappings, nutrition/serving aggregates, retirement,
  optimistic concurrency, and atomic rollback;
- authenticated MockMvc coverage for public UUID-only contracts, pagination,
  FULLTEXT/alias paths, active-vs-retired visibility, and /me cross-user
  isolation;
- response assertions that no BIGINT identifiers, JPA entities, source secrets,
  or unsafe exception details appear.

No H2 replacement, mock-only persistence test, or AI search test substitutes for
the MySQL catalog coverage.

## 24. Definition of Done

P7 is complete only when:

- the V001 Food/Ingredient schema is mapped without rewriting the applied
  migration;
- Food nutrition remains normalized and serving/unit facts remain honest;
- Ingredient != Food is preserved in persistence, application contracts, and API
  DTOs;
- aliases, mappings, allergen facts, ingredient conversions, and user avoidance
  semantics are implemented in their assigned slices;
- public APIs use UUIDs/reference codes only and have deterministic pagination;
- catalog curation is limited to ROLE_ADMIN while private user avoidance state
  remains owner-scoped;
- active/retirement, provenance, revision, and optimistic-concurrency rules are
  verified;
- MySQL integration, web/security, and cross-user isolation tests pass;
- no Python/AI/search-engine/recipe/pantry/meal-plan scope has leaked into P7;
- documentation, git diff --check, security checks, and the authoritative full
  Maven verification pass before review.
