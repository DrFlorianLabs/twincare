# ADR 0012 — Explicit preparation-case closure

Status: accepted for TC-007b-a

## Context

A consultation preparation that remains open after the encounter creates an
ambiguous professional worklist and keeps patient credentials usable longer
than needed. Closing a case must not depend on a scheduling, EHR, notification
or AI provider.

The existing readiness confirmation proves which revision the clinician
reviewed, but it is not closure evidence. Closure advances the case once more,
so the evidence chain is:

```text
reviewed revision N -> ready confirmation N+1 -> closure N+2
```

## Decision

Only a platform physician with an active `CLINICIAN` membership receives the
dedicated `CLOSE_PREPARATION_CASE` capability. The server derives the actor;
the request contains only the tenant/case route and the expected ready-case
revision.

The command is:

```text
PUT /api/v1/organizations/{organizationId}/preparation-cases/{caseId}/closure
{"expectedRevision": 8}
```

The use case authorizes first, then locks in this order:

```text
case -> invitations ordered by identifier -> sessions ordered by identifier
```

It accepts only `READY_FOR_CONSULTATION`, the exact current revision, a
consistent immutable readiness confirmation and zero open tasks. It then:

1. transitions the case to `CLOSED` and persists the new revision;
2. inserts one immutable `preparation_case_closure` row;
3. revokes every non-terminal invitation and every non-revoked patient
   session owned by the case;
4. appends minimized revocation events when state changed;
5. appends `PREPARATION_CLOSED` with the server actor, time and persisted
   closure revision.

All steps share one database transaction. Any persistence or audit failure
rolls back the case, closure evidence and credential revocations.

The closure evidence contains only organization/case identifiers, the
server-derived clinician identifier, server time, the ready-case revision and
the closed-case revision. It contains no patient reference, clinical text,
task label, filename, storage key, credential or arbitrary payload.

An exact replay with the original expected ready revision returns the original
closure evidence and creates no revision, revocation or audit event. A
different revision or inconsistent state fails with a conflict. Reopening is
not supported.

## Read model

A closed case remains readable to authorized clinical staff. Its readiness is
`NOT_APPLICABLE`, while the stored confirmation must still match the explicit
two-transition revision chain. Both list and detail projections load the
tenant-scoped closure row and fail closed unless its ready/closed revisions and
timestamp match the case. A `CLOSED` status without that proof is not presented
as a normal closure. Terminal status does not disable evidence validation.

## Consequences

- A physician deliberately ends the preparation episode after consultation.
- No patient credential remains usable because of an overlooked consumed
  invitation or active session.
- Closure is deterministic, local and independently auditable.
- The audit trail and closure row remain health-related metadata subject to the
  future retention, deletion, backup, restore and legal-hold policy.
- A clinician membership referenced by closure evidence is deactivated rather
  than hard-deleted; the database explicitly restricts deletion so actor
  provenance cannot be orphaned.
- The professional action is delivered separately by TC-007b-b and consumes
  this versioned contract without automatic retry.
- Automatic closure, closure reasons, reopening and document deletion remain
  separate concerns.
