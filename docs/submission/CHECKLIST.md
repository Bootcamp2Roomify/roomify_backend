# Roomify submission checklist (2026-10-26, release v1.0.1)

The Devpost project page needs a login and the hackathon's rules page could not be read, so this maps
the requirements from the project brief plus Devpost's standard submission form. If the hackathon has
extra rules, add them here.

Status values: **Done**, **Owner** (needs the project owner or team).

The OpenAI account was unusable at submission (one key rejected with 401, the other out of credit with
429), so the release runs its AI features in **demo mode** (`AI_DEMO_MODE=true`): pre-generated results from a
bundled library, labelled "Demo" in the app and `source: "demo"` in the API. See EVALUATION.md §2.

## Required deliverables

| Requirement | Status | Where / what remains |
|---|---|---|
| Working app: all 11 MVP features implemented and merged | Done | all three repos, `main` = `develop` |
| Non-AI flows verified end to end in a real browser | Done | `roomify_frontend/tests/live` (journey, privacy, logout, CV-down) |
| AI flows (plan, concept, suggestions, DIY) verified end to end | Done (demo mode) | live suite with `LIVE_AI=1 LIVE_DEMO=1`; not verified against the live OpenAI API |
| Demo mode + fallback when AI fails | Done | backend#52, frontend#33; `DemoLibraryTest`, `DemoFallbackTest` |
| Graceful behaviour without a key (fallback off) | Done | EVALUATION.md §3; `MissingAiKeyTest`, frontend tests |
| Merge to `main`, `main` synced back into `develop` | Done | admin squash-merge on green CI (bypasses listed in PROGRESS.md) |
| Tag v1.0.0, v1.0.1 (live deployment) | Done | all three repos |
| Technical report (Markdown + PDF + DOCX) | Done | `final/Roomify - Technical Report.pdf` / `.docx` (source REPORT.md) |
| Repos with README, `.env.example`, run commands | Done | each repo's README |
| Repo visibility decision | Done | REPOS.md: all three public |
| Licenses | Done | frontend/backend MIT; roomify_cv AGPL-3.0 (matches Ultralytics); demo photos CC0 (`src/main/resources/demo/CREDITS.md`) |
| Pinned dependencies + DEPENDENCIES.md | Done | DEPENDENCIES.md |
| Architecture diagram (Mermaid + PNG/SVG) | Done | architecture/; `final/Roomify - Architecture.pdf` |
| Web endpoint (live URL) | Done | **<https://roomify-rooms.vercel.app>** (Vercel + Neon, free plans, demo mode; DEPLOYMENT.md). Live suite passed against it, incl. a visual concept |
| Demo video < 5 min | Done (2:43, silent, captioned, title and end card with the live URL) | `video/Roomify - Demo.mp4`, video/SCRIPT.md |
| Screenshots of every main screen | Done | `screenshots/` (desktop and phone, incl. demo-mode plan, concept, suggestions, DIY) |
| Evaluation: detection quality + latency | Done | EVALUATION.md §1 (60 COCO images) |
| Evaluation: plan budget compliance, AI latency, cost | Demo mode (pre-generated results) | EVALUATION.md §2: no model numbers reported |
| Failure cases with screenshots | Done (detection) | evaluation/failures/ |
| Secrets: history scanned, none committed | Done | PROGRESS.md (Security) |

## Devpost form fields

| Field | Status | Source |
|---|---|---|
| Project name, tagline | Done draft | DEVPOST_STORY.md |
| Story (inspiration, what it does, how, challenges, accomplishments, learned, next) | Done draft · Owner review | DEVPOST_STORY.md (states demo mode) |
| Built with | Done | DEVPOST_STORY.md |
| Image gallery | Done | gallery/ (1–5 product and architecture, 6–8 demo-mode AI results) |
| Video link (YouTube/Vimeo) | Owner | upload `video/Roomify - Demo.mp4` (final, no voice-over) |
| "Try it out" links | Done | <https://roomify-rooms.vercel.app> plus the three repos (REPOS.md) |
| Team members added on Devpost | Owner | Devpost |
| Submit before the deadline | Owner | Devpost |

## When a funded OpenAI key is available
1. Put it in `roomify_backend/.env` and in the Vercel project (`vercel env add OPENAI_API_KEY production`
   in roomify_backend), set `AI_DEMO_MODE=false` there, redeploy (`vercel deploy --prod`).
2. `LIVE_AI=1` live suite (without `LIVE_DEMO`), then the generation evaluation → EVALUATION.md §2.
3. `LIVE_AI=1 LIVE_RECORD=1` → `assemble.sh` → re-cut the AI scenes.

## Judge-ready documents

All in `final/`, rebuilt from the Markdown sources by `./build.sh`: Technical Report, Evaluation
Evidence, Architecture, Dependencies, Repositories and Deployment as PDF and DOCX, plus
`Roomify - Submission Pack.pdf` (cover, contents, page numbers).
