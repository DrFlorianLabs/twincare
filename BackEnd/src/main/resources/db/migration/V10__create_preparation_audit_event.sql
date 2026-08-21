CREATE TABLE preparation_audit_event (
    event_id UUID PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    preparation_case_id UUID NOT NULL,
    event_sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version SMALLINT NOT NULL,
    actor_kind VARCHAR(16) NOT NULL,
    actor_user_id BIGINT,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    case_revision BIGINT NOT NULL,
    subject_type VARCHAR(32),
    subject_id UUID,
    CONSTRAINT fk_preparation_audit_case_organization
        FOREIGN KEY (preparation_case_id, organization_id)
        REFERENCES preparation_case (id, organization_id),
    CONSTRAINT fk_preparation_audit_actor_membership
        FOREIGN KEY (organization_id, actor_user_id)
        REFERENCES organization_membership (organization_id, user_id),
    CONSTRAINT uq_preparation_audit_case_sequence
        UNIQUE (organization_id, preparation_case_id, event_sequence),
    CONSTRAINT ck_preparation_audit_sequence_revision
        CHECK (event_sequence > 0 AND case_revision >= 0),
    CONSTRAINT ck_preparation_audit_event_version
        CHECK (event_version = 1),
    CONSTRAINT ck_preparation_audit_event_type
        CHECK (event_type IN (
            'PREPARATION_CREATED',
            'INVITATION_ISSUED', 'INVITATION_REVOKED', 'INVITATION_EXPIRED',
            'PATIENT_ACCESS_STARTED', 'PATIENT_ACCESS_ENDED', 'PATIENT_ACCESS_REVOKED',
            'PATIENT_REASON_RECORDED',
            'ATTACHMENT_CREATED', 'ATTACHMENT_REPLACED', 'ATTACHMENT_DELETED',
            'ATTACHMENT_DOWNLOAD_AUTHORIZED_AND_VERIFIED',
            'TASK_CREATED', 'TASK_COMPLETED', 'TASK_CANCELLED',
            'READY_CONFIRMED'
        )),
    CONSTRAINT ck_preparation_audit_creation_sequence
        CHECK (event_type <> 'PREPARATION_CREATED' OR event_sequence = 1),
    CONSTRAINT ck_preparation_audit_actor
        CHECK (
            (actor_kind = 'STAFF' AND actor_user_id IS NOT NULL)
            OR (actor_kind IN ('PATIENT', 'SYSTEM') AND actor_user_id IS NULL)
        ),
    CONSTRAINT ck_preparation_audit_subject
        CHECK (
            (event_type IN (
                'PREPARATION_CREATED', 'PATIENT_ACCESS_STARTED', 'PATIENT_ACCESS_ENDED',
                'PATIENT_ACCESS_REVOKED',
                'PATIENT_REASON_RECORDED', 'READY_CONFIRMED'
            ) AND subject_type IS NULL AND subject_id IS NULL)
            OR (event_type IN ('INVITATION_ISSUED', 'INVITATION_REVOKED', 'INVITATION_EXPIRED')
                AND subject_type = 'INVITATION' AND subject_id IS NOT NULL)
            OR (event_type IN (
                    'ATTACHMENT_CREATED', 'ATTACHMENT_REPLACED', 'ATTACHMENT_DELETED',
                    'ATTACHMENT_DOWNLOAD_AUTHORIZED_AND_VERIFIED'
                ) AND subject_type = 'ATTACHMENT' AND subject_id IS NOT NULL)
            OR (event_type IN ('TASK_CREATED', 'TASK_COMPLETED', 'TASK_CANCELLED')
                AND subject_type = 'PREPARATION_TASK' AND subject_id IS NOT NULL)
        ),
    CONSTRAINT ck_preparation_audit_actor_event
        CHECK (
            (event_type IN (
                'PREPARATION_CREATED', 'INVITATION_ISSUED',
                'ATTACHMENT_DOWNLOAD_AUTHORIZED_AND_VERIFIED',
                'TASK_CREATED', 'TASK_COMPLETED', 'TASK_CANCELLED', 'READY_CONFIRMED'
            ) AND actor_kind = 'STAFF')
            OR (event_type IN (
                'PATIENT_ACCESS_STARTED', 'PATIENT_ACCESS_ENDED',
                'PATIENT_REASON_RECORDED',
                'ATTACHMENT_CREATED', 'ATTACHMENT_REPLACED', 'ATTACHMENT_DELETED'
            ) AND actor_kind = 'PATIENT')
            OR (event_type = 'PATIENT_ACCESS_REVOKED' AND actor_kind IN ('STAFF', 'SYSTEM'))
            OR (event_type = 'INVITATION_REVOKED' AND actor_kind IN ('STAFF', 'SYSTEM'))
            OR (event_type = 'INVITATION_EXPIRED' AND actor_kind = 'SYSTEM')
        )
);

CREATE INDEX idx_preparation_audit_case_sequence
    ON preparation_audit_event (organization_id, preparation_case_id, event_sequence DESC);
