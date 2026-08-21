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
- storage implementation remains replaceable by local/HDS-compatible object storage later.

## 8. Frontend boundary

Patient UI and professional UI may remain separate deployable frontends for now, but they share the same versioned HTTP API contract.

No frontend contains a hard-coded production provider URL. API origin is relative by default and configurable through environment configuration.

## 9. Error handling and observability

- Errors exposed to clients are typed and do not leak stack traces or filesystem paths.
- Logs contain event type, technical correlation IDs and non-sensitive operational metadata only.
- Never log free-text patient content, document names/content, clinical scores attached to an identifiable patient, email addresses or access tokens.
- Security/audit events are a distinct concern from application logs.

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
