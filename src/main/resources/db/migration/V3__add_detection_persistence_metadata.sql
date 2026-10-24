ALTER TABLE detected_objects
ADD COLUMN object_uuid UUID;

UPDATE detected_objects
SET object_uuid = gen_random_uuid()
WHERE object_uuid IS NULL;

ALTER TABLE detected_objects
ALTER COLUMN object_uuid SET DEFAULT gen_random_uuid();

ALTER TABLE detected_objects
ALTER COLUMN object_uuid SET NOT NULL;

ALTER TABLE detected_objects
ADD CONSTRAINT uq_detected_objects_object_uuid UNIQUE (object_uuid);

ALTER TABLE detected_objects
ADD COLUMN project_id UUID;

UPDATE detected_objects d
SET project_id = ri.project_id
FROM room_images ri
WHERE d.image_id = ri.image_id;

ALTER TABLE detected_objects
ALTER COLUMN project_id SET NOT NULL;

ALTER TABLE detected_objects
ADD CONSTRAINT fk_detected_objects_project_id
FOREIGN KEY (project_id)
REFERENCES room_projects(project_id)
ON DELETE CASCADE;

ALTER TABLE detected_objects
ADD COLUMN model_version VARCHAR(100);

UPDATE detected_objects
SET model_version = 'legacy-unknown'
WHERE model_version IS NULL;

ALTER TABLE detected_objects
ALTER COLUMN model_version SET NOT NULL;

ALTER TABLE detected_objects
ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_detected_objects_project_active
ON detected_objects(project_id, active);