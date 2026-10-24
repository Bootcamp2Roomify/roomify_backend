-- MVP Feature 2: each project records what room it is and whether permanent changes are allowed.
-- The API always supplies these; defaults only cover rows created before this migration.
ALTER TABLE room_projects
    ADD COLUMN name VARCHAR(100) NOT NULL DEFAULT 'My room',
    ADD COLUMN room_type VARCHAR(30) NOT NULL DEFAULT 'OTHER'
        CHECK (room_type IN ('BEDROOM', 'LIVING_ROOM', 'STUDY', 'DORM_ROOM', 'STUDIO', 'OTHER')),
    ADD COLUMN rental_status VARCHAR(20) NOT NULL DEFAULT 'OWNED'
        CHECK (rental_status IN ('RENTAL', 'DORMITORY', 'OWNED'));

CREATE INDEX idx_room_projects_user_updated ON room_projects(user_id, updated_at DESC, project_id);
