ALTER TABLE room_images
ADD COLUMN bucket VARCHAR(255);

UPDATE room_images
SET bucket = 'local'
WHERE bucket IS NULL;

ALTER TABLE room_images
ALTER COLUMN bucket SET NOT NULL;

ALTER TABLE room_images
ADD CONSTRAINT uq_room_images_project_id UNIQUE (project_id);