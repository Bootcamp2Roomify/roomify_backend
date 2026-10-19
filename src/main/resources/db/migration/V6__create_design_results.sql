CREATE TABLE design_results (
    design_id BIGSERIAL PRIMARY KEY,
    project_id UUID NOT NULL UNIQUE
        REFERENCES room_projects(project_id) ON DELETE CASCADE,

    plan_json JSONB NOT NULL,
    estimated_total_cost NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    model_name VARCHAR(100) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_design_results_cost
        CHECK (estimated_total_cost >= 0),

    CONSTRAINT chk_design_results_plan_json_object
        CHECK (jsonb_typeof(plan_json) = 'object')
);

CREATE INDEX idx_design_results_project_id
    ON design_results(project_id);