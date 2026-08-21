CREATE TABLE care_organization (
    id VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE organization_membership (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    membership_role VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_membership_organization
        FOREIGN KEY (organization_id) REFERENCES care_organization(id),
    CONSTRAINT fk_membership_user
        FOREIGN KEY (user_id) REFERENCES app_utilisateur(id),
    CONSTRAINT uq_membership_organization_user UNIQUE (organization_id, user_id),
    CONSTRAINT ck_membership_role CHECK (
        membership_role IN ('CLINICIAN', 'MEDICAL_ASSISTANT', 'ORGANIZATION_ADMIN')
    )
);

CREATE INDEX idx_membership_user_active
    ON organization_membership (user_id, active);

CREATE TABLE preparation_case (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    patient_reference VARCHAR(128) NOT NULL,
    appointment_reference VARCHAR(128),
    scheduled_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(40) NOT NULL,
    patient_reason VARCHAR(4000),
    patient_reason_provenance VARCHAR(40),
    patient_reason_recorded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_preparation_case_organization
        FOREIGN KEY (organization_id) REFERENCES care_organization(id),
    CONSTRAINT uq_preparation_case_appointment
        UNIQUE (organization_id, appointment_reference),
    CONSTRAINT uq_preparation_case_patient_schedule
        UNIQUE (organization_id, patient_reference, scheduled_at),
    CONSTRAINT ck_preparation_case_consultation CHECK (
        appointment_reference IS NOT NULL OR scheduled_at IS NOT NULL
    ),
    CONSTRAINT ck_preparation_case_timeline CHECK (
        updated_at >= created_at
        AND (scheduled_at IS NULL OR scheduled_at >= created_at)
    ),
    CONSTRAINT ck_preparation_case_status CHECK (
        status IN (
            'CREATED', 'PATIENT_STARTED', 'COLLECTING', 'READY_FOR_REVIEW',
            'NEEDS_ACTION', 'READY_FOR_CONSULTATION', 'CLOSED', 'EXPIRED', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_preparation_case_reason_consistency CHECK (
        (patient_reason IS NULL
            AND patient_reason_provenance IS NULL
            AND patient_reason_recorded_at IS NULL)
        OR
        (patient_reason IS NOT NULL
            AND patient_reason_provenance IS NOT NULL
            AND patient_reason_recorded_at IS NOT NULL)
    ),
    CONSTRAINT ck_preparation_case_reason_status CHECK (
        status IN ('EXPIRED', 'CANCELLED')
        OR (status IN ('CREATED', 'PATIENT_STARTED') AND patient_reason IS NULL)
        OR (status IN (
                'COLLECTING', 'READY_FOR_REVIEW', 'NEEDS_ACTION',
                'READY_FOR_CONSULTATION', 'CLOSED'
            ) AND patient_reason IS NOT NULL)
    )
);

CREATE INDEX idx_preparation_case_organization_status
    ON preparation_case (organization_id, status);

CREATE INDEX idx_preparation_case_organization_scheduled
    ON preparation_case (organization_id, scheduled_at);

CREATE INDEX idx_preparation_case_organization_created
    ON preparation_case (organization_id, created_at);

CREATE INDEX idx_preparation_case_organization_patient
    ON preparation_case (organization_id, patient_reference);
