# Pantry / Fridge Flutter foundation and read-only integration (P14-J2/J3)

P14-J2 adds the typed Pantry data contract, exact quantity validation, and an
HTTP repository in `mobile_app/lib/features/pantry/`. P14-J3 adds a read-only
controller and list/detail screens. A Pantry item represents one physical lot; the client neither
aggregates lots nor assigns an owner ID. The backend derives ownership from the
authenticated access token.

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
data layer has no `g`/`kg`/`ml`/`piece` enum. A later UI may offer a small
common-unit subset without changing this contract.

## Authentication and CSRF

Every repository call uses shared `ApiClient` with `authenticated: true`.
`ApiClient` and `SessionController` own Bearer access tokens and refresh. Read
operations do not call the CSRF callback. Every mutation calls the optional
CSRF-token provider and sends its returned header when configured, matching
`HttpMealPlanningRepository`. App composition wires the repository through the
shared `ApiClient` and passes the same web CSRF callback as meal planning.
J3 invokes only GET operations. The current Spring
Security configuration does not exempt Pantry mutations from CSRF checks; the
Android Bearer-only mutation path needs an end-to-end check before the later
integration job selects its callback policy. This foundation does not modify
auth infrastructure.

## Read-only app integration (P14-J3)

`SmartMealPlannerApp` owns `PantryController` in production, resets it on
logout or authenticated principal change, and disposes it with the app. The
controller has independent list and detail state. Each new read increments its
generation; reset and disposal invalidate both generations. A late response
from another account, an older include-closed mode, or an older selected detail
cannot restore stale data. List refresh and retries re-read the selected mode;
detail retries re-read the selected public ID. No local persistence is used.

The protected `/pantry` and `/pantry/:publicId` routes use `SessionRouteGate`.
Safe post-login destinations include `/pantry` and UUID-valid detail paths;
arbitrary external redirects remain excluded. Pantry is one destination in the
authenticated drawer and navigation rail, selected on both routes. The
read-only pages use `ResponsiveContent`, show loading, empty/error/retry states,
and retain each backend lot as a separate row. Quantity display uses the exact
`PantryDecimal` text; expiry dates are displayed as calendar dates and do not
change backend lifecycle status. User labels are centralized in `AppStrings`
and `PantryLocalizations`.

## Deferred work

Create/edit UI and forms, ingredient and food pickers, adjust, consume,
discard, shopping-list invalidation, mutation end-to-end CSRF verification,
and expiry filtering/polish remain deferred. Additional responsive and
accessibility polish remains for later UI work. After successful
Pantry mutations, later integration should invalidate the meal-planning
shopping-list projection because its backend response depends on current
AVAILABLE stock.
