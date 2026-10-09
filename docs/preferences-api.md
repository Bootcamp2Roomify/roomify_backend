# Project preferences (ROOM-84)

Project IDs are UUIDs. Preferences are independent of object IDs.

- `GET /api/projects/{projectId}/preferences`: 200 with saved preferences; 204 before the first save; 404 if the project does not exist.
- `PUT /api/projects/{projectId}/preferences`: idempotently creates or updates one preference record, returns 200 and the saved data.
- `GET /api/projects/{projectId}/preferences/limits`: returns `{ "maxBudgetAmount": 1000000 }`, using the configured maximum.
- `GET /api/projects/{projectId}`: includes saved data under `preferences` (null if unsaved).

Example PUT body:

```json
{
  "style": "SCANDINAVIAN",
  "budgetAmount": 9000,
  "currency": "TWD",
  "preferredColors": ["sage", "cream"],
  "roomPurpose": "STUDY_AND_SLEEP",
  "rentalFriendly": true,
  "specialRequirements": "Add storage without moving the bed."
}
```

The response adds `projectId` and `maxBudgetAmount`.

Styles: SCANDINAVIAN, COZY_MINIMALIST, MINIMALIST, MODERN, INDUSTRIAL, NO_PREFERENCE.
Purposes: STUDY_AND_SLEEP, STUDY, SLEEP, LIVING, MULTIPURPOSE.
Currencies: TWD or USD, with no conversion.
Budget must be at least 0.01 with at most two fractional digits, and at most `PREFERENCES_MAX_BUDGET_AMOUNT` (default 1000000). Database storage uses NUMERIC(12,2) and Java uses BigDecimal.
Up to 8 preferred colors, each 1–32 characters. Requirements: at most 1000 characters.
Omitted rentalFriendly defaults to false; omitted colors and requirements default to empty.

Validation errors return 400 with `code=VALIDATION_ERROR`, `message`, and `fieldErrors` keyed by request field. An invalid project UUID returns 400 INVALID_PROJECT_ID.

V5 creates project_preferences; V1–V4 are unchanged. A PostgreSQL ON CONFLICT upsert maintains one row per project, including concurrent saves. Existing room_preferences data is not copied automatically because its budget-range schema has no unambiguous single budgetAmount.

Tests: ProjectPreferencesControllerTest covers round trips, repeated updates, snapshot inclusion, defaults, configured maximum, invalid inputs, and unknown projects. Run through the existing Docker test profile or ./mvnw test with PostgreSQL configured.
