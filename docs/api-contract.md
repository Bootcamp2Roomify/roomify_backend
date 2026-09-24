# Roomify REST API Contract

Status: Completed contract draft — pending team approval
Version: 0.1.0
Date: 2026-09-17
Jira ticket: ROOM-6

## Purpose

Define how the frontend, Spring Boot backend, and Python
computer-vision service communicate so teams can work independently.

## Scope

- Frontend-to-backend API
- Backend-to-computer-vision API
- Request and response formats
- Authentication, validation, and error responses

## Shared API Rules

This document is a proposed interface agreement, not a description of implemented behavior.
The consolidated decisions below provide concrete draft defaults; they require team approval.
No approval, implementation, or completion of Jira workflow is implied.

### Paths and Data Format

- Frontend-facing endpoints use the `/api` prefix.
- Requests and responses use JSON, except image uploads.
- JSON field names use camelCase, such as `roomId`.

### Authentication and Ownership

- Registration and login do not require a login token.
- All room-project endpoints require a valid JWT access token.
- Send the token using: `Authorization: Bearer <accessToken>`.
- The backend identifies the user from the verified token.
- Users may access only their own room projects.
- The internal computer-vision service's authentication is defined separately.

### Validation

- The backend validates incoming data.
- Project names are trimmed and must not be empty.
- Invalid requests must not create a project.

### Common Error Format

Errors contain a stable code and a readable message.

Example:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Project name is required."
}
```

## Frontend-to-Backend Endpoints
### Register an Account

POST /api/auth/register

Creates a user account using an email address and password.

**Authentication:** No existing login token required.

**Request content type:** `application/json`

| Field | Type | Required | Validation |
|---|---|---|---|
| email | string | Yes | Must satisfy the agreed email format and uniqueness rules |
| password | string | Yes | Must satisfy the team's password policy |

Use the account validation rules in Consolidated Draft Decisions.
Passwords must not be silently trimmed or changed.

**Example request**

```json
{
  "email": "student@example.com",
  "password": "<user-chosen-password>"
}
```

The password above is a placeholder, not a recommended password.

**Success: 201 Created**

```json
{
  "id": 501,
  "email": "student@example.com"
}
```

The ID is illustrative.
The backend assigns the user ID.
Passwords and password hashes are never returned.

This draft does not issue a login token during registration.
After successful registration, the frontend directs the user to login.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | VALIDATION_ERROR | Missing fields, incorrect types, or invalid account details |
| 400 Bad Request | INVALID_JSON | Malformed JSON |
| 409 Conflict | EMAIL_ALREADY_REGISTERED | Email is already associated with an account |
| 429 Too Many Requests | RATE_LIMITED | Registration attempts exceed the allowed rate |

**Example validation error**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "A valid email address is required."
}
```


### Log In

POST /api/auth/login

Verifies account credentials and returns an access token.

**Authentication:** No existing login token required.
**Request content type:** `application/json`

| Field | Type | Required |
|---|---|---|
| email | string | Yes |
| password | string | Yes |

**Example request**

```json
{
  "email": "student@example.com",
  "password": "<user-chosen-password>"
}
```

**Success: 200 OK**

```json
{
  "accessToken": "<jwt-access-token>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 501,
    "email": "student@example.com"
  }
}
```

`expiresIn` is the token lifetime in seconds.
The one-hour lifetime is a proposal requiring team approval.
IDs and tokens are illustrative.

Send the access token on protected requests:
`Authorization: Bearer <accessToken>`.

For this draft, an expired token requires login again.
Refresh tokens are not defined.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | VALIDATION_ERROR | Required credentials are missing or have incorrect types |
| 400 Bad Request | INVALID_JSON | Malformed JSON |
| 401 Unauthorized | INVALID_CREDENTIALS | Email or password is incorrect |
| 429 Too Many Requests | RATE_LIMITED | Too many login attempts |

Use the same response for an unknown email and an incorrect password:

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Email or password is incorrect."
}
```

See Consolidated Draft Decisions for the proposed default.

### Create a Room Project

POST /api/rooms

Creates a room project owned by the authenticated user.

**Authentication:** Required.
Send `Authorization: Bearer <accessToken>`.

**Request content type:** `application/json`

| Field | Type | Required | Validation |
|---|---|---|---|
| name | string | Yes | Trim surrounding whitespace; reject an empty result |

**Example request**

```json
{
  "name": "My bedroom"
}
```

**Success: 201 Created**

```json
{
  "id": 42,
  "name": "My bedroom"
}
```

The backend assigns the ID and returns the trimmed name.
The ID is illustrative; shared ID rules apply.
The owner is determined from the verified token, not the request body.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | VALIDATION_ERROR | Missing, blank, or non-string name |
| 400 Bad Request | INVALID_JSON | Request body is malformed JSON |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |

**Example validation error**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Project name is required."
}
```
### List My Room Projects

GET /api/rooms

Returns room projects owned by the authenticated user.

**Authentication:** Required.
Send `Authorization: Bearer <accessToken>`.

**Request body:** None.
**Query parameters:** `page` and `pageSize`; shared pagination rules apply.

**Success: 200 OK**

```json
{
  "rooms": [
    {
      "id": 42,
      "name": "My bedroom"
    },
    {
      "id": 43,
      "name": "Dorm room"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "hasNext": false
}
```

IDs are illustrative; shared ID rules apply.
The backend returns only projects owned by the authenticated user.

If the user has no projects, return `200 OK` with:

```json
{
  "rooms": [],
  "page": 1,
  "pageSize": 20,
  "hasNext": false
}
```

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | VALIDATION_ERROR | Invalid pagination |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |

See Consolidated Draft Decisions for the proposed default.

### Get a Room Project

GET /api/rooms/{id}

Returns a room project owned by the authenticated user.

**Authentication:** Required.
Send `Authorization: Bearer <accessToken>`.

**Path parameter:** `id` — the project's identifier.
Shared ID rules apply.

**Request body:** None.

**Example request:** `GET /api/rooms/42`

**Success: 200 OK**

```json
{
  "id": 42,
  "name": "My bedroom"
}
```

The ID is illustrative. Images, detected objects, preferences,
and designs are outside this response and require separate endpoints.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | ID does not match the agreed format |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | ROOM_NOT_FOUND | Project does not exist or belongs to another user |

**Example error**

```json
{
  "code": "ROOM_NOT_FOUND",
  "message": "Room project not found."
}
```

### Upload a Room Image

POST /api/rooms/{id}/images

Uploads one room photo to a project owned by the authenticated user.

**Authentication:** Required.
Send `Authorization: Bearer <accessToken>`.

**Path parameter:** `id` — the room project's identifier.

**Request content type:** `multipart/form-data`

| Field | Type | Required | Validation |
|---|---|---|---|
| file | File | Yes | Non-empty, valid JPG or PNG; maximum 10,000,000 bytes |

The 10 MB limit is a draft pending team approval.
The backend validates the actual image content, not just its filename.

**Example request**

- URL: `/api/rooms/42/images`
- Form field: `file`
- Attached file: `bedroom.jpg`

**Success: 201 Created**

```json
{
  "id": 101,
  "roomId": 42,
  "fileName": "bedroom.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 2450000
}
```

IDs are illustrative; shared ID rules apply.
The returned image ID identifies the saved upload.
Uploading does not automatically start room analysis.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | Room ID does not match the agreed format |
| 400 Bad Request | INVALID_IMAGE | Missing, empty, or unreadable image |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | ROOM_NOT_FOUND | Project does not exist or belongs to another user |
| 413 Content Too Large | IMAGE_TOO_LARGE | File exceeds 10,000,000 bytes |
| 415 Unsupported Media Type | UNSUPPORTED_IMAGE_TYPE | File format is not JPG or PNG |

**Example error**

```json
{
  "code": "IMAGE_TOO_LARGE",
  "message": "Image must be 10 MB or smaller."
}
```

See Consolidated Draft Decisions for the proposed default.
### List Room Images

GET /api/rooms/{id}/images

Returns uploaded images for an owned room, newest first. This proposed addition
lets a returning user view the original photo and select an image for analysis.

**Authentication:** Required; verify room ownership before issuing image URLs.
**Path parameter:** `id` — room ID. **Request body:** None.
**Query parameters:** `page` and `pageSize`; shared pagination rules apply.

**Success: 200 OK**

```json
{
  "images": [
    {
      "id": 101,
      "roomId": 42,
      "fileName": "bedroom.jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 2450000,
      "imageWidth": 1200,
      "imageHeight": 900,
      "createdAt": "2026-09-17T13:00:00Z",
      "imageUrl": "https://example.com/temporary-room-image",
      "imageUrlExpiresAt": "2026-09-17T13:15:00Z"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "hasNext": false
}
```

`imageWidth` and `imageHeight` are positive integer dimensions after orientation
correction. The served image uses that same orientation so boxes align.
URLs are temporary HTTPS strings, valid for 15 minutes; timestamps are UTC RFC 3339.
Fetch this endpoint again to obtain fresh links. Empty pages return `images: []`.
IDs, dates, and URLs above are illustrative.

| Status | Code | Meaning |
|---|---|---|
| 400 | INVALID_ID | Malformed room ID |
| 400 | VALIDATION_ERROR | Invalid pagination |
| 401 | UNAUTHENTICATED | Missing, invalid, or expired token |
| 404 | ROOM_NOT_FOUND | Room absent or not owned by caller |

### Start Room Analysis

POST /api/rooms/{id}/analyze

Starts furniture detection for an uploaded image.

**Authentication:** Required. The user must own the room project.

**Request content type:** `application/json`

| Field | Type | Required | Validation |
|---|---|---|---|
| imageId | integer | Yes | Must identify a saved image belonging to this room |

**Example request**

```json
{
  "imageId": 101
}
```

**Success: 202 Accepted**

```json
{
  "analysisId": 201,
  "roomId": 42,
  "imageId": 101,
  "status": "pending"
}
```

IDs are illustrative.
Acceptance means the job was saved for processing, not that detection succeeded.
The frontend checks progress using the analysis ID.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | Invalid room ID |
| 400 Bad Request | VALIDATION_ERROR | Missing or invalid image ID |
| 400 Bad Request | INVALID_JSON | Malformed JSON |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | ROOM_NOT_FOUND | Room does not exist or belongs to another user |
| 404 Not Found | IMAGE_NOT_FOUND | Image does not exist in this room |
| 409 Conflict | PROCESSING_IN_PROGRESS | This room already has an active analysis or redesign |
| 503 Service Unavailable | ANALYSIS_UNAVAILABLE | Analysis cannot currently be accepted |

### Check Analysis Progress

GET /api/rooms/{id}/analyses/{analysisId}

Returns the current state of an analysis job.

**Authentication:** Required. The user must own the room project.

**Request body:** None.

**Success: 200 OK**

```json
{
  "analysisId": 201,
  "roomId": 42,
  "imageId": 101,
  "status": "processing",
  "error": null
}
```

| Status value | Meaning |
|---|---|
| pending | Waiting to start |
| processing | Detection is running |
| succeeded | Detection finished and results were saved |
| failed | Detection could not finish |

Once the status is `succeeded`, fetch detections using
`GET /api/rooms/{id}/objects`.
Successful detection may return zero objects.

For a failed job, the progress request still returns `200 OK`:

```json
{
  "analysisId": 201,
  "roomId": 42,
  "imageId": 101,
  "status": "failed",
  "error": {
    "code": "DETECTION_FAILED",
    "message": "Room analysis failed. Please try again."
  }
}
```

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | A path ID has an invalid format |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | ROOM_NOT_FOUND | Room does not exist or belongs to another user |
| 404 Not Found | ANALYSIS_NOT_FOUND | Analysis does not exist in this room |


### List Detected Furniture

GET /api/rooms/{id}/objects

Returns furniture detections from the room's latest successful analysis.

**Authentication:** Required. The user must own the room project.

**Request body:** None.

**Success: 200 OK**

```json
{
  "analysisId": 201,
  "imageId": 101,
  "objects": [
    {
      "id": 301,
      "class": "bed",
      "confidence": 0.94,
      "bbox": [
        120,
        210,
        580,
        640
      ],
      "decision": "unsure"
    }
  ]
}
```

IDs are illustrative.

| Field | Meaning |
|---|---|
| analysisId | Analysis that produced these detections |
| imageId | Image analyzed |
| objects[].id | Backend-assigned identifier for this detected item |
| objects[].class | Detected furniture category |
| objects[].confidence | Model confidence score from 0 to 1; not a guarantee |
| objects[].bbox | `[left, top, right, bottom]` in pixels, measured from the analyzed image's top-left corner |
| objects[].decision | `keep`, `replace`, `remove`, or `unsure`; initially `unsure` |

Bounding boxes must use the uploaded image's orientation-corrected
pixel dimensions. Any detection-service resizing must be mapped
back to that coordinate system. Right and bottom boundaries are exclusive.

If a successful analysis finds no furniture, return the analysis ID,
image ID, and an empty `objects` array.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | Room ID has an invalid format |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | ROOM_NOT_FOUND | Room does not exist or belongs to another user |
| 409 Conflict | ANALYSIS_NOT_READY | No successful analysis is available |


### Update a Furniture Decision

PATCH /api/objects/{id}/decision

Updates the user's decision for one detected furniture item.

**Authentication:** Required.
The user must own the room project containing the item.

**Path parameter:** `id` — the detected item's identifier,
not the room ID.

**Request content type:** `application/json`

| Field | Type | Required | Allowed values |
|---|---|---|---|
| decision | string | Yes | `keep`, `replace`, `remove`, `unsure` |

Values are lowercase and case-sensitive.

**Example request:** `PATCH /api/objects/301/decision`

```json
{
  "decision": "keep"
}
```

**Success: 200 OK**

```json
{
  "id": 301,
  "decision": "keep"
}
```

The ID is illustrative.
Only the decision changes; detection details remain unchanged.
Submitting the current decision again returns `200 OK`.

Selecting `remove` records a redesign preference.
It does not delete the detected item.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | Item ID has an invalid format |
| 400 Bad Request | VALIDATION_ERROR | Decision is missing or is not an allowed value |
| 400 Bad Request | INVALID_JSON | Malformed JSON |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | OBJECT_NOT_FOUND | Item does not exist or belongs to another user |

**Example validation error**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Decision must be keep, replace, remove, or unsure."
}
```

### Save Room Preferences

PUT /api/rooms/{id}/preferences

Creates or replaces the complete preferences for a room project.

**Authentication:** Required.
The user must own the room project.

**Path parameter:** `id` — the room project's identifier.

**Request content type:** `application/json`

All fields are required, including when updating existing preferences.

| Field | Type | Validation |
|---|---|---|
| style | string | `minimalist`, `cozy`, `modern`, or `scandinavian` |
| budgetLimit | number | Zero or greater; at most two decimal places |
| currency | string | `USD` only for this draft |
| rentalFriendly | boolean | `true` or `false` |

A zero budget means no planned spending, not an unlimited budget.
Style values are lowercase and case-sensitive.
USD-only support is a proposal requiring team approval.

**Example request**

```json
{
  "style": "cozy",
  "budgetLimit": 150.0,
  "currency": "USD",
  "rentalFriendly": true
}
```

**Success**

- `201 Created` when preferences are first created.
- `200 OK` when existing preferences are replaced.

Both responses return the saved preferences:

```json
{
  "roomId": 42,
  "style": "cozy",
  "budgetLimit": 150.0,
  "currency": "USD",
  "rentalFriendly": true
}
```

The room ID is illustrative.
Repeating the same request produces the same saved settings.
Omitted fields are rejected; they do not retain previous values.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | Room ID has an invalid format |
| 400 Bad Request | VALIDATION_ERROR | Missing field, wrong type, or unsupported value |
| 400 Bad Request | INVALID_JSON | Malformed JSON |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | ROOM_NOT_FOUND | Room does not exist or belongs to another user |

**Example validation error**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Budget limit must be zero or greater."
}
```

### Get Room Preferences

GET /api/rooms/{id}/preferences

Returns the room's saved preferences.

This endpoint is a proposed addition to the ticket's initial list.

**Authentication:** Required.
The user must own the room project.

**Path parameter:** `id` — the room project's identifier.

**Request body:** None.

**Success: 200 OK**

```json
{
  "roomId": 42,
  "style": "cozy",
  "budgetLimit": 150.0,
  "currency": "USD",
  "rentalFriendly": true
}
```

The room ID is illustrative.
Fields follow the same definitions as Save Room Preferences.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | Room ID has an invalid format |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | ROOM_NOT_FOUND | Room does not exist or belongs to another user |
| 404 Not Found | PREFERENCES_NOT_FOUND | The owned room exists, but preferences have not been saved |

**Example error**

```json
{
  "code": "PREFERENCES_NOT_FOUND",
  "message": "No preferences have been saved for this room."
}
```

When preferences have not been saved, the frontend displays
the initial setup form. The backend does not invent saved values.

### Start a Room Redesign

POST /api/rooms/{id}/redesign

Starts redesign generation using a successful analysis,
the user's furniture decisions, and saved room preferences.

**Authentication:** Required.
The user must own the room project.

**Request content type:** `application/json`

| Field | Type | Required | Validation |
|---|---|---|---|
| analysisId | integer | Yes | Must identify the room's latest successful analysis |

**Example request**

```json
{
  "analysisId": 201
}
```

**Prerequisites**

- The selected analysis succeeded and belongs to this room.
- Room preferences have been saved.
- No analysis or redesign is currently running for this room.
- Every detected item has a valid furniture decision.

For this draft, `unsure` is allowed and treated as `keep`
during generation. The saved decision remains `unsure`.
This behavior requires team approval.

The backend captures the selected image, detections, decisions,
and preferences when accepting the job. Later edits do not
change the inputs of an already accepted job.

**Success: 202 Accepted**

```json
{
  "designId": 401,
  "roomId": 42,
  "analysisId": 201,
  "status": "pending"
}
```

IDs are illustrative.
Acceptance does not mean generation has succeeded.

The frontend checks progress using the proposed endpoint:
`GET /api/rooms/{id}/designs/{designId}`.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | Room ID has an invalid format |
| 400 Bad Request | VALIDATION_ERROR | Missing or invalid analysis ID |
| 400 Bad Request | INVALID_JSON | Malformed JSON |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | ROOM_NOT_FOUND | Room does not exist or belongs to another user |
| 404 Not Found | ANALYSIS_NOT_FOUND | Analysis does not exist in this room |
| 409 Conflict | ANALYSIS_NOT_READY | Selected analysis has not succeeded |
| 409 Conflict | STALE_ANALYSIS | Selected analysis is not the latest successful one |
| 409 Conflict | PREFERENCES_REQUIRED | Room preferences have not been saved |
| 409 Conflict | PROCESSING_IN_PROGRESS | This room already has an active analysis or redesign |
| 503 Service Unavailable | REDESIGN_UNAVAILABLE | Generation cannot currently be accepted |

**Example error**

```json
{
  "code": "PREFERENCES_REQUIRED",
  "message": "Save your room preferences before generating a redesign."
}
```


### Get Redesign Progress and Result

GET /api/rooms/{id}/designs/{designId}

Returns a redesign job's status and its result when available.
This endpoint is a proposed addition to the ticket's initial list.

**Authentication:** Required.
The user must own the room project.

**Path parameters**

- `id`: Room project identifier.
- `designId`: Redesign identifier belonging to that room.

**Request body:** None.

**Success: 200 OK**

Example while processing:

```json
{
  "designId": 401,
  "roomId": 42,
  "analysisId": 201,
  "status": "processing",
  "result": null,
  "error": null
}
```

| Status | Meaning | result | error |
|---|---|---|---|
| pending | Waiting to start | null | null |
| processing | Generation is running | null | null |
| succeeded | Result was saved | Result object | null |
| failed | Generation failed | null | Error object |

Example after success:

```json
{
  "designId": 401,
  "roomId": 42,
  "analysisId": 201,
  "status": "succeeded",
  "result": {
    "imageUrl": "https://example.com/temporary-design-image",
    "imageUrlExpiresAt": "2026-09-17T15:30:00Z",
    "summary": "Keep the bed and add freestanding lighting.",
    "recommendations": [
      {
        "id": "rec-1",
        "name": "Freestanding floor lamp",
        "placement": "Beside the bed, clear of the walkway.",
        "estimatedCost": 35.0,
        "rentalFriendly": true,
        "lowerCostAlternative": {
          "name": "Secondhand floor lamp",
          "estimatedCost": 15.0
        }
      }
    ],
    "diySuggestions": [
      {
        "id": "diy-1",
        "objectId": 301,
        "title": "Refresh the bed with a washable throw",
        "supplies": [
          {
            "name": "Washable throw",
            "quantity": 1,
            "estimatedCost": 20.0
          }
        ],
        "steps": [
          "Check the throw's care instructions.",
          "Wash and dry as directed.",
          "Fold it across the foot of the bed."
        ],
        "safetyWarnings": [
          "Keep fabric away from heaters."
        ],
        "estimatedCost": 20.0,
        "rentalFriendly": true
      }
    ],
    "budget": {
      "currency": "USD",
      "budgetLimit": 150.0,
      "recommendationsTotal": 35.0,
      "diyTotal": 20.0,
      "estimatedTotal": 55.0,
      "remainingBudget": 95.0,
      "overBudget": false,
      "excludedCosts": [
        "Tax",
        "Shipping"
      ]
    }
  },
  "error": null
}
```

IDs, URL, and timestamp are illustrative.
The result image is a static redesign concept.

| Result field | Type | Meaning |
|---|---|---|
| imageUrl | string | Temporary HTTPS URL for viewing the generated image |
| imageUrlExpiresAt | string | URL expiry time in RFC 3339 UTC format |
| summary | string | Short explanation of the redesign |
| recommendations | array | Suggested items and optional lower-cost alternatives |
| diySuggestions | array | Supplies, ordered steps, safety notes, and costs |
| budget | object | Captured budget, totals, exclusions, and over-budget flag |

The backend issues a fresh temporary image URL when necessary.
After expiry, the frontend fetches this endpoint again.
The backend checks ownership before issuing the URL.

Example after failure:

```json
{
  "designId": 401,
  "roomId": 42,
  "analysisId": 201,
  "status": "failed",
  "result": null,
  "error": {
    "code": "GENERATION_FAILED",
    "message": "Redesign generation failed. Please try again."
  }
}
```

A failed job still returns `200 OK` because its status was
retrieved successfully. Internal provider errors are not exposed.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | A path ID has an invalid format |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | ROOM_NOT_FOUND | Room does not exist or belongs to another user |
| 404 Not Found | DESIGN_NOT_FOUND | Design does not exist in this room |



### Recommendation, DIY, and Budget Rules

These additions are proposed for team review.

- `recommendations` and `diySuggestions` are arrays; use `[]` when empty.
- Recommendation and DIY IDs are strings unique within a design.
- All costs use the currency in `budget.currency`.
- Costs are non-negative estimates, not live prices or purchase guarantees.
- `lowerCostAlternative` is an object or `null`.
- Alternatives are optional swaps and are excluded from the estimated total.
- Each recommendation's estimated cost covers the complete suggested item.
- Each supply's estimated cost covers its stated quantity.
- A DIY suggestion's estimated cost equals its supplies' total.
- `objectId` references furniture from the selected analysis, or is `null`
  for suggestions not associated with a detected item.
- `steps` is an ordered array of instructions.
- `safetyWarnings` is an array of relevant precautions.
- `rentalFriendly` is a boolean, not a guarantee of lease compliance.
- When rental-friendly mode is enabled, suggestions must avoid permanent
  alterations such as drilling or painting.
- Suggestions must respect furniture decisions: kept items cannot be
  replaced or removed. Proposed treatment of `unsure` remains `keep`.
- Recommendations and DIY supplies must not double-count the same expense.

Budget calculations:

- recommendationsTotal = sum of recommendation estimated costs.
- diyTotal = sum of DIY estimated costs.
- estimatedTotal = recommendationsTotal + diyTotal.
- remainingBudget = budgetLimit - estimatedTotal; may be negative.
- overBudget = estimatedTotal > budgetLimit.
- budgetLimit and currency come from the preferences captured at generation.
- Use decimal arithmetic for money.
- Excluded costs must be listed explicitly.
- A result exceeding the budget must set overBudget to true so the
  frontend can display a warning.

Generated advice must remain beginner-friendly and avoid hazardous
electrical, structural, or load-bearing modifications.

### List Room Redesigns

GET /api/rooms/{id}/designs

Returns redesign jobs for a room, including pending, processing,
succeeded, and failed jobs.

**Authentication:** Required.
The user must own the room project.

**Path parameter:** `id` — the room project's identifier.

**Request body:** None.
**Query parameters:** `page` and `pageSize`; shared pagination rules apply.

**Success: 200 OK**

```json
{
  "designs": [
    {
      "designId": 401,
      "analysisId": 201,
      "status": "succeeded",
      "createdAt": "2026-09-17T14:00:00Z"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "hasNext": false
}
```

IDs and timestamps are illustrative.

| Field | Type | Meaning |
|---|---|---|
| designId | integer | Redesign identifier |
| analysisId | integer | Analysis used for generation |
| status | string | `pending`, `processing`, `succeeded`, or `failed` |
| createdAt | string | Job acceptance time in RFC 3339 UTC format |

Return jobs newest first by `createdAt`.
The frontend retrieves progress or a complete result using
`GET /api/rooms/{id}/designs/{designId}`.

If the room has no redesigns, return `200 OK` with:

```json
{
  "designs": [],
  "page": 1,
  "pageSize": 20,
  "hasNext": false
}
```

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_ID | Room ID has an invalid format |
| 400 Bad Request | VALIDATION_ERROR | Invalid pagination |
| 401 Unauthorized | UNAUTHENTICATED | Missing, invalid, or expired login token |
| 404 Not Found | ROOM_NOT_FOUND | Room does not exist or belongs to another user |



## Backend-to-Computer-Vision Endpoints

### Detect Furniture — Internal Service

POST /cv/detect

Analyzes one image and returns furniture detections to the backend.

**Caller:** Spring Boot backend only.
**Authentication:** A separate service credential is required.
Do not forward the user's login token.
Send `Authorization: Bearer <cv-service-token>` using a separate service secret.
The secret value is deployment configuration and must not appear in this document.

**Request content type:** `multipart/form-data`

| Field | Type | Required | Validation |
|---|---|---|---|
| file | File | Yes | Valid, non-empty JPG/PNG; at most 10,000,000 bytes |

The backend retrieves the selected stored image and sends its bytes.
Apply the image dimension and timeout defaults in Consolidated Draft Decisions.

This internal call is synchronous: it returns detections when complete.
The public analysis job remains asynchronous while the backend waits.

**Success: 200 OK**

```json
{
  "imageWidth": 1200,
  "imageHeight": 900,
  "objects": [
    {
      "class": "bed",
      "confidence": 0.94,
      "bbox": [
        120,
        210,
        580,
        640
      ]
    }
  ]
}
```

| Field | Type | Meaning |
|---|---|---|
| imageWidth | integer | Orientation-corrected image width in pixels |
| imageHeight | integer | Orientation-corrected image height in pixels |
| objects | array | Detected furniture; empty if none found |
| objects[].class | string | Furniture category from the agreed label set |
| objects[].confidence | number | Score from 0 to 1 |
| objects[].bbox | array of four integers | `[left, top, right, bottom]` |

Bounding-box rules:

- Origin is the top-left corner of the orientation-corrected image.
- `0 <= left < right <= imageWidth`.
- `0 <= top < bottom <= imageHeight`.
- Right and bottom boundaries are exclusive.
- Coordinates must be mapped back after any model resizing.

CV does not assign persistent furniture IDs or user decisions.
The backend validates the response, assigns IDs, and stores detections.
It marks the public analysis job `succeeded` only after saving results.

No detections is a successful result with an empty `objects` array.

**Errors**

| Status | Code | Meaning |
|---|---|---|
| 400 Bad Request | INVALID_IMAGE | Missing, empty, or unreadable image |
| 401 Unauthorized | INVALID_SERVICE_CREDENTIALS | Missing or invalid service credential |
| 413 Content Too Large | IMAGE_TOO_LARGE | File exceeds the size limit |
| 415 Unsupported Media Type | UNSUPPORTED_IMAGE_TYPE | Format is not JPG or PNG |
| 500 Internal Server Error | DETECTION_FAILED | Unexpected detection failure |
| 503 Service Unavailable | MODEL_UNAVAILABLE | Detection model is unavailable |

Errors follow the shared `code` and `message` format.

The backend maps internal failures, timeouts, and invalid CV responses
to a failed public analysis job without exposing internal details.

See Consolidated Draft Decisions for the proposed default.

## Consolidated Draft Decisions

These are explicit proposed defaults for version 0.1.0. The team must approve or
amend them before implementation; they supersede earlier illustrative wording.
Backend, frontend, and CV reviewers should review the same version together.

### IDs, formats, and versioning

- Persistent user, room, image, analysis, and design IDs are positive JSON integers,
  at most 9,007,199,254,740,991 (safe for JavaScript clients). Path IDs use decimal
  digits without a sign or leading zeros. Recommendation/DIY IDs remain strings.
- Public routes retain the Jira-proposed `/api` prefix; CV retains `/cv`.
  This is an unversioned initial API. A breaking released change requires a new
  versioned route namespace and a coordinated migration; do not silently change v0.1.
- JSON requests reject unknown fields, incorrect types, and null required fields
  with `400 VALIDATION_ERROR`. Responses may add optional fields; clients ignore
  unknown response fields. Every shown response field is required unless nullable
  or explicitly optional. Successful responses use `application/json`.
- For all JSON endpoints, an unsupported request content type returns
  `415 UNSUPPORTED_MEDIA_TYPE`; invalid JSON returns `400 INVALID_JSON`.
- Common unexpected failures return `500 INTERNAL_ERROR` with a generic message.
  Do not return provider messages, credentials, stack traces, or private data.
- Protected endpoints verify authentication and ownership before exposing child
  records. `401` responses include `WWW-Authenticate: Bearer`. A foreign room or
  child resource is indistinguishable from a missing one.

### Pagination and ordering

- All room, image, and design collection endpoints accept `page` (integer >=1,
  default 1) and `pageSize` (integer 1–100, default 20).
- Responses include their named array, `page`, `pageSize`, and `hasNext` (boolean).
  An out-of-range page succeeds with an empty array and `hasNext: false`.
- Sort by server-recorded creation time descending, then ID descending to break
  ties. Room creation timestamps may remain internal to the backend.
- Furniture objects are returned as one analysis result, without pagination.

### Account and field validation

- Room names: trim surrounding whitespace; require 1–100 Unicode code points.
- Email: trim surrounding whitespace, lowercase for storage/login/uniqueness;
  require a single address without display name, whitespace, or control characters,
  one `@`, a non-empty local part and a valid DNS-style domain; maximum 254 ASCII
  characters. Internationalized email support is outside this draft.
- Password: 15–128 Unicode code points; do not trim or silently transform it.
  No required mix of character categories. Reject known-compromised passwords at
  registration using a server-managed check that never logs plaintext passwords.
- Registration does not issue a token. Email verification is not enabled in this
  initial draft; the email is an unverified account identifier, not proof of ownership.
- Access tokens expire after 3,600 seconds. No refresh-token endpoint in this draft.
  Logout clears the client token; immediate server revocation is not provided.
- Proposed rate limits: registration 5 attempts per IP per 15 minutes; login 10
  failed attempts per normalized email and 50 attempts per IP per 15 minutes.
  `429` includes `Retry-After` as integer seconds until another attempt is allowed.
  Team security review must approve these deployment-sensitive choices.
- Draft currency: USD. Monetary amounts use decimal arithmetic, non-negative values
  with at most two decimal places, except remainingBudget may be negative.
  Budget limit maximum: 1,000,000.00 USD. JSON does not preserve trailing zeroes.

### Images, analysis, and reanalysis

- Uploads create additional immutable images; never overwrite existing image IDs.
  Images are owned through their room. The user selects an image for analysis.
- JPG/PNG only, 1–10,000,000 bytes; decoded image maximum 25,000,000 pixels and
  maximum 10,000 pixels per dimension. Oversize dimensions return
  `400 INVALID_IMAGE`. Apply limits on both backend and CV service.
- List Room Images serves orientation-corrected previews and dimensions.
  Generated image links and original image links expire after 15 minutes.
- Accepting analysis or redesign reserves one room-wide processing slot atomically.
  A duplicate active request returns `409 PROCESSING_IN_PROGRESS`.
- An analysis request captures its image. Reanalysis produces new object IDs with
  `unsure` decisions. Old results remain until a new analysis succeeds; failure
  leaves the previous successful result available. A successful reanalysis replaces
  the current result set. Old design snapshots remain unchanged.
- Decision updates on superseded detections return `409 STALE_OBJECT`; updates on
  current detections during processing return `409 PROCESSING_IN_PROGRESS`.
  These checks and updates must be atomic with job acceptance.
- Furniture responses include analysisId and imageId. Clients compare these with
  the job they tracked; a newer completed analysis may have replaced the current set.
- Preference updates and new uploads do not modify already captured job inputs.
- Treat `unsure` as `keep` for generation without changing the saved decision.
- Successful redesigns are saved automatically. No separate save action is required.

### Job lifecycle and internal CV contract

- Job transitions: pending -> processing -> succeeded or failed. Terminal states
  do not change. A retry creates a new job ID; POST is not automatically retried
  after an ambiguous network failure. Refresh job lists before resubmitting redesigns.
- Frontend polls every 3 seconds while its status screen is visible; stop on terminal
  status or navigation away. Client waiting does not imply server cancellation.
- Proposed deadlines from acceptance: analysis 120 seconds; redesign 300 seconds.
  Expired jobs fail with `ANALYSIS_TIMEOUT` or `GENERATION_TIMEOUT`, clear the
  processing slot, and reject late results. These are `error.code` values in the
  successful status lookup response, not HTTP errors from the lookup itself.
- CV HTTP timeout: 90 seconds; no automatic retries in v0.1. Backend network/response
  failures map to DETECTION_FAILED unless the analysis deadline has expired.
- CV uses a separate bearer service secret over HTTPS in deployed environments.
  Rotate it at least every 90 days and on suspected exposure, allowing a short
  coordinated overlap. Unauthorized calls return the documented 401 error.
- Draft CV labels: bed, sofa, chair, table, desk, wardrobe, dresser, bookshelf,
  nightstand, cabinet, lamp. CV maps model-specific labels to this vocabulary;
  unsupported classes are omitted. Confidence threshold: >=0.50, maximum 100
  results, ordered by descending confidence. CV team must confirm model support.
- CV maps boxes to orientation-corrected original dimensions and uses exclusive
  right/bottom bounds. Backend validates labels, finite confidence in [0,1],
  four integer coordinates, and bounds before persisting a result.
- FastAPI validation exceptions must be mapped to this documented error envelope
  and 400 status rather than exposing its default validation response format.

### Nested redesign result types

All nested fields shown in the successful example are required. Empty arrays
are permitted. Text fields are strings. IDs follow the shared rules except local
recommendation and DIY IDs, which are non-empty strings unique per design.

| Object | Fields and types |
|---|---|
| Recommendation | id, name, placement: string; estimatedCost: number; rentalFriendly: boolean; lowerCostAlternative: object or null |
| Alternative | name: string; estimatedCost: number, strictly below the recommendation cost |
| DIY suggestion | id, title: string; objectId: integer or null; supplies: array; steps: non-empty array of strings; safetyWarnings: array of strings; estimatedCost: number; rentalFriendly: boolean |
| Supply | name: string; quantity: positive integer; estimatedCost: number for the complete quantity |
| Budget | currency: string; budgetLimit, recommendationsTotal, diyTotal, estimatedTotal, remainingBudget: numbers; overBudget: boolean; excludedCosts: array of strings |

A zero-price recommendation has no lower-cost alternative. Kept items cannot
be removed/replaced by the proposed design. A result is accepted only after the
backend checks required fields and the documented arithmetic/constraint rules;
invalid provider output fails the job with GENERATION_FAILED. Image fidelity and
product fit remain estimates, not guaranteed measurements.

## Acceptance Criteria Review

| ROOM-6 criterion | Coverage |
|---|---|
| MVP endpoints documented | 16 public endpoints and one internal CV endpoint |
| Request/response examples | JSON examples plus multipart field definitions |
| Appropriate HTTP methods | POST creation/jobs, GET reads, PUT full preferences, PATCH decisions |
| Common errors | Shared envelope, global error rules, endpoint tables, job errors |
| Authentication marked | Public account routes, protected owner-only routes, internal service credential |
| FE-to-BE and BE-to-CV defined | Separate sections with image/coordinate and async/sync boundaries |
| Can be represented in OpenAPI | Explicit paths, types, required fields, enums, statuses, nullability and defaults |

This Markdown contract does not itself generate Swagger UI. OpenAPI authoring
or controller annotations can follow the agreed contract in implementation.
No live API tests were performed as part of drafting this documentation.

## Approval and Delivery

All consolidated defaults are proposals, including those introduced to close
review gaps. Required reviewers: backend, frontend, and CV representatives;
account/security and model limits particularly need owner confirmation.

1. Review proposed defaults and amend this document in the ROOM-6 branch.
2. Validate examples and review the Markdown preview.
3. Open a PR into develop, titled `[ROOM-6] docs: define REST API contract`.
4. Obtain the required teammate review and resolve feedback before merging.
5. Mark the ticket complete only after the team's acceptance workflow is satisfied.

No GitHub commit, PR, merge, or Jira transition is claimed by this document.
