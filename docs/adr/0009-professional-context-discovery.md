# ADR 0009 — Server-derived professional context discovery

## Status

Accepted for the TC-006d frontend prerequisite.

## Context

The professional frontend must select an organization before requesting a
tenant-scoped preparation worklist. An organization identifier configured in
the browser or entered by a user is not an authorization source and cannot
reliably represent professionals who work in more than one organization.

The legacy global user API is not tenant-scoped and exposes entities that do
not belong in the V0.1 preparation path. It must not be reused for context or
task assignment.

## Decision

`GET /api/v1/professional-context` returns only the authenticated actor's own
minimal display name, server identifier and active organizations for which the
actor has at least one clinical preparation capability. Each organization
contains its identifier, configured display name and the capabilities derived
from both the server-side platform role and active membership.

The capability list is advisory UI context. Every preparation command and
query continues to authorize the actor independently at the application
boundary. A client cannot gain access by altering an organization identifier
or capability value.

Clinicians and medical assistants may discover context. Inactive memberships
and organization-administrator-only memberships are omitted. General
administrators and patients are denied. Ready confirmation is exposed only for
a physician with an active clinician membership; both clinical roles may view,
manage human tasks and download attachments when their membership permits.

The response and all errors on this path are `no-store`. It contains no patient
or preparation data and is built from the authenticated server identity; the
client provides no actor, role, organization or capability input.

## Consequences

- the professional UI has no hard-coded tenant and can handle multiple active
  organizations;
- organization choice improves usability but never becomes an authorization
  mechanism;
- the legacy `/api/users` surface remains outside the active V0.1 workflow;
- no database migration or external dependency is required.

## Non-goals

- organization or membership administration;
- a staff directory or task-assignee search;
- exposing patient data, email addresses or authentication claims;
- replacing server-side authorization with frontend capability checks.
