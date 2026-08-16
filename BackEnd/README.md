# TwinCare backend

Spring Boot / Java 21 backend for the historical prototype capabilities retained
during Sprint 0 hardening. It is not yet authorized for real patient data.

## Required configuration

Set these values outside source control:

- `JDBC_DATABASE_URL`, `JDBC_DATABASE_USERNAME`, `JDBC_DATABASE_PASSWORD`;
- `JWT_SECRET` with at least 32 bytes of secret material;
- `CORS_ALLOWED_ORIGINS` for the deployed frontends;
- `FILE_UPLOAD_DIR` to a private, persistent and backed-up storage location.

Optional settings include `JWT_EXPIRATION_MS`, `FILE_MAX_SIZE_BYTES` and
`SPRINGDOC_ENABLED`. API documentation is disabled by default and restricted to
administrators when enabled.

## Authentication and provisioning

Only `POST /api/auth/login` is anonymous.

- `POST /api/auth/register` provisions patient accounts and requires an
  authenticated assistant, physician or administrator. The endpoint cannot
  create staff or administrator roles.
- `POST /api/auth/reset-password` is an administrative operation.
- Document content is retrieved through authenticated
  `GET /api/documents/{documentId}/content`; storage keys are never public URLs.

Authorization is enforced again in services. Never infer access from a client
role, client-supplied actor identifier, document UUID or route identifier alone.

## Database safety

Flyway migration V3 is a destructive historical prototype migration and is kept
unchanged to preserve checksums. Before connecting an existing database, back it
up and inspect its schema and `flyway_schema_history`.

- A non-empty pre-Flyway legacy schema is baselined at version 4 by default.
- A legacy schema with Flyway history older than V3 fails startup instead of
  replaying the destructive migration automatically.
- A new empty database runs V3 then V4 normally.

Do not override this protection without an explicit, reviewed migration plan.

## Documents

Runtime documents must stay outside Git. Uploads are validated, stored under an
opaque server-generated key and tied to database transactions. Deletions use a
recoverable quarantine under `FILE_UPLOAD_DIR/.pending-deletions` and are
reconciled from metadata at startup after interruption.

The local filesystem implementation is suitable for development only unless the
deployment supplies durable private storage, encryption, backups and tested
recovery appropriate to its risk and compliance requirements.

## Tests

From `BackEnd/` with Java 21:

```sh
sh ./mvnw --batch-mode --no-transfer-progress verify
```

Tests use synthetic fixtures and H2 only. H2 is not a runtime dependency.
