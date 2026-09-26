# Pantry / Fridge Backend (P10)

P10 adds the authenticated, lot-level pantry backend on top of the existing
`pantry_items` and `pantry_item_events` tables. It does not add a migration or
change the V001 schema.

## Ownership and identity

Pantry is a user-owned module. Every read and mutation resolves the user from
the authenticated JWT and queries by `(public_id, user_id)` or by the owner
internal id. A request for another user's lot therefore has the same not-found
semantics as an unknown lot.

Ingredient remains the canonical culinary identity and Food remains an optional
nutrition/product representation. Pantry requests use their public UUIDs. An
optional Food is accepted only when the Food module confirms that it is mapped
to the selected Ingredient. Measurement units are resolved by their canonical
code through the Nutrition-owned reference boundary; Pantry does not hardcode
unit surrogate ids or guess conversions. The seeded canonical examples accepted
by P10 are `g`, `kg`, `ml`, and `piece`; the stored row retains the resolved
unit reference and responses serialize the canonical code and display name.
Ingredient is mandatory, while a lot with no Food mapping is valid.

## Lot model and quantity ledger

Each `PantryItem` is one physical lot. `quantityInitial` records the acquired
amount and `quantityRemaining` records the current amount. Both use
`BigDecimal` and the V001 `DECIMAL(12,4)` bounds. A lot is never physically
deleted.

Creating a lot and writing its `ADDED` event is one transaction. Adjust,
consume, and discard update the item and append the corresponding event in the
same transaction. Consume uses a negative delta and closes the lot only when
the remaining quantity reaches zero. Discard writes a negative delta for the
whole remaining quantity and closes the lot as `DISCARDED`. `ADDED` is
positive, `ADJUSTED` retains the requested signed delta, and every event stores
the resulting `quantityAfter`. A failed mutation rolls back both the lot change
and its event.

The event table is append-only from the Pantry application API. P10 leaves
`mealPlanEntryId` null because meal-plan-linked consumption is a later phase.
The item uses JPA optimistic locking through `pantry_items.version`; a
concurrent write is reported through the existing conflict handling rather
than silently overwriting another change. P10 does not currently accept a
client version or HTTP precondition on mutation routes: actual concurrent stale
writes fail through `@Version` and are rendered as the standard HTTP `409`
`CONFLICT` response. Application-generated close and ledger timestamps are
normalized to the schema's `DATETIME(6)` microsecond precision.

## Expiry honesty and ordering

Expiry is explicit. A null `expiryDate` is normalized to `UNKNOWN` kind and
`UNKNOWN` confidence. A dated expiry must carry a known kind and confidence,
and cannot precede `acquiredOn`. P10 never derives a date from shelf-life
metadata, Food data, storage location, recipe data, or an external assumption.

Open-list ordering explicitly puts known expiry dates first, earliest known
date first, and unknown expiry after known dates. This is intentional: MySQL
sorts NULL first for an ascending order unless the query adds an explicit NULL
ordering expression. `includeClosed=true` includes retained consumed,
discarded, and expired history; the default list contains only `AVAILABLE` and
`RESERVED` lots.

## API

All routes are authenticated and owner-scoped:

```text
GET  /api/v1/me/pantry
GET  /api/v1/me/pantry/{publicId}
POST /api/v1/me/pantry
PUT  /api/v1/me/pantry/{publicId}
POST /api/v1/me/pantry/{publicId}/adjust
POST /api/v1/me/pantry/{publicId}/consume
POST /api/v1/me/pantry/{publicId}/discard
```

The PUT operation changes metadata only. Quantity changes are deliberately
represented by event-producing operations. Any JSON presence of `quantity` on
PUT, including `"quantity": null`, is explicitly rejected rather than silently
treated as a metadata field. There is no physical-delete endpoint.

Responses expose public UUIDs, canonical codes, display metadata, quantities,
expiry information, lifecycle status, version, and timestamps. Internal user,
ingredient, Food, unit, item, and event ids are never serialized.

## P11 availability boundary

`PantryAvailabilityQueryService` is the Pantry-owned read boundary for future
recommendation and meal-planning code. It resolves the authenticated user once,
loads `AVAILABLE` lots in one bounded query, and batch-resolves Ingredient,
Food, and unit metadata. `RESERVED` stock is intentionally excluded from
immediately usable availability. Future modules consume
`PantryAvailabilitySnapshot` rather than Pantry entities or repositories. The
query is read-only: it creates no ledger events and returns each lot in its
stored canonical unit without aggregation or speculative conversion between
mass, volume, and count.

P10 does not reserve stock, expire rows automatically, generate notifications,
or implement meal-plan matching.
