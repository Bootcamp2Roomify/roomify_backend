# Recommendation and DIY contracts — v1

These draft-2020-12 schemas describe generated payloads independently of a service/runtime. Generation lives in `RecommendationGenerationService` (`POST /api/projects/{id}/recommendations`, `POST …/recommendations/{itemId}/diy`). The existing Spring backend owns validation and persistence; ROOM-99/100 own generation.

## Files and usage

- `v1/recommendation-set.schema.json`: ordered shopping suggestions and budget summary.
- `v1/diy-guide.schema.json`: ordered DIY instructions, cost, tools and safety notes.
- `v1/examples/`: valid exchange payloads; `invalid/` demonstrates structural rejections.
- `com.roomify.dto.recommendation`: matching immutable Java records.
- `RecommendationContractCodec`: strict decoder/encoder and domain validator.

Java consumers call `codec.readSet(json)` or `codec.readGuide(json)` before using generated data. Typed callers call `codec.validate(payload)` or the corresponding write method. Invalid input throws a generic IllegalArgumentException without including supplied payloads. Schema checks are portable; the Java domain checks remain mandatory.

## Money, currency and alternatives

Every amount is a JSON number in one set/guide currency, TWD or USD, from 0 to 9999999999.99 in 0.01 increments. Trailing zeros are not significant. Java uses BigDecimal: 0.10 + 0.20 = 0.30. Clients format amounts for display; the backend calculates authoritative totals. Numeric schemas do not change JavaScript floating-point arithmetic. No shared Jackson configuration changes are required.

The set has `schemaVersion: "1.0"`, `currency`, `budget`, `estimatedTotal`, `remainingBudget`, and up to six `items`. An empty affordable-purchases list is allowed. Generation owns the three-to-six-items rule when budget permits.

Each item requires `name`, `category`, `reason`, `placement`, `estimatedPrice`, `cheaperAlternative`, `searchQuery`, and boolean `diyEligible`. `placement` (added for MVP Feature 7, "where it could be placed") is a required key whose value is null or a nonblank string of at most 300 characters. A required `cheaperAlternative` key is null or an object with required name/reason/searchQuery/estimatedPrice. Its price must be strictly lower than the item price in the same currency. Alternatives are options, not additional purchases; exclude them from the total. A zero-price primary item cannot have a cheaper alternative. Search queries are descriptive suggestions; prices are estimates, not live retailer quotes.

Domain validation additionally requires the sum of primary item prices to equal estimatedTotal, estimatedTotal <= budget, and remainingBudget = budget - estimatedTotal. These relationships and the cheaper-than rule cannot be represented by the structural schemas alone.

## DIY and safety

Guide fields are schemaVersion/currency/materials/tools/estimatedCost/difficulty/estimatedTime/steps/safetyNotes. Materials/tools are arrays of nonblank strings and may be empty. Steps and safetyNotes are nonempty arrays of nonblank strings. Array order is step order; the UI derives numbering. Rich step objects are deferred. Safety notes are always required; structural validation cannot certify that instructions are safe. The generation owner must reject unsafe structural/electrical work and compare DIY cost with purchase cost.

## IDs and metadata

Generated payloads contain no database IDs; extra fields are rejected. Persistence assigns stable public UUIDs. GenerationMetadata is a separate allowlisted record: provider/model/schemaVersion and nullable requestId. Do not supply credentials, raw prompts or HTTP headers. A request ID is a correlation value, not a deduplication promise. A repeated successful store call creates a new version; callers must not blindly retry an uncertain write expecting idempotency.

## Verification

With Java 21/Maven and the existing backend's local test database configured, run `mvn test`. The focused contract checks require no database: `mvn -Dtest=RecommendationContractsTest,RecommendationContractCodecTest,RecommendationSchemaTest test`.

The test-scoped networknt 1.5.9 validator verifies portable schemas with Jackson 2, matching Spring Boot 3.5. Invalid fixtures must fail both structural validation and the codec; a separate test demonstrates that structurally valid but arithmetically wrong totals fail the codec. No external schema references or network lookups are needed.
