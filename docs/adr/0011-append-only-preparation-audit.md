# ADR 0011 — Append-only preparation audit

Status: accepted for TC-007a

## Context

TwinCare must be able to establish the workflow chronology of one consultation
preparation without turning ordinary application logs into a medical record.
The transient `PreparationCaseEvent` values are insufficient: they are not
persisted, do not cover invitations, patient sessions, attachments or human
tasks, and do not carry a reliably derived actor.

Existing rows predate durable audit. Their complete chronology and actors
cannot be reconstructed honestly from the current database.

## Decision

V10 introduces `preparation_audit_event`, an append-only, tenant-scoped event
store. It is an audit trail, not an event-sourcing mechanism: current aggregate
tables remain the source of workflow state.

Each event contains only:

- an opaque event identifier;
- organization and preparation-case ownership;
- a monotone sequence within that case;
- a closed event type and schema version;
- `STAFF`, `PATIENT` or `SYSTEM` actor kind;
- the server-derived user identifier only for a staff actor;
- a server timestamp and the persisted case revision;
- an optional typed technical subject identifier for an invitation, attachment
  or preparation task.

It never contains a patient reference, free text, task label, original
filename, MIME type, document content, storage key, digest, credential, IP
address, User-Agent or arbitrary JSON payload.

Consequential mutations append their event directly inside the same database
transaction. Audit persistence failure rolls the mutation back. There is no
asynchronous listener, best-effort `afterCommit` hook, aspect or external audit
provider. No event is appended for an idempotent replay or no-op.

Every existing mutation follows the shared lock order beginning with the
owning case. `MAX(sequence) + 1` is therefore calculated only while the case
pessimistic lock is held; creation is the sole exception because the new case
is not yet visible to concurrent transactions. Database uniqueness on
`(organization_id, preparation_case_id, event_sequence)` is the final guard.

The case repository returns the revision actually persisted after an update.
Audit writers must use that value rather than assume the in-memory revision has
already observed the JPA version increment.

`ATTACHMENT_DOWNLOAD_AUTHORIZED_AND_VERIFIED` means authorization, tenant
resolution and storage integrity verification completed. It deliberately does
not claim that the client received every byte because streaming occurs after
the database transaction and can be interrupted by the client.

`PATIENT_ACCESS_ENDED` records an explicit patient logout. A professional or
system revocation that actually invalidates an active patient session records
the distinct `PATIENT_ACCESS_REVOKED` event in addition to the invitation
event. The repository reports whether session state changed so retries neither
rewrite the revocation timestamp nor append duplicate evidence. No session or
credential identifier is stored in either event.

## Protected read access

The API is:

```text
GET /api/v1/organizations/{organizationId}/preparation-cases/{caseId}/audit-events
    ?beforeSequence={exclusive cursor}
    &limit={1..50, default 25}
```

It is cursor-paginated in descending sequence order and has no organization-wide
search, export or actor filter. `VIEW_PREPARATION_AUDIT` is granted only to a
platform physician with an active `CLINICIAN` membership. Medical assistants,
organization administrators, platform administrators, patients and anonymous
requests fail closed. Authorization occurs before the tenant-scoped case and
event lookups. Success and errors are `no-store`.

The response reports `completeFromCaseCreation=false` when a case has no
durable `PREPARATION_CREATED` event. No synthetic backfill is generated for
pre-V10 history.

## Retention and deletion

There is no update, delete or purge port or endpoint in TC-007a. Audit metadata
remains linked to its case until an approved retention, deletion, backup,
restore and legal-hold policy exists. Audit is health-related metadata and
requires the same deployment protection as the preparation data.

## Consequences

- New cases have an honest, bounded chronology from creation.
- Existing cases explicitly disclose partial historical coverage.
- Core behavior remains provider-independent and uses no new dependency.
- Explicit closure and its immutable proof remain TC-007b.
- A real PostgreSQL concurrency proof and the approved retention policy remain
  release gates before real patient use.
