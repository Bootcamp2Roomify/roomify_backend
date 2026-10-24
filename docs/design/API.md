# Design contracts, persistence, and generated assets

Implements ROOM-88, ROOM-92, ROOM-96 on top of provider PR #22 (commit 538f157).
Merge #22 first. The upstream generation prompt/output contract is updated to the explicit v1 shape below; consumers of its unpublished `designRationale` / `layoutChanges` fields must move to `summary` / `actions`.

## Version 1 contract

Canonical schemas live at `src/main/resources/schemas/design/v1/`. They use JSON Schema draft 2020-12, forbid unknown properties, bound text/list sizes, and use UUIDs for public object/project references. Example documents are in this folder. `$id` values are identifiers, not hosted endpoints.

`DesignRequestSnapshot` captures image ID and storage reference (never a signed URL), active detections for that image, public object UUIDs, confidence/normalized bounds, saved decisions (UNSURE when absent), preferences, and BigDecimal budget/currency. It is serialized once at the start of each request and never overwritten. The image reference is a pointer to an existing asset, not an independent archive copy of its bytes.

`DesignPlan` contains `planVersion`, `summary`, `designStyle`, `actions`, `layoutNotes`, `budgetAllocation`, `estimatedTotalCost`, `currency`, `warnings`, `itemsNeeded`, `rentalFriendlyWarnings`, and `constraints`.

Every detected object must appear exactly once in actions. Non-ADD actions require a known objectId and its saved decision/class. ADD uses null objectId and decision. KEEP permits KEEP or KEEP_AND_REPOSITION; other decisions permit their matching type. Item references, when present, must identify a detected object.

Allocation total must equal estimatedTotalCost exactly and be <= the captured budget. Values are nonnegative, have at most two decimal places, and use the captured currency; there is no conversion or rounding tolerance. These cross-field/reference rules are enforced by Java in addition to the structural schema. Rental-friendly input requires explicit nonempty constraints. The prompt prohibits permanent work; automated text checks cannot guarantee all generated prose obeys real lease conditions.

## HTTP API

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/api/projects/{projectId}/designs` | Generate one validated plan; 201 with stored result |
| GET | `/api/projects/{projectId}/designs/latest` | Latest attempt, including failed attempts; 204 if none |
| GET | `/api/projects/{projectId}/designs/{designId}` | Fetch a historical attempt by ID, scoped to project |
| POST multipart | `/api/projects/{projectId}/designs/{designId}/image` | Store normalized generated image bytes under part `file` |
| GET | `/api/projects/{projectId}/designs/{designId}/image/content` | Retrieve a successful image with its content type |

Result envelope: designId (existing Long ID), requestId (UUID), projectId, status, errorCode, plan, provider, modelName, requestVersion, promptVersion, createdAt, completedAt, and image. No snapshot, raw provider response, provider credentials, or exception text is returned. The input snapshot remains in PostgreSQL for debugging.

Image metadata: status, safe errorCode, contentType, sizeBytes, width, height, and relative imageUrl. Resolve imageUrl against the backend origin. UPLOADING/FAILED images have no display URL. Missing/wrong-project resources return 404; invalid generation state or concurrent generation returns 409. Controlled provider/generation/storage failures return 503 with a safe code and designId. Invalid image bytes/MIME/size return 400.

Version-6 legacy plan rows remain stored. Without a v1 request snapshot they return `plan: null` and null version metadata; they are not silently relabeled as v1. Generate a new plan to obtain the new contract.

## Transactions and failure handling

1. Lock the project briefly; capture input and create PENDING request/result in a committed transaction.
2. Call the existing AiDesignProvider outside a database transaction.
3. Strictly parse and validate. At most one corrective retry is made for invalid output; upstream provider transport retry settings remain separate.
4. Lock project again, confirm current input still equals the captured snapshot, and commit SUCCEEDED result plus DESIGN_READY together.
5. On error, a separate transaction stores FAILED and a fixed code. Raw provider errors and raw invalid output are neither stored nor sent to the corrective prompt.

Only the validated persistence path can set DESIGN_READY; the generic manual status endpoint cannot bypass it. Editing preferences moves an existing DESIGN_READY project back to PREFERENCES_READY. Prior attempts remain historical records. Concurrent PENDING attempts are prevented by both a lock and a partial unique index.

Asset flow: reserve UPLOADING; upload through StorageService; commit SUCCEEDED metadata. A storage or metadata-commit exception attempts object cleanup and records FAILED. A retry uses a fresh object key. Repeated upload after success returns the existing image metadata rather than overwriting it. Foreign keys enforce matching project/result ownership. No image bytes are stored in the database.

Object keys: `rooms/{projectId}/generated/{designResultId}/{assetUuid}.png` (or `.jpg`). STORAGE_TYPE selects the existing local or S3 adapter. Returned media URLs use the backend, so a public bucket is unnecessary. Configure STORAGE_TYPE, AWS_REGION, S3_BUCKET, and credentials using the team's normal environment setup. DESIGN_IMAGE_MAX_SIZE_BYTES defaults to 10 MiB. PNG/JPEG signatures must match MIME; dimensions and decoding are checked with a 40-million-pixel limit before success.

ROOM-94's future provider adapter calls `GeneratedDesignImageService.store(projectId, designId, contentType, bytes)` after normalizing its response. This ticket does not call an image-generation provider, fetch arbitrary provider URLs, or create paid image generations.

## Operational limits

A hard process crash can leave PENDING/UPLOADING rows or an orphan storage object. There is no background job/lease recovery worker in this MVP. Investigate those rows and the generated/ prefix before retrying; do not falsely label them successful. If cleanup itself fails, the asset still stays failed, but orphan cleanup must be handled operationally. Changing storage backends requires migrating existing objects; downloads use the currently configured adapter.

Existing project APIs have no user authentication; these endpoints use the same project-scoping convention. They do not introduce a claim of authenticated ownership.

Use a dedicated test database for integration tests. Flyway changes schemas; do not run a new migration suite against a database whose data you need to preserve without following the team's migration process. V1–V6 are unchanged; V7 adds request auditing and assets and extends the existing result table.

`compose.design-test.yaml` is a standalone PostgreSQL 17 test environment with no host database ports. It uses separate named data/Maven volumes; run it under the project name `roomify-design-test`. Backend Compose now forwards existing AI provider configuration from the environment; real keys remain outside Git.
