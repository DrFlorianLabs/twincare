# ADR 0013 — Patient reason resume read

Status: **accepted for V0.1**

## Context

ADR 0004 deliberately deferred patient-side reading of the consultation reason.
The secure patient interface now needs to survive a reload without asking the
patient to reconstruct text that TwinCare has already recorded. This solves a
pre-consultation continuity problem for the patient and avoids duplicate work;
it does not introduce a medical record, questionnaire, classification or new
clinical rule.

## Decision

### Exact patient API

`GET /api/v1/patient-access/reason` is authenticated only by the opaque patient
session cookie established by ADR 0002 and resolved by ADR 0003. It accepts no
client-owned organization, case, patient, invitation or other scope. Request
metadata is never used to select clinical data.

An active preparation without a reason returns:

```json
{"present":false}
```

An active preparation with a patient-declared reason returns:

```json
{
  "present":true,
  "reason":"Patient-declared free text",
  "recordedAt":"2026-08-22T09:00:00Z"
}
```

`recordedAt` is the server-owned timestamp already persisted when the reason
was accepted. The response contains no technical identifier, patient
reference, organization, workflow status or provenance field. The text is
redacted from diagnostic string representations.

Every success response has `Cache-Control: no-store`, `Referrer-Policy:
no-referrer` and `X-Content-Type-Options: nosniff`. Generic patient security
failures carry the same protection headers. The frontend must keep the
response in memory and must not persist it in browser storage.

### Authorization and persistence read

The authenticated principal is not treated as sufficient evidence by the
read use case. The application derives one lookup exclusively from the
server-created `PatientAccessGrant` and the current server time. A single
read-only persistence query joins and revalidates:

1. the exact session, invitation, organization and preparation identifiers;
2. the contribution scope and the session expiry captured in the grant;
3. a non-revoked session inside its validity interval;
4. the consumed, unexpired source invitation;
5. a preparation state that still permits patient contribution;
6. patient-declared provenance whenever a reason is present.

No match fails closed through the existing generic `401` patient-session
response and clears the cookie. The atomic query narrows the revocation window
between authentication and clinical read without introducing a write lock or
mutation. Reading the reason does not advance the case revision and creates no
audit event.

## Consequences

- A patient can resume after a reload and see whether TwinCare has already
  recorded the reason, reducing re-entry and uncertainty.
- The capability works locally without an account, LLM, messaging provider or
  practice-software integration.
- The response deliberately exposes only the patient's own singleton statement
  for the current preparation and is not a longitudinal clinical read API.
- ADR 0004 remains the write contract; its statement that no read endpoint was
  included in that earlier slice is superseded only by this ADR.
- This capability does not authorize real patient data. Deployment, security
  and regulatory pilot gates remain mandatory.
