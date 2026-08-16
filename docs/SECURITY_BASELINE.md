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

## 6. Logs

Application logs must not contain health data or directly identifying patient content.

Do not log:

- names;
- email addresses;
- patient free text;
- original document filenames;
- document contents;
- clinical summaries;
- scores linked to patient identity;
- JWTs or secrets.

Prefer event names, technical correlation IDs and aggregate timing/status metadata.

Auditability is required, but audit events should be designed separately with access control and minimised payloads.

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

The current prototype does not yet establish a complete organisation/tenant authorization model. This is a **release blocker before multi-structure commercial deployment**.

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
