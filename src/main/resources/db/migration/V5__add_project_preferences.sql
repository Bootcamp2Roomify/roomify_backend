CREATE TABLE project_preferences (
    project_id UUID PRIMARY KEY REFERENCES room_projects(project_id) ON DELETE CASCADE,
    style VARCHAR(32) NOT NULL,
    budget_amount NUMERIC(12,2) NOT NULL CHECK (budget_amount > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency IN ('TWD', 'USD')),
    preferred_colors JSONB NOT NULL DEFAULT '[]'::jsonb,
    room_purpose VARCHAR(32) NOT NULL,
    rental_friendly BOOLEAN NOT NULL DEFAULT FALSE,
    special_requirements VARCHAR(1000) NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (jsonb_typeof(preferred_colors) = 'array')
);
