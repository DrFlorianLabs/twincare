# ADR 0006 — Deterministic readiness and professional preparation view

## Status

Accepted for TC-006a.

## Context

Before a consultation, the primary-care professional needs to understand the
available preparation quickly without reconstructing the case or asking a
model to make an implicit clinical decision. The current vertical slice already
contains the case, the exact patient-declared reason and one optional
attachment. TC-006a establishes the read-only policy and view; task and
confirmation mutations are specified separately by ADR 0007.

The professional view contains health-related data. A general organization
administrator must not receive clinical access merely because they can manage
the tenant.

## Decision

TwinCare derives readiness with the pure, versioned `CORE_PREPARATION` rule set,
version `1`. Every result carries the rule-set identifier, version and
`DERIVED_DETERMINISTIC` provenance.

The states are:

- `INCOMPLETE`: the patient reason is absent;
- `ACTION_REQUIRED`: at least one explicit human preparation task is open;
- `REVIEWABLE`: the reason is present and no task is open;
- `READY`: the case is consistently marked ready after professional
  confirmation;
- `NOT_APPLICABLE`: the case is terminal.

The policy emits `READY` only when a persisted professional confirmation agrees
with `READY_FOR_CONSULTATION`. If stored case status claims readiness without
that evidence, assessment fails closed. ADR 0007 defines the evidence, actor,
reviewed revision and atomic status transition added by TC-006b.

A stored `NEEDS_ACTION` state without an open task fails closed instead of
being mislabeled `REVIEWABLE`. ADR 0007 defines the task aggregate and the
atomic task-count/case-status changes added by TC-006b.

The typed blockers are `PATIENT_REASON_MISSING` and
`OPEN_PREPARATION_TASKS`. Missing optional attachments are not blockers. The
rule never interprets patient prose, infers a missing clinical document or
creates a task. Unsupported rule identifiers, versions and inconsistent ready
evidence fail closed.

TC-006a exposes two read-only professional operations:

- a bounded, paginated worklist without full patient free text;
- a structured case view containing the exact declared reason, safe attachment
  metadata and the deterministic assessment.

The view is built on demand from a tenant-scoped query. It is not persisted,
does not generate narrative text. Every outcome on the professional preparation
paths, including authentication, authorization and validation errors, is
returned with `Cache-Control: no-store`.
The list query joins attachment presence in one query to avoid N+1 reads.

Clinical capabilities are separate from general staff creation access:

- clinicians and active medical assistants may view preparations;
- organization administrators have no clinical view by default;
- task management, attachment download and ready confirmation use separate
  capabilities;
- ready confirmation is reserved for clinicians.

Both the HTTP boundary and application service enforce access. The application
authorizes the actor before any clinical query, and every query scopes by
organization and case identifiers together.

## Consequences

- The professional can see the preparation state without an LLM, provider or
  materialized summary.
- The contract is explainable and regression-testable.
- TC-006b feeds human-task counts and persisted confirmation evidence into this
  policy as specified by ADR 0007.
- TC-006c will add professional byte download behind the dedicated download
  capability and a separate security review.
- TC-006d will consume the stable API in the professional frontend.

## Non-goals

- clinical interpretation, urgency or diagnostic advice;
- generated summary, OCR, classification or LLM use;
- automatic task generation;
- declaring a document missing from attachment absence;
- document download, task-label editing or reassignment, closure or durable
  audit events.
