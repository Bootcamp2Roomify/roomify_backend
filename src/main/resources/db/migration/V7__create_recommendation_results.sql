CREATE TABLE recommendation_sets (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    project_id UUID NOT NULL REFERENCES room_projects(project_id) ON DELETE CASCADE,
    design_result_id BIGINT NOT NULL REFERENCES design_results(design_id) ON DELETE CASCADE,
    schema_version VARCHAR(10) NOT NULL CHECK (schema_version = '1.0'),
    currency VARCHAR(3) NOT NULL CHECK (currency IN ('TWD', 'USD')),
    budget NUMERIC(12,2) NOT NULL CHECK (budget >= 0),
    estimated_total NUMERIC(12,2) NOT NULL CHECK (estimated_total >= 0 AND estimated_total <= budget),
    remaining_budget NUMERIC(12,2) NOT NULL CHECK (remaining_budget = budget - estimated_total),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    provider_metadata JSONB NOT NULL CHECK (jsonb_typeof(provider_metadata) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_recommendation_sets_project_latest ON recommendation_sets (project_id, id DESC);

CREATE TABLE recommendation_items (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    recommendation_set_id BIGINT NOT NULL REFERENCES recommendation_sets(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 5),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    UNIQUE (recommendation_set_id, position)
);

CREATE TABLE recommendation_diy_guides (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    recommendation_item_id BIGINT NOT NULL REFERENCES recommendation_items(id) ON DELETE CASCADE,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    provider_metadata JSONB NOT NULL CHECK (jsonb_typeof(provider_metadata) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_recommendation_diy_item_latest ON recommendation_diy_guides (recommendation_item_id, id DESC);
