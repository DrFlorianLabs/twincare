ALTER TABLE preparation_ready_confirmation
    ADD CONSTRAINT uq_ready_confirmation_case_organization
    UNIQUE (preparation_case_id, organization_id);

CREATE TABLE preparation_case_closure (
    preparation_case_id UUID PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    closed_by_user_id BIGINT NOT NULL,
    closed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    ready_case_revision BIGINT NOT NULL,
    closed_case_revision BIGINT NOT NULL,
    CONSTRAINT fk_preparation_closure_case_organization
        FOREIGN KEY (preparation_case_id, organization_id)
        REFERENCES preparation_case (id, organization_id),
    CONSTRAINT fk_preparation_closure_confirmation
        FOREIGN KEY (preparation_case_id, organization_id)
        REFERENCES preparation_ready_confirmation (preparation_case_id, organization_id),
    CONSTRAINT fk_preparation_closure_actor_membership
        FOREIGN KEY (organization_id, closed_by_user_id)
        REFERENCES organization_membership (organization_id, user_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_preparation_closure_revision_chain
        CHECK (ready_case_revision >= 1 AND closed_case_revision = ready_case_revision + 1)
);

CREATE INDEX idx_preparation_closure_organization_time
    ON preparation_case_closure (organization_id, closed_at);

ALTER TABLE preparation_audit_event
    DROP CONSTRAINT ck_preparation_audit_event_type;

ALTER TABLE preparation_audit_event
    ADD CONSTRAINT ck_preparation_audit_event_type
    CHECK (event_type IN (
        'PREPARATION_CREATED',
        'INVITATION_ISSUED', 'INVITATION_REVOKED', 'INVITATION_EXPIRED',
        'PATIENT_ACCESS_STARTED', 'PATIENT_ACCESS_ENDED', 'PATIENT_ACCESS_REVOKED',
        'PATIENT_REASON_RECORDED',
        'ATTACHMENT_CREATED', 'ATTACHMENT_REPLACED', 'ATTACHMENT_DELETED',
        'ATTACHMENT_DOWNLOAD_AUTHORIZED_AND_VERIFIED',
        'TASK_CREATED', 'TASK_COMPLETED', 'TASK_CANCELLED',
        'READY_CONFIRMED', 'PREPARATION_CLOSED'
    ));

ALTER TABLE preparation_audit_event
    DROP CONSTRAINT ck_preparation_audit_subject;

ALTER TABLE preparation_audit_event
    ADD CONSTRAINT ck_preparation_audit_subject
    CHECK (
        (event_type IN (
            'PREPARATION_CREATED', 'PATIENT_ACCESS_STARTED', 'PATIENT_ACCESS_ENDED',
            'PATIENT_ACCESS_REVOKED', 'PATIENT_REASON_RECORDED', 'READY_CONFIRMED',
            'PREPARATION_CLOSED'
        ) AND subject_type IS NULL AND subject_id IS NULL)
        OR (event_type IN ('INVITATION_ISSUED', 'INVITATION_REVOKED', 'INVITATION_EXPIRED')
            AND subject_type = 'INVITATION' AND subject_id IS NOT NULL)
        OR (event_type IN (
                'ATTACHMENT_CREATED', 'ATTACHMENT_REPLACED', 'ATTACHMENT_DELETED',
                'ATTACHMENT_DOWNLOAD_AUTHORIZED_AND_VERIFIED'
            ) AND subject_type = 'ATTACHMENT' AND subject_id IS NOT NULL)
        OR (event_type IN ('TASK_CREATED', 'TASK_COMPLETED', 'TASK_CANCELLED')
            AND subject_type = 'PREPARATION_TASK' AND subject_id IS NOT NULL)
    );

ALTER TABLE preparation_audit_event
    DROP CONSTRAINT ck_preparation_audit_actor_event;

ALTER TABLE preparation_audit_event
    ADD CONSTRAINT ck_preparation_audit_actor_event
    CHECK (
        (event_type IN (
            'PREPARATION_CREATED', 'INVITATION_ISSUED',
            'ATTACHMENT_DOWNLOAD_AUTHORIZED_AND_VERIFIED',
            'TASK_CREATED', 'TASK_COMPLETED', 'TASK_CANCELLED',
            'READY_CONFIRMED', 'PREPARATION_CLOSED'
        ) AND actor_kind = 'STAFF')
        OR (event_type IN (
            'PATIENT_ACCESS_STARTED', 'PATIENT_ACCESS_ENDED',
            'PATIENT_REASON_RECORDED',
            'ATTACHMENT_CREATED', 'ATTACHMENT_REPLACED', 'ATTACHMENT_DELETED'
        ) AND actor_kind = 'PATIENT')
        OR (event_type = 'PATIENT_ACCESS_REVOKED' AND actor_kind IN ('STAFF', 'SYSTEM'))
        OR (event_type = 'INVITATION_REVOKED' AND actor_kind IN ('STAFF', 'SYSTEM'))
        OR (event_type = 'INVITATION_EXPIRED' AND actor_kind = 'SYSTEM')
    );
