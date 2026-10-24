# Dependencies

Every version below is pinned exactly in the file named in the last column. Resolved on 2026-10-24
from the `develop` branches; nothing uses ranges (`^`, `~`, `latest`, bare major tags).

## Runtimes and container images

| Component | Version | Pinned in |
|---|---|---|
| Java (Temurin) | 21 | `roomify_backend/pom.xml` (`java.version`), images below |
| Maven | 3.9.16 | `roomify_backend/.mvn/wrapper/maven-wrapper.properties` |
| Backend build image | `maven:3.9.16-eclipse-temurin-21` | `roomify_backend/Dockerfile`, `compose.yaml` |
| Backend runtime image | `eclipse-temurin:21.0.12_8-jre` | `roomify_backend/Dockerfile` |
| PostgreSQL | `postgres:17.11` | `roomify_backend/compose.yaml`, CI service |
| Python | 3.14 (`python:3.14.9-slim`) | `roomify_cv/Dockerfile`, CI |
| Node.js | 26.4.0 | `roomify_frontend/.nvmrc`, `package.json` `engines`, CI |
| GitHub Actions | checkout v7.0.1, setup-java v6.1.0, setup-node v7.1.0, setup-python v7.1.0 | each repo's `.github/workflows/ci.yml`, pinned by commit SHA |

## Backend (`roomify_backend/pom.xml`)

Versions not written in the POM come from the Spring Boot 3.5.0 parent BOM, which fixes them exactly.

| Dependency | Version | Use |
|---|---|---|
| spring-boot-starter-web / validation / data-jpa | 3.5.0 | REST API, bean validation, JPA |
| spring-boot-starter-security / oauth2-resource-server | 3.5.0 (Spring Security 6.5.0) | BCrypt, HS256 JWT issuing and verification |
| flyway-core / flyway-database-postgresql | 11.7.2 | Schema migrations V1–V10 |
| org.postgresql:postgresql | 42.7.5 | JDBC driver |
| software.amazon.awssdk s3 / signin | 2.55.5 (`aws.sdk.version`) | Image storage on S3 |
| *test:* spring-boot-starter-test 3.5.0, spring-security-test 6.5.0, networknt json-schema-validator 1.5.9 | | Unit/integration tests, contract schema checks |

## CV service (`roomify_cv/requirements.txt`, full tree in `requirements.lock`)

| Dependency | Version | Use |
|---|---|---|
| fastapi | 0.142.2 | HTTP API |
| uvicorn[standard] | 0.54.0 | ASGI server |
| python-multipart | 0.0.32 | File uploads |
| ultralytics | 8.4.171 | YOLO11 inference |
| torch / torchvision (via ultralytics) | 2.14.1 / 0.29.1 | CPU inference (CPU wheels in Docker/CI) |
| pillow | 12.3.0 | Image decoding |
| httpx | 0.28.1 | `image_url` fetching |
| *dev:* pytest | 9.1.1 | Tests (`requirements-dev.txt`) |
| Model weights | `yolo11n.pt` (Ultralytics release asset) | Baked into the image at build time |

## Frontend (`roomify_frontend/package.json` + `package-lock.json`)

| Dependency | Version | Use |
|---|---|---|
| next | 15.5.27 | App Router, route handlers, middleware |
| react / react-dom | 19.0.0 | UI |
| gsap / @gsap/react | 3.13.0 / 2.1.2 | Landing-page motion |
| *dev:* typescript 5.6.2, tailwindcss 3.4.13, eslint 8.57.1 + eslint-config-next 15.5.27, vitest 5.0.3, @testing-library/react 16.2.0, @playwright/test 1.63.0 | | Build, lint, tests |

## External services (not packages)

| Service | Model / API | Configured by |
|---|---|---|
| OpenAI Chat Completions | `gpt-4o-mini`, `response_format: json_object` | `AI_MODEL`, `OPENAI_API_KEY` |
| OpenAI Images (edits) | `gpt-image-2.5-sunburst`, 1024×1024, `low` quality (replaces `gpt-image-1`, retired 2026-10-23; not yet called live) | `AI_IMAGE_MODEL`, `AI_IMAGE_SIZE`, `AI_IMAGE_QUALITY` |
| AWS S3 | any bucket in `AWS_REGION` | `STORAGE_TYPE=s3`, `S3_BUCKET` |

Model names are configuration, not code: switching models needs no rebuild.
