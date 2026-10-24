# Roomify Backend

Spring Boot API for **Roomify**, which turns one photo of your own room into a budget-aware,
rental-aware redesign: detected furniture you can keep, replace or remove, an AI redesign plan,
a visual concept of the same room, shopping suggestions with cheaper alternatives, and beginner
DIY guides. *Redesign more, buy less.*

> **Release status: v0.9.0-rc1 — AI features unverified.** Accounts, projects, upload, detection,
> review and preferences are tested end to end against the real stack. Plan, visual concept,
> suggestion and DIY generation are implemented and tested with fake providers and with no key (clear
> `503 AI_CONFIGURATION_ERROR`), but have **not yet been run against the live OpenAI API**, including
> the new default image model `gpt-image-2.5-sunburst`.

Related services: [roomify_frontend](https://github.com/Bootcamp2Roomify/roomify_frontend) (Next.js) ·
[roomify_cv](https://github.com/Bootcamp2Roomify/roomify_cv) (FastAPI + YOLO11).
Architecture and the hackathon submission package live in [`docs/submission/`](docs/submission/).

## What it does

| MVP feature | Endpoints |
|---|---|
| Accounts and private projects | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| Project setup, dashboard | `POST /api/projects`, `GET /api/projects`, `GET /api/projects/{id}` |
| Photo upload | `POST /api/projects/{id}/image`, `GET …/image`, `GET …/image/content` |
| Furniture detection (via CV service) | `POST /api/projects/{id}/analysis`, `GET …/analysis` |
| Keep / Replace / Remove / Unsure, manual corrections | `PATCH …/objects/{objectId}`, `POST …/objects`, `PUT …/objects/{objectId}/label`, `DELETE …/objects/{objectId}` |
| Style, budget, rental mode, requirements | `GET/PUT …/preferences`, `GET …/preferences/limits` |
| Redesign plan (LLM) | `POST …/designs`, `GET …/designs/latest`, `GET …/designs/{designId}` |
| Visual concept (image model) | `POST …/designs/{designId}/prototype`, `GET …/designs/{designId}/image/content` |
| Shopping suggestions and DIY guides | `POST/GET …/recommendations`, `POST/GET …/recommendations/{itemId}/diy` |

Every `/api/projects/{id}/**` route requires `Authorization: Bearer <token>` and the caller must own
the project; anything else returns `404 PROJECT_NOT_FOUND`. Generated plans and suggestions are
validated server-side (budget totals, furniture decisions, rental restrictions, DIY safety) before they
are stored; invalid model output is retried once and otherwise rejected.

Contracts: [`docs/api-contract.md`](docs/api-contract.md), [`docs/design/API.md`](docs/design/API.md),
[`contracts/recommendations/`](contracts/recommendations/README.md).

## Stack

Java 21 · Spring Boot 3.5.0 (Web, Validation, Data JPA, Security / OAuth2 resource server) ·
PostgreSQL 17 + Flyway · OpenAI (`gpt-4o-mini` text, `gpt-image-2.5-sunburst` image edits) · AWS S3 or local
disk for images. Exact versions: [`docs/submission/DEPENDENCIES.md`](docs/submission/DEPENDENCIES.md).

## Run locally

Prerequisites: Java 21, Docker. The CV service should be running on `http://localhost:8000`
(see roomify_cv) for photo analysis.

```bash
cp .env.example .env          # then edit: DB_PASSWORD, JWT_SECRET (>= 32 bytes), OPENAI_API_KEY
docker compose up -d db       # Postgres 17.11 on 127.0.0.1:5433
DB_PORT=5433 ./mvnw spring-boot:run
curl localhost:8080/api/health
```

Or everything in containers: `docker compose up --build` (backend on `127.0.0.1:8080`).

`.env` is git-ignored and loaded automatically; never commit real values. Without `OPENAI_API_KEY`,
everything works except generation, which returns `503 AI_CONFIGURATION_ERROR`.

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | `localhost`, `5432`, `roomify`, –, – | PostgreSQL |
| `JWT_SECRET` | – (required) | HS256 signing secret, at least 32 bytes |
| `JWT_TTL` | `1h` | Access-token lifetime |
| `OPENAI_API_KEY` | – | OpenAI key for plans, suggestions and images (`AI_API_KEY` overrides it for text) |
| `AI_MODEL` | `gpt-4o-mini` | Text model |
| `AI_IMAGE_MODEL`, `AI_IMAGE_SIZE`, `AI_IMAGE_QUALITY` | `gpt-image-2.5-sunburst`, `1024x1024`, `low` | Visual concept (gpt-image-1 was retired 2026-10-23) |
| `AI_READ_TIMEOUT`, `AI_MAX_OUTPUT_TOKENS`, `AI_MAX_RETRIES` | `180s`, `4000`, `1` | Provider limits |
| `AI_TEMPERATURE`, `AI_REASONING_EFFORT` | `0.1`, empty | Leave temperature empty and set effort (e.g. `low`) for reasoning models such as `gpt-6-luna` |
| `CV_SERVICE_URL` (or `VISION_SERVICE_BASE_URL`) | `http://localhost:8000` | CV service |
| `STORAGE_TYPE` | `local` | `local` or `s3` |
| `STORAGE_LOCAL_ROOT` | `./storage` | Image folder when `local` |
| `S3_BUCKET`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | – | Image bucket when `s3` (prefer an IAM role over keys) |
| `ROOM_IMAGE_MAX_SIZE_BYTES`, `DESIGN_IMAGE_MAX_SIZE_BYTES` | `10485760` | Upload limits |
| `PREFERENCES_MAX_BUDGET_AMOUNT` | `1000000` | Highest accepted budget |

## Test

Tests need a PostgreSQL database (they run Flyway migrations):

```bash
docker compose up -d db
DB_PORT=5433 DB_PASSWORD=<from .env> JWT_SECRET=<from .env> ./mvnw verify
```

CI (`.github/workflows/ci.yml`) runs the same suite against `postgres:17.11` and builds the image.

## Contributing

Branching, commit and PR conventions: [CONTRIBUTING.md](CONTRIBUTING.md). Licensed under [LICENSE](LICENSE).
