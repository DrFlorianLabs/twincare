# ADR 0002 — Patient invitation credential exchange

Status: **accepted for V0.1**

## Context

A patient must enter one consultation-preparation episode without a mandatory
TwinCare account or external SMS/email provider. A link is convenient but its
secret must not become a reusable, broadly authorized patient credential or be
stored in server URLs, logs or the database.

This capability solves the gap immediately after a professional creates a
preparation case. It lets the patient begin in seconds, while keeping access
limited to that case. The measurable pilot outcomes are invitation-to-start
rate, time to first useful contribution and failed/revoked exchange counts.

## Decision

### Separate invitation and session

TwinCare creates a 256-bit random invitation secret for one `PreparationCase`.
The professional receives it once through a `no-store` response. Anonymous
exchange consumes it once and atomically:

1. verifies active state and server-controlled expiry;
2. moves the case from `CREATED` to `PATIENT_STARTED`;
3. marks the invitation `CONSUMED`;
4. creates a second, scoped and expiring patient access session.

The session is delivered in a path-limited `HttpOnly`, `Secure`,
`SameSite=Strict` cookie. It is not exposed in the response body. Revoking the
invitation also revokes a session already derived from it.

### Secret storage and rotation

Invitation and session secrets use `SecureRandom` and URL-safe Base64 without
padding. The database stores only HMAC-SHA-256 digests. Invitation and session
digests have separate calculation domains and use a dedicated pepper, never the
JWT secret. The pepper has no default and must contain at least 32 bytes.

Each record stores a non-secret key identifier. V0.1 deliberately treats pepper
rotation as mass revocation: outstanding invitations and sessions stop matching
and must be reissued. This is simpler and safer than silently retaining an
unbounded historical key ring. A future deployment may add a bounded key ring
behind the same digest port if operational evidence requires graceful rotation.

### Browser transport

The future patient frontend will transport the initial secret in a browser
fragment, for example:

```text
https://patient.example/preparation#token=<secret>
```

The fragment is not sent in the initial HTTP request. The frontend must remove
it immediately with `history.replaceState` and post it in the JSON exchange
body. The entry page must set `Referrer-Policy: no-referrer` and load no
analytics, pixels or third-party content. TwinCare does not accept the secret in
a server path or query parameter.

### Authorization, enumeration and concurrency

Issuance and revocation reload the professional identity and active organization
membership on the server. Organization and case identifiers supplied in the URL
remain untrusted selectors. The database enforces organization/case consistency
with composite foreign keys.

Every invalid, malformed, expired, consumed, revoked or unknown invitation gets
the same public status and body. Exchange has no case or organization identifier
and performs a keyed digest lookup. A pessimistic row lock and transaction ensure
that two concurrent exchanges produce exactly one session.

An in-process fixed-window limiter provides a provider-free local baseline and
stores no IP address. Before a multi-instance real-data deployment, an edge or
shared limiter is additionally required. Rate-limit failures use the same public
response as other exchange failures.

### Expiry

The client never chooses an expiry. It is the earliest of the configured server
TTL (maximum seven days) and the known consultation time. The exact boundary is
exclusive: an invitation is invalid when `now == expiresAt`.

## Consequences and follow-up gates

- No SMS, email, scheduling, cloud or model provider is required.
- An invitation is not patient identity proof, consent or a longitudinal portal.
- Session authentication, CSRF and logout are defined by
  [`0003-patient-session-authentication-and-csrf.md`](0003-patient-session-authentication-and-csrf.md).
- Every unimplemented `/api/v1/patient-access/**` route remains denied.
- Formal protected audit access remains TC-007. The invitation/session tables
  already retain minimized technical issuer and lifecycle timestamps without
  patient reference, raw token or clinical content.
