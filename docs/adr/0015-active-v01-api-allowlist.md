# ADR 0015 — Active V0.1 API allowlist

Status: **accepted for V0.1**

## Context

The repository retains prototype controllers for accounts, patient records,
documents, validations and chatbot interactions. They are historically useful,
but they predate the `PreparationCase` organization boundary and cannot be
considered safe merely because HTTP role checks exist. Keeping those controllers
mapped in the default runtime leaves object-level authorization and tenant
isolation paths outside the V0.1 security model.

TwinCare must preserve historically important source artefacts while making the
active consultation-preparation runtime unambiguous and fail closed.

## Decision

The default runtime exposes an explicit HTTP allowlist only:

- professional login;
- server-derived professional context;
- organization-scoped `PreparationCase` commands and read models;
- invitation commands;
- protected professional attachment download and audit access;
- the dedicated patient-access filter chain.

Every other `/api/v1/**` route, every historical `/api/**` route, interactive
API documentation and every otherwise unmatched request are denied. The final
security rule is `denyAll`, not authenticated fallback.

Prototype controllers are additionally conditional on the inactive
`legacy-prototype` Spring profile. Registration and administrative password
reset are separated from the active login controller and placed behind that
profile. The historical storage-recovery listener uses the same profile so the
default application never scans legacy storage at startup. The existing
environment module remains behind its dedicated legacy profile and is still
denied by the default allowlist.

This creates two independent barriers:

1. unsafe controllers and startup listeners are absent from the active Spring
   context;
2. unknown and historical routes are denied even if a component is accidentally
   mapped later.

Tests enumerate every role, including administrator, against representative
legacy and unknown routes. They also assert that the dormant controllers and
startup recovery bean are absent from the active context. Application/use-case
authorization remains mandatory behind the HTTP allowlist.

## Consequences

- The active runtime surface now matches the documented V0.1 product boundary.
- An administrator is not a clinical super-user and cannot revive legacy APIs.
- Historical source remains available for provenance and later archival work,
  but enabling `legacy-prototype` is not an approved deployment configuration.
- Account provisioning and professional identity lifecycle require a separate,
  production-grade IAM decision; they are not silently inherited from the
  prototype.
- This reduction is necessary but not sufficient for real patient data. HDS/RGPD
  governance, professional session hardening, operations, PostgreSQL evidence,
  monitoring and independent assurance remain release gates.
