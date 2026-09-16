# Roomify Database Design

## 1. Overview

Roomify uses PostgreSQL as its relational database.

The database stores:

* User accounts
* Room projects
* Room image metadata
* Computer Vision detection results
* Furniture decisions
* Room preferences and budget
* AI redesign recommendations
* AI-generated room designs
* Furniture products and recommendations
* DIY recommendations
* Shopping lists
* Purchase options

Room images and generated images are **not stored directly in PostgreSQL as binary data**. Images are stored in external object storage, while PostgreSQL stores references and metadata such as storage keys, filenames, MIME types, dimensions, and file sizes.

---

# 2. Database Files

The database-related files are organized as follows:

```text
roomify_backend/
│
├── docs/
│   └── database/
│       ├── README.md
│       ├── roomify-erd.md
│       └── roomify-erd.png
│
└── src/
    └── main/
        └── resources/
            └── db/
                └── migration/
                    └── V1__initial_schema.sql
```

* `README.md`: Documents the database design.
* `roomify-erd.md`: Contains the Mermaid ERD source.
* `roomify-erd.png`: Rendered ERD for documentation.
* `V1__initial_schema.sql`: Initial PostgreSQL database migration.

---

# 3. Entity Overview

The initial Roomify database contains the following main entities:

1. USER
2. ROOM_PROJECT
3. ROOM_IMAGE
4. DETECTED_OBJECT
5. FURNITURE_DECISION
6. ROOM_PREFERENCE
7. REDESIGN_RECOMMENDATION
8. GENERATED_DESIGN
9. PRODUCT
10. FURNITURE_RECOMMENDATION
11. DIY_PROJECT
12. DIY_MATERIAL
13. SHOPPING_LIST
14. SHOPPING_LIST_ITEM
15. PURCHASE_OPTION

---

# 4. Entity Definitions

## 4.1 USER

### Purpose

Stores registered Roomify users.

### Primary Key

* `user_id`

### Foreign Keys

None.

### Required Fields

* `email`
* `password_hash`
* `created_at`
* `updated_at`

### Optional Fields

* `display_name`

### Constraints

* `email` must be unique.
* `email` cannot be null.
* Passwords must never be stored in plain text.
* `password_hash` stores the securely hashed password.

### Relationships

```text
USER 1:N ROOM_PROJECT
```

One user may own multiple room projects.

---

## 4.2 ROOM_PROJECT

### Purpose

Represents one room redesign project belonging to a user.

Most project-specific entities eventually belong to a `ROOM_PROJECT`.

### Primary Key

* `project_id`

### Foreign Keys

* `user_id` → `USER.user_id`

### Required Fields

* `user_id`
* `name`
* `status`
* `created_at`
* `updated_at`

### Optional Fields

None for the initial MVP.

### Constraints

`status` should contain supported project states such as:

```text
DRAFT
ANALYZED
REDESIGN_GENERATED
COMPLETED
```

### Relationships

```text
USER 1:N ROOM_PROJECT

ROOM_PROJECT 1:N ROOM_IMAGE
ROOM_PROJECT 1:0..1 ROOM_PREFERENCE
ROOM_PROJECT 1:N REDESIGN_RECOMMENDATION
ROOM_PROJECT 1:N GENERATED_DESIGN
ROOM_PROJECT 1:N FURNITURE_RECOMMENDATION
ROOM_PROJECT 1:N DIY_PROJECT
ROOM_PROJECT 1:N SHOPPING_LIST
```

---

## 4.3 ROOM_IMAGE

### Purpose

Stores metadata and object-storage references for uploaded room images.

The actual image is **not stored inside PostgreSQL**.

### Primary Key

* `image_id`

### Foreign Keys

* `project_id` → `ROOM_PROJECT.project_id`

### Required Fields

* `project_id`
* `storage_key`
* `original_filename`
* `mime_type`
* `file_size_bytes`
* `created_at`

### Optional Fields

* `width`
* `height`

### Image Metadata Example

```text
storage_key = rooms/15/original-room.jpg
original_filename = bedroom.jpg
mime_type = image/jpeg
file_size_bytes = 2458291
width = 1920
height = 1080
```

### Constraints

* `file_size_bytes > 0`
* `width > 0` when provided
* `height > 0` when provided
* MVP-supported image MIME types include:

  * `image/jpeg`
  * `image/png`

### Relationships

```text
ROOM_PROJECT 1:N ROOM_IMAGE
ROOM_IMAGE 1:N DETECTED_OBJECT
ROOM_IMAGE 1:N GENERATED_DESIGN
```

A room image may be used as the source image for generated redesign prototypes.

---

## 4.4 DETECTED_OBJECT

### Purpose

Stores furniture or room objects detected from an uploaded image by the Computer Vision system.

It also supports furniture objects manually added by the user.

### Primary Key

* `object_id`

### Foreign Keys

* `image_id` → `ROOM_IMAGE.image_id`

### Required Fields

* `image_id`
* `object_class`
* `source`
* `created_at`
* `updated_at`

### Optional Fields

* `confidence`
* `x_min`
* `y_min`
* `x_max`
* `y_max`

### CV Fields

Example:

```text
object_class = chair
confidence = 0.94

x_min = 0.20
y_min = 0.35
x_max = 0.42
y_max = 0.80
```

`object_class` describes what the CV model detected, for example:

```text
bed
desk
table
chair
sofa
tv
plant
```

`confidence` represents how confident the CV model is in the detection.

Bounding-box coordinates identify the object's position within the image.

### Source

Supported values:

```text
CV
MANUAL
```

`CV` means the object was automatically detected.

`MANUAL` means the user manually added an object that the CV system missed.

### Constraints

When provided:

```text
0 <= confidence <= 1
0 <= x_min <= 1
0 <= y_min <= 1
0 <= x_max <= 1
0 <= y_max <= 1

x_min < x_max
y_min < y_max
```

### Relationships

```text
ROOM_IMAGE 1:N DETECTED_OBJECT

DETECTED_OBJECT 1:0..1 FURNITURE_DECISION
```

---

## 4.5 FURNITURE_DECISION

### Purpose

Stores the user's decision about an existing detected furniture object.

This table represents what the user wants Roomify to do with furniture already present in the room.

### Primary Key

* `decision_id`

### Foreign Keys

* `object_id` → `DETECTED_OBJECT.object_id`

### Required Fields

* `object_id`
* `decision`
* `created_at`
* `updated_at`

### Optional Fields

* `notes`

### Supported Decisions

```text
KEEP
REPLACE
REMOVE
UNSURE
```

### Constraints

* `object_id` should be unique in this table.
* One detected object has at most one current furniture decision.
* `decision` must contain one of the supported decision values.

### Relationships

```text
DETECTED_OBJECT 1:0..1 FURNITURE_DECISION
```

---

## 4.6 ROOM_PREFERENCE

### Purpose

Stores the user's current redesign preferences for a room project.

It combines the MVP preference inputs:

* Room style
* Preferred budget range
* Custom requirements
* Rental-Friendly Mode

### Primary Key

* `preference_id`

### Foreign Keys

* `project_id` → `ROOM_PROJECT.project_id`

### Required Fields

* `project_id`
* `style`
* `budget_min`
* `budget_max`
* `currency`
* `rental_friendly`
* `created_at`
* `updated_at`

### Optional Fields

* `custom_requirements`

### Supported Styles

Initial MVP values may include:

```text
MINIMALIST
SCANDINAVIAN
COZY
MODERN
NO_PREFERENCE
```

### Budget

Budget is represented as a preferred range rather than only a strict maximum.

Example:

```text
budget_min = 800
budget_max = 1200
currency = AUD
```

This allows Roomify to recommend a redesign around the user's desired spending range.

### Constraints

```text
budget_min >= 0
budget_max > 0
budget_max >= budget_min
```

Each room project should have at most one current preference record.

Therefore:

```text
project_id UNIQUE
```

### Relationships

```text
ROOM_PROJECT 1:0..1 ROOM_PREFERENCE
```

---

## 4.7 REDESIGN_RECOMMENDATION

### Purpose

Stores the AI-generated redesign plan.

This represents the conceptual redesign recommendation rather than the generated image.

### Primary Key

* `redesign_id`

### Foreign Keys

* `project_id` → `ROOM_PROJECT.project_id`

### Required Fields

* `project_id`
* `summary`
* `status`
* `created_at`
* `updated_at`

### Optional Fields

* `recommendation_data`
* `estimated_total_cost`

### Recommendation Data

`recommendation_data` may use PostgreSQL `JSONB` because AI redesign output can contain structured information such as:

```text
items_to_keep
items_to_replace
items_to_remove
items_to_add
layout_changes
reasons
```

### Example

```json
{
  "items_to_keep": ["bed"],
  "items_to_replace": ["desk"],
  "items_to_remove": ["old chair"],
  "items_to_add": ["storage shelf"],
  "reasons": [
    "Improve storage while maintaining a minimalist layout"
  ]
}
```

### Status

Example values:

```text
PENDING
PROCESSING
COMPLETED
FAILED
```

### Relationships

```text
ROOM_PROJECT 1:N REDESIGN_RECOMMENDATION

REDESIGN_RECOMMENDATION 1:N GENERATED_DESIGN

REDESIGN_RECOMMENDATION 1:N FURNITURE_RECOMMENDATION
```

---

## 4.8 GENERATED_DESIGN

### Purpose

Stores metadata for AI-generated visual redesign prototypes.

The generated image itself is stored in object storage.

### Primary Key

* `design_id`

### Foreign Keys

* `project_id` → `ROOM_PROJECT.project_id`
* `redesign_id` → `REDESIGN_RECOMMENDATION.redesign_id`
* `source_image_id` → `ROOM_IMAGE.image_id`

### Required Fields

* `project_id`
* `redesign_id`
* `source_image_id`
* `status`
* `created_at`

### Optional Fields

* `storage_key`
* `prompt`
* `error_message`

### Status

Example values:

```text
PENDING
PROCESSING
COMPLETED
FAILED
```

### Relationships

```text
ROOM_PROJECT 1:N GENERATED_DESIGN

REDESIGN_RECOMMENDATION 1:N GENERATED_DESIGN

ROOM_IMAGE 1:N GENERATED_DESIGN
```

`source_image_id` identifies the original room image used as visual context.

`project_id` makes project ownership and retrieval explicit.

---

## 4.9 PRODUCT

### Purpose

Stores furniture or decoration products that Roomify may recommend.

The MVP uses a curated product catalogue.

### Primary Key

* `product_id`

### Foreign Keys

None.

### Required Fields

* `name`
* `category`
* `created_at`
* `updated_at`

### Optional Fields

* `brand`
* `description`
* `width_cm`
* `height_cm`
* `depth_cm`
* `image_url`
* `is_active`

### Example

```text
name = Minimalist Study Desk
category = desk
brand = IKEA
width_cm = 120
height_cm = 75
depth_cm = 60
```

### Constraints

Dimensions must be greater than zero when provided.

### Relationships

```text
PRODUCT 1:N FURNITURE_RECOMMENDATION
```

---

## 4.10 FURNITURE_RECOMMENDATION

### Purpose

Stores furniture or decoration products recommended by Roomify.

This differs from `FURNITURE_DECISION`.

`FURNITURE_DECISION` records what the user wants to do with existing furniture.

`FURNITURE_RECOMMENDATION` stores new products recommended by Roomify.

### Primary Key

* `recommendation_id`

### Foreign Keys

* `project_id` → `ROOM_PROJECT.project_id`
* `redesign_id` → `REDESIGN_RECOMMENDATION.redesign_id`
* `product_id` → `PRODUCT.product_id`
* `object_id` → `DETECTED_OBJECT.object_id`
* `alternative_for_recommendation_id` → `FURNITURE_RECOMMENDATION.recommendation_id`

### Required Fields

* `project_id`
* `product_id`
* `recommendation_type`
* `recommendation_reason`
* `created_at`
* `updated_at`

### Optional Fields

* `redesign_id`
* `object_id`
* `alternative_for_recommendation_id`
* `is_selected`

`object_id` may be null when Roomify recommends a completely new item rather than replacing existing furniture.

### Recommendation Types

```text
PRIMARY
CHEAPER_ALTERNATIVE
```

### Cheaper Alternatives

This table has a self-referencing relationship.

Example:

```text
Recommendation 10:
Primary desk recommendation

Recommendation 11:
Cheaper desk
alternative_for_recommendation_id = 10
```

### Relationships

```text
ROOM_PROJECT 1:N FURNITURE_RECOMMENDATION

REDESIGN_RECOMMENDATION 1:N FURNITURE_RECOMMENDATION

PRODUCT 1:N FURNITURE_RECOMMENDATION

DETECTED_OBJECT 1:N FURNITURE_RECOMMENDATION

FURNITURE_RECOMMENDATION 1:N FURNITURE_RECOMMENDATION

FURNITURE_RECOMMENDATION 1:N DIY_PROJECT

FURNITURE_RECOMMENDATION 1:N SHOPPING_LIST_ITEM
```

---

## 4.11 DIY_PROJECT

### Purpose

Stores Roomify DIY alternatives for suitable furniture or decorations.

### Primary Key

* `diy_project_id`

### Foreign Keys

* `project_id` → `ROOM_PROJECT.project_id`
* `recommendation_id` → `FURNITURE_RECOMMENDATION.recommendation_id`

### Required Fields

* `project_id`
* `title`
* `description`
* `difficulty`
* `instructions`
* `created_at`
* `updated_at`

### Optional Fields

* `recommendation_id`
* `estimated_cost`
* `estimated_minutes`
* `tools`
* `tutorial_video_url`

### Instructions

Instructions may be stored using PostgreSQL `JSONB`.

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
  },
  {
    "step": 3,
    "instruction": "Attach the brackets."
  }
]
```

### Tutorial Video

`tutorial_video_url` may contain an optional external instructional video.

### Difficulty

Example values:

```text
EASY
MEDIUM
HARD
```

### Relationships

```text
ROOM_PROJECT 1:N DIY_PROJECT

FURNITURE_RECOMMENDATION 1:N DIY_PROJECT

DIY_PROJECT 1:N DIY_MATERIAL
```

---

## 4.12 DIY_MATERIAL

### Purpose

Stores materials required for a DIY project.

Materials are separated from `DIY_PROJECT` to avoid storing repeated material information inside fixed columns.

### Primary Key

* `material_id`

### Foreign Keys

* `diy_project_id` → `DIY_PROJECT.diy_project_id`

### Required Fields

* `diy_project_id`
* `name`
* `created_at`

### Optional Fields

* `quantity`
* `unit`
* `estimated_price`

### Constraints

When provided:

```text
quantity > 0
estimated_price >= 0
```

### Relationships

```text
DIY_PROJECT 1:N DIY_MATERIAL
```

---

## 4.13 SHOPPING_LIST

### Purpose

Stores a shopping list belonging to a Roomify project.

The shopping list helps users collect recommended items they may want to purchase.

It does not represent an order or transaction.

### Primary Key

* `shopping_list_id`

### Foreign Keys

* `project_id` → `ROOM_PROJECT.project_id`

### Required Fields

* `project_id`
* `name`
* `created_at`
* `updated_at`

### Optional Fields

None for the MVP.

### Relationships

```text
ROOM_PROJECT 1:N SHOPPING_LIST

SHOPPING_LIST 1:N SHOPPING_LIST_ITEM
```

---

## 4.14 SHOPPING_LIST_ITEM

### Purpose

Represents a furniture recommendation included in a shopping list.

### Primary Key

* `shopping_list_item_id`

### Foreign Keys

* `shopping_list_id` → `SHOPPING_LIST.shopping_list_id`
* `recommendation_id` → `FURNITURE_RECOMMENDATION.recommendation_id`

### Required Fields

* `shopping_list_id`
* `recommendation_id`
* `quantity`
* `created_at`

### Optional Fields

None for the MVP.

### Constraints

```text
quantity > 0
```

The combination:

```text
shopping_list_id + recommendation_id
```

should be unique to prevent the same recommendation from accidentally being added multiple times to the same shopping list.

### Relationships

```text
SHOPPING_LIST 1:N SHOPPING_LIST_ITEM

FURNITURE_RECOMMENDATION 1:N SHOPPING_LIST_ITEM

SHOPPING_LIST_ITEM 1:N PURCHASE_OPTION
```

---

## 4.15 PURCHASE_OPTION

### Purpose

Stores different places where the user may purchase a shopping-list item.

A purchase option does **not** mean the user has bought the product.

It only represents a possible purchase source.

### Primary Key

* `purchase_option_id`

### Foreign Keys

* `shopping_list_item_id` → `SHOPPING_LIST_ITEM.shopping_list_item_id`

### Required Fields

* `shopping_list_item_id`
* `retailer`
* `price`
* `currency`
* `created_at`

### Optional Fields

* `purchase_url`
* `store_name`
* `store_address`

### Example

```text
retailer = IKEA
price = 149.00
currency = AUD

purchase_url = https://...
store_name = IKEA Tempe
store_address = Tempe NSW
```

One shopping-list item may therefore have:

```text
IKEA Online
Target Online
IKEA Physical Store
Local Furniture Store
```

as separate purchase options.

### Constraints

```text
price >= 0
```

At least one of the following should normally be available:

```text
purchase_url
store_name
store_address
```

### Relationships

```text
SHOPPING_LIST_ITEM 1:N PURCHASE_OPTION
```

---

# 5. High-Level Relationship Flow

```text
USER
 │
 └── ROOM_PROJECT
      │
      ├── ROOM_IMAGE
      │      │
      │      └── DETECTED_OBJECT
      │             │
      │             └── FURNITURE_DECISION
      │
      ├── ROOM_PREFERENCE
      │
      ├── REDESIGN_RECOMMENDATION
      │      │
      │      ├── GENERATED_DESIGN
      │      │
      │      └── FURNITURE_RECOMMENDATION
      │
      ├── DIY_PROJECT
      │      │
      │      └── DIY_MATERIAL
      │
      └── SHOPPING_LIST
             │
             └── SHOPPING_LIST_ITEM
                    │
                    └── PURCHASE_OPTION
```

---

# 6. Roomify Data Flow

The main MVP database flow is:

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
User selects
KEEP / REPLACE / REMOVE / UNSURE
        ↓
FURNITURE_DECISION
        ↓
User enters preferences
        ↓
ROOM_PREFERENCE
        ↓
Roomify creates redesign plan
        ↓
REDESIGN_RECOMMENDATION
        ↓
Generated visual prototype
        ↓
GENERATED_DESIGN
        ↓
Furniture recommendations
        ↓
FURNITURE_RECOMMENDATION
        ↓
Shopping List / DIY alternatives
```

---

# 7. Normalization Decisions

The schema avoids obvious unnecessary duplication.

Examples:

### Furniture decisions are separated from detected objects

`DETECTED_OBJECT` represents what exists in the image.

`FURNITURE_DECISION` represents what the user wants to do with that object.

---

### Products are separated from recommendations

`PRODUCT` represents the furniture item itself.

`FURNITURE_RECOMMENDATION` explains why Roomify recommends the product for a particular redesign.

---

### Shopping-list items are separated from shopping lists

A shopping list does not contain columns such as:

```text
product_1
product_2
product_3
```

Instead, each item is represented as its own `SHOPPING_LIST_ITEM`.

---

### Purchase options are separated from shopping-list items

One shopping-list item may be available from multiple retailers or stores.

Therefore:

```text
SHOPPING_LIST_ITEM 1:N PURCHASE_OPTION
```

---

### DIY materials are separated from DIY projects

One DIY project may require multiple materials.

Therefore:

```text
DIY_PROJECT 1:N DIY_MATERIAL
```

---

# 8. Image Storage Strategy

PostgreSQL must not store room images as binary blobs.

Instead:

```text
Room Image
    ↓
Object Storage
    ↓
storage_key stored in PostgreSQL
```

Example:

```text
storage_key =
rooms/project-15/room-original-01.jpg
```

This architecture keeps the relational database focused on structured data while object storage handles large media files.

---

# 9. Database Migration Strategy

Database schema changes should be version-controlled using database migrations.

Initial migration:

```text
src/main/resources/db/migration/
└── V1__initial_schema.sql
```

Future schema changes should create new migrations rather than editing an already-applied production migration.

Examples:

```text
V1__initial_schema.sql
V2__add_project_description.sql
V3__add_new_product_fields.sql
```

This allows all team members and deployment environments to reproduce the same database structure.

---

# 10. Task 5 Acceptance Criteria Mapping

## ERD is created

Covered by:

```text
docs/database/roomify-erd.md
docs/database/roomify-erd.png
```

## Core MVP entities are represented

Covered by the entities documented above.

## Relationships have correct cardinality

Relationships are defined in the ERD and this document.

## Image metadata can be stored

Covered by:

```text
ROOM_IMAGE
```

## CV detection results can be stored

Covered by:

```text
DETECTED_OBJECT
```

including:

```text
object_class
confidence
x_min
y_min
x_max
y_max
```

## Keep / Replace / Remove / Unsure can be represented

Covered by:

```text
FURNITURE_DECISION
```

## User preferences and budget can be represented

Covered by:

```text
ROOM_PREFERENCE
```

including:

```text
style
budget_min
budget_max
currency
custom_requirements
rental_friendly
```

## Generated designs can reference their source project

Covered through:

```text
GENERATED_DESIGN.project_id
```

and its relationship with `ROOM_PROJECT`.

The source room image is also recorded through:

```text
source_image_id
```

## Schema avoids obvious unnecessary duplication

The schema separates products, recommendations, purchase options, shopping-list items, furniture decisions, and DIY materials according to their different responsibilities.
