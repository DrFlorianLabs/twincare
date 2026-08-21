# TwinCare Consultation — Organization bootstrap

Status: **controlled operational procedure for synthetic/local environments**

TwinCare does not create a default organization and does not attach historical
accounts automatically. After migration V5, every professional fails closed
until an explicit active membership has been provisioned.

This procedure does **not** authorize real patient data. The real-patient pilot
gates in `SECURITY_BASELINE.md` and `ROADMAP_V0.1.md` still apply.

## Preconditions

- the database is at Flyway version 5 or later;
- the operator has a named, privileged database account;
- the target professional account and its server-side numeric user ID have
  been verified through a controlled administrative channel;
- the organization identifier and membership identifier are newly generated
  opaque values;
- the change is recorded in the operational change log without patient data.

Do not copy an email address, patient identifier, token or password into the
SQL client history, ticket, source repository or ordinary logs.

## Transactional bootstrap

Run the following as one reviewed database transaction, replacing every named
parameter through the database client's protected parameter mechanism:

```sql
BEGIN;

INSERT INTO care_organization (id, display_name, created_at)
VALUES (:organization_id, :display_name, CURRENT_TIMESTAMP);

INSERT INTO organization_membership (
    id, organization_id, user_id, membership_role, active, created_at
)
SELECT
    :membership_id,
    :organization_id,
    id,
    :membership_role,
    TRUE,
    CURRENT_TIMESTAMP
FROM app_utilisateur
WHERE id = :verified_user_id
  AND role IN ('MEDECIN', 'ASSISTANT_MEDICAL', 'ADMIN');

SELECT COUNT(*) AS expected_one
FROM organization_membership
WHERE organization_id = :organization_id
  AND user_id = :verified_user_id
  AND active = TRUE;
```

The verification query must return exactly `1`. Otherwise run `ROLLBACK;`,
investigate and start again with new opaque identifiers. If it returns `1`, run
`COMMIT;` and verify that a synthetic creation request is authorized only for
that organization.

Allowed membership roles are:

- `CLINICIAN`;
- `MEDICAL_ASSISTANT`;
- `ORGANIZATION_ADMIN`.

An application `ADMIN` receives no automatic clinical access. It must also have
an explicit active organization membership.

## Revocation

Disable access without deleting history:

```sql
UPDATE organization_membership
SET active = FALSE
WHERE organization_id = :organization_id
  AND user_id = :verified_user_id;
```

The new preparation module checks active membership on every use case. Formal
protected audit storage and self-service organization administration remain
future controlled work; neither should be simulated with a default tenant.
