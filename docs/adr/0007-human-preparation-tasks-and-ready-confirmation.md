# ADR 0007 — Human preparation tasks and clinician ready confirmation

## Status

Accepted for TC-006b.

## Context

The professional preparation view can already explain whether a case is
incomplete, reviewable or inconsistent. It needs two human-controlled facts to
become actionable: explicit work that remains before consultation and durable
evidence that a clinician reviewed the current preparation before marking it
ready.

These facts are clinically sensitive workflow data. They must never be inferred
from patient prose, the absence of an optional document or a client-supplied
role. A confirmation must also attest the exact version the clinician saw; a
case may change between reading the view and pressing the confirmation button.

## Decision

### Human tasks

`PreparationTask` is a bounded entity owned by one organization and one
`PreparationCase`. A task has an immutable human-entered label, an actual
assignee user, a server-derived creator, an explicit `OPEN`, `COMPLETED` or
`CANCELLED` status and server timestamps. It is not a calendar integration or a
clinical recommendation.

Creation requires a client-generated UUID request identifier. Replaying the
same identifier with the same immutable data returns the existing task;
reusing it with different data fails with a conflict. A case may contain at
most 100 tasks. Assignees must be active clinicians or medical assistants in
the same organization. Task labels are never logged.

Clinicians and active medical assistants may create and close tasks.
Organization administrators have no clinical task access by default. The
authenticated actor is resolved on the server and never accepted in the
request body.

Creating a task moves the case to `NEEDS_ACTION`. Closing the last open task
moves it to `READY_FOR_REVIEW`. A subsequent patient reason update preserves
`NEEDS_ACTION` while any task remains open. Task commands lock the owning case
before reading or changing task state so task count and case status remain
atomic.

No rule automatically creates a task from free text. The absence of the single
optional attachment does not create a task and is not a readiness blocker.

### Clinician confirmation

Only an active clinician may confirm `READY_FOR_CONSULTATION`. Confirmation is
allowed when the reason exists, no task is open and the deterministic readiness
policy returns `REVIEWABLE`. The command and the case transition execute in one
transaction while holding the case lock.

The professional view exposes the case revision. Confirmation requires that
revision as `expectedRevision`; a stale value fails with a conflict. Every
successful mutation visible in the preparation view — patient reason, task,
attachment creation, replacement or deletion — updates the case and advances
that revision. Idempotent no-op retries do not.

The immutable confirmation stores the server-derived clinician, timestamp,
readiness rule-set identifier and version, reviewed case revision, reviewed
reason timestamp and zero open-task count. The read model emits `READY` only
when this evidence, current reason timestamp, current revision, zero open-task
count and case status all agree. Duplicate confirmation with the same reviewed
revision is idempotent; conflicting evidence fails closed.

### Persistence and API

Database foreign keys bind tasks, assignees, actors and confirmation evidence
to the owning organization. The task creation request is unique within its
case and confirmation is a singleton per case.

The professional API adds:

- `POST /api/v1/organizations/{organizationId}/preparation-cases/{caseId}/tasks`;
- `PUT /api/v1/organizations/{organizationId}/preparation-cases/{caseId}/tasks/{taskId}/status`;
- `PUT /api/v1/organizations/{organizationId}/preparation-cases/{caseId}/ready-confirmation`.

All responses, including errors on these paths, use `Cache-Control: no-store`.
The worklist exposes only the open-task count and confirmation-derived
readiness. The detailed view exposes the bounded task list and minimized
confirmation evidence. Because the detailed view reads its header and bounded
task list separately, both queries run in one repeatable-read transaction and
therefore describe the same database snapshot.

## Consequences

- Remaining preparation work is explicit and assignable without an external
  provider or mandatory assistant step.
- A clinician cannot accidentally confirm a view that became stale after a
  task, reason or attachment mutation.
- Concurrency is serialized on the case; task creation and ready confirmation
  cannot both succeed against the same revision.
- Durable audit events and protected audit access remain TC-007. The task and
  confirmation tables already retain the minimum actor and time evidence
  needed to produce those events without clinical text.

## Non-goals

- automatic, AI-generated or prose-derived tasks;
- clinical urgency, diagnosis or treatment decisions;
- calendar, SMS, EHR or third-party task synchronization;
- treating an optional attachment as required;
- editing task labels or reassigning tasks;
- professional attachment byte download, frontend worklist or case closure.
