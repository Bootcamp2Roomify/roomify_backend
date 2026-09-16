erDiagram
    USER {
        int user_id PK
        string email UK
        string password_hash
        string display_name
        datetime created_at
        datetime updated_at
    }
    ROOM_PROJECT {
        int project_id PK
        int user_id FK
        string name
        string status
        datetime created_at
        datetime updated_at
    }
    ROOM_IMAGE {
        int image_id PK
        int project_id FK
        string storage_key
        string original_filename
        string mime_type
        bigint file_size_bytes
        int width
        int height
        datetime created_at
    }
    DETECTED_OBJECT {
        int object_id PK
        int image_id FK
        string object_class
        decimal confidence
        decimal x_min
        decimal y_min
        decimal x_max
        decimal y_max
        string source
        datetime created_at
        datetime updated_at
    }
    FURNITURE_DECISION {
        int decision_id PK
        int object_id FK,UK
        string decision
        string notes
        datetime created_at
        datetime updated_at
    }
    ROOM_PREFERENCE {
        int preference_id PK
        int project_id FK,UK
        string style
        decimal budget_min
        decimal budget_max
        string currency
        string custom_requirements
        boolean rental_friendly
        datetime created_at
        datetime updated_at
    }
    REDESIGN_RECOMMENDATION {
        int redesign_id PK
        int project_id FK
        string summary
        decimal estimated_total_cost
        string status
        string model_name
        datetime created_at
        datetime updated_at
    }
    FURNITURE {
        int furniture_id PK
        string furniture_type
        string name
        string style
        string color
        string material
        decimal width_cm
        decimal height_cm
        decimal depth_cm
        datetime created_at
        datetime updated_at
    }
    BED {
        int furniture_id PK,FK
        string bed_size
        boolean has_headboard
    }
    CHAIR {
        int furniture_id PK,FK
        decimal seat_height_cm
        string upholstery
    }
    DESK {
        int furniture_id PK,FK
        boolean height_adjustable
        decimal max_load_kg
    }
    SOFA {
        int furniture_id PK,FK
        int seat_count
        string upholstery
    }
    LAMP {
        int furniture_id PK,FK
        string light_type
        int brightness_lumens
        string color_temperature
    }
    SHELF {
        int furniture_id PK,FK
        int shelf_count
        boolean wall_mounted
    }
    REDESIGN_ITEM {
        int redesign_item_id PK
        int redesign_id FK
        int existing_object_id FK
        int furniture_id FK
        string action
        string target_location
        string reason
        datetime created_at
        datetime updated_at
    }
    GENERATED_DESIGN {
        int design_id PK
        int project_id FK
        int redesign_id FK
        int source_image_id FK
        string storage_key
        string prompt
        string model_name
        string status
        string error_message
        datetime created_at
    }
    PRODUCT {
        int product_id PK
        string name
        string category
        string brand
        string description
        decimal width_cm
        decimal height_cm
        decimal depth_cm
        string image_url
        boolean is_active
        datetime created_at
        datetime updated_at
    }
    FURNITURE_RECOMMENDATION {
        int recommendation_id PK
        int redesign_item_id FK
        int product_id FK
        int alternative_for_recommendation_id FK
        string recommendation_type
        string recommendation_reason
        boolean is_selected
        datetime created_at
        datetime updated_at
    }
    DIY_PROJECT {
        int diy_project_id PK
        int project_id FK
        int recommendation_id FK
        string title
        string description
        decimal estimated_cost
        int estimated_minutes
        string difficulty
        json tools
        json instructions
        string tutorial_video_url
        datetime created_at
        datetime updated_at
    }
    DIY_MATERIAL {
        int material_id PK
        int diy_project_id FK
        string name
        decimal quantity
        string unit
        decimal estimated_price
        datetime created_at
    }
    SHOPPING_LIST {
        int shopping_list_id PK
        int project_id FK
        string name
        datetime created_at
        datetime updated_at
    }
    SHOPPING_LIST_ITEM {
        int shopping_list_item_id PK
        int shopping_list_id FK
        int recommendation_id FK
        int quantity
        datetime created_at
    }
    PURCHASE_OPTION {
        int purchase_option_id PK
        int shopping_list_item_id FK
        string retailer
        decimal price
        string currency
        string purchase_url
        string store_name
        string store_address
        datetime created_at
    }
    USER ||--o{ ROOM_PROJECT : "owns"
    ROOM_PROJECT ||--o{ ROOM_IMAGE : "contains"
    ROOM_IMAGE ||--o{ DETECTED_OBJECT : "has"
    DETECTED_OBJECT ||--o| FURNITURE_DECISION : "has decision"
    ROOM_PROJECT ||--o| ROOM_PREFERENCE : "has"
    ROOM_PROJECT ||--o{ REDESIGN_RECOMMENDATION : "generates"
    REDESIGN_RECOMMENDATION ||--o{ REDESIGN_ITEM : "contains"
    DETECTED_OBJECT ||--o{ REDESIGN_ITEM : "existing object"
    FURNITURE ||--o{ REDESIGN_ITEM : "desired furniture"
    FURNITURE ||--o| BED : "may be"
    FURNITURE ||--o| CHAIR : "may be"
    FURNITURE ||--o| DESK : "may be"
    FURNITURE ||--o| SOFA : "may be"
    FURNITURE ||--o| LAMP : "may be"
    FURNITURE ||--o| SHELF : "may be"
    ROOM_PROJECT ||--o{ GENERATED_DESIGN : "has"
    REDESIGN_RECOMMENDATION ||--o{ GENERATED_DESIGN : "produces"
    ROOM_IMAGE ||--o{ GENERATED_DESIGN : "used as source"
    REDESIGN_ITEM ||--o{ FURNITURE_RECOMMENDATION : "receives recommendations"
    PRODUCT ||--o{ FURNITURE_RECOMMENDATION : "recommended as"
    FURNITURE_RECOMMENDATION ||--o{ FURNITURE_RECOMMENDATION : "has cheaper alternative"
    ROOM_PROJECT ||--o{ DIY_PROJECT : "has"
    FURNITURE_RECOMMENDATION ||--o{ DIY_PROJECT : "may have DIY alternative"
    DIY_PROJECT ||--o{ DIY_MATERIAL : "requires"
    ROOM_PROJECT ||--o{ SHOPPING_LIST : "has"
    SHOPPING_LIST ||--o{ SHOPPING_LIST_ITEM : "contains"
    FURNITURE_RECOMMENDATION ||--o{ SHOPPING_LIST_ITEM : "added to"
    SHOPPING_LIST_ITEM ||--o{ PURCHASE_OPTION : "has purchase options"