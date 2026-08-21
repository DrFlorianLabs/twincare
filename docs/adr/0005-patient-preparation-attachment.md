# ADR 0005 — Bounded patient preparation attachment

Status: **accepted for V0.1**

## Context

Before a consultation, the patient may already hold one useful result, report
or image that the physician would otherwise have to locate during the visit.
Accepting one optional document can reduce missing information and retrieval
time. This capability must remain part of one consultation-preparation episode;
it must not become a medical-record library or reuse the historical prototype's
dossier/document runtime.

The first slice must work locally without a cloud storage, antivirus, OCR, LLM,
SMS or scheduling provider. The repository is still prohibited for real health
data until the deployment, HDS, encryption, retention, backup, threat-model and
malware-scanning decisions applicable to a pilot are closed.

## Decision

### Bounded model and API

`PreparationAttachment` is a separate aggregate owned by exactly one
`OrganizationId` and one `PreparationCaseId`. V0.1 permits a singleton optional
attachment. Uploading different content replaces it; an exact retry is a
metadata and storage no-op. The server fixes provenance to `PATIENT_UPLOADED`.

The scoped patient API is:

- `PUT /api/v1/patient-access/attachment` — create or replace one multipart
  `file` part;
- `GET /api/v1/patient-access/attachment` — metadata only;
- `DELETE /api/v1/patient-access/attachment` — idempotent removal.

The browser supplies no organization, case, patient, storage key, provenance or
author. Authentication uses the opaque patient cookie; unsafe methods require
the session-bound CSRF token and exact patient origin. The use cases repeat the
server-side scope checks inside their transaction and share the lock order
`case -> invitation -> session -> attachment`. Responses are `no-store` and
expose neither domain IDs, storage keys nor digests.

Patient byte download is deliberately absent. The later authenticated,
organization-authorized professional download is defined separately in
[`0008-professional-attachment-download.md`](0008-professional-attachment-download.md)
and never treats an opaque storage key as authorization.

### File validation and minimisation

Only PDF, JPEG and PNG are accepted, up to 10 MiB of bytes actually streamed.
The adapter compares the declared allowlisted MIME type with deterministic file
signatures and end markers, rejects truncation and size mismatch, and computes a
SHA-256 digest for idempotence. It reduces the original name to a Unicode-
normalized, control-free basename and applies the canonical extension. No raw
content or original filename is logged.

The singleton limit is intentional. It satisfies the first field need while
bounding data collection, storage growth and abuse. Multiple documents,
archives, active formats and document extraction remain out of scope until
measured pilot need and stronger scanning controls justify them.

### Private replaceable storage

The application depends on TwinCare-owned `PrivateAttachmentStorage` and
`PreparationAttachmentRepository` ports. The local adapter stores extensionless
opaque UUID keys in a dedicated private root with owner-only POSIX permissions
where supported. Paths are direct children, and symlinks or non-regular files
fail closed. Neither the historical document service nor its upload directory
is used.

The servlet multipart parser keeps its zero-byte memory threshold but spools to
a second dedicated private runtime directory, not a generic container temp
location. Startup validates this directory, applies owner-only permissions and
deletes only abandoned regular files older than the configured grace period.
One upload per scoped patient session is processed at a time before multipart
body parsing, bounding concurrent staging from one credential.

### Transaction and crash consistency

Streaming and validation occur in staging before the database transaction. The
transaction then revalidates authorization after acquiring locks. New bytes are
promoted before metadata is flushed and are removed on rollback. Replaced or
deleted bytes are moved to a deletion quarantine, finalized only after commit
and restored after rollback.

Startup reconciliation compares opaque storage keys with database metadata. It
removes abandoned staging and unreferenced final objects, restores referenced
quarantines and finalizes unreferenced quarantines. Missing referenced bytes or
any reconciliation failure stops startup. Conversely, an already-missing final
object does not prevent a later authorized replacement or deletion of stale
metadata.

## Consequences

- The patient can make one likely-useful document available before the visit
  without an assistant or external provider.
- Upload, replacement, retries, deletion, rollback and interrupted-process
  recovery have explicit deterministic behavior.
- Storage remains replaceable by a later HDS-compatible adapter.
- This patient-contribution slice itself added no professional download, OCR,
  classification, clinical interpretation, questionnaire, readiness rule or
  audit-event store.
- Success should later be measured by useful-document availability before the
  visit, retrieval time avoided, rejected/failed uploads and patient effort.
