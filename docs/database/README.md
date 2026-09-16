# Roomify Database Design

## 1. Overview
Roomify uses PostgreSQL as its relational database.
The schema supports the MVP workflow from room-image upload and computer-vision detection through user furniture decisions, room preferences, AI redesign recommendations, reusable furniture specifications, generated room designs, product recommendations, DIY alternatives, shopping lists, and purchase options.
Room images and generated images are **not stored directly in PostgreSQL as binary blobs**. The actual files are stored in external object storage, while PostgreSQL stores references and metadata such as storage keys, filenames, MIME types, dimensions, and file sizes.
The redesign architecture stores important redesign data relationally instead of placing the whole AI response inside a single JSON field. In particular:
- `REDESIGN_RECOMMENDATION` stores the high-level redesign plan.
- `REDESIGN_ITEM` stores each action in the redesign.
- `FURNITURE` stores reusable desired-furniture specifications.
- Furniture subtype tables store type-specific attributes.
- `PRODUCT` stores real catalogue products.
- `FURNITURE_RECOMMENDATION` connects redesign needs to actual products.

## 2. Database Files
```text
roomify_backend/
├── docs/
│   └── database/
│       ├── README.md
│       ├── roomify-erd.md
│       └── roomify-erd.png
└── src/
    └── main/
        └── resources/
            └── db/
                └── migration/
                    └── V1__initial_schema.sql
```
- `README.md`: Documents the database design and design decisions.
- `roomify-erd.md`: Contains the Mermaid ERD source.
- `roomify-erd.png`: Rendered ERD for documentation.
- `V1__initial_schema.sql`: Initial PostgreSQL database migration.

## 3. Entity Overview
The current ERD contains the following entities:
1. `USER`
2. `ROOM_PROJECT`
3. `ROOM_IMAGE`
4. `DETECTED_OBJECT`
5. `FURNITURE_DECISION`
6. `ROOM_PREFERENCE`
7. `REDESIGN_RECOMMENDATION`
8. `FURNITURE`
9. `BED`
10. `CHAIR`
11. `DESK`
12. `SOFA`
13. `LAMP`
14. `SHELF`
15. `REDESIGN_ITEM`
16. `GENERATED_DESIGN`
17. `PRODUCT`
18. `FURNITURE_RECOMMENDATION`
19. `DIY_PROJECT`
20. `DIY_MATERIAL`
21. `SHOPPING_LIST`
22. `SHOPPING_LIST_ITEM`
23. `PURCHASE_OPTION`

## 4. Entity Definitions

### 4.1 USER
**Purpose:** Stores registered Roomify users.

**Primary Key**
- `user_id`

**Foreign Keys**
- None

**Required Fields**
- `email`
- `password_hash`
- `created_at`
- `updated_at`

**Optional Fields**
- `display_name`

**Constraints**
- `email` must be unique.
- `email` cannot be null.
- Passwords must never be stored in plain text.
- `password_hash` stores the securely hashed password.

**Relationships**
```text
USER 1:N ROOM_PROJECT
```
One user may own multiple room projects.

### 4.2 ROOM_PROJECT
**Purpose:** Represents one room redesign project belonging to a user.

**Primary Key**
- `project_id`

**Foreign Keys**
- `user_id` → `USER.user_id`

**Required Fields**
- `user_id`
- `name`
- `status`
- `created_at`
- `updated_at`

**Optional Fields**
- None for the initial MVP.

**Suggested Status Values**
```text
DRAFT
ANALYZED
REDESIGN_GENERATED
COMPLETED
```

**Relationships**
```text
USER 1:N ROOM_PROJECT
ROOM_PROJECT 1:N ROOM_IMAGE
ROOM_PROJECT 1:0..1 ROOM_PREFERENCE
ROOM_PROJECT 1:N REDESIGN_RECOMMENDATION
ROOM_PROJECT 1:N GENERATED_DESIGN
ROOM_PROJECT 1:N DIY_PROJECT
ROOM_PROJECT 1:N SHOPPING_LIST
```

### 4.3 ROOM_IMAGE
**Purpose:** Stores metadata and object-storage references for uploaded room images. The actual image is not stored inside PostgreSQL.

**Primary Key**
- `image_id`

**Foreign Keys**
- `project_id` → `ROOM_PROJECT.project_id`

**Required Fields**
- `project_id`
- `storage_key`
- `original_filename`
- `mime_type`
- `file_size_bytes`
- `created_at`

**Optional Fields**
- `width`
- `height`

**Constraints**
- `file_size_bytes > 0`
- `width > 0` when provided.
- `height > 0` when provided.
- MVP-supported MIME types should include `image/jpeg` and `image/png`.

**Example**
```text
storage_key = rooms/15/original-room.jpg
original_filename = bedroom.jpg
mime_type = image/jpeg
file_size_bytes = 2458291
width = 1920
height = 1080
```

**Relationships**
```text
ROOM_PROJECT 1:N ROOM_IMAGE
ROOM_IMAGE 1:N DETECTED_OBJECT
ROOM_IMAGE 1:N GENERATED_DESIGN
```

### 4.4 DETECTED_OBJECT
**Purpose:** Stores furniture or room objects detected in an uploaded image by the Computer Vision service. It can also represent objects manually added by the user.

**Primary Key**
- `object_id`

**Foreign Keys**
- `image_id` → `ROOM_IMAGE.image_id`

**Required Fields**
- `image_id`
- `object_class`
- `source`
- `created_at`
- `updated_at`

**Optional Fields**
- `confidence`
- `x_min`
- `y_min`
- `x_max`
- `y_max`

**Computer Vision Fields**
```text
object_class
confidence
x_min
y_min
x_max
y_max
```

Example:
```text
object_class = chair
confidence = 0.94
x_min = 0.20
y_min = 0.35
x_max = 0.42
y_max = 0.80
source = CV
```

**Suggested Source Values**
```text
CV
MANUAL
```

**Constraints**
```text
0 <= confidence <= 1
0 <= x_min <= 1
0 <= y_min <= 1
0 <= x_max <= 1
0 <= y_max <= 1
x_min < x_max
y_min < y_max
```
Bounding-box constraints apply when coordinates are provided.

**Relationships**
```text
ROOM_IMAGE 1:N DETECTED_OBJECT
DETECTED_OBJECT 1:0..1 FURNITURE_DECISION
DETECTED_OBJECT 1:N REDESIGN_ITEM
```

### 4.5 FURNITURE_DECISION
**Purpose:** Stores the user's decision about an existing detected furniture object.

**Primary Key**
- `decision_id`

**Foreign Keys**
- `object_id` → `DETECTED_OBJECT.object_id`

**Required Fields**
- `object_id`
- `decision`
- `created_at`
- `updated_at`

**Optional Fields**
- `notes`

**Supported Decisions**
```text
KEEP
REPLACE
REMOVE
UNSURE
```

**Constraints**
- `object_id` must be unique in this table.
- One detected object can have at most one current furniture decision.
- `decision` must contain one of the supported values.

**Relationships**
```text
DETECTED_OBJECT 1:0..1 FURNITURE_DECISION
```

### 4.6 ROOM_PREFERENCE
**Purpose:** Stores the user's current redesign preferences for a room project.

**Primary Key**
- `preference_id`

**Foreign Keys**
- `project_id` → `ROOM_PROJECT.project_id`

**Required Fields**
- `project_id`
- `style`
- `budget_min`
- `budget_max`
- `currency`
- `rental_friendly`
- `created_at`
- `updated_at`

**Optional Fields**
- `custom_requirements`

**Suggested Style Values**
```text
MINIMALIST
SCANDINAVIAN
COZY
MODERN
NO_PREFERENCE
```

**Constraints**
```text
project_id UNIQUE
budget_min >= 0
budget_max > 0
budget_max >= budget_min
```

**Relationships**
```text
ROOM_PROJECT 1:0..1 ROOM_PREFERENCE
```

### 4.7 REDESIGN_RECOMMENDATION
**Purpose:** Stores one high-level AI-generated redesign plan for a room project. Detailed redesign actions are stored relationally in `REDESIGN_ITEM`.

**Primary Key**
- `redesign_id`

**Foreign Keys**
- `project_id` → `ROOM_PROJECT.project_id`

**Required Fields**
- `project_id`
- `summary`
- `status`
- `created_at`
- `updated_at`

**Optional Fields**
- `estimated_total_cost`
- `model_name`

**Suggested Status Values**
```text
PENDING
PROCESSING
COMPLETED
FAILED
```

**Important Design Decision**
The previous `recommendation_data` JSON field is no longer used as the source of truth for redesign details. Actions such as adding, removing, moving, keeping, and replacing furniture are represented through `REDESIGN_ITEM` and reusable `FURNITURE` records.

**Relationships**
```text
ROOM_PROJECT 1:N REDESIGN_RECOMMENDATION
REDESIGN_RECOMMENDATION 1:N REDESIGN_ITEM
REDESIGN_RECOMMENDATION 1:N GENERATED_DESIGN
```

### 4.8 FURNITURE
**Purpose:** Stores reusable desired-furniture specifications used by AI redesigns.

`FURNITURE` represents what the redesign wants, not necessarily a specific real-world product.

Example:
```text
furniture_type = SHELF
name = Minimalist Wall Shelf
style = MINIMALIST
color = WHITE
material = WOOD
width_cm = 120
height_cm = 180
depth_cm = 30
```

**Primary Key**
- `furniture_id`

**Foreign Keys**
- None

**Required Fields**
- `furniture_type`
- `created_at`
- `updated_at`

**Optional Fields**
- `name`
- `style`
- `color`
- `material`
- `width_cm`
- `height_cm`
- `depth_cm`

**Constraints**
- Dimensions must be greater than zero when provided.
- `furniture_type` should match a supported furniture category.

**Relationships**
```text
FURNITURE 1:N REDESIGN_ITEM
FURNITURE 1:0..1 BED
FURNITURE 1:0..1 CHAIR
FURNITURE 1:0..1 DESK
FURNITURE 1:0..1 SOFA
FURNITURE 1:0..1 LAMP
FURNITURE 1:0..1 SHELF
```

### 4.9 Furniture Inheritance
`FURNITURE` is the parent entity for reusable furniture specifications. Common attributes are stored once in the parent table, while type-specific attributes are stored in child tables.

Conceptually:
```text
FURNITURE
├── BED
├── CHAIR
├── DESK
├── SOFA
├── LAMP
└── SHELF
```

This follows a joined-table inheritance style:
- Parent table: common fields such as style, color, material, and dimensions.
- Child table: fields that only apply to a specific furniture type.
- The child table's `furniture_id` is both its primary key and a foreign key to `FURNITURE.furniture_id`.

This design avoids repeatedly storing shared furniture fields in every subtype table.

#### 4.9.1 BED
**Primary Key / Foreign Key**
- `furniture_id` → `FURNITURE.furniture_id`

**Subtype-Specific Fields**
- `bed_size`
- `has_headboard`

#### 4.9.2 CHAIR
**Primary Key / Foreign Key**
- `furniture_id` → `FURNITURE.furniture_id`

**Subtype-Specific Fields**
- `seat_height_cm`
- `upholstery`

#### 4.9.3 DESK
**Primary Key / Foreign Key**
- `furniture_id` → `FURNITURE.furniture_id`

**Subtype-Specific Fields**
- `height_adjustable`
- `max_load_kg`

#### 4.9.4 SOFA
**Primary Key / Foreign Key**
- `furniture_id` → `FURNITURE.furniture_id`

**Subtype-Specific Fields**
- `seat_count`
- `upholstery`

#### 4.9.5 LAMP
**Primary Key / Foreign Key**
- `furniture_id` → `FURNITURE.furniture_id`

**Subtype-Specific Fields**
- `light_type`
- `brightness_lumens`
- `color_temperature`

#### 4.9.6 SHELF
**Primary Key / Foreign Key**
- `furniture_id` → `FURNITURE.furniture_id`

**Subtype-Specific Fields**
- `shelf_count`
- `wall_mounted`

### 4.10 REDESIGN_ITEM
**Purpose:** Represents one action within a redesign recommendation.

**Primary Key**
- `redesign_item_id`

**Foreign Keys**
- `redesign_id` → `REDESIGN_RECOMMENDATION.redesign_id`
- `existing_object_id` → `DETECTED_OBJECT.object_id`
- `furniture_id` → `FURNITURE.furniture_id`

**Required Fields**
- `redesign_id`
- `action`
- `reason`
- `created_at`
- `updated_at`

**Conditionally Required Fields**
- `existing_object_id`
- `furniture_id`
- `target_location`

**Supported Actions**
```text
KEEP
REMOVE
MOVE
ADD
REPLACE
```

**Action Rules**
```text
KEEP:
existing_object_id required

REMOVE:
existing_object_id required

MOVE:
existing_object_id required
target_location required

ADD:
furniture_id required

REPLACE:
existing_object_id required
furniture_id required
```

Examples:
```text
KEEP existing bed
REMOVE old chair
MOVE desk near the window
ADD white minimalist shelf
REPLACE existing desk with Scandinavian light-oak desk
```

**Relationships**
```text
REDESIGN_RECOMMENDATION 1:N REDESIGN_ITEM
DETECTED_OBJECT 1:N REDESIGN_ITEM
FURNITURE 1:N REDESIGN_ITEM
REDESIGN_ITEM 1:N FURNITURE_RECOMMENDATION
```

### 4.11 GENERATED_DESIGN
**Purpose:** Stores metadata for AI-generated visual redesign prototypes. The generated image itself is stored in object storage.

**Primary Key**
- `design_id`

**Foreign Keys**
- `project_id` → `ROOM_PROJECT.project_id`
- `redesign_id` → `REDESIGN_RECOMMENDATION.redesign_id`
- `source_image_id` → `ROOM_IMAGE.image_id`

**Required Fields**
- `project_id`
- `redesign_id`
- `source_image_id`
- `status`
- `created_at`

**Optional Fields**
- `storage_key`
- `prompt`
- `model_name`
- `error_message`

**Suggested Status Values**
```text
PENDING
PROCESSING
COMPLETED
FAILED
```

**Relationships**
```text
ROOM_PROJECT 1:N GENERATED_DESIGN
REDESIGN_RECOMMENDATION 1:N GENERATED_DESIGN
ROOM_IMAGE 1:N GENERATED_DESIGN
```

`project_id` keeps project ownership explicit, while `source_image_id` identifies the original room image used as visual context.

### 4.12 PRODUCT
**Purpose:** Stores actual furniture or decoration products from Roomify's curated product catalogue.

`PRODUCT` is deliberately separate from `FURNITURE`:
- `FURNITURE` represents a desired design specification.
- `PRODUCT` represents a real catalogue product.
- Matching between them happens through the redesign and recommendation flow.

**Primary Key**
- `product_id`

**Foreign Keys**
- None

**Required Fields**
- `name`
- `category`
- `created_at`
- `updated_at`

**Optional Fields**
- `brand`
- `description`
- `width_cm`
- `height_cm`
- `depth_cm`
- `image_url`
- `is_active`

**Constraints**
- Dimensions must be greater than zero when provided.

**Example**
```text
name = KALLAX Shelf Unit
category = SHELF
brand = IKEA
width_cm = 77
height_cm = 147
depth_cm = 39
```

**Relationships**
```text
PRODUCT 1:N FURNITURE_RECOMMENDATION
```

### 4.13 FURNITURE_RECOMMENDATION
**Purpose:** Connects a redesign requirement to an actual product from the catalogue.

This table differs from `FURNITURE_DECISION`:
- `FURNITURE_DECISION` stores the user's choice for existing room furniture.
- `FURNITURE_RECOMMENDATION` stores products Roomify recommends for a redesign item.

**Primary Key**
- `recommendation_id`

**Foreign Keys**
- `redesign_item_id` → `REDESIGN_ITEM.redesign_item_id`
- `product_id` → `PRODUCT.product_id`
- `alternative_for_recommendation_id` → `FURNITURE_RECOMMENDATION.recommendation_id`

**Required Fields**
- `redesign_item_id`
- `product_id`
- `recommendation_type`
- `recommendation_reason`
- `created_at`
- `updated_at`

**Optional Fields**
- `alternative_for_recommendation_id`
- `is_selected`

**Suggested Recommendation Types**
```text
PRIMARY
CHEAPER_ALTERNATIVE
```

**Cheaper Alternative Example**
```text
Recommendation 10:
Primary desk recommendation

Recommendation 11:
Cheaper desk recommendation
alternative_for_recommendation_id = 10
```

**Relationships**
```text
REDESIGN_ITEM 1:N FURNITURE_RECOMMENDATION
PRODUCT 1:N FURNITURE_RECOMMENDATION
FURNITURE_RECOMMENDATION 1:N FURNITURE_RECOMMENDATION
FURNITURE_RECOMMENDATION 1:N DIY_PROJECT
FURNITURE_RECOMMENDATION 1:N SHOPPING_LIST_ITEM
```

### 4.14 DIY_PROJECT
**Purpose:** Stores DIY alternatives associated with furniture recommendations.

**Primary Key**
- `diy_project_id`

**Foreign Keys**
- `project_id` → `ROOM_PROJECT.project_id`
- `recommendation_id` → `FURNITURE_RECOMMENDATION.recommendation_id`

**Required Fields**
- `project_id`
- `title`
- `description`
- `difficulty`
- `instructions`
- `created_at`
- `updated_at`

**Optional Fields**
- `recommendation_id`
- `estimated_cost`
- `estimated_minutes`
- `tools`
- `tutorial_video_url`

**Suggested Difficulty Values**
```text
EASY
MEDIUM
HARD
```

**JSON Usage**
`tools` and `instructions` may use PostgreSQL `JSONB` because they are variable-length document-like data rather than core relational objects.

Example:
```json
[
  {
    "step": 1,
    "instruction": "Measure the available wall space."
  },
  {
    "step": 2,
    "instruction": "Cut the wooden board to size."
  }
]
```

**Relationships**
```text
ROOM_PROJECT 1:N DIY_PROJECT
FURNITURE_RECOMMENDATION 1:N DIY_PROJECT
DIY_PROJECT 1:N DIY_MATERIAL
```

### 4.15 DIY_MATERIAL
**Purpose:** Stores materials required for a DIY project.

**Primary Key**
- `material_id`

**Foreign Keys**
- `diy_project_id` → `DIY_PROJECT.diy_project_id`

**Required Fields**
- `diy_project_id`
- `name`
- `created_at`

**Optional Fields**
- `quantity`
- `unit`
- `estimated_price`

**Constraints**
```text
quantity > 0 when provided
estimated_price >= 0 when provided
```

**Relationships**
```text
DIY_PROJECT 1:N DIY_MATERIAL
```

### 4.16 SHOPPING_LIST
**Purpose:** Stores a shopping list belonging to a Roomify project. It is a planning feature, not an order or transaction record.

**Primary Key**
- `shopping_list_id`

**Foreign Keys**
- `project_id` → `ROOM_PROJECT.project_id`

**Required Fields**
- `project_id`
- `name`
- `created_at`
- `updated_at`

**Optional Fields**
- None for the MVP.

**Relationships**
```text
ROOM_PROJECT 1:N SHOPPING_LIST
SHOPPING_LIST 1:N SHOPPING_LIST_ITEM
```

### 4.17 SHOPPING_LIST_ITEM
**Purpose:** Represents a furniture recommendation included in a shopping list.

**Primary Key**
- `shopping_list_item_id`

**Foreign Keys**
- `shopping_list_id` → `SHOPPING_LIST.shopping_list_id`
- `recommendation_id` → `FURNITURE_RECOMMENDATION.recommendation_id`

**Required Fields**
- `shopping_list_id`
- `recommendation_id`
- `quantity`
- `created_at`

**Optional Fields**
- None for the MVP.

**Constraints**
```text
quantity > 0
UNIQUE (shopping_list_id, recommendation_id)
```

**Relationships**
```text
SHOPPING_LIST 1:N SHOPPING_LIST_ITEM
FURNITURE_RECOMMENDATION 1:N SHOPPING_LIST_ITEM
SHOPPING_LIST_ITEM 1:N PURCHASE_OPTION
```

### 4.18 PURCHASE_OPTION
**Purpose:** Stores possible places where a user may purchase a shopping-list item. A purchase option does not mean the product has been purchased.

**Primary Key**
- `purchase_option_id`

**Foreign Keys**
- `shopping_list_item_id` → `SHOPPING_LIST_ITEM.shopping_list_item_id`

**Required Fields**
- `shopping_list_item_id`
- `retailer`
- `price`
- `currency`
- `created_at`

**Optional Fields**
- `purchase_url`
- `store_name`
- `store_address`

**Constraints**
```text
price >= 0
```
At least one purchase location should normally be available:
```text
purchase_url
OR store_name
OR store_address
```

**Relationships**
```text
SHOPPING_LIST_ITEM 1:N PURCHASE_OPTION
```

## 5. High-Level Relationship Flow
```text
USER
└── ROOM_PROJECT
    ├── ROOM_IMAGE
    │   └── DETECTED_OBJECT
    │       └── FURNITURE_DECISION
    ├── ROOM_PREFERENCE
    ├── REDESIGN_RECOMMENDATION
    │   ├── REDESIGN_ITEM
    │   │   ├── DETECTED_OBJECT
    │   │   └── FURNITURE
    │   │       ├── BED
    │   │       ├── CHAIR
    │   │       ├── DESK
    │   │       ├── SOFA
    │   │       ├── LAMP
    │   │       └── SHELF
    │   └── GENERATED_DESIGN
    ├── DIY_PROJECT
    │   └── DIY_MATERIAL
    └── SHOPPING_LIST
        └── SHOPPING_LIST_ITEM
            └── PURCHASE_OPTION

REDESIGN_ITEM
└── FURNITURE_RECOMMENDATION
    ├── PRODUCT
    ├── DIY_PROJECT
    └── SHOPPING_LIST_ITEM
```

## 6. Roomify Data Flow
```text
User creates project
        ↓
Uploads room image
        ↓
Image stored in object storage
        ↓
ROOM_IMAGE metadata saved
        ↓
CV analyzes image
        ↓
DETECTED_OBJECT records saved
        ↓
User selects KEEP / REPLACE / REMOVE / UNSURE
        ↓
FURNITURE_DECISION
        ↓
User enters style, budget, custom requirements, rental preference
        ↓
ROOM_PREFERENCE
        ↓
Roomify creates redesign plan
        ↓
REDESIGN_RECOMMENDATION
        ↓
REDESIGN_ITEM actions created
        ↓
Reusable desired FURNITURE specifications referenced
        ↓
Generated visual prototype
        ↓
GENERATED_DESIGN
        ↓
Catalogue products matched to redesign items
        ↓
FURNITURE_RECOMMENDATION
        ↓
DIY alternatives and/or SHOPPING_LIST
        ↓
PURCHASE_OPTION
```

## 7. Normalization Decisions

### 7.1 Detected objects and furniture decisions are separated
`DETECTED_OBJECT` represents what exists in the uploaded image.
`FURNITURE_DECISION` represents what the user wants to do with that object.
This prevents user intent from overwriting raw CV results.

### 7.2 Redesign metadata and redesign actions are separated
`REDESIGN_RECOMMENDATION` stores high-level AI redesign metadata.
`REDESIGN_ITEM` stores individual redesign actions such as `KEEP`, `REMOVE`, `MOVE`, `ADD`, and `REPLACE`.
This avoids placing core business data inside one large JSON field.

### 7.3 Furniture specifications are reusable
`FURNITURE` stores desired furniture attributes such as type, style, color, material, and dimensions.
The same reusable furniture specification can be referenced by multiple redesign items rather than repeating the same attributes in AI output.

### 7.4 Furniture subtype fields are normalized
Common furniture attributes are stored in `FURNITURE`.
Type-specific fields are stored only in subtype tables such as `BED`, `CHAIR`, `DESK`, `SOFA`, `LAMP`, and `SHELF`.

### 7.5 Products are separate from desired furniture specifications
`FURNITURE` means what the redesign wants.
`PRODUCT` means what the product catalogue actually offers.
`FURNITURE_RECOMMENDATION` connects a redesign item to a matching real product.
This allows one product to be recommended in different redesign contexts.

### 7.6 Products are separated from recommendations
A `PRODUCT` can exist in the catalogue independently of any redesign.
`FURNITURE_RECOMMENDATION` stores the context and reason why that product was recommended.

### 7.7 Shopping-list items are normalized
A shopping list does not contain fixed columns such as `product_1`, `product_2`, or `product_3`.
Each selected recommendation is represented by a separate `SHOPPING_LIST_ITEM`.

### 7.8 Purchase options are separated from shopping-list items
A selected item may have multiple possible retailers, online links, or physical stores.
Therefore:
```text
SHOPPING_LIST_ITEM 1:N PURCHASE_OPTION
```

### 7.9 DIY materials are separated from DIY projects
One DIY project may require multiple materials.
Therefore:
```text
DIY_PROJECT 1:N DIY_MATERIAL
```

## 8. Image Storage Strategy
PostgreSQL does not store room images or generated designs as binary blobs.
Instead:
```text
Room Image / Generated Design
            ↓
       Object Storage
            ↓
storage_key stored in PostgreSQL
```
Example:
```text
storage_key = rooms/project-15/room-original-01.jpg
```
This keeps the relational database focused on structured data while object storage handles large media files.

## 9. JPA / Hibernate Mapping
The schema is designed to map cleanly to Spring Data JPA entities.

Example relationships:
```text
User
@OneToMany
RoomProject
```

```text
RoomProject
@OneToMany
RoomImage
```

```text
RoomImage
@OneToMany
DetectedObject
```

The furniture hierarchy can be mapped using joined-table inheritance:
```java
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Furniture {
}
```

Example subtype:
```java
@Entity
public class Lamp extends Furniture {
}
```

This maps naturally to:
```text
FURNITURE
    |
    └── LAMP
```
The exact Java entity implementation belongs to the backend persistence task, while this task defines the relational model that JPA/Hibernate will map to.

## 10. Database Migration Strategy
Database schema changes are version-controlled using migrations.
Initial migration:
```text
src/main/resources/db/migration/
└── V1__initial_schema.sql
```
Future schema changes should create new migrations rather than editing an already-applied migration.
Examples:
```text
V1__initial_schema.sql
V2__add_new_furniture_type.sql
V3__add_product_fields.sql
```
This allows every team member and deployment environment to reproduce the same database structure.

## 11. Task 5 Acceptance Criteria Mapping

### ERD is created
Covered by:
```text
docs/database/roomify-erd.md
docs/database/roomify-erd.png
```

### Core MVP entities are represented
Covered by:
```text
USER
ROOM_PROJECT
ROOM_IMAGE
DETECTED_OBJECT
FURNITURE_DECISION
ROOM_PREFERENCE
GENERATED_DESIGN
FURNITURE_RECOMMENDATION
DIY_PROJECT
SHOPPING_LIST
```
Additional entities support normalization, redesign detail, furniture inheritance, product matching, DIY materials, and purchase options.

### Relationships have correct cardinality
Relationships are defined in the ERD and documented in this README.

### Image metadata can be stored
Covered by `ROOM_IMAGE`, including:
```text
storage_key
original_filename
mime_type
file_size_bytes
width
height
```

### CV detection results can be stored
Covered by `DETECTED_OBJECT`, including:
```text
object_class
confidence
x_min
y_min
x_max
y_max
```

### Keep / Replace / Remove / Unsure can be represented
Covered by:
```text
FURNITURE_DECISION
```

### User preferences and budget can be represented
Covered by `ROOM_PREFERENCE`, including:
```text
style
budget_min
budget_max
currency
custom_requirements
rental_friendly
```

### Generated designs can reference their source project
Covered through:
```text
GENERATED_DESIGN.project_id
```
The source room image is also recorded through:
```text
GENERATED_DESIGN.source_image_id
```

### Schema avoids obvious unnecessary duplication
The schema separates:
- CV results from user decisions.
- High-level redesigns from individual redesign actions.
- Reusable furniture specifications from actual products.
- Common furniture fields from subtype-specific fields.
- Products from recommendations.
- Recommendations from shopping-list selections.
- Shopping-list items from purchase options.
- DIY projects from DIY materials.

## 12. Summary
The Roomify database separates the application's main stages:
```text
CV Detection
    ↓
User Decisions
    ↓
Room Preferences
    ↓
AI Redesign
    ↓
Redesign Actions
    ↓
Reusable Furniture Specifications
    ↓
Generated Design
    ↓
Product Recommendations
    ↓
DIY / Shopping
```
The design removes the previous need to store important redesign information in a large `recommendation_data` JSON field. Instead, redesign actions and desired furniture are represented relationally through `REDESIGN_ITEM` and `FURNITURE`.
The result is a schema that is more reusable, normalized, easier to query, easier to validate, and suitable for future JPA/Hibernate mapping.