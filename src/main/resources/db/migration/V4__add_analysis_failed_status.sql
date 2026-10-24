ALTER TABLE room_projects
    DROP CONSTRAINT chk_room_projects_status;

ALTER TABLE room_projects
    ADD CONSTRAINT chk_room_projects_status
        CHECK (status IN (
            'CREATED',
            'IMAGE_UPLOADED',
            'ANALYZED',
            'ANALYSIS_FAILED',
            'PREFERENCES_READY',
            'DESIGN_READY'
        ));