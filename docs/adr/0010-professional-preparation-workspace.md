# ADR 0010 — Minimal professional preparation workspace

Status: accepted for TC-006d-b

## Context

The backend now exposes a tenant-scoped professional worklist, a concise
preparation view, human task commands, revision-bound readiness confirmation
and protected attachment download. The historical back-office still routes
clinicians through dossier, user, document and chatbot screens that are outside
the TwinCare Consultation V0.1 product boundary.

The field need is to let a clinician understand one preparation in less than
30 seconds before the consultation. A medical assistant may perform useful
preparation tasks but must not become a mandatory workflow step.

## Decision

Replace the active professional routes with one responsive workspace:

- organisation choice comes only from `GET /api/v1/professional-context`;
- the paginated worklist shows reference, deadline, server readiness, open task
  count and document availability;
- the detail shows the patient-declared unverified reason, deterministic
  blockers, human tasks and optional document metadata;
- active clinicians and medical assistants may create self-assigned tasks,
  complete/cancel tasks and download the protected document when the server
  advertises the corresponding capability;
- only a clinician with `CONFIRM_READY_FOR_CONSULTATION` may explicitly confirm
  the revision actually reviewed;
- every mutation refreshes both server read models, and HTTP 409 never triggers
  an automatic retry of readiness confirmation;
- unknown states disable consequential actions.

Readiness is never recomputed in Vue. Absence of the optional document is never
presented as a blocker, and no task is inferred from patient free text.

## Browser data boundary

The professional JWT is held in `sessionStorage` because the existing backend
does not yet provide a professional cookie session. No clinical response,
organisation context or actor metadata is stored persistently. The application
uses Vue text interpolation only, has no analytics/service worker, and removes
the session plus historical prototype keys on logout.

Abort controllers and monotonically increasing request sequences bind late
responses to the current organisation and case. A context/authorization loss
immediately clears the worklist and detail before rediscovery.

The attachment is fetched with authenticated XHR, checked against the bounded
server metadata size and exposed through a short-lived object URL. It is never
addressed by a public URL or storage key.

## Non-goals

- creation, invitation or closure UI;
- patient UI or structured questionnaire;
- staff directory or reassignment workflow;
- EHR/scheduling/provider integration;
- clinical inference, LLM or local readiness rules;
- persistent offline mode, notifications or realtime updates.

## Consequences

The old professional prototype pages are no longer reachable from the active
router, but their backend endpoints are not deleted in this slice. A
server-managed professional cookie session, CSP/deployment headers, real
PostgreSQL proof, audit access and the real-health-data release gates remain
required before any patient deployment.
