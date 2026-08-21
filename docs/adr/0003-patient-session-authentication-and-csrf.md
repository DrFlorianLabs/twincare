# ADR 0003 — Patient session authentication and CSRF boundary

Status: **accepted for V0.1**

## Context

After exchanging a one-time invitation, a patient must be able to resume one
consultation preparation without creating an account. The existing session
cookie was deliberately unusable until its authentication, authorization and
CSRF boundary could be implemented independently from clinical collection.

This slice serves the patient between invitation exchange and the first useful
contribution. It should reduce failed restarts without exposing a patient,
organization or case identifier and without giving historical patient JWTs a
second route into the new preparation module.

## Decision

### Dedicated patient security chain

`/api/v1/patient-access/**` is handled by a first-priority Spring Security
chain. It does not install the historical JWT filter. A staff, administrator or
legacy patient JWT therefore cannot authenticate this namespace.

Only these routes exist:

- `POST /exchange`, anonymous and exempt from CSRF because it has no ambient
  cookie authority;
- `GET /session`, authenticated by the opaque patient cookie;
- `DELETE /session`, authenticated and protected by CSRF plus strict origin;
- every other path or method is denied.

The general JWT chain remains separate and continues to protect staff and
historical prototype routes.

### Server-side session resolution

The browser supplies only the `tc_preparation_session` cookie. It never
supplies an organization, case, role or scope. The application computes the
session HMAC and uses a scalar persistence projection to resolve exactly one:

`session -> consumed invitation -> organization -> PreparationCase`

Resolution fails closed when the cookie is absent, duplicated, malformed,
unknown, expired at or before the current instant, revoked, has another scope,
comes from an invitation not in `CONSUMED`, or points to a case outside a
patient-contribution state. All such failures return the same `401`, clear the
cookie and expose no identifier. Malformed tokens still perform a dummy
HMAC/lookup.

The authenticated principal contains only the server-derived technical grant
and one authority, `PREPARATION_CASE_CONTRIBUTE`. Its string representation is
redacted.

### Session-bound CSRF

`GET /session` returns a 256-bit HMAC in the `X-TC-CSRF` response header. The
HMAC is domain-separated and bound to the session identifier, organization,
case, and session expiry. It is derived from the existing dedicated patient
token pepper, not the JWT secret. It is neither persisted nor placed in a URL,
cookie or business response body.

The patient frontend keeps the value in memory and sends it back in
`X-TC-CSRF` on every unsafe request. Spring Security performs the CSRF
comparison. Unsafe requests must also carry an exact `Origin` from the
dedicated `PATIENT_CORS_ALLOWED_ORIGINS` allowlist. `SameSite=Strict` remains a
third, independent browser defense.

The patient and professional CORS allowlists are separate. Deployments must
use a same-site patient frontend/API topology for the strict session cookie and
must not add professional origins to the patient allowlist.

### Logout and future writes

`DELETE /session` is the first cookie-authenticated mutation. It persists
revocation, clears the cookie and is idempotent if a concurrent professional
revocation already won.

For any future clinical mutation, the filter grant is not sufficient on its
own. The use case must revalidate inside its transaction with this lock order:

1. scalar session scope lookup;
2. `PreparationCase` lock;
3. source invitation lock;
4. patient session lock;
5. full state/scope/expiry/revocation validation;
6. domain mutation.

This preserves the `case -> invitation -> session` order already used by the
invitation lifecycle and prevents a concurrent revocation from being bypassed.

## Consequences

- No new database migration, dependency or external provider is required.
- A page reload obtains a fresh deterministic CSRF header through `GET
  /session`; the token is never written to persistent browser storage.
- CSRF and invalid-session errors use dedicated `no-store` security handlers
  because filter errors do not pass through controller advice.
- TC-004 may now add the patient reason, but only through a transaction that
  repeats the authorization checks above and never accepts case scope from the
  client.
- This control does not authorize real patient data. The pilot gates in the
  security baseline and roadmap still apply.
