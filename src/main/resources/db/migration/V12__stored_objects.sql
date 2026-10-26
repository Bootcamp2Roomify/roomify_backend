-- Object bytes for STORAGE_TYPE=database (hosts without S3 or a persistent disk). Unused with s3/local storage.
CREATE TABLE stored_objects (
    object_key TEXT PRIMARY KEY,
    content_type VARCHAR(100),
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
