CREATE TABLE preparation_task (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    preparation_case_id UUID NOT NULL,
    creation_request_id UUID NOT NULL,
    description VARCHAR(240) NOT NULL,
    assignee_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    closed_by_user_id BIGINT,
    closed_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_preparation_task_case_organization
        FOREIGN KEY (preparation_case_id, organization_id)
        REFERENCES preparation_case (id, organization_id),
    CONSTRAINT fk_preparation_task_assignee_membership
        FOREIGN KEY (organization_id, assignee_user_id)
        REFERENCES organization_membership (organization_id, user_id),
    CONSTRAINT fk_preparation_task_creator_membership
        FOREIGN KEY (organization_id, created_by_user_id)
        REFERENCES organization_membership (organization_id, user_id),
    CONSTRAINT fk_preparation_task_closer_membership
        FOREIGN KEY (organization_id, closed_by_user_id)
        REFERENCES organization_membership (organization_id, user_id),
    CONSTRAINT uq_preparation_task_creation_request
        UNIQUE (organization_id, preparation_case_id, creation_request_id),
    CONSTRAINT ck_preparation_task_description
        CHECK (CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 240),
    CONSTRAINT ck_preparation_task_status
        CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_preparation_task_timeline
        CHECK (updated_at >= created_at AND (closed_at IS NULL OR closed_at >= created_at)),
    CONSTRAINT ck_preparation_task_closure
        CHECK (
            (status = 'OPEN'
                AND closed_by_user_id IS NULL
                AND closed_at IS NULL
                AND updated_at = created_at)
            OR
            (status IN ('COMPLETED', 'CANCELLED')
                AND closed_by_user_id IS NOT NULL
                AND closed_at IS NOT NULL
                AND updated_at = closed_at)
        )
);

CREATE INDEX idx_preparation_task_case_status
    ON preparation_task (organization_id, preparation_case_id, status);

CREATE TABLE preparation_ready_confirmation (
    preparation_case_id UUID PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    confirmed_by_user_id BIGINT NOT NULL,
    confirmed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    rule_set_id VARCHAR(64) NOT NULL,
    rule_set_version INTEGER NOT NULL,
    reviewed_case_revision BIGINT NOT NULL,
    reviewed_reason_recorded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    open_task_count INTEGER NOT NULL,
    CONSTRAINT fk_ready_confirmation_case_organization
        FOREIGN KEY (preparation_case_id, organization_id)
        REFERENCES preparation_case (id, organization_id),
    CONSTRAINT fk_ready_confirmation_actor_membership
        FOREIGN KEY (organization_id, confirmed_by_user_id)
        REFERENCES organization_membership (organization_id, user_id),
    CONSTRAINT ck_ready_confirmation_rule_set
        CHECK (CHAR_LENGTH(TRIM(rule_set_id)) BETWEEN 1 AND 64 AND rule_set_version > 0),
    CONSTRAINT ck_ready_confirmation_review_evidence
        CHECK (reviewed_case_revision >= 0 AND open_task_count = 0)
);

CREATE INDEX idx_ready_confirmation_organization_time
    ON preparation_ready_confirmation (organization_id, confirmed_at);
