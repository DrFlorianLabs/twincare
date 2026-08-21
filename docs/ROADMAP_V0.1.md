# TwinCare Consultation — Roadmap V0.1

Status: **active product roadmap**

TwinCare Consultation prepares one primary-care consultation after booking and
before the physician sees the patient. It is not an EHR and does not replace the
practice-management software.

## Product outcome

Before opening the consultation, the professional should understand in less
than 30 seconds:

- why the patient is coming;
- which useful information and documents are already available;
- what remains missing or ambiguous;
- which preparation tasks are completed or pending.

The pilot must measure physician time saved, avoided interruptions, missing
information detected before consultation, patient effort and corrections or
safety incidents.

## Delivery sequence

### Sprint 1 — PreparationCase foundation

1. Pure domain model, explicit states, provenance and minimal audit events.
2. Manual professional creation with organisation ownership and authorization.
3. Persistence and versioned professional API.
4. Short-lived, hashed, revocable, single-purpose patient invitation.

Exit: a professional can create an isolated preparation case and expose a safe
patient entry point without an external provider.

### Sprint 2 — Patient collection

1. Patient submits the consultation reason in free text.
2. Minimal structured answers are progressively disclosed.
3. Optional protected document upload is attached to the preparation case.
4. The patient can resume safely until expiry or submission.

Exit: the patient can provide the first useful information in seconds and
complete the preparation without a mandatory human intermediary.

### Sprint 3 — Professional preparation view

1. Deterministic readiness and missing-information rules.
2. Internal preparation tasks with explicit ownership and state.
3. Concise professional vignette/read model with provenance.
4. Explicit ready-for-consultation and closure actions.

Exit: the physician receives a concise, actionable view before consultation.

### Sprint 4 — Pilot safety and operations

1. Complete organisation/tenant authorization.
2. Formal audit-event model and protected audit access.
3. Retention, deletion, backup and restore procedures with tests.
4. Threat model, dependency/secret scanning and external security review.
5. Data map, AIPD working file, processor register and HDS deployment decision.
6. Formal assessment of whether any intended functionality could qualify as a
   medical device.

Exit: a documented go/no-go review determines whether a synthetic-data pilot or
a real-health-data pilot is permitted. Real patient data remains prohibited
until every applicable gate is closed.

### Later — Optional adapters and bounded AI

- scheduling, notification and practice-software adapters;
- document extraction and language understanding behind TwinCare-owned ports;
- local/no-op implementation retained for every core workflow;
- no model directly changes workflow state or performs a consequential action.

## Delivery rules

- One focused issue and pull request per independently reviewable change.
- Parallel agents receive separate branches, worktrees and exclusive write
  scopes; shared migrations and contracts are serialized.
- Every pull request states the field problem, user, workflow moment, measurable
  gain, non-goals, security impact and verification evidence.
- CI, authorization tests and migration checks must be green before merge.
- No real patient data, patient document, token or secret enters source control,
  tests, ordinary logs or AI prompts.

## Current wave

- `TC-001` — completed: pure `PreparationCase` domain and invariants ([PR #19](https://github.com/DrFlorianLabs/twincare-amont/pull/19)).
- `TC-002` — completed: manual professional creation, persistence and authorization ([PR #20](https://github.com/DrFlorianLabs/twincare-amont/pull/20)).
- `TC-003` — completed: secure patient invitation lifecycle and scoped patient session bootstrap ([PR #21](https://github.com/DrFlorianLabs/twincare-amont/pull/21)).
- `TC-004a` — completed: patient session authentication, CSRF and explicit logout ([PR #22](https://github.com/DrFlorianLabs/twincare-amont/pull/22)).
- `TC-004b` — completed: patient free-text reason only; structured responses remain a separate slice ([PR #23](https://github.com/DrFlorianLabs/twincare-amont/pull/23)).
- `TC-005` — delivered: secure optional patient attachment linked to a preparation case ([PR #24](https://github.com/DrFlorianLabs/twincare-amont/pull/24)).
- `TC-006`: deterministic readiness, tasks and professional vignette.
- `TC-007`: explicit closure and protected audit events.
