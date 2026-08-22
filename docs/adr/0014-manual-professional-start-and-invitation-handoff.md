# ADR 0014 — Manual professional start and patient-link handoff

Status: **accepted for V0.1**

## Context

The preparation core can already create a tenant-owned `PreparationCase`, issue
a one-time patient invitation and collect the patient's reason. However, the
professional interface cannot start that workflow. A clinician or medical
assistant would need technical API access, so the local/provider-independent
path is not usable end to end.

This slice solves that operational gap immediately after an appointment is
booked and before the patient contributes. Its target is a preparation link
ready to hand off in under 60 seconds, with no mandatory assistant, messaging
provider, scheduling provider or practice-software integration. It collects
only an opaque local patient reference, an optional opaque appointment
reference and the consultation time; it does not create a medical record.

## Decision

### Idempotent case creation

The professional client calls:

`POST /api/v1/organizations/{organizationId}/preparation-cases`

with a required UUID `requestId`, `patientReference`, optional
`appointmentReference` and future `scheduledAt`. The browser generates one
cryptographically secure request identifier and captures one normalized payload
for one unchanged draft. It keeps both only in memory across an explicit retry
after an ambiguous network failure, and discards them when the draft changes,
the operation succeeds, the dialog closes without ambiguity, the organization
changes or the user logs out. After an ambiguous result, the fields are frozen
and the dialog can only replay the exact captured payload or explicitly abandon
it after a successful refresh of the case list. A failed or stale refresh keeps
the request identifier, captured payload and dialog intact. The client never
retries automatically.

The backend canonicalizes the accepted business fields and stores a versioned
SHA-256 fingerprint with the case. The pair `(organizationId, requestId)` is
unique. Creation serializes on the owning organization row before checking its
uniqueness constraints. A first accepted command returns `201 Created`; an exact replay returns
`200 OK` with the same case and does not append a second audit event. Reusing the
request identifier with a different canonical payload returns `409 Conflict`.
Concurrent identical commands serialize through the database uniqueness
boundary and converge on one case. `Location` identifies the readable,
tenant-scoped professional preparation-view resource and every response is
`no-store`. An exact late replay may report the case's current known workflow
status; the client selects that case and reloads the authoritative view.

Case creation remains authorized in the application boundary. The server-owned
professional context exposes `CREATE_PREPARATION_CASE` only for active
clinicians and active medical assistants in the organization. Client capability
checks control presentation only; the client never supplies an actor or role.

### Separate invitation issuance and handoff

After creation, the professional selects the persisted case and explicitly
calls the existing invitation command. `ISSUE_PATIENT_INVITATION` is likewise
derived from active clinical membership and rechecked in the use case. An
invitation can be issued only while the trusted server read model reports the
case in `CREATED` state.

Creation and invitation remain two commands. If invitation issuance fails, the
professional retries against the same case and never creates a replacement
case. No automatic retry is performed because a successful response lost in
transit is ambiguous and an explicit new issuance revokes the previous
invitation.

The patient application base URL comes from the professional frontend
configuration. It must be an absolute HTTPS URL without credentials, query or
fragment. Plain HTTP is accepted only for an exact local loopback development
host. The browser constructs only:

`<configured-patient-url>#token=<43-character-base64url-secret>`

The fragment prevents the invitation credential from being sent in the initial
HTTP request. The complete link is held only in volatile component state. It is
copied only after an explicit professional action and is cleared when the dialog
closes, the case or organization changes, the user logs out or the component is
destroyed. It is never placed in the professional URL, browser storage, logs,
analytics or an automatic SMS/email request. An explicit copy remains in the
operating-system clipboard after TwinCare clears its own state and is presented
as confidential to the user.

The consultation date/time is entered in the browser's explicitly displayed
local time zone. Invalid or daylight-saving clock-shift times that are missing
or ambiguous are rejected rather than silently normalized.

## Consequences

- The provider-independent TwinCare workflow is operable from the two V0.1
  interfaces without technical API assistance.
- Clinicians and medical assistants can start preparation; the assistant is not
  a mandatory workflow step.
- Idempotency protects against duplicate cases while preserving explicit,
  understandable retry behavior.
- The handoff uses the professional's existing communication channel. Future
  notification or appointment adapters remain optional behind TwinCare-owned
  interfaces.
- Patient names, email addresses and telephone numbers are deliberately absent
  from this slice, reducing unnecessary identifying data.
- SMS/email automation, Doctolib/Weda integration, questionnaires, document UI,
  LLM use and recovery after a consumed/expired patient access are out of scope.
- This capability does not authorize real patient data. Pilot security,
  deployment, regulatory and operational gates remain mandatory.
