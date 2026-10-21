CREATE TABLE design_requests (
    request_id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES room_projects(project_id) ON DELETE CASCADE,
    input_snapshot JSONB NOT NULL CHECK (jsonb_typeof(input_snapshot)='object'),
    request_version VARCHAR(20) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','SUCCEEDED','FAILED')),
    error_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    UNIQUE(request_id,project_id),
    CHECK ((status='PENDING' AND completed_at IS NULL AND error_code IS NULL)
        OR (status='SUCCEEDED' AND completed_at IS NOT NULL AND error_code IS NULL)
        OR (status='FAILED' AND completed_at IS NOT NULL AND error_code IS NOT NULL))
);
CREATE UNIQUE INDEX uq_pending_design_per_project ON design_requests(project_id) WHERE status='PENDING';
ALTER TABLE design_results DROP CONSTRAINT design_results_project_id_key;
ALTER TABLE design_results ALTER COLUMN plan_json DROP NOT NULL;
ALTER TABLE design_results ALTER COLUMN estimated_total_cost DROP NOT NULL;
ALTER TABLE design_results ADD COLUMN request_id UUID UNIQUE;
ALTER TABLE design_results ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SUCCEEDED'
    CHECK(status IN ('PENDING','SUCCEEDED','FAILED'));
ALTER TABLE design_results ADD COLUMN error_code VARCHAR(50);
ALTER TABLE design_results ADD COLUMN completed_at TIMESTAMPTZ;
UPDATE design_results SET completed_at=created_at;
ALTER TABLE design_results ADD CONSTRAINT fk_design_request_project
    FOREIGN KEY(request_id,project_id) REFERENCES design_requests(request_id,project_id) ON DELETE CASCADE;
ALTER TABLE design_results ADD CONSTRAINT uq_design_result_project UNIQUE(design_id,project_id);
ALTER TABLE design_results ADD CONSTRAINT chk_design_result_success CHECK (
    (status='SUCCEEDED' AND plan_json IS NOT NULL AND estimated_total_cost IS NOT NULL AND completed_at IS NOT NULL AND error_code IS NULL)
    OR (status='PENDING' AND plan_json IS NULL AND completed_at IS NULL AND error_code IS NULL)
    OR (status='FAILED' AND plan_json IS NULL AND completed_at IS NOT NULL AND error_code IS NOT NULL));
CREATE INDEX idx_design_latest ON design_results(project_id,created_at DESC,design_id DESC);
CREATE TABLE design_assets (
    design_result_id BIGINT PRIMARY KEY,
    project_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK(status IN ('UPLOADING','SUCCEEDED','FAILED')),
    bucket VARCHAR(255), storage_key TEXT, content_type VARCHAR(100),
    file_size_bytes BIGINT, width INTEGER, height INTEGER, error_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(design_result_id,project_id) REFERENCES design_results(design_id,project_id) ON DELETE CASCADE,
    CHECK (status!='SUCCEEDED' OR (bucket IS NOT NULL AND storage_key IS NOT NULL
        AND content_type IN ('image/jpeg','image/png') AND file_size_bytes>0 AND width>0 AND height>0 AND error_code IS NULL)),
    CHECK(status!='FAILED' OR error_code IS NOT NULL)
);
