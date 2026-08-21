# ADR 0001 — Organization membership and request scope

Status: **accepted for the TwinCare Consultation module**

Date: 2026-08-21

## Context

The prototype authorizes staff globally and has no organization boundary. A
preparation case intended for health information cannot rely on that behavior.
TwinCare must isolate each new case while preserving a future ability for one
professional to work in more than one care structure.

## Decision

- `care_organization` is the ownership boundary for every `PreparationCase`.
- `organization_membership` links a server-side user to zero, one or several
  organizations, with an explicit role and active flag.
- No organization or membership is created implicitly. Historical accounts
  therefore fail closed after V5 until the controlled bootstrap procedure is
  executed.
- The organization identifier in an HTTP path is an **untrusted scope
  selector**, not an authorization claim. The use case reloads the authenticated
  user from the database and verifies application role, active membership and
  membership role before any identifier generation or write.
- Application `ADMIN` has no automatic cross-organization clinical access. An
  explicit active membership is still required.
- Repository reads include both case ID and organization ID. New code must not
  load a preparation case by case ID alone.
- Patient and appointment references are opaque within one organization; they
  are not global patient identities and do not create a longitudinal record.

The initial authorized organization roles are `CLINICIAN`,
`MEDICAL_ASSISTANT` and `ORGANIZATION_ADMIN`. Changing those permissions is a
reviewed security decision, not a frontend choice.

## Consequences

- Multi-organization professionals can select a scope in the URL, but cannot
  grant themselves access to it.
- A membership lookup is required for every organization-bound use case.
- Organization onboarding is an explicit privileged operation documented in
  `docs/ORGANIZATION_BOOTSTRAP.md` until a separately reviewed administration
  capability exists.
- Database uniqueness and indexes are organization-scoped.
- The new boundary currently protects only the `backend.preparation` module.
  Historical prototype routes retain their previous limitations; the entire
  repository remains prohibited for real patient data.

## Rejected alternatives

- **Global staff access:** fails organization isolation.
- **Organization supplied only in a JWT or request body:** trusts stale or
  client-controlled authority data.
- **Automatic default organization:** silently joins unrelated historical
  users and creates a cross-tenant exposure risk.
- **One organization column on the user:** simpler initially, but prevents
  legitimate multi-structure work and forces a disruptive migration later.
