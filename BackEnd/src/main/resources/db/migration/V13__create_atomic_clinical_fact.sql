CREATE TABLE clinical_fact (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    preparation_case_id UUID NOT NULL,
    fact_kind VARCHAR(48) NOT NULL,
    text_value VARCHAR(4000) NOT NULL,
    provenance VARCHAR(40) NOT NULL,
    certainty VARCHAR(40) NOT NULL,
    recorded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    effective_from TIMESTAMP(6) WITH TIME ZONE,
    effective_to TIMESTAMP(6) WITH TIME ZONE,
    lifecycle_status VARCHAR(24) NOT NULL,
    current_slot SMALLINT,
    superseded_at TIMESTAMP(6) WITH TIME ZONE,
    review_status VARCHAR(24) NOT NULL,
    reviewed_at TIMESTAMP(6) WITH TIME ZONE,
    reviewer_reference VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_clinical_fact_case_organization
        FOREIGN KEY (preparation_case_id, organization_id)
        REFERENCES preparation_case (id, organization_id),
    CONSTRAINT uq_clinical_fact_current_kind
        UNIQUE (preparation_case_id, organization_id, fact_kind, current_slot),
    CONSTRAINT ck_clinical_fact_kind
        CHECK (fact_kind = 'CONSULTATION_REASON'),
    CONSTRAINT ck_clinical_fact_text
        CHECK (CHAR_LENGTH(TRIM(text_value)) BETWEEN 1 AND 4000),
    CONSTRAINT ck_clinical_fact_provenance
        CHECK (provenance IN (
            'PATIENT_DECLARED',
            'DOCUMENT_EXTRACTED',
            'DOCUMENT_VERIFIED',
            'HUMAN_ENTERED',
            'IMPORTED_CONTEXT',
            'DERIVED_DETERMINISTIC',
            'AI_PROPOSED'
        )),
    CONSTRAINT ck_clinical_fact_certainty
        CHECK (certainty IN ('NOT_ASSESSED', 'ASSERTED', 'EXPRESSED_AS_UNCERTAIN')),
    CONSTRAINT ck_clinical_fact_effective_period
        CHECK (
            (effective_from IS NULL AND effective_to IS NULL)
            OR (effective_from IS NOT NULL
                AND (effective_to IS NULL OR effective_to >= effective_from))
        ),
    CONSTRAINT ck_clinical_fact_lifecycle
        CHECK (
            (lifecycle_status = 'CURRENT' AND current_slot = 1 AND superseded_at IS NULL)
            OR (lifecycle_status = 'SUPERSEDED' AND current_slot IS NULL
                AND superseded_at IS NOT NULL AND superseded_at >= recorded_at)
        ),
    CONSTRAINT ck_clinical_fact_review
        CHECK (
            (review_status = 'UNREVIEWED' AND reviewed_at IS NULL AND reviewer_reference IS NULL)
            OR (review_status IN ('CONFIRMED', 'REJECTED')
                AND reviewed_at IS NOT NULL
                AND reviewed_at >= recorded_at
                AND reviewer_reference IS NOT NULL
                AND CHAR_LENGTH(TRIM(reviewer_reference)) BETWEEN 1 AND 128)
        )
);

CREATE INDEX idx_clinical_fact_context_timeline
    ON clinical_fact (organization_id, preparation_case_id, recorded_at);

CREATE INDEX idx_clinical_fact_review_queue
    ON clinical_fact (organization_id, lifecycle_status, review_status);

-- V5-V7 stored one current patient statement directly on PreparationCase.
-- Reusing the case UUID for this one-time backfill is deterministic and avoids
-- a database-specific UUID generator. New facts receive independent UUIDs.
INSERT INTO clinical_fact (
    id,
    organization_id,
    preparation_case_id,
    fact_kind,
    text_value,
    provenance,
    certainty,
    recorded_at,
    lifecycle_status,
    current_slot,
    review_status,
    version
)
SELECT
    id,
    organization_id,
    id,
    'CONSULTATION_REASON',
    patient_reason,
    patient_reason_provenance,
    'NOT_ASSESSED',
    patient_reason_recorded_at,
    'CURRENT',
    1,
    'UNREVIEWED',
    0
FROM preparation_case
WHERE patient_reason IS NOT NULL;
