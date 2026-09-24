# Roomify Backend

Backend service for **Roomify**, an AI-powered room redesign and DIY planning application that helps students, renters, young adults, and budget-conscious users create personalized room designs based on their actual space, existing furniture, preferred style, budget, and practical requirements.

---

## Features

- 🔐 User Authentication and Authorization
- 👤 User Profile Management
- 🏠 Room Project Management
- 🖼️ Room Image Upload and Storage
- 🪑 Detected Furniture Management
- ✅ Keep, Replace, Remove, or Unsure Decisions
- 🎨 Style, Color, Budget, and Requirement Management
- 🤖 AI Redesign Generation
- 👁️ Computer Vision Service Integration
- 🛋️ Furniture and Decoration Recommendations
- 🛠️ Beginner-Friendly DIY Suggestions
- 💰 Estimated Project Cost
- 💾 Saved Projects and Dashboard Data

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot |
| Language | Java |
| API | REST API |
| Database | PostgreSQL |
| Computer Vision | Python and FastAPI service |
| AI Services | OpenAI and Gemini |
| Authentication | JWT |
| Image Storage | AWS S3 or compatible cloud object storage |
| Secret Management | AWS Secrets Manager |
| Deployment | To be determined |

---

## System Architecture

Roomify uses separate repositories for the frontend, backend, and computer-vision service.

```text
Next.js Frontend
        |
        v
Spring Boot REST API
        |
        +--------------------+
        |                    |
        v                    v
PostgreSQL          Python FastAPI CV Service
        |
        v
Cloud Object Storage / AWS S3
```

The Spring Boot backend acts as the main API layer. It manages authentication, users, room projects, furniture decisions, design preferences, budgets, recommendations, and saved redesign results.

The computer-vision service analyzes uploaded room images and returns detected furniture information to the backend.

Room images and generated redesign images should be stored in cloud object storage rather than directly inside PostgreSQL.

---

## Planned Project Structure

The backend source code will follow a layered Spring Boot structure:

```text
src/
├── main/
│   ├── java/
│   │   └── com/roomify/
│   │       ├── config/
│   │       ├── controller/
│   │       ├── dto/
│   │       ├── entity/
│   │       ├── exception/
│   │       ├── repository/
│   │       ├── security/
│   │       ├── service/
│   │       └── RoomifyApplication.java
│   └── resources/
│       ├── application.properties
│       └── application.yml
└── test/
    └── java/
```

The exact structure may be updated when the Spring Boot application is initialized.

---

## MVP User Workflow

```text
Create an Account
        ↓
Create a Room Project
        ↓
Upload a Room Image
        ↓
Analyze the Room
        ↓
Review Detected Furniture
        ↓
Keep / Replace / Remove / Unsure
        ↓
Enter Style, Budget, and Requirements
        ↓
Generate a Redesign
        ↓
Review Furniture and DIY Suggestions
        ↓
Save the Project
```

---

## Development Setup

The Spring Boot application will be initialized in Jira task `ROOM-8`.

### Clone the Repository

```bash
git clone https://github.com/Bootcamp2Roomify/roomify_backend.git
cd roomify_backend
```

### Configure the Environment

Create a local environment file from the provided example:

```bash
cp .env.example .env
```

Replace the placeholder values with local development credentials.

Never commit the completed `.env` file.

### Required Environment Variables

| Variable | Purpose |
|---|---|
| `SERVER_PORT` | Port used by the Spring Boot backend |
| `DATABASE_URL` | PostgreSQL connection URL |
| `DATABASE_USERNAME` | PostgreSQL username |
| `DATABASE_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | Secret used to sign authentication tokens |
| `OPENAI_API_KEY` | OpenAI API credential |
| `GEMINI_API_KEY` | Gemini API credential |
| `CV_SERVICE_URL` | URL of the Python computer-vision service |
| `AWS_REGION` | AWS deployment region |
| `AWS_ACCESS_KEY_ID` | AWS access credential |
| `AWS_SECRET_ACCESS_KEY` | AWS secret credential |
| `AWS_S3_BUCKET` | S3 bucket used for image storage |

The repository only contains placeholder values in `.env.example`. Real credentials must be stored securely and should use AWS Secrets Manager in deployed environments.

### Build and Run

Build and startup commands will be added after the Spring Boot project and its build system are initialized.

The planned local backend URL is:

```text
http://localhost:8080
```

Spring Boot API documentation will be documented after the API and Swagger/OpenAPI configuration are implemented.

---

## Security and Sensitive Data

The following information must never be committed to GitHub:

- Completed `.env` files
- Database credentials
- JWT secrets
- OpenAI or Gemini API keys
- AWS access keys
- Cloud-storage credentials
- Private keys or certificates
- Personal user data
- Uploaded room images

Production secrets should be stored securely using AWS Secrets Manager.

The `.gitignore` file excludes environment files, secrets, build output, logs, temporary files, uploaded images, generated images, local databases, and IDE configuration files.

---

## Git Workflow

Roomify follows a feature-branch and Pull Request workflow:

```text
main
│
└── develop
      ├── feature/*
      ├── fix/*
      └── docs/*
```

- **`main`** — stable and production-ready code
- **`develop`** — integration branch for reviewed development work
- **`feature/*`** — new features
- **`fix/*`** — bug fixes
- **`docs/*`** — documentation changes

Team members should not push development work directly into `main` or `develop`.

Each change should be completed on a separate branch and submitted to `develop` through a Pull Request.

---
## Local Development with Docker — ROOM-9

### Requirements

Docker Desktop with Docker Compose running.
Java and PostgreSQL run inside containers.

### Configuration

Create `.env` in the repository root:

```dotenv
DB_PASSWORD=replace_with_your_own_local_password
```

Set your own password. Never commit `.env`.

Compose supplies `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`,
and `DB_PASSWORD` to Spring Boot. Spring Boot does not automatically
load the `.env` file itself.

### Start

```bash
docker compose up --build -d
curl -i http://localhost:8080/api/health
```

The backend is available on port 8080. PostgreSQL is accessible
within the Compose network.

Flyway applies migrations from `src/main/resources/db/migration`.
Do not edit migrations after they have been applied; add a new
versioned migration for subsequent schema changes.

### Verify Persistence

```bash
docker compose --profile test run --rm test
```

The integration test saves a RoomProject, clears the persistence
context, retrieves it for its owner, and checks that another owner
cannot retrieve it through the owner-filtered repository method.

Test records are rolled back. This uses the local development
database and must not target a production database.

### Logs and Shutdown

```bash
docker compose logs --tail=100 backend
docker compose down
```

Database data persists in the named volume after shutdown.
`docker compose down -v` deletes that data.

The Dockerfile skips tests during image creation; run the test
command separately before submitting changes.

## Commit Convention

Roomify uses Conventional Commits:

```text
feat:
fix:
docs:
refactor:
test:
chore:
```

Examples:

```text
feat(auth): add user registration endpoint
fix(project): validate empty room project name
docs: update backend setup instructions
test(auth): add authentication service tests
chore: configure environment example
```

---

## Pull Request Convention

Pull Request titles must include the related Jira issue key:

```text
[ROOM-###] <type>: Brief description
```

Example:

```text
[ROOM-7] docs: Set up backend repository and Git workflow
```

Every Pull Request should:

- Target the `develop` branch
- Include a clear description and summary
- Reference the related Jira issue
- Include testing information when applicable
- Be reviewed by at least one team member
- Resolve review comments and merge conflicts before merging
- Contain no passwords, API keys, or other secrets

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the complete contribution guidelines.

---

## MVP Technical Limitations

The Roomify MVP:

- Does not claim centimeter-perfect room measurements from a single photograph
- Cannot guarantee that recommended furniture will physically fit
- Does not create a complete interactive 3D room model
- Does not support augmented-reality furniture placement
- Does not provide drag-and-drop furniture placement
- Generates a static redesign concept rather than multiple interactive variations
- Does not guarantee real-time product prices or inventory
- Does not automatically purchase recommended products
- Only provides beginner-friendly, low-risk DIY suggestions
- Does not replace a professional interior designer, contractor, or safety inspection

Users should verify room and furniture measurements before purchasing products or making physical changes.

---

## Related Repositories

- [Roomify Frontend](https://github.com/Bootcamp2Roomify/roomify_frontend) — Next.js, React, TypeScript, and Tailwind CSS
- `roomify_cv` — Python and FastAPI computer-vision service; repository will be added during project setup

---

## License

This project is licensed under the MIT License.
