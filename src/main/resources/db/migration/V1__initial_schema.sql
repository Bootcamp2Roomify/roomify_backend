-- ============================================================
-- Roomify - Initial PostgreSQL Schema
-- Migration: V1__initial_schema.sql
-- ============================================================

-- ============================================================
-- 1. USERS
-- ============================================================

CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- 2. ROOM PROJECTS
-- ============================================================

CREATE TABLE room_projects (
    project_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,

    name VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_room_projects_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_room_project_status
        CHECK (
            status IN (
                'DRAFT',
                'ANALYZED',
                'REDESIGN_GENERATED',
                'COMPLETED'
            )
        )
);


-- ============================================================
-- 3. ROOM IMAGES
-- Actual image files are stored in object storage.
-- PostgreSQL stores only references and metadata.
-- ============================================================

CREATE TABLE room_images (
    image_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,

    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,

    width INTEGER,
    height INTEGER,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_room_images_project
        FOREIGN KEY (project_id)
        REFERENCES room_projects(project_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_room_image_file_size
        CHECK (file_size_bytes > 0),

    CONSTRAINT chk_room_image_width
        CHECK (width IS NULL OR width > 0),

    CONSTRAINT chk_room_image_height
        CHECK (height IS NULL OR height > 0),

    CONSTRAINT chk_room_image_mime_type
        CHECK (
            mime_type IN (
                'image/jpeg',
                'image/png'
            )
        )
);


-- ============================================================
-- 4. DETECTED OBJECTS
-- Stores CV results or manually added furniture.
-- Bounding box values are normalized to 0..1.
-- ============================================================

CREATE TABLE detected_objects (
    object_id BIGSERIAL PRIMARY KEY,
    image_id BIGINT NOT NULL,

    object_class VARCHAR(100) NOT NULL,
    confidence NUMERIC(5,4),

    x_min NUMERIC(6,5),
    y_min NUMERIC(6,5),
    x_max NUMERIC(6,5),
    y_max NUMERIC(6,5),

    source VARCHAR(20) NOT NULL DEFAULT 'CV',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_detected_objects_image
        FOREIGN KEY (image_id)
        REFERENCES room_images(image_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_detected_object_source
        CHECK (source IN ('CV', 'MANUAL')),

    CONSTRAINT chk_detected_object_confidence
        CHECK (
            confidence IS NULL
            OR (confidence >= 0 AND confidence <= 1)
        ),

    CONSTRAINT chk_detected_object_x_min
        CHECK (
            x_min IS NULL
            OR (x_min >= 0 AND x_min <= 1)
        ),

    CONSTRAINT chk_detected_object_y_min
        CHECK (
            y_min IS NULL
            OR (y_min >= 0 AND y_min <= 1)
        ),

    CONSTRAINT chk_detected_object_x_max
        CHECK (
            x_max IS NULL
            OR (x_max >= 0 AND x_max <= 1)
        ),

    CONSTRAINT chk_detected_object_y_max
        CHECK (
            y_max IS NULL
            OR (y_max >= 0 AND y_max <= 1)
        ),

    CONSTRAINT chk_detected_object_x_order
        CHECK (
            x_min IS NULL
            OR x_max IS NULL
            OR x_min < x_max
        ),

    CONSTRAINT chk_detected_object_y_order
        CHECK (
            y_min IS NULL
            OR y_max IS NULL
            OR y_min < y_max
        )
);


-- ============================================================
-- 5. FURNITURE DECISIONS
-- One detected object has at most one current decision.
-- ============================================================

CREATE TABLE furniture_decisions (
    decision_id BIGSERIAL PRIMARY KEY,
    object_id BIGINT NOT NULL UNIQUE,

    decision VARCHAR(20) NOT NULL,
    notes TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_furniture_decisions_object
        FOREIGN KEY (object_id)
        REFERENCES detected_objects(object_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_furniture_decision
        CHECK (
            decision IN (
                'KEEP',
                'REPLACE',
                'REMOVE',
                'UNSURE'
            )
        )
);


-- ============================================================
-- 6. ROOM PREFERENCES
-- One current preference record per project.
-- ============================================================

CREATE TABLE room_preferences (
    preference_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE,

    style VARCHAR(30) NOT NULL DEFAULT 'NO_PREFERENCE',

    budget_min NUMERIC(12,2) NOT NULL,
    budget_max NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'AUD',

    custom_requirements VARCHAR(2000),
    rental_friendly BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_room_preferences_project
        FOREIGN KEY (project_id)
        REFERENCES room_projects(project_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_room_preference_style
        CHECK (
            style IN (
                'MINIMALIST',
                'SCANDINAVIAN',
                'COZY',
                'MODERN',
                'NO_PREFERENCE'
            )
        ),

    CONSTRAINT chk_room_preference_budget_min
        CHECK (budget_min >= 0),

    CONSTRAINT chk_room_preference_budget_max
        CHECK (budget_max > 0),

    CONSTRAINT chk_room_preference_budget_range
        CHECK (budget_max >= budget_min)
);


-- ============================================================
-- 7. REDESIGN RECOMMENDATIONS
-- Stores textual/structured redesign plans.
-- ============================================================

CREATE TABLE redesign_recommendations (
    redesign_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,

    summary TEXT NOT NULL,
    recommendation_data JSONB,
    estimated_total_cost NUMERIC(12,2),

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    model_name VARCHAR(100),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_redesign_recommendations_project
        FOREIGN KEY (project_id)
        REFERENCES room_projects(project_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_redesign_estimated_cost
        CHECK (
            estimated_total_cost IS NULL
            OR estimated_total_cost >= 0
        ),

    CONSTRAINT chk_redesign_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'COMPLETED',
                'FAILED'
            )
        )
);


-- ============================================================
-- 8. GENERATED DESIGNS
-- Actual generated images are stored in object storage.
-- ============================================================

CREATE TABLE generated_designs (
    design_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    redesign_id BIGINT NOT NULL,
    source_image_id BIGINT NOT NULL,

    storage_key VARCHAR(500),
    prompt TEXT,
    model_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_generated_designs_project
        FOREIGN KEY (project_id)
        REFERENCES room_projects(project_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_generated_designs_redesign
        FOREIGN KEY (redesign_id)
        REFERENCES redesign_recommendations(redesign_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_generated_designs_source_image
        FOREIGN KEY (source_image_id)
        REFERENCES room_images(image_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_generated_design_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'COMPLETED',
                'FAILED'
            )
        )
);


-- ============================================================
-- 9. PRODUCTS
-- Curated Roomify furniture/product catalogue.
-- Retailer-specific price and buying details are stored in
-- purchase_options rather than duplicated here.
-- ============================================================

CREATE TABLE products (
    product_id BIGSERIAL PRIMARY KEY,

    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    brand VARCHAR(150),
    description TEXT,

    width_cm NUMERIC(10,2),
    height_cm NUMERIC(10,2),
    depth_cm NUMERIC(10,2),

    image_url VARCHAR(1000),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_product_width
        CHECK (width_cm IS NULL OR width_cm > 0),

    CONSTRAINT chk_product_height
        CHECK (height_cm IS NULL OR height_cm > 0),

    CONSTRAINT chk_product_depth
        CHECK (depth_cm IS NULL OR depth_cm > 0)
);


-- ============================================================
-- 10. FURNITURE RECOMMENDATIONS
-- Can represent either a primary recommendation or a cheaper
-- alternative to another recommendation.
-- ============================================================

CREATE TABLE furniture_recommendations (
    recommendation_id BIGSERIAL PRIMARY KEY,

    project_id BIGINT NOT NULL,
    redesign_id BIGINT,
    product_id BIGINT NOT NULL,
    object_id BIGINT,

    alternative_for_recommendation_id BIGINT,

    recommendation_type VARCHAR(30) NOT NULL DEFAULT 'PRIMARY',
    recommendation_reason TEXT NOT NULL,
    is_selected BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_furniture_recommendations_project
        FOREIGN KEY (project_id)
        REFERENCES room_projects(project_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_furniture_recommendations_redesign
        FOREIGN KEY (redesign_id)
        REFERENCES redesign_recommendations(redesign_id)
        ON DELETE SET NULL,

    CONSTRAINT fk_furniture_recommendations_product
        FOREIGN KEY (product_id)
        REFERENCES products(product_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_furniture_recommendations_object
        FOREIGN KEY (object_id)
        REFERENCES detected_objects(object_id)
        ON DELETE SET NULL,

    CONSTRAINT fk_furniture_recommendations_alternative
        FOREIGN KEY (alternative_for_recommendation_id)
        REFERENCES furniture_recommendations(recommendation_id)
        ON DELETE SET NULL,

    CONSTRAINT chk_furniture_recommendation_type
        CHECK (
            recommendation_type IN (
                'PRIMARY',
                'CHEAPER_ALTERNATIVE'
            )
        )
);


-- ============================================================
-- 11. DIY PROJECTS
-- ============================================================

CREATE TABLE diy_projects (
    diy_project_id BIGSERIAL PRIMARY KEY,

    project_id BIGINT NOT NULL,
    recommendation_id BIGINT,

    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,

    estimated_cost NUMERIC(12,2),
    estimated_minutes INTEGER,

    difficulty VARCHAR(20) NOT NULL,

    tools JSONB,
    instructions JSONB NOT NULL,
    tutorial_video_url VARCHAR(1000),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_diy_projects_project
        FOREIGN KEY (project_id)
        REFERENCES room_projects(project_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_diy_projects_recommendation
        FOREIGN KEY (recommendation_id)
        REFERENCES furniture_recommendations(recommendation_id)
        ON DELETE SET NULL,

    CONSTRAINT chk_diy_project_estimated_cost
        CHECK (
            estimated_cost IS NULL
            OR estimated_cost >= 0
        ),

    CONSTRAINT chk_diy_project_estimated_minutes
        CHECK (
            estimated_minutes IS NULL
            OR estimated_minutes > 0
        ),

    CONSTRAINT chk_diy_project_difficulty
        CHECK (
            difficulty IN (
                'EASY',
                'MEDIUM',
                'HARD'
            )
        )
);


-- ============================================================
-- 12. DIY MATERIALS
-- ============================================================

CREATE TABLE diy_materials (
    material_id BIGSERIAL PRIMARY KEY,
    diy_project_id BIGINT NOT NULL,

    name VARCHAR(255) NOT NULL,
    quantity NUMERIC(10,2),
    unit VARCHAR(50),
    estimated_price NUMERIC(12,2),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_diy_materials_project
        FOREIGN KEY (diy_project_id)
        REFERENCES diy_projects(diy_project_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_diy_material_quantity
        CHECK (
            quantity IS NULL
            OR quantity > 0
        ),

    CONSTRAINT chk_diy_material_estimated_price
        CHECK (
            estimated_price IS NULL
            OR estimated_price >= 0
        )
);


-- ============================================================
-- 13. SHOPPING LISTS
-- ============================================================

CREATE TABLE shopping_lists (
    shopping_list_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,

    name VARCHAR(255) NOT NULL DEFAULT 'Shopping List',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_shopping_lists_project
        FOREIGN KEY (project_id)
        REFERENCES room_projects(project_id)
        ON DELETE CASCADE
);


-- ============================================================
-- 14. SHOPPING LIST ITEMS
-- Represents a furniture recommendation added to a shopping list.
-- ============================================================

CREATE TABLE shopping_list_items (
    shopping_list_item_id BIGSERIAL PRIMARY KEY,

    shopping_list_id BIGINT NOT NULL,
    recommendation_id BIGINT NOT NULL,

    quantity INTEGER NOT NULL DEFAULT 1,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_shopping_list_items_list
        FOREIGN KEY (shopping_list_id)
        REFERENCES shopping_lists(shopping_list_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_shopping_list_items_recommendation
        FOREIGN KEY (recommendation_id)
        REFERENCES furniture_recommendations(recommendation_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_shopping_list_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT uq_shopping_list_item_recommendation
        UNIQUE (shopping_list_id, recommendation_id)
);


-- ============================================================
-- 15. PURCHASE OPTIONS
-- Stores possible online or physical-store purchase sources
-- for a specific shopping-list item.
-- ============================================================

CREATE TABLE purchase_options (
    purchase_option_id BIGSERIAL PRIMARY KEY,
    shopping_list_item_id BIGINT NOT NULL,

    retailer VARCHAR(255) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'AUD',

    purchase_url VARCHAR(1000),
    store_name VARCHAR(255),
    store_address VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_purchase_options_shopping_list_item
        FOREIGN KEY (shopping_list_item_id)
        REFERENCES shopping_list_items(shopping_list_item_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_purchase_option_price
        CHECK (price >= 0),

    CONSTRAINT chk_purchase_option_destination
        CHECK (
            purchase_url IS NOT NULL
            OR store_name IS NOT NULL
            OR store_address IS NOT NULL
        )
);


-- ============================================================
-- INDEXES
-- PostgreSQL does not automatically create indexes for all FKs.
-- ============================================================

CREATE INDEX idx_room_projects_user_id
    ON room_projects(user_id);

CREATE INDEX idx_room_images_project_id
    ON room_images(project_id);

CREATE INDEX idx_detected_objects_image_id
    ON detected_objects(image_id);

CREATE INDEX idx_redesign_recommendations_project_id
    ON redesign_recommendations(project_id);

CREATE INDEX idx_generated_designs_project_id
    ON generated_designs(project_id);

CREATE INDEX idx_generated_designs_redesign_id
    ON generated_designs(redesign_id);

CREATE INDEX idx_generated_designs_source_image_id
    ON generated_designs(source_image_id);

CREATE INDEX idx_furniture_recommendations_project_id
    ON furniture_recommendations(project_id);

CREATE INDEX idx_furniture_recommendations_redesign_id
    ON furniture_recommendations(redesign_id);

CREATE INDEX idx_furniture_recommendations_product_id
    ON furniture_recommendations(product_id);

CREATE INDEX idx_furniture_recommendations_object_id
    ON furniture_recommendations(object_id);

CREATE INDEX idx_furniture_recommendations_alternative_id
    ON furniture_recommendations(alternative_for_recommendation_id);

CREATE INDEX idx_diy_projects_project_id
    ON diy_projects(project_id);

CREATE INDEX idx_diy_projects_recommendation_id
    ON diy_projects(recommendation_id);

CREATE INDEX idx_diy_materials_project_id
    ON diy_materials(diy_project_id);

CREATE INDEX idx_shopping_lists_project_id
    ON shopping_lists(project_id);

CREATE INDEX idx_shopping_list_items_list_id
    ON shopping_list_items(shopping_list_id);

CREATE INDEX idx_shopping_list_items_recommendation_id
    ON shopping_list_items(recommendation_id);

CREATE INDEX idx_purchase_options_shopping_list_item_id
    ON purchase_options(shopping_list_item_id);
