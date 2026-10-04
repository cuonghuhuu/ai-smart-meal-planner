# Pantry / Fridge Flutter integration (P14-J2/J3/J4)

P14-J2 adds the typed Pantry data contract, exact quantity validation, and an
HTTP repository in `mobile_app/lib/features/pantry/`. P14-J3 adds a controller
and list/detail screens. P14-J4 adds lot creation, ingredient and mapped-food
selection, and metadata-only editing. A Pantry item represents one physical
lot; the client neither aggregates lots nor assigns an owner ID. The backend
derives ownership from the authenticated access token.

## Backend API used by the repository

| Operation | HTTP route | Result |
| --- | --- | --- |
| List open lots | `GET /api/v1/me/pantry` | Item array |
| Include closed lots | `GET /api/v1/me/pantry?includeClosed=true` | Item array |
| Read lot | `GET /api/v1/me/pantry/{publicId}` | Item |
| Create lot | `POST /api/v1/me/pantry` | Item, HTTP 200 |
| Replace metadata | `PUT /api/v1/me/pantry/{publicId}` | Item |
| Correct quantity | `POST /api/v1/me/pantry/{publicId}/adjust` | Item |
| Consume quantity | `POST /api/v1/me/pantry/{publicId}/consume` | Item |
| Discard remaining | `POST /api/v1/me/pantry/{publicId}/discard` | Item |

There is no delete operation, list pagination, or server-side list filter beyond
`includeClosed`. The repository preserves `ApiClient` HTTP/problem and transport
exceptions. A malformed response raises `ApiResponseFormatException`.
The Flutter UI currently uses list, detail, create, and metadata replacement;
adjust, consume, and discard remain data-contract operations without UI flows.

## Models and requests

`PantryItem` parses public item, ingredient, and optional Food UUIDs; catalog
codes and names; initial and remaining quantity; unit code and display name;
storage location; acquisition and expiry dates; expiry kind and confidence;
status; closure timestamp; note; version; and creation/update timestamps.
Storage, status, kind, and confidence are typed enums with exact backend wire
values. No data-layer localization or derived lifecycle status is applied.

Creation sends the ingredient UUID, optional mapped Food UUID, positive
quantity, unit code, storage location, optional dates, kind, confidence, and
note. Unknown expiry is the request default. Each successful create is a new
lot, even when an ingredient already has another lot.

Metadata PUT sends only `storageLocation`, `acquiredOn`, `expiryDate`,
`expiryKind`, `expiryConfidence`, and `note`. It includes null dates and note so
the caller can deliberately clear them. The complete intended metadata state
must be supplied. Quantity, ingredient, Food, unit, status, and version are
never serialized in this request. The backend rejects any `quantity` property,
including JSON null.

Adjust sends a nonzero signed `quantityDelta` and optional event note. It is
valid for AVAILABLE lots only and must leave remaining quantity greater than
zero and no higher than the initial quantity. Consume sends a positive
`quantity` no greater than remaining; consuming all remaining stock closes
the lot as CONSUMED. Discard closes all remaining stock on AVAILABLE or
RESERVED lots and accepts an optional note body. It has no partial-discard
quantity. Action notes are event notes, distinct from metadata note.

## Exact decimal handling

`PantryDecimal` parses plain decimal text at four fractional places into a
signed `BigInt` count of ten-thousandths. It rejects malformed text, scientific
notation, more than four fractional digits, and magnitudes exceeding
99,999,999.9999. Zero is representable for closed-lot responses but is
rejected by positive request checks; adjustment rejects zero delta. Comparison,
adjustment result checks, and consumption limits use scaled integers. JSON
request values are emitted as JSON numbers from normalized decimal text; no
binary floating-point arithmetic determines business validity. Response JSON
numbers are converted to the same value type.

## Dates, expiry, notes, and units

Acquisition and expiry use `YYYY-MM-DD` calendar fields. Parsing and formatting
do not shift dates through UTC. Backend `LocalDateTime` timestamps use the
project's `DateTime.tryParse` convention without assigning a missing offset.

An absent expiry date requires UNKNOWN kind and UNKNOWN confidence. A present
date requires known kind and confidence and cannot precede acquisition when
both dates exist. Past expiry and future acquisition dates remain valid.
The client does not infer EXPIRED status from a date.

Notes are trimmed; blank text becomes null; normalized text is limited to 255
UTF-16 code units, matching Java `String.length()`. Unit codes are trimmed on
outbound requests and must be nonblank. Any nonblank backend unit code is
accepted; server-returned unit code and display name remain unchanged. The
create form prefers the selected ingredient's default unit, offers `g`, `kg`,
`ml`, and `piece` as common suggestions, and accepts free-form nonblank unit
codes. These suggestions are presentation policy, not the backend vocabulary.

The create form searches ingredients through the existing `CatalogRepository`
and exposes only the selected ingredient's mapped Foods from its detail data.
Food is optional. Selecting another ingredient clears the previous selection.
The lot quantity uses `PantryDecimal` for local four-place, upper-bound, and
positive-value checks and is serialized as an exact JSON number from normalized
decimal text. The form permits past expiry dates and future acquisition dates;
clearing expiry resets expiry kind and confidence to UNKNOWN. Metadata editing
exposes only storage location, acquisition/expiry dates and classification,
and note. Its PUT always sends the full intended editable state, including nulls
for fields the user clears, and never sends quantity, ingredient, food, unit,
status, or version.

## Authentication and CSRF

Every repository call uses shared `ApiClient` with `authenticated: true`.
`ApiClient` and `SessionController` own Bearer access tokens and refresh. Read
operations do not call the CSRF callback. Every mutation calls the optional
CSRF-token provider and sends its returned header when configured, matching
`HttpMealPlanningRepository`. App composition passes the web CSRF callback to
Pantry and meal planning; on native platforms the callback is null.

Pantry mutations require an authenticated Bearer access token. Spring OAuth2
Resource Server exempts Bearer-token requests from CSRF, including requests
that also carry the browser refresh cookie. This exemption does not grant
authentication: invalid or missing access tokens cannot authorize Pantry
operations. Flutter Web may still send a CSRF header on Pantry mutations; it
is redundant for Bearer-authenticated requests but harmless. Pantry
authorization does not rely on the refresh cookie. Browser refresh and logout
operations that use the cookie remain CSRF-protected separately. Ordinary
non-Bearer unsafe requests also remain subject to CSRF protection.

## App composition, routes, and responsive UI (P14-J3/J4)

`SmartMealPlannerApp` owns `PantryController` in production, resets it on
logout or authenticated principal change, and disposes it with the app. The
controller has independent list, detail, and mutation state. Each new read
increments its generation; reset and disposal invalidate list, detail, and
mutation generations. A late response from another account, an older
include-closed mode, an older selected detail, or a previous session mutation
cannot restore stale data. Successful create/update responses are authoritative:
the controller reconciles the corresponding row into the current list snapshot
and invalidates in-flight list reads. Create adds a distinct lot and does not
aggregate by ingredient. List refresh/retry re-reads the selected mode; detail
retry re-reads the selected public ID. No local persistence is used.

The protected `/pantry`, `/pantry/new`, `/pantry/:publicId`, and
`/pantry/:publicId/edit` routes use `SessionRouteGate`. Safe post-login
destinations include the list, create route, and UUID-valid detail/edit paths;
arbitrary external redirects remain excluded. Pantry is one destination in the
authenticated drawer and navigation rail, selected on nested Pantry routes.
The responsive list and detail pages show loading, empty/error/retry states and
retain each backend lot as a separate row. Quantity display uses exact
`PantryDecimal` text; expiry dates are calendar dates and do not change backend
lifecycle status. User-facing copy is centralized in `AppStrings`, with backend
code labels in `PantryLocalizations`.

## Deferred work

Quantity adjust, consume, discard, shopping-list invalidation, Pantry
ledger/history, and expiry filtering/polish remain deferred. Final
accessibility and release regression work also remain.
