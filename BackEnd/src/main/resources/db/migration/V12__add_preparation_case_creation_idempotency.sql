ALTER TABLE preparation_case
    ADD COLUMN creation_request_id UUID;

ALTER TABLE preparation_case
    ADD COLUMN creation_request_fingerprint VARCHAR(64);

ALTER TABLE preparation_case
    ADD CONSTRAINT ck_preparation_case_creation_request_consistency CHECK (
        (creation_request_id IS NULL AND creation_request_fingerprint IS NULL)
        OR
        (creation_request_id IS NOT NULL AND creation_request_fingerprint IS NOT NULL)
    );

ALTER TABLE preparation_case
    ADD CONSTRAINT ck_preparation_case_creation_request_fingerprint CHECK (
        creation_request_fingerprint IS NULL
        OR creation_request_fingerprint ~ '^[0-9a-f]{64}$'
    );

ALTER TABLE preparation_case
    ADD CONSTRAINT uq_preparation_case_creation_request
        UNIQUE (organization_id, creation_request_id);
