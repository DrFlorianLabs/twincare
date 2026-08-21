ALTER TABLE preparation_case
    ADD CONSTRAINT uq_preparation_case_id_organization
    UNIQUE (id, organization_id);

CREATE TABLE patient_invitation (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    preparation_case_id UUID NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    token_key_id VARCHAR(64) NOT NULL,
    token_digest VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    active_slot VARCHAR(16),
    issued_by_user_id BIGINT NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_patient_invitation_case_organization
        FOREIGN KEY (preparation_case_id, organization_id)
        REFERENCES preparation_case (id, organization_id),
    CONSTRAINT uq_patient_invitation_digest UNIQUE (token_key_id, token_digest),
    CONSTRAINT uq_patient_invitation_active_slot UNIQUE (preparation_case_id, active_slot),
    CONSTRAINT uq_patient_invitation_scope UNIQUE (id, organization_id, preparation_case_id),
    CONSTRAINT ck_patient_invitation_purpose CHECK (
        purpose = 'PREPARATION_CASE_ACCESS'
    ),
    CONSTRAINT ck_patient_invitation_status CHECK (
        status IN ('ACTIVE', 'CONSUMED', 'REVOKED', 'EXPIRED')
    ),
    CONSTRAINT ck_patient_invitation_timeline CHECK (
        expires_at > issued_at
        AND updated_at >= issued_at
        AND (consumed_at IS NULL OR (consumed_at >= issued_at AND consumed_at <= updated_at))
        AND (revoked_at IS NULL OR (revoked_at >= issued_at AND revoked_at <= updated_at))
    ),
    CONSTRAINT ck_patient_invitation_state CHECK (
        (status = 'ACTIVE' AND active_slot = 'ACTIVE' AND consumed_at IS NULL AND revoked_at IS NULL)
        OR (status = 'CONSUMED' AND active_slot IS NULL AND consumed_at IS NOT NULL AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND active_slot IS NULL AND revoked_at IS NOT NULL)
        OR (status = 'EXPIRED' AND active_slot IS NULL AND consumed_at IS NULL AND revoked_at IS NULL)
    )
);

CREATE INDEX idx_patient_invitation_case_history
    ON patient_invitation (organization_id, preparation_case_id, issued_at);

CREATE INDEX idx_patient_invitation_expiry_status
    ON patient_invitation (expires_at, status);

CREATE TABLE patient_access_session (
    id UUID PRIMARY KEY,
    invitation_id UUID NOT NULL,
    organization_id VARCHAR(128) NOT NULL,
    preparation_case_id UUID NOT NULL,
    scope VARCHAR(48) NOT NULL,
    token_key_id VARCHAR(64) NOT NULL,
    token_digest VARCHAR(64) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_patient_access_session_invitation_scope
        FOREIGN KEY (invitation_id, organization_id, preparation_case_id)
        REFERENCES patient_invitation (id, organization_id, preparation_case_id),
    CONSTRAINT uq_patient_access_session_invitation UNIQUE (invitation_id),
    CONSTRAINT uq_patient_access_session_digest UNIQUE (token_key_id, token_digest),
    CONSTRAINT ck_patient_access_session_scope CHECK (
        scope = 'PREPARATION_CASE_CONTRIBUTE'
    ),
    CONSTRAINT ck_patient_access_session_timeline CHECK (
        expires_at > issued_at
        AND (revoked_at IS NULL OR revoked_at >= issued_at)
    )
);

CREATE INDEX idx_patient_access_session_case
    ON patient_access_session (organization_id, preparation_case_id);

CREATE INDEX idx_patient_access_session_expiry
    ON patient_access_session (expires_at, revoked_at);
