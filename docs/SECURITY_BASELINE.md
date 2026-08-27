# TwinCare — Security Baseline

Status: **mandatory before real health-data pilot**

This document defines minimum engineering controls. It is not a legal certification and does not replace a formal security/risk/compliance assessment.

## 1. Data minimisation

Collect only information useful to preparation of the intended consultation or an explicitly configured pathway.

Do not collect information merely because it may become useful later.

## 2. Source control

Never commit:

- patient documents;
- exports of medical records;
- screenshots containing identifiable health data;
- database dumps containing personal/health data;
- secrets, tokens or production credentials;
- application logs containing patient content.

Runtime upload directories and local data directories must be ignored by Git.

If a committed file may contain real health data, treat removal from the current branch as insufficient: assess whether Git history and remote copies must be purged.

## 3. Authentication

- No default JWT secret.
- Startup must fail when the required secret is absent or cryptographically inadequate.
- Login is the only anonymous authentication endpoint in the current prototype baseline.
- Patient/staff account provisioning is an explicit privileged operation.
- Password recovery must not permit unauthenticated reset based only on knowledge of an email address.
- Access tokens are never logged.

## 4. Authorization

Authorization must be checked at both route and service/domain boundaries for sensitive operations.

For a patient:

- access is limited to that patient's own preparation/dossier context and documents.

For staff:

- access must be limited by role and, before real deployment, by organisation/tenant membership.
- clinical access is a separate capability from tenant administration;
- clinicians and active medical assistants may view preparation data;
- organisation administrators have no clinical view by default;
- attachment download, task management and readiness confirmation require
  dedicated capabilities, with readiness confirmation reserved for clinicians.
- task assignees and actors must be resolved against active clinical membership
  in the same organisation; client-supplied actor identities are ignored.
- ready confirmation must bind to the case revision actually reviewed and fail
  on any intervening reason, task or attachment change.
- ready evidence must be revalidated on every read, and multi-query clinical
  views must use one consistent database snapshot.
- professional preparation responses, including errors, must be `no-store`.
- professional organization and capability discovery is derived exclusively
  from the authenticated server identity and active memberships; client-side
  context remains advisory and never replaces use-case authorization.
- preparation audit access requires a dedicated clinician-only capability;
  assistants and administrative roles receive no audit access by default.
- explicit preparation closure requires a dedicated clinician-only capability,
  the exact ready-case revision and consistent readiness evidence;
- closure revokes every remaining invitation and patient session in the same
  transaction and stores only minimized immutable closure metadata.
- professional case creation and invitation issuance require distinct
  server-derived capabilities for an active clinician or medical assistant;
  the client never supplies the actor, role or organization membership.
- case creation is idempotent within one organization through a required UUID
  request identifier and a server-persisted canonical payload fingerprint;
  exact replay creates neither a second case nor a second audit event, while a
  different payload fails closed.

An object identifier, storage filename or UUID is **not** an authorization mechanism.

## 5. Documents

- No public `/files/{filename}` access.
- Retrieve content through an authenticated document endpoint.
- Verify ownership/role before resolving bytes from storage.
- Validate allowed content type and file signature, not only filename extension.
- Generate an opaque storage key server-side.
- Preserve the original filename only as display metadata after sanitisation.
- Block path traversal and symlink escape.
- Delete stored bytes when document metadata is deleted, with a recoverable cleanup strategy as persistence evolves.
- Keep metadata and bytes consistent across transaction failure: quarantine
  deletions until database commit, restore them on rollback, remove new uploads
  on rollback, and reconcile interrupted quarantines at startup.
- Keep multipart parser temporary files in a dedicated private runtime
  directory, outside Git and historical upload roots; purge abandoned regular
  files without following symlinks.
- Fail application startup when attachment metadata references missing bytes or
  storage reconciliation cannot complete. A missing byte object must not block
  deletion or replacement of its stale metadata.
- For professional download, authorize the active organization membership
  before resolving metadata or bytes; lock `case -> attachment`, then open the
  final object once with symlink following disabled.
- Verify actual size and SHA-256 on the pinned handle before committing response
  headers. Stream that same handle only after the database transaction ends;
  never lazily reopen a storage path after releasing the locks.
- Force `Content-Disposition: attachment`, allowlisted `Content-Type`, exact
  `Content-Length`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`
  and `Pragma: no-cache`. Do not expose range, inline, ETag or public URL support
  in V0.1.

## 6. Logs

Application logs must not contain health data or directly identifying patient content.

Do not log:

- names;
- email addresses;
- patient free text;
- original document filenames;
- document contents;
- clinical summaries;
- preparation task labels;
- scores linked to patient identity;
- JWTs or secrets.

Prefer event names, technical correlation IDs and aggregate timing/status metadata.

Preparation audit events are stored separately from logs with a closed type,
schema version, server-derived actor category, server timestamp, tenant/case
scope, persisted case revision and optional technical subject identifier.
They must never contain patient references, clinical text, task labels,
filenames, storage keys, digests, credentials, IP addresses, User-Agent values
or arbitrary JSON. A consequential mutation and its audit append share one
transaction; audit failure rolls the mutation back. Idempotent retries and
no-ops do not create duplicate evidence.

Audit reads are tenant-scoped, bounded, cursor-paginated and `no-store`.
Pre-audit cases are explicitly marked as historically incomplete; no synthetic
backfill may imply a chronology that cannot be proven. There is no audit
deletion endpoint until retention, deletion, backup, restore and legal-hold
rules have been formally approved.

Closure evidence has no update or deletion endpoint. It contains only technical
tenant/case scope, server-derived clinician identity, server time and the
ready/closed revision chain. A failed closure audit append rolls back the case
transition and every credential revocation.

## 7. AI / model boundary

- Raw health data is not sent to an external model implicitly.
- External model use, if introduced, requires an explicit deployment/configuration choice and appropriate compliance analysis.
- A local/no-op implementation must exist for every AI port needed by core workflows.
- Model output is untrusted input: validate structure, provenance and allowed values.
- Models cannot directly perform irreversible or consequential domain transitions.

## 8. External integrations

No vendor API is a single point of failure for TwinCare core.

Adapters must implement stable internal interfaces. Credentials live in environment/secret management, never source code. Failure of an integration must degrade to a safe local/manual workflow when possible.

## 9. Multi-tenancy

The active preparation endpoints scope membership and case queries by
organisation. The historical prototype does not yet establish this boundary
consistently, so complete tenant isolation remains a **release blocker before
multi-structure commercial deployment**.

Future access checks must include at minimum:

`authenticated actor -> organisation membership -> role -> patient/preparation relationship -> requested operation`

## 10. Security testing gates

Before a real-health-data pilot, automated tests must cover at minimum:

- anonymous document access rejected;
- patient A cannot access patient B dossier/document;
- patient cannot enumerate all dossiers/users;
- privileged endpoints reject patient role;
- file traversal attempts rejected;
- unsupported/mismatched file signatures rejected;
- missing/weak JWT secret prevents startup;
- unknown clinical trigger fails closed;
- logs do not include known test PHI markers in critical flows.
- audit access rejects assistants, administrators, inactive memberships and
  cross-organization case identifiers;
- a failed audit append rolls back the corresponding business mutation;
- audit pagination is bounded and legacy cases disclose incomplete coverage.

## 11. Deployment

Production-like use requires an environment appropriate for French health data, with encryption, backups, recovery procedures, secrets management, monitoring, audit controls and the applicable hosting/compliance requirements assessed explicitly.

The current repository must not imply that a generic demo deployment is sufficient for real health data.

Interactive API documentation is disabled by default. If it is explicitly
enabled for a controlled environment, the HTTP security boundary restricts it
to authenticated administrators.

Before connecting an existing database, inspect its `flyway_schema_history` and
schema state. Prototype migration V3 is destructive and must never be replayed
over a populated legacy schema. Existing schemas without Flyway history must be
baselined at version 4 only after their structure and backups have been verified.
Startup also fails closed if a schema-history version older than V3 coexists
with legacy application tables; that state requires an explicit, backed-up
migration decision rather than an automatic destructive replay.

## 12. Professional browser client

The current legacy JWT login cannot yet issue an `HttpOnly` professional
session cookie. The V0.1 professional client therefore keeps the JWT in
`sessionStorage`, never `localStorage`, and removes historical prototype keys
on login/logout. This limits persistence but does not remove the impact of an
XSS; strict content-security policy and a server-managed cookie session remain
deployment work before real patient use.

Clinical API responses are rendered only through Vue text interpolation. The
client contains no `v-html`, analytics, service worker or clinical-data cache.
Patient reason, task labels, attachment names and read models stay in memory
only and are discarded on logout, authorization loss or page close. Unknown
readiness/blocker values fail closed and never enable a command.

The manual creation client generates an unpredictable UUID and captures the
exact normalized payload for one unchanged draft. It retains both only in memory
for an explicit retry after an ambiguous failure. It never retries creation or
invitation automatically. An ambiguous
creation cannot be silently closed and restarted with a new request identifier;
the user must retry the unchanged request or explicitly abandon it after
successfully refreshing the list. A failed or stale refresh preserves the
request identifier. The configured patient-application URL must be absolute
HTTPS without credentials, query or fragment; HTTP is restricted to exact
local loopback development hosts. The invitation secret is added only as an
exact URL fragment, remains in volatile memory, is copied only on explicit
action and is cleared when its dialog, case, organization or professional
session is invalidated. It must not enter the professional route, browser
storage, logs, analytics or an automatic notification request. TwinCare cannot
erase a user-requested operating-system clipboard copy and labels the copied
link as confidential.

## 13. Patient browser client

The V0.1 patient entry point accepts invitation credentials only from the URL
fragment. It removes every fragment before the first API request and rejects a
credential supplied in the query string, an ambiguous fragment or an invalid
base64url shape. The patient page sets a `no-referrer` policy and contains no
analytics, pixels, third-party scripts or service worker.

The one-time invitation secret, session CSRF value and patient wording remain
only in volatile JavaScript memory. They must never be written to
`localStorage`, `sessionStorage`, browser caches, URLs or logs. Continuing access
uses the server-issued `HttpOnly`, `Secure`, `SameSite=Strict` cookie scoped to
the patient namespace. CSRF is refreshed from the authenticated session read
after reload and sent only on explicit unsafe requests.

The active patient application calls only `/api/v1/patient-access/**`; it does
not use the historical account, chatbot or dossier endpoints. Patient reason
resume revalidates the server-owned organization/case/session/invitation scope
inside the application and persistence boundaries and returns no technical
identifier. Every patient response and generic failure is `no-store`,
`no-referrer` and `nosniff`.

This browser hardening does not authorize real patient data. A strict deployed
content-security policy, end-to-end deployment review and all remaining pilot
gates still apply.
