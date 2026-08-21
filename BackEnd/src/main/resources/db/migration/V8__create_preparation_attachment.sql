CREATE TABLE preparation_attachment (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    preparation_case_id UUID NOT NULL,
    storage_key UUID NOT NULL,
    display_filename VARCHAR(200) NOT NULL,
    media_type VARCHAR(16) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256_digest VARCHAR(64) NOT NULL,
    provenance VARCHAR(32) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_preparation_attachment_case_organization
        FOREIGN KEY (preparation_case_id, organization_id)
        REFERENCES preparation_case (id, organization_id),
    CONSTRAINT uq_preparation_attachment_case
        UNIQUE (preparation_case_id, organization_id),
    CONSTRAINT uq_preparation_attachment_storage_key
        UNIQUE (storage_key),
    CONSTRAINT ck_preparation_attachment_filename
        CHECK (LENGTH(TRIM(display_filename)) BETWEEN 1 AND 200),
    CONSTRAINT ck_preparation_attachment_media_type
        CHECK (media_type IN ('PDF', 'JPEG', 'PNG')),
    CONSTRAINT ck_preparation_attachment_size
        CHECK (size_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT ck_preparation_attachment_digest
        CHECK (
            LENGTH(sha256_digest) = 64
            AND REPLACE(REPLACE(REPLACE(REPLACE(
                REPLACE(REPLACE(REPLACE(REPLACE(
                REPLACE(REPLACE(REPLACE(REPLACE(
                REPLACE(REPLACE(REPLACE(REPLACE(
                    sha256_digest,
                    '0', ''), '1', ''), '2', ''), '3', ''),
                    '4', ''), '5', ''), '6', ''), '7', ''),
                    '8', ''), '9', ''), 'a', ''), 'b', ''),
                    'c', ''), 'd', ''), 'e', ''), 'f', '') = ''
        ),
    CONSTRAINT ck_preparation_attachment_provenance
        CHECK (provenance = 'PATIENT_UPLOADED'),
    CONSTRAINT ck_preparation_attachment_timeline
        CHECK (updated_at >= uploaded_at)
);

CREATE INDEX idx_preparation_attachment_organization_updated
    ON preparation_attachment (organization_id, updated_at);
