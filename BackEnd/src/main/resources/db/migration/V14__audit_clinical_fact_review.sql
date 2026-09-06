-- Clinical review evidence contains server identities and fact IDs, never clinical text.
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
        'READY_CONFIRMED', 'PREPARATION_CLOSED',
        'CLINICAL_FACT_CONFIRMED', 'CLINICAL_FACT_REJECTED'
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
        OR (event_type IN ('CLINICAL_FACT_CONFIRMED', 'CLINICAL_FACT_REJECTED')
            AND subject_type IS NOT NULL AND subject_type = 'CLINICAL_FACT'
            AND subject_id IS NOT NULL)
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
            'READY_CONFIRMED', 'PREPARATION_CLOSED',
            'CLINICAL_FACT_CONFIRMED', 'CLINICAL_FACT_REJECTED'
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
