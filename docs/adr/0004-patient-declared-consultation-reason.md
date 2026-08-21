# ADR 0004 — Patient-declared consultation reason

Status: **accepted for V0.1**

## Context

The first clinically useful contribution is the patient's own reason for the
consultation. Capturing it before the visit reduces the time spent rebuilding
the request at the start of the consultation. This is one statement attached
to one `PreparationCase`, not a longitudinal record, diagnosis or clinical
classification.

## Decision

### Exact patient API

`PUT /api/v1/patient-access/reason` accepts only:

```json
{"reason":"Patient-declared free text"}
```

The opaque patient cookie, in-memory `X-TC-CSRF` token and exact patient
`Origin` are mandatory. The request never accepts an organization, case,
patient, invitation, author, provenance, timestamp or workflow status. Unknown
fields and malformed JSON fail with a generic `400` response.

Success returns `204 No Content` with `Cache-Control: no-store`. The endpoint
does not echo clinical text or technical identifiers. Invalid sessions and
concurrent scope changes fail uniformly with `401` and an expired cookie; CSRF
or origin failures return the existing generic `403`.

No patient reason read endpoint is added in this slice. Resume UX, submission
and professional read models remain separate work.

### Domain rules

The server fixes provenance to `PATIENT_DECLARED` and records the time after
transactional authorization. Text is trimmed only at its outer boundaries;
internal whitespace and line breaks are preserved. Blank text, `U+0000` and
text longer than 4,000 characters are rejected without including the content
in errors.

The first statement moves `PATIENT_STARTED` to `COLLECTING`. A changed statement
in `COLLECTING` replaces the singleton value. A changed response from
`NEEDS_ACTION` returns the case to `COLLECTING`. No patient write automatically
marks a case ready for review. An identical normalized retry is a no-op: it
does not change the timestamp, version or domain events.

### Transaction and authorization

The use case never trusts the filter grant by itself. In one transaction it:

1. checks a scalar, non-managed session projection against every identifier in
   the server-derived grant and the contribution scope;
2. locks `PreparationCase`;
3. locks the source invitation;
4. locks the patient session;
5. captures the current instant after all lock waits;
6. revalidates the consumed invitation, expiry, active session, exact
   relationships and patient-contribution case state;
7. applies and persists the domain mutation.

This keeps the shared `case -> invitation -> session` lock order. Concurrent
professional revocation either follows a completed write or wins before it;
the patient write can never proceed after observing revocation.

### Persistence and privacy

The reason columns already exist in migration V5. Additive migration V7 adds
database checks for non-blank text, known provenance and a recorded timestamp
inside the case timeline. `PatientReasonRecorded` remains content-free and is
not represented as a durable security audit; protected audit storage remains
TC-007.

DTOs, commands, responses and domain values redact the reason from string
representations. Application logs never contain request bodies or clinical
text.

## Consequences

- The patient can provide the first useful information with no account,
  assistant step, LLM or external provider.
- Repeated network retries are safe and do not create misleading activity.
- The slice adds no questionnaire, structured response, document, frontend,
  submission action, professional vignette or durable audit store.
- Real patient data remains prohibited until the roadmap's deployment,
  security and regulatory gates are closed.
