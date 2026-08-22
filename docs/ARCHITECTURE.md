# TwinCare — Architecture cible V0.1

Status: **normative direction**, implementation progressive.

## 1. Architecture decision

TwinCare uses a modular monolith for V0.1. The product does not need microservices, an agent framework or an event platform to prove value.

The architecture must preserve four properties:

1. the clinical/workflow core is deterministic and testable;
2. external vendors are adapters, never hard dependencies;
3. AI capabilities are replaceable implementations behind ports;
4. health data access is explicit, authenticated, authorized and auditable.

## 2. Core domain

The primary aggregate should converge toward `PreparationCase` (working name).

One preparation case represents **one consultation preparation episode**, not the patient's complete medical record.

Conceptual fields:

- `id` — internal opaque identifier;
- `patientRef` — internal patient reference;
- `appointmentRef` — optional opaque reference to an appointment source;
- `scheduledAt` — consultation date/time when known;
- `status` — explicit state machine;
- `reasonFreeText` — initial patient wording;
- `structuredFacts` — typed facts with provenance;
- `attachments` — protected document references;
- `clinicalMeasures` — versioned validated instrument results;
- `preparationTasks` — actions to complete before consultation;
- `vignette` — concise derived read model;
- timestamps and audit metadata.

`DossierPatient` is an inherited prototype concept. Do not expand it as a competing EHR. New work should move semantics toward a consultation preparation case.

## 3. State machine

The exact states may evolve, but transitions must be explicit. A suitable starting model is:

`CREATED -> PATIENT_STARTED -> COLLECTING -> READY_FOR_REVIEW | NEEDS_ACTION -> READY_FOR_CONSULTATION -> CLOSED`

Exceptional states:

`EXPIRED`, `CANCELLED`, `BLOCKED`

No LLM is allowed to mutate state directly. A model may propose structured information; application/domain code validates the proposal and performs the transition.

## 4. Ports and adapters

The core defines interfaces; adapters implement them.

### Appointment source

`AppointmentSourcePort`

Possible adapters:

- manual/local import — required baseline;
- CSV/PDF import — possible transitional adapter;
- vendor API adapter — optional future integration.

### Patient notification

`NotificationPort`

Core behavior must not depend on a particular SMS/email provider. Development and pilot modes can expose/copy the preparation link locally.

### Clinical context

`ClinicalContextPort`

Possible sources:

- locally accumulated TwinCare context;
- manual PDF export from a practice-management system;
- future EHR connector.

The domain consumes a normalized internal representation, never vendor-specific objects.

### Language understanding

`LanguageUnderstandingPort`

Implementations may include:

- deterministic/local parser for limited cases;
- local model;
- explicitly configured external model if compliance and deployment policy allow it later.

The core must run without an external model.

### Document understanding

`DocumentUnderstandingPort`

Same replacement rule: local/no-op implementation is always possible. Raw health documents are never implicitly sent to a third party.

### Task scheduling

`PreparationTaskPort`

V0.1 baseline is an internal task queue. Calendar integration is an adapter. If a calendar API is unavailable, the task still exists and remains actionable inside TwinCare.

## 4.1 Organization ownership

The organization boundary for the new consultation-preparation module is
defined in [`adr/0001-organization-membership-and-scope.md`](adr/0001-organization-membership-and-scope.md).
Its controlled initial provisioning procedure is
[`ORGANIZATION_BOOTSTRAP.md`](ORGANIZATION_BOOTSTRAP.md). This isolation does not
retroactively make the historical prototype routes suitable for real patient
data.

## 4.2 Patient invitation boundary

The patient invitation and credential exchange are defined in
[`adr/0002-patient-invitation-credential-exchange.md`](adr/0002-patient-invitation-credential-exchange.md).
An invitation is a one-time bootstrap credential for exactly one preparation
case; it is not the continuing patient session, proof of identity or consent.
Both invitation and session secrets are stored only as domain-separated keyed
digests. Expiry, consumption, revocation, case transition and session creation
are server-controlled and transactional.

## 4.3 Patient session boundary

Patient cookie authentication and CSRF are defined in
[`adr/0003-patient-session-authentication-and-csrf.md`](adr/0003-patient-session-authentication-and-csrf.md).
The patient namespace uses a dedicated security chain without JWT
authentication. A server-derived grant resolves to exactly one active
preparation case; the browser never selects its organization, case or scope.
Every clinical write must revalidate that grant inside the write transaction.

## 4.4 Patient contribution boundary

The first clinical write is defined in
[`adr/0004-patient-declared-consultation-reason.md`](adr/0004-patient-declared-consultation-reason.md).
It is one patient-declared statement owned by one `PreparationCase`. The
browser supplies neither scope nor provenance. The use case revalidates the
session after acquiring the shared case, invitation and session locks, and no
clinical text enters ordinary logs or security events.

## 4.5 Patient attachment boundary

The bounded patient attachment capability is defined in
[`adr/0005-patient-preparation-attachment.md`](adr/0005-patient-preparation-attachment.md).
It is a separate `PreparationAttachment` owned by exactly one organization and
one `PreparationCase`; it does not reuse or enlarge the historical dossier and
document model. V0.1 currently permits one optional PDF, JPEG or PNG of at most
10 MiB. The patient API exposes metadata only and derives every scope identifier
and the `PATIENT_UPLOADED` provenance on the server.

Raw bytes are behind a TwinCare-owned storage port. The local adapter uses an
opaque key in a dedicated private root, stages bytes before the database
transaction and coordinates promotion, replacement and deletion with commit or
rollback callbacks. Startup reconciliation repairs interrupted operations and
fails closed when metadata references missing bytes or cleanup cannot be
completed. Multipart parser temporary files use a separate private directory;
they never enter the historical upload root or Git.

## 4.6 Deterministic readiness and professional read model

The professional worklist and structured preparation view are defined in
[`adr/0006-deterministic-readiness-and-professional-view.md`](adr/0006-deterministic-readiness-and-professional-view.md).
Readiness is a pure, explicit and versioned policy. It does not interpret
patient prose, infer clinical requirements or depend on an LLM. An optional
attachment being absent is never itself a blocker.

The professional vignette is assembled at read time from tenant-scoped data; it
is not a stored narrative. The bounded list omits full patient free text and
uses a joined projection to avoid N+1 document lookups. The detailed view
returns the exact human-entered facts and safe attachment metadata with
`Cache-Control: no-store`.

Clinical access is capability-based. Active clinicians and medical assistants
may view preparation data; organization administrators have no clinical access
by default. Authorization happens before each query and every case lookup is
scoped by both organization and case identifiers.

## 4.7 Human task and confirmation boundary

Human preparation tasks and clinician confirmation are defined in
[`adr/0007-human-preparation-tasks-and-ready-confirmation.md`](adr/0007-human-preparation-tasks-and-ready-confirmation.md).
A task is an explicit human action, never a clinical inference from prose or an
absent optional document. It belongs to exactly one organization and case and
is assigned to an active clinician or medical assistant in that organization.

All task and confirmation mutations serialize on the owning case. The case
revision is the optimistic review token exposed to the professional: reason,
task and attachment changes advance it, while no-op retries do not. A clinician
confirmation must provide the revision that was actually reviewed and stores
that revision with its server-derived actor, time and readiness-rule version.
This prevents a stale professional screen from confirming unseen changes.
The read path independently verifies that the current revision is exactly the
confirmed revision plus the confirmation transition, that the reviewed reason
timestamp still matches and that no task is open. Its multi-query detailed view
uses repeatable-read isolation to avoid mixing two concurrent states.

Task management is available to active clinicians and medical assistants;
ready confirmation is clinician-only. Organization administrators have neither
capability by default. The read model returns task labels only in the detailed
view, never in the bounded worklist or ordinary logs.

## 4.8 Professional attachment download boundary

The authenticated byte path is defined in
[`adr/0008-professional-attachment-download.md`](adr/0008-professional-attachment-download.md).
It requires the dedicated `DOWNLOAD_ATTACHMENT` capability before any case,
metadata or storage read. Case and attachment are resolved under their shared
tenant scope and locked in that order, matching patient replace/delete.

The private-storage adapter opens the opaque final object once with symlink
following disabled and verifies actual size plus SHA-256 on that same file
channel. The verified handle is pinned before the transaction ends; the HTTP
layer streams it after locks and the database connection have been released.
It never lazily reopens a path that a concurrent replacement could move. The
response is forced to download, uses only allowlisted MIME metadata, is
`no-store`/`nosniff`, and exposes no key, digest or public URL.

## 4.9 Professional context boundary

The professional frontend discovers its active clinical organizations through
`GET /api/v1/professional-context`. The server derives the actor and
capabilities from the authenticated account plus active organization
memberships; the request accepts no role, actor or organization input.

The returned capabilities only control presentation. Every downstream use case
repeats its own authorization before reading preparation data. Inactive and
administrative-only memberships are omitted, and the response contains no
patient or preparation data. See
[`adr/0009-professional-context-discovery.md`](adr/0009-professional-context-discovery.md).

## 4.10 Preparation audit boundary

The durable workflow chronology is defined in
[`adr/0011-append-only-preparation-audit.md`](adr/0011-append-only-preparation-audit.md).
It is a small append-only store, not an event bus or event-sourcing platform.
Every consequential preparation mutation records a typed, versioned and
minimized event in the same database transaction; an audit failure rolls the
mutation back.

Audit events contain workflow metadata only. Patient wording, patient
references, task labels, filenames, storage data, credentials and arbitrary
payloads are forbidden. Events are sequenced per case under the existing case
lock and retain the persisted case revision. The protected read API is
tenant-scoped, cursor-paginated and clinician-only. Historical cases are never
given a fabricated history: the read model explicitly reports when coverage
does not begin at creation.

## 4.11 Explicit closure boundary

Explicit case closure is defined in
[`adr/0012-explicit-preparation-case-closure.md`](adr/0012-explicit-preparation-case-closure.md).
It is a clinician-only transition from `READY_FOR_CONSULTATION` to `CLOSED`,
guarded by the exact ready-case revision and the persisted readiness evidence.

The case, its invitations and its patient sessions are locked in deterministic
order. Closure state, immutable closure evidence, defensive credential
revocation and minimized audit events share one transaction. Exact replay is a
no-op. Professional list and detail reads load and validate the tenant-scoped
closure evidence; a closed status without the matching revision chain and
timestamp fails closed. Reopening and automatic closure are outside V0.1.

The professional client discovers the closure capability from server context
and offers one explicit, human-confirmed action only for a trusted `READY`
view. A revision conflict reloads the case but never retries the closure
command automatically. Client capability checks improve the interface; the
server use case remains the authorization boundary.

## 4.12 Patient browser and reason-resume boundary

The active V0.1 patient interface is a single-purpose preparation screen. It
replaces the historical account/chatbot portal in the runtime entry point; the
legacy sources may remain as historical artefacts but are not imported by the
active application.

The invitation secret is accepted only from an exact `#token=...` fragment and
is removed with `history.replaceState` before the first API request. Query-string
tokens, duplicate parameters and malformed fragments fail closed. The secret,
patient session CSRF token and patient wording remain in memory only; the client
uses neither `localStorage` nor `sessionStorage` and contains no analytics or
third-party runtime call.

Invitation exchange, cookie session restoration and reason write use the
dedicated `/api/v1/patient-access/**` namespace. The patient-side resume read is
defined in [`adr/0013-patient-reason-resume-read.md`](adr/0013-patient-reason-resume-read.md).
It revalidates the full server-owned grant in one persistence query and returns
only whether the current patient's statement exists, its exact text and its
server timestamp. No tenant, case, patient or invitation identifier is accepted
or returned.

The browser never retries a reason mutation automatically. If a write fails,
the draft remains only in volatile memory and a new attempt requires an explicit
patient action. Network ambiguity during one-time invitation exchange is handled
by trying the server cookie session before replaying the invitation. The useful
core remains same-origin and provider-independent.

## 5. Provenance

Every clinically relevant structured fact must retain provenance. Suggested categories:

- `PATIENT_DECLARED`;
- `DOCUMENT_EXTRACTED`;
- `DOCUMENT_VERIFIED`;
- `HUMAN_ENTERED`;
- `IMPORTED_CONTEXT`;
- `DERIVED_DETERMINISTIC`;
- `AI_PROPOSED`.

An AI-proposed fact is not silently promoted to verified fact.

## 6. Clinical instruments

Validated scales/questionnaires require a versioned catalog, separate from generic conversational prompts.

Each catalog entry should eventually store:

- stable identifier and version;
- indication/context;
- population constraints;
- source/reference metadata;
- wording and answer options;
- scoring function;
- interpretation metadata;
- licensing/usage constraints;
- clinical reviewer and validation date.

Scoring is deterministic code with tests. The conversational layer may decide to offer an instrument only through explicit, versioned rules.

## 7. Document boundary

Documents are sensitive objects, not static web assets.

Rules:

- storage keys are internal and not used as authorization tokens;
- document content is fetched by document ID through an authenticated endpoint;
- authorization checks link the authenticated user to the document's preparation/patient context;
- MIME type and file signature are validated on upload;
- repository and logs never contain document content;
- deletion removes both metadata and stored bytes or emits a recoverable cleanup task;
- parser temporary files are isolated in a private, access-restricted runtime directory;
- storage implementation remains replaceable by local/HDS-compatible object storage later.

## 8. Frontend boundary

Patient UI and professional UI may remain separate deployable frontends for now, but they share the same versioned HTTP API contract.

No frontend contains a hard-coded production provider URL. API origin is relative by default and configurable through environment configuration.

The professional frontend discovers its active organisations and advisory UI
capabilities through the server-owned `/api/v1/professional-context` endpoint.
It never derives authorization from a route, a locally stored role or a client
supplied actor. Every clinical read and command remains authorized again by the
backend.

The V0.1 workspace is deliberately a thin task-oriented client over the
professional read model. It does not call the historical dossier, document,
user or chatbot APIs; does not compute readiness; and does not persist patient
reason, task labels, attachment metadata or response bodies in browser storage.
Concurrent organisation/case changes cancel and supersede pending reads so a
late response cannot repopulate another tenant's screen.

## 9. Error handling and observability

- Errors exposed to clients are typed and do not leak stack traces or filesystem paths.
- Logs contain event type, technical correlation IDs and non-sensitive operational metadata only.
- Never log free-text patient content, document names/content, clinical scores attached to an identifiable patient, email addresses or access tokens.
- Security/audit events are a distinct concern from application logs. The
  preparation audit is persisted atomically and is never reconstructed from
  ordinary log output.

## 10. Dependency policy

A new runtime dependency is accepted only if it provides substantial value that cannot be implemented cleanly with the existing platform.

Forbidden architectural shortcuts:

- direct calls to a vendor SDK from domain/service code;
- provider-specific IDs as domain primary keys;
- business rules encoded in prompt text only;
- stringly-typed clinical rules without a schema/version;
- silent fallback from failed rule parsing to “true”;
- public file URLs;
- health data in source control.

## 11. Migration strategy

Do not rewrite the prototype wholesale.

Order:

1. secure current boundaries;
2. freeze product scope;
3. introduce tests around retained behavior;
4. create `PreparationCase` domain alongside legacy concepts;
5. migrate one end-to-end preparation path;
6. retire legacy chatbot/environment/pathway code only after the new path is functional;
7. add integrations only after the standalone path demonstrates measurable field value.
