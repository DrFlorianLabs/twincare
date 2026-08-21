# ADR 0008 — Verified professional attachment download

## Status

Accepted for TC-006c.

## Context

The professional preparing a consultation must be able to retrieve the single
optional document already supplied by the patient. A metadata-only vignette
still leaves the clinician searching elsewhere or asking for the document
again. The byte path contains health data and must not turn an opaque storage
key, case identifier or broad organization role into an authorization token.

Patient download, public links, inline preview and synchronization with a
practice system remain out of scope.

## Decision

TwinCare exposes one endpoint for the current singleton attachment:

`GET /api/v1/organizations/{organizationId}/preparation-cases/{caseId}/attachment`

The route accepts no attachment identifier, storage key or scope in a body or
query parameter. Spring Security first restricts the route to clinician and
medical-assistant platform roles. The application then requires the dedicated
`DOWNLOAD_ATTACHMENT` capability backed by an active clinical membership in
the requested organization. Organization administrators have no clinical
download capability by default.

After authorization, the use case locks the tenant-scoped case and then its
singleton attachment, matching the order used by patient replacement and
deletion. While those locks are held, the storage adapter opens the current
opaque object once with symbolic-link following disabled. It verifies the
actual byte count and SHA-256 digest against persisted metadata on that same
open file channel and resets the channel to its beginning. Missing, non-regular
or corrupted content fails closed before headers or bytes are sent.

The database transaction ends after the verified handle has been acquired.
The controller then streams that already-open, pinned handle synchronously with
an 8 KiB buffer and closes it on success, failure or client disconnection. It
never reopens a path after the transaction and never holds a database
connection or case lock for the duration of the client transfer. A concurrent
replacement or deletion may move the old object after the handle is acquired;
the in-flight request still receives the complete version it authorized, while
subsequent requests observe the new state.

The response uses the allowlisted persisted MIME type, exact content length,
Spring's UTF-8 `Content-Disposition: attachment` builder,
`X-Content-Type-Options: nosniff`, `Cache-Control: no-store` and
`Pragma: no-cache`. It exposes no storage key, digest or public URL. Range,
inline rendering, ETag and last-modified negotiation are not supported in this
slice.

Invalid identifiers return 400; missing tenant-scoped cases or attachment
metadata return 404; denied membership returns 403; unavailable or inconsistent
stored bytes return a generic 503. The professional no-store filter covers
success and error paths. Ordinary logs contain neither filenames, scope
identifiers, digests nor content.

## Consequences

- The clinician or medical assistant can retrieve the useful document directly
  from the preparation vignette without an external provider.
- The integrity check completes before the response is committed.
- Streaming is bounded and does not allocate the 10 MiB document as one byte
  array.
- The local adapter remains replaceable behind TwinCare-owned ports.
- No database migration, third-party dependency, LLM or network adapter is
  introduced.
- Time from vignette open to document access and failed retrieval rate become
  measurable product/operational signals for a future pilot.

## Non-goals

- patient download or anonymous/public links;
- browser preview, range requests or multiple attachments;
- malware scanning, OCR, classification or clinical interpretation;
- practice-software synchronization;
- durable access audit events, which belong to TC-007.
