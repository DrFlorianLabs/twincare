ALTER TABLE preparation_case
    ADD CONSTRAINT ck_preparation_case_reason_nonblank
    CHECK (patient_reason IS NULL OR CHAR_LENGTH(TRIM(patient_reason)) > 0);

ALTER TABLE preparation_case
    ADD CONSTRAINT ck_preparation_case_reason_provenance
    CHECK (
        patient_reason_provenance IS NULL
        OR patient_reason_provenance IN (
            'PATIENT_DECLARED',
            'DOCUMENT_EXTRACTED',
            'DOCUMENT_VERIFIED',
            'HUMAN_ENTERED',
            'IMPORTED_CONTEXT',
            'DERIVED_DETERMINISTIC',
            'AI_PROPOSED'
        )
    );

ALTER TABLE preparation_case
    ADD CONSTRAINT ck_preparation_case_reason_timeline
    CHECK (
        patient_reason_recorded_at IS NULL
        OR (
            patient_reason_recorded_at >= created_at
            AND patient_reason_recorded_at <= updated_at
        )
    );
