-- V1__initial_schema.sql
-- Roomify initial PostgreSQL schema based on the updated ERD.

BEGIN;

CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE room_projects (
    project_id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_room_projects_status
        CHECK (status IN ('CREATED', 'IMAGE_UPLOADED', 'ANALYZED', 'PREFERENCES_READY', 'DESIGN_READY'))
);

CREATE TABLE room_images (
    image_id BIGSERIAL PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES room_projects(project_id) ON DELETE CASCADE,
    storage_key TEXT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_room_images_file_size CHECK (file_size_bytes > 0),
    CONSTRAINT chk_room_images_width CHECK (width IS NULL OR width > 0),
    CONSTRAINT chk_room_images_height CHECK (height IS NULL OR height > 0),
    CONSTRAINT chk_room_images_mime_type CHECK (mime_type IN ('image/jpeg', 'image/png'))
);

CREATE INDEX idx_room_images_project_id ON room_images(project_id);

CREATE TABLE detected_objects (
    object_id BIGSERIAL PRIMARY KEY,
    image_id BIGINT NOT NULL REFERENCES room_images(image_id) ON DELETE CASCADE,
    object_class VARCHAR(100) NOT NULL,
    confidence NUMERIC(6,5),
    x_min NUMERIC(8,6),
    y_min NUMERIC(8,6),
    x_max NUMERIC(8,6),
    y_max NUMERIC(8,6),
    source VARCHAR(20) NOT NULL DEFAULT 'CV',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_detected_objects_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT chk_detected_objects_x_min CHECK (x_min IS NULL OR (x_min >= 0 AND x_min <= 1)),
    CONSTRAINT chk_detected_objects_y_min CHECK (y_min IS NULL OR (y_min >= 0 AND y_min <= 1)),
    CONSTRAINT chk_detected_objects_x_max CHECK (x_max IS NULL OR (x_max >= 0 AND x_max <= 1)),
    CONSTRAINT chk_detected_objects_y_max CHECK (y_max IS NULL OR (y_max >= 0 AND y_max <= 1)),
    CONSTRAINT chk_detected_objects_bbox_x CHECK (x_min IS NULL OR x_max IS NULL OR x_min < x_max),
    CONSTRAINT chk_detected_objects_bbox_y CHECK (y_min IS NULL OR y_max IS NULL OR y_min < y_max),
    CONSTRAINT chk_detected_objects_source CHECK (source IN ('CV', 'MANUAL'))
);

CREATE INDEX idx_detected_objects_image_id ON detected_objects(image_id);

CREATE TABLE furniture_decisions (
    decision_id BIGSERIAL PRIMARY KEY,
    object_id BIGINT NOT NULL UNIQUE REFERENCES detected_objects(object_id) ON DELETE CASCADE,
    decision VARCHAR(20) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_furniture_decisions_decision CHECK (decision IN ('KEEP', 'REPLACE', 'REMOVE', 'UNSURE'))
);

CREATE TABLE room_preferences (
    preference_id BIGSERIAL PRIMARY KEY,
    project_id UUID NOT NULL UNIQUE REFERENCES room_projects(project_id) ON DELETE CASCADE,
    style VARCHAR(50) NOT NULL,
    budget_min NUMERIC(12,2) NOT NULL,
    budget_max NUMERIC(12,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'AUD',
    custom_requirements TEXT,
    rental_friendly BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_room_preferences_style CHECK (style IN ('MINIMALIST', 'SCANDINAVIAN', 'COZY', 'MODERN', 'NO_PREFERENCE')),
    CONSTRAINT chk_room_preferences_budget_min CHECK (budget_min >= 0),
    CONSTRAINT chk_room_preferences_budget_max CHECK (budget_max > 0),
    CONSTRAINT chk_room_preferences_budget_range CHECK (budget_max >= budget_min)
);

CREATE TABLE redesign_recommendations (
    redesign_id BIGSERIAL PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES room_projects(project_id) ON DELETE CASCADE,
    summary TEXT NOT NULL,
    estimated_total_cost NUMERIC(12,2),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    model_name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_redesign_recommendations_cost CHECK (estimated_total_cost IS NULL OR estimated_total_cost >= 0),
    CONSTRAINT chk_redesign_recommendations_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_redesign_recommendations_project_id ON redesign_recommendations(project_id);

CREATE TABLE furniture (
    furniture_id BIGSERIAL PRIMARY KEY,
    furniture_type VARCHAR(50) NOT NULL,
    name VARCHAR(255),
    style VARCHAR(100),
    color VARCHAR(100),
    material VARCHAR(100),
    width_cm NUMERIC(10,2),
    height_cm NUMERIC(10,2),
    depth_cm NUMERIC(10,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_furniture_type CHECK (furniture_type IN ('BED', 'CHAIR', 'DESK', 'SOFA', 'LAMP', 'SHELF')),
    CONSTRAINT chk_furniture_width CHECK (width_cm IS NULL OR width_cm > 0),
    CONSTRAINT chk_furniture_height CHECK (height_cm IS NULL OR height_cm > 0),
    CONSTRAINT chk_furniture_depth CHECK (depth_cm IS NULL OR depth_cm > 0)
);

CREATE INDEX idx_furniture_type ON furniture(furniture_type);

CREATE TABLE beds (
    furniture_id BIGINT PRIMARY KEY REFERENCES furniture(furniture_id) ON DELETE CASCADE,
    bed_size VARCHAR(50),
    has_headboard BOOLEAN
);

CREATE TABLE chairs (
    furniture_id BIGINT PRIMARY KEY REFERENCES furniture(furniture_id) ON DELETE CASCADE,
    seat_height_cm NUMERIC(10,2),
    upholstery VARCHAR(100),
    CONSTRAINT chk_chairs_seat_height CHECK (seat_height_cm IS NULL OR seat_height_cm > 0)
);

CREATE TABLE desks (
    furniture_id BIGINT PRIMARY KEY REFERENCES furniture(furniture_id) ON DELETE CASCADE,
    height_adjustable BOOLEAN,
    max_load_kg NUMERIC(10,2),
    CONSTRAINT chk_desks_max_load CHECK (max_load_kg IS NULL OR max_load_kg > 0)
);

CREATE TABLE sofas (
    furniture_id BIGINT PRIMARY KEY REFERENCES furniture(furniture_id) ON DELETE CASCADE,
    seat_count INTEGER,
    upholstery VARCHAR(100),
    CONSTRAINT chk_sofas_seat_count CHECK (seat_count IS NULL OR seat_count > 0)
);

CREATE TABLE lamps (
    furniture_id BIGINT PRIMARY KEY REFERENCES furniture(furniture_id) ON DELETE CASCADE,
    light_type VARCHAR(100),
    brightness_lumens INTEGER,
    color_temperature VARCHAR(100),
    CONSTRAINT chk_lamps_brightness CHECK (brightness_lumens IS NULL OR brightness_lumens > 0)
);

CREATE TABLE shelves (
    furniture_id BIGINT PRIMARY KEY REFERENCES furniture(furniture_id) ON DELETE CASCADE,
    shelf_count INTEGER,
    wall_mounted BOOLEAN,
    CONSTRAINT chk_shelves_count CHECK (shelf_count IS NULL OR shelf_count > 0)
);

CREATE TABLE redesign_items (
    redesign_item_id BIGSERIAL PRIMARY KEY,
    redesign_id BIGINT NOT NULL REFERENCES redesign_recommendations(redesign_id) ON DELETE CASCADE,
    existing_object_id BIGINT REFERENCES detected_objects(object_id) ON DELETE SET NULL,
    furniture_id BIGINT REFERENCES furniture(furniture_id) ON DELETE SET NULL,
    action VARCHAR(20) NOT NULL,
    target_location TEXT,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_redesign_items_action CHECK (action IN ('KEEP', 'REMOVE', 'MOVE', 'ADD', 'REPLACE')),
    CONSTRAINT chk_redesign_items_action_fields CHECK (
        (action = 'KEEP' AND existing_object_id IS NOT NULL AND furniture_id IS NULL)
        OR
        (action = 'REMOVE' AND existing_object_id IS NOT NULL AND furniture_id IS NULL)
        OR
        (action = 'MOVE' AND existing_object_id IS NOT NULL AND furniture_id IS NULL AND target_location IS NOT NULL)
        OR
        (action = 'ADD' AND existing_object_id IS NULL AND furniture_id IS NOT NULL)
        OR
        (action = 'REPLACE' AND existing_object_id IS NOT NULL AND furniture_id IS NOT NULL)
    )
);

CREATE INDEX idx_redesign_items_redesign_id ON redesign_items(redesign_id);
CREATE INDEX idx_redesign_items_existing_object_id ON redesign_items(existing_object_id);
CREATE INDEX idx_redesign_items_furniture_id ON redesign_items(furniture_id);

CREATE TABLE generated_designs (
    design_id BIGSERIAL PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES room_projects(project_id) ON DELETE CASCADE,
    redesign_id BIGINT NOT NULL REFERENCES redesign_recommendations(redesign_id) ON DELETE CASCADE,
    source_image_id BIGINT NOT NULL REFERENCES room_images(image_id) ON DELETE RESTRICT,
    storage_key TEXT,
    prompt TEXT,
    model_name VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_generated_designs_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_generated_designs_project_id ON generated_designs(project_id);
CREATE INDEX idx_generated_designs_redesign_id ON generated_designs(redesign_id);
CREATE INDEX idx_generated_designs_source_image_id ON generated_designs(source_image_id);

CREATE TABLE products (
    product_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    brand VARCHAR(255),
    description TEXT,
    width_cm NUMERIC(10,2),
    height_cm NUMERIC(10,2),
    depth_cm NUMERIC(10,2),
    image_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_products_width CHECK (width_cm IS NULL OR width_cm > 0),
    CONSTRAINT chk_products_height CHECK (height_cm IS NULL OR height_cm > 0),
    CONSTRAINT chk_products_depth CHECK (depth_cm IS NULL OR depth_cm > 0)
);

CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_active ON products(is_active);

CREATE TABLE furniture_recommendations (
    recommendation_id BIGSERIAL PRIMARY KEY,
    redesign_item_id BIGINT NOT NULL REFERENCES redesign_items(redesign_item_id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(product_id) ON DELETE RESTRICT,
    alternative_for_recommendation_id BIGINT REFERENCES furniture_recommendations(recommendation_id) ON DELETE SET NULL,
    recommendation_type VARCHAR(30) NOT NULL DEFAULT 'PRIMARY',
    recommendation_reason TEXT NOT NULL,
    is_selected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_furniture_recommendations_type CHECK (recommendation_type IN ('PRIMARY', 'CHEAPER_ALTERNATIVE')),
    CONSTRAINT chk_furniture_recommendations_not_self_alternative CHECK (
        alternative_for_recommendation_id IS NULL OR alternative_for_recommendation_id <> recommendation_id
    ),
    CONSTRAINT chk_furniture_recommendations_alternative_link CHECK (
        (recommendation_type = 'PRIMARY' AND alternative_for_recommendation_id IS NULL)
        OR
        (recommendation_type = 'CHEAPER_ALTERNATIVE' AND alternative_for_recommendation_id IS NOT NULL)
    )
);

CREATE INDEX idx_furniture_recommendations_redesign_item_id ON furniture_recommendations(redesign_item_id);
CREATE INDEX idx_furniture_recommendations_product_id ON furniture_recommendations(product_id);
CREATE INDEX idx_furniture_recommendations_alternative_id ON furniture_recommendations(alternative_for_recommendation_id);

CREATE TABLE diy_projects (
    diy_project_id BIGSERIAL PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES room_projects(project_id) ON DELETE CASCADE,
    recommendation_id BIGINT REFERENCES furniture_recommendations(recommendation_id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    estimated_cost NUMERIC(12,2),
    estimated_minutes INTEGER,
    difficulty VARCHAR(20) NOT NULL,
    tools JSONB,
    instructions JSONB NOT NULL,
    tutorial_video_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_diy_projects_cost CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    CONSTRAINT chk_diy_projects_minutes CHECK (estimated_minutes IS NULL OR estimated_minutes > 0),
    CONSTRAINT chk_diy_projects_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'))
);

CREATE INDEX idx_diy_projects_project_id ON diy_projects(project_id);
CREATE INDEX idx_diy_projects_recommendation_id ON diy_projects(recommendation_id);

CREATE TABLE diy_materials (
    material_id BIGSERIAL PRIMARY KEY,
    diy_project_id BIGINT NOT NULL REFERENCES diy_projects(diy_project_id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    quantity NUMERIC(12,3),
    unit VARCHAR(50),
    estimated_price NUMERIC(12,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_diy_materials_quantity CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT chk_diy_materials_price CHECK (estimated_price IS NULL OR estimated_price >= 0)
);

CREATE INDEX idx_diy_materials_diy_project_id ON diy_materials(diy_project_id);

CREATE TABLE shopping_lists (
    shopping_list_id BIGSERIAL PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES room_projects(project_id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shopping_lists_project_id ON shopping_lists(project_id);

CREATE TABLE shopping_list_items (
    shopping_list_item_id BIGSERIAL PRIMARY KEY,
    shopping_list_id BIGINT NOT NULL REFERENCES shopping_lists(shopping_list_id) ON DELETE CASCADE,
    recommendation_id BIGINT NOT NULL REFERENCES furniture_recommendations(recommendation_id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_shopping_list_items_quantity CHECK (quantity > 0),
    CONSTRAINT uq_shopping_list_items_list_recommendation UNIQUE (shopping_list_id, recommendation_id)
);

CREATE INDEX idx_shopping_list_items_shopping_list_id ON shopping_list_items(shopping_list_id);
CREATE INDEX idx_shopping_list_items_recommendation_id ON shopping_list_items(recommendation_id);

CREATE TABLE purchase_options (
    purchase_option_id BIGSERIAL PRIMARY KEY,
    shopping_list_item_id BIGINT NOT NULL REFERENCES shopping_list_items(shopping_list_item_id) ON DELETE CASCADE,
    retailer VARCHAR(255) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'AUD',
    purchase_url TEXT,
    store_name VARCHAR(255),
    store_address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_purchase_options_price CHECK (price >= 0),
    CONSTRAINT chk_purchase_options_location CHECK (
        purchase_url IS NOT NULL OR store_name IS NOT NULL OR store_address IS NOT NULL
    )
);

CREATE INDEX idx_purchase_options_shopping_list_item_id ON purchase_options(shopping_list_item_id);

COMMIT;