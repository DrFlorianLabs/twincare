# TwinCare backend

Spring Boot / Java 21 backend for the historical prototype capabilities retained
during Sprint 0 hardening. It is not yet authorized for real patient data.

## Required configuration

Set these values outside source control:

- `JDBC_DATABASE_URL`, `JDBC_DATABASE_USERNAME`, `JDBC_DATABASE_PASSWORD`;
- `JWT_SECRET` with at least 32 bytes of secret material;
- `PATIENT_INVITATION_TOKEN_PEPPER` with at least 32 bytes of secret material,
  distinct from the JWT secret;
- `CORS_ALLOWED_ORIGINS` for the deployed frontends;
- `PATIENT_CORS_ALLOWED_ORIGINS` for patient frontends only; never include a
  professional application origin;
- `FILE_UPLOAD_DIR` to a private, persistent and backed-up storage location;
- `PREPARATION_ATTACHMENT_STORAGE_DIR` to a distinct private and persistent
  location for preparation-case attachments;
- `PREPARATION_MULTIPART_TEMP_DIR` to a distinct private temporary location on
  the same trusted host, outside Git and the historical upload root.

Optional settings include `JWT_EXPIRATION_MS`, `FILE_MAX_SIZE_BYTES`,
`PATIENT_INVITATION_TTL`, `PATIENT_INVITATION_TOKEN_KEY_ID`,
`PATIENT_INVITATION_EXCHANGE_RATE_LIMIT`, `PATIENT_SESSION_COOKIE_SECURE` and
`SPRINGDOC_ENABLED`. Patient session cookies are secure by default; disabling
that flag is limited to an explicit local HTTP setup. API documentation is
disabled by default and restricted to administrators when enabled.

Optional preparation-attachment settings are
`PREPARATION_ATTACHMENT_MAX_SIZE_BYTES` (hard ceiling 10 MiB) and
`PREPARATION_ATTACHMENT_ORPHAN_GRACE`. The configured persistent and temporary
directories must be writable only by the application identity. On POSIX
filesystems the application enforces owner-only directory/file permissions.

## Authentication and provisioning

Only `POST /api/auth/login` and the exact one-time invitation exchange endpoint
`POST /api/v1/patient-access/exchange` are anonymous. The patient namespace has
a dedicated cookie-authenticated security chain that never accepts historical
JWT roles.

- `POST /api/auth/register` provisions patient accounts and requires an
  authenticated assistant, physician or administrator. The endpoint cannot
  create staff or administrator roles.
- `POST /api/auth/reset-password` is an administrative operation.
- Document content is retrieved through authenticated
  `GET /api/documents/{documentId}/content`; storage keys are never public URLs.

Authorization is enforced again in services. Never infer access from a client
role, client-supplied actor identifier, document UUID or route identifier alone.

## Patient invitation security

An authorized organization member may issue a short-lived invitation for one
preparation case. The raw invitation secret is returned once, while only a
domain-separated HMAC-SHA-256 digest is stored. Anonymous exchange consumes the
invitation atomically, starts the case and creates a case-scoped session in an
`HttpOnly`, `Secure`, `SameSite=Strict` cookie.

The future patient frontend must receive the invitation secret in the browser
URL fragment, remove it immediately with `history.replaceState`, and send it in
the JSON exchange body. Query parameters and server URL paths are not accepted
as credentials. No analytics or third-party content belongs on that entry page.

Rotating `PATIENT_INVITATION_TOKEN_PEPPER` intentionally invalidates outstanding
invitations and sessions; affected invitations must be reissued. The in-process exchange limiter is a safe local baseline; a
multi-instance real-data deployment also requires an edge or shared limiter.

`GET /api/v1/patient-access/session` validates the opaque session and returns a
session-bound `X-TC-CSRF` header with no case, organization or patient
identifier. The patient frontend keeps it in memory. Every unsafe patient
request sends that header and an exact patient `Origin`. `DELETE
/api/v1/patient-access/session` provides a protected, persistent logout and
clears the session cookie. All other patient paths remain denied until their
transactional authorization is implemented and tested.

`PUT /api/v1/patient-access/reason` records the single patient-declared reason
for the scoped preparation case. It accepts only a JSON `reason`, returns no
clinical body, and repeats authorization after acquiring the case, invitation
and session locks. Identical retries are no-ops. No read, submission,
questionnaire or classification endpoint is included in this slice.

`PUT`, `GET` and `DELETE /api/v1/patient-access/attachment` respectively create
or replace one optional PDF/JPEG/PNG, return metadata only, and remove it
idempotently. Upload is limited to one in-flight request per patient session,
10 MiB of actual bytes, exact MIME/signature agreement and a sanitized filename.
The patient API never exposes bytes, scope identifiers, storage keys or digests.

## Professional preparation view

`GET /api/v1/organizations/{organizationId}/preparation-cases` returns a
bounded paginated worklist. It deliberately excludes the full patient reason.
`GET /api/v1/organizations/{organizationId}/preparation-cases/{caseId}/preparation-view`
returns the exact declared reason, safe attachment metadata and a deterministic
readiness assessment. Both responses are `no-store` and never expose storage
keys or digests.

Readiness currently uses the versioned `CORE_PREPARATION` V1 rules. An absent
optional attachment is not a blocker and no clinical meaning is inferred from
patient prose. Active clinicians and medical assistants may view these data;
organization administrators have no clinical access by default.

TC-006a cannot emit `READY` because it deliberately has no persisted clinician
confirmation. A stored ready status without confirmation evidence fails closed;
TC-006b adds that evidence and its authorized transition.

## Database safety

Flyway migration V3 is a destructive historical prototype migration and is kept
unchanged to preserve checksums. Before connecting an existing database, back it
up and inspect its schema and `flyway_schema_history`.

- A non-empty pre-Flyway legacy schema is baselined at version 4 by default.
- A legacy schema with Flyway history older than V3 fails startup instead of
  replaying the destructive migration automatically.
- A new empty database runs V3 then V4 normally.

Do not override this protection without an explicit, reviewed migration plan.

## Documents

Runtime documents must stay outside Git. Uploads are validated, stored under an
opaque server-generated key and tied to database transactions. Deletions use a
recoverable quarantine under `FILE_UPLOAD_DIR/.pending-deletions` and are
reconciled from metadata at startup after interruption.

The local filesystem implementation is suitable for development only unless the
deployment supplies durable private storage, encryption, backups and tested
recovery appropriate to its risk and compliance requirements.

Preparation attachments use their own storage root with `.staging` and
`.pending-deletions` directories. Multipart parsing spools to the separate
private temporary directory before validation. Startup purges abandoned
temporary/staging objects, reconciles promotion/deletion windows against
metadata and refuses to start if referenced bytes are missing or recovery
fails. Diagnose and restore the persistent storage before restarting; do not
delete metadata merely to force readiness without an authorized incident and
recovery decision.

## Tests

From `BackEnd/` with Java 21:

```sh
sh ./mvnw --batch-mode --no-transfer-progress verify
```

Tests use synthetic fixtures and H2 only. H2 is not a runtime dependency.
