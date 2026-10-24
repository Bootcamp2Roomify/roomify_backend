-- Projects belong to exactly one user. Rows created before authentication existed keep
-- user_id NULL and are therefore unreachable through the API.
ALTER TABLE room_projects
    ADD COLUMN user_id BIGINT REFERENCES users(user_id) ON DELETE CASCADE;

CREATE INDEX idx_room_projects_user ON room_projects(user_id, created_at DESC);

CREATE UNIQUE INDEX uq_users_email_lower ON users(LOWER(email));
