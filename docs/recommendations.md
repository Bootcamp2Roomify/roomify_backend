# Saved recommendations and DIY guides

ROOM-101 builds on ROOM-98's [versioned contracts](../contracts/recommendations/README.md). Spring owns project/design-linked storage and read APIs. Generation remains ROOM-99/100 work; this change does not call providers, implement prompts, create a separate runtime or add ingestion routes.

## Generator integration

Inject `RecommendationResultService` and call:

```java
StoredRecommendationSet saved = results.storeSet(projectId, designResultId, payload,
    new GenerationMetadata("provider-name", "model-name", "1.0", requestId));
UUID itemId = saved.items().get(1).itemId(); // Select an actual eligible saved item.
StoredDIYGuide guide = results.storeGuide(projectId, itemId, diyPayload, metadata);
```

The set must reference an existing design owned by that project, match its design and saved preference currency, and stay within the saved/configured purchase budget. Guide writes require an item owned by that project, marked DIY eligible, and matching the set currency. Generated payloads contain no IDs; persistence assigns UUIDs. Provider metadata is typed and allowlisted; never include raw prompts, credentials or headers.

Each store call is transactional. A set and all item rows commit together; a guide commits completely. Validation and storage failures preserve earlier successful records. Repeated successful calls append new versions, even with the same correlation requestId. There is no idempotency guarantee: do not blindly retry an uncertain write expecting deduplication. A guide write returns its own assigned UUID, including when another guide is saved concurrently.

## Read API

`GET /api/projects/{projectId}/recommendations` returns the latest successful valid set by descending internal sequence ID. Equal timestamps cannot make the choice ambiguous. Response fields:

```json
{
  "setId": "a041fcf9-f428-4020-a9ea-be162df2c437",
  "projectId": "795b5dc6-ac40-45f9-9570-47255c5d627c",
  "designResultId": 12,
  "schemaVersion": "1.0", "currency": "USD",
  "budget": 100.00, "estimatedTotal": 10.00, "remainingBudget": 90.00,
  "items": [{
    "itemId": "d4e8d550-b646-4ec8-abd0-783b5e992164",
    "name": "Storage basket", "category": "storage", "reason": "Organize supplies.",
    "estimatedPrice": 10.00, "cheaperAlternative": null,
    "searchQuery": "small fabric basket", "diyEligible": true
  }],
  "metadata": {"provider": "local", "model": "fixture", "schemaVersion": "1.0", "requestId": null},
  "createdAt": "2026-10-20T00:00:00Z"
}
```

`GET /api/projects/{projectId}/recommendations/{itemId}/diy` returns the latest saved guide for that stable item UUID. Its fields are guideId/itemId/projectId, all generated DIYGuide fields, metadata and createdAt. `{itemId}` is not a set ID. A guide for an older set remains accessible through its original item ID; a newer set never reassigns that guide. These paths match the related Jira generation contract. V1 promises no history query parameters.

Missing project/set/item/guide and a wrong-project item return 404:

```json
{"code":"RECOMMENDATION_NOT_FOUND","message":"Saved recommendation result not found."}
```

Malformed project/item UUIDs return 400 with `code: "INVALID_ID"`. GET performs no generation, status change or write. Existing preference/design/image/analysis APIs retain their behavior.

## Storage and integrity

V7 adds recommendation_sets, recommendation_items and recommendation_diy_guides; V1–V6 remain unchanged. The canonical set document is JSONB. Item JSONB rows are immutable projections at unique array positions, providing public UUID anchors for guides. A transaction writes both representations; reads verify their agreement. Latest-set/project and latest-guide/item indexes support the read queries; unique set/position already supplies the item index.

Foreign keys cascade project/design deletion to sets, sets to items, and items to guides. This is relational cleanup, not a complete account deletion workflow. Independent FKs enforce parent existence; service writes and read-query joins additionally enforce that the design belongs to the same project. An inconsistent link introduced by an out-of-band writer is not exposed by the public reads. Direct database writes bypass contract validation and are not a supported ingestion interface.

## Local verification

Use Java 21, Maven and a dedicated PostgreSQL database. The full suite uses Spring datasource configuration; do not point it at real user/cloud data. No provider calls or credentials are needed. Example for the project-local test runtime (adjust absolute project paths if using another checkout):

```sh
export JAVA_HOME=/Users/ducthanhnguyenpham/Coding/Roomify/.backend-runtime/env/lib/jvm
/Users/ducthanhnguyenpham/Coding/Roomify/.backend-runtime/env/bin/mvn -o \
  -Dmaven.repo.local=/Users/ducthanhnguyenpham/Coding/Roomify/.backend-runtime/m2 \
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55432/roomify_test \
  -Dspring.datasource.username=roomify_test -Dspring.datasource.password= \
  -Dstorage.type=local -Dai.provider=openai -Dai.api-key= \
  -Dai.base-url=http://127.0.0.1:1 -Dollama.base-url=http://127.0.0.1:1 test
```

The local test-only database uses loopback trust, so its password is empty; that setup is not a production authentication recommendation. Maven offline mode requires dependencies to have been downloaded first. Focused classes: RecommendationResultServiceTest, RecommendationControllerTest and RecommendationRecoveryTest. Recovery tests make real committed writes to disposable UUID projects, induce actual PostgreSQL constraint failures, verify transaction rollback and previous successful results, reload in a fresh transaction, test concurrent-write response identity and verify cascades/indexes. Cleanup removes only the UUID projects created by those tests. The tests do not rely on an outer test transaction to hide broken transaction boundaries.
