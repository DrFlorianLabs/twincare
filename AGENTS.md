# AGENTS.md — TwinCare repository rules

These rules apply to all automated coding agents and contributors in this repository.

## Product objective

TwinCare V0.1 prepares a primary-care consultation **after an appointment is booked and before the physician sees the patient**.

The product must reduce physician time lost to incomplete context, missing documents and preparatory tasks that could happen beforehand.

The default professional output is a concise clinical vignette plus explicit pending/completed preparation tasks.

## Mandatory product test

Before implementing a feature, answer:

1. What concrete problem does it solve?
2. For which user?
3. At what moment in the workflow?
4. What measurable gain should it create: time, cognitive load or clinical quality?
5. How does it integrate without forcing replacement of existing practice software?

If no strong answer exists, do not implement the feature.

## Current V0.1 boundary

Prioritize:

- consultation preparation;
- natural-language patient input;
- secure document intake;
- structured and provenance-aware facts;
- validated clinical instruments when medically justified;
- preparation tasks;
- concise physician vignette;
- deterministic workflow and auditability.

Do not expand V0.1 into:

- a full EHR;
- a broad patient portal;
- chronic pathways unless explicitly requested for a later milestone;
- environmental health features;
- autonomous diagnosis or treatment;
- a generic AI agent platform.

Historical prototype code may contain these concepts. Historical presence is not a reason to keep expanding them.

## Architecture rules

- Production-grade code only.
- Prefer a modular monolith until scaling evidence requires otherwise.
- Keep domain/application logic independent from infrastructure providers.
- No hard dependency on Doctolib, Weda, Render, an SMS provider, an LLM provider or any other external API.
- External systems are adapters behind project-owned interfaces.
- A useful local/manual fallback must exist for the core workflow.
- Do not introduce a dependency when Java/Spring/Vue platform capabilities are sufficient.
- Do not create an internal generic agent framework.
- Use explicit types/state machines instead of stringly-typed business rules.
- Unknown clinical rules fail closed.
- AI output is untrusted input and cannot directly perform consequential state changes.

## Security and health data

Non-negotiable:

- no health data, patient exports or real patient documents in Git;
- no health data or identifying patient content in application logs;
- no public document URLs;
- authenticate and authorize sensitive object access at service/domain boundaries;
- storage keys are not access-control tokens;
- validate uploaded content type and signature;
- no default secrets;
- no secrets committed to source control;
- minimize collected data;
- preserve provenance for clinically relevant information;
- design for auditability without putting PHI in ordinary logs.

Do not use real patient data in development/tests. Use synthetic fixtures only.

## Clinical content

Do not invent medical question sets, thresholds or care recommendations.

Validated scales/instruments require explicit source/version/licensing metadata and deterministic tested scoring.

If clinical content is not validated, keep the technical mechanism generic and use clearly synthetic test fixtures.

## Coding standards

Backend:

- Java 21 / Spring Boot conventions;
- constructor injection;
- explicit validation;
- typed exceptions;
- transaction boundaries only where needed;
- no catch-all exception swallowing;
- no entity exposure when a stable DTO is more appropriate for a new API;
- Flyway for persistent schema changes;
- unit tests for domain/rules and integration/security tests for boundaries.

Frontend:

- Vue components remain simple and task-oriented;
- one primary action per screen where practical;
- no hard-coded production URLs;
- API access through a single configured client/service layer;
- never put authorization secrets in URLs;
- accessible labels, loading/error states and mobile-first patient flows.

## Change discipline

- Keep PRs focused.
- Document material architecture decisions in `docs/` or ADRs.
- Do not silently change the current product boundary.
- Never delete historically important project/IP artefacts merely because they are out of current scope; archive/document them unless they are unsafe data.
- Remove unsafe runtime data immediately from the current tree and flag possible history purge separately.

## Performance and resilience

- Target interactive responses under 2 seconds for normal non-AI actions.
- Timeouts/circuit-breaking belong in adapters, not domain logic.
- External integration failure must not corrupt a preparation case.
- Use idempotent operations for invitations, ingestion and external synchronization where applicable.
- Prefer deterministic retries and explicit failure states over hidden retries.

## Before marking work complete

Run the relevant backend/frontend tests and builds. If a check cannot be run in the current environment, state that clearly in the PR and do not claim it passed.
