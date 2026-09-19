# P12 Minimal Admin Backend

P12 adds the smallest administrator workflow required by the demo. Existing
Spring Security protects `/api/v1/admin/**` with `ROLE_ADMIN`; this phase does
not create a second role system.

## User administration

Administrators can page/search users, view a public-safe user projection, and
move an account only between `ACTIVE` and `SUSPENDED`. `PENDING_VERIFICATION`
and `DEACTIVATED` are not editable by this workflow. An administrator cannot
suspend the account represented by the current JWT subject. Password hashes,
internal ids, sessions, and tokens are never returned.

## Recipe administration

The admin Recipe API can list and view all Recipe lifecycle states, create a
`CURATED` draft, replace a draft definition, publish a draft, and archive a
published Recipe. It does not physically delete recipes. Draft replacement
validates the complete aggregate and replaces its ingredient, step, tag, and
meal-slot children in one transaction. Ingredient and unit identities are
resolved through the existing Food and Nutrition read boundaries; no catalog
row is created by P12.

Publishing calls the existing Java `RecipeNutritionComputationService` before
the lifecycle transition. A nutrition failure therefore rolls back publishing.
Publish and archive timestamps are truncated to microseconds to match the
schema's `DATETIME(6)` columns. Archived recipes remain available for history,
while the public catalog continues to expose only published recipes.

Food and Ingredient administration is intentionally not duplicated under
`/admin`; their existing authenticated read APIs remain the catalog view.
P12 does not implement role grant/revoke, analytics, nutrition overrides, or
user deletion.

## Local development admin bootstrap

The reference seed creates the `ROLE_ADMIN` role but deliberately does not create an
administrator account. For local development only, after registering and
verifying an account, grant the role with a parameterized/local SQL client
(replace the placeholder without committing an email or credential):

```sql
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'ROLE_ADMIN'
WHERE u.email = '<LOCAL_ACCOUNT_EMAIL>';
```

Run this only against a local development database. There is no production
HTTP endpoint for creating the first administrator, and no password belongs in
this documentation.
