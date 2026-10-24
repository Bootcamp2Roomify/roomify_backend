# Roomify submission checklist (2026-10-24, release candidate v0.9.0-rc1)

The Devpost project page needs a login and the hackathon's rules page could not be read, so this maps
the requirements from the project brief plus Devpost's standard submission form. If the hackathon has
extra rules, add them here.

Status values: **Done**, **Pending key** (requires an OpenAI key), **Owner** (needs the project owner).

## Required deliverables

| Requirement | Status | Where / what remains |
|---|---|---|
| Working app: all 11 MVP features implemented and merged | Done | All three repos' `develop` |
| Non-AI flows verified end to end in a real browser | Done | `roomify_frontend/tests/live` (journey, privacy, logout, CV-down) |
| AI flows (plan, concept, suggestions, DIY) verified live | Pending key | Run `LIVE_AI=1` live suite once the key is in `.env` |
| Graceful behaviour without a key | Done | EVALUATION.md §3; `MissingAiKeyTest`, frontend tests |
| Release PRs develop → main, CI green | Done | backend#40, cv#10, frontend#26 — all checks pass |
| Merge to `main` | Owner | `main` requires 1 approval from someone other than the last pusher and linear history: a teammate approves, then **Squash and merge** (auto-merge is disabled in repo settings) |
| CI green on `main` | Owner then Done | runs automatically on the squash commit |
| Tag v0.9.0-rc1 on `main` | Owner | after the merges: `git tag -a v0.9.0-rc1 -m "Release candidate; AI features unverified" origin/main && git push origin v0.9.0-rc1` in each repo, then sync `main` back into `develop` (squash makes them diverge) |
| Tag v1.0.0 | Pending key | after the live AI run |
| Technical report (Markdown + PDF) | Done draft · Pending key final | `final/Roomify - Technical Report.pdf` and `.docx` (source REPORT.md); §6.2 AI results pending |
| Repos with README, `.env.example`, run commands | Done | each repo's README |
| Repo visibility decision | Done | REPOS.md: all three public; no change needed |
| Licenses | Done | frontend/backend MIT; roomify_cv AGPL-3.0 (matches Ultralytics) |
| Pinned dependencies + DEPENDENCIES.md | Done | DEPENDENCIES.md |
| Architecture diagram (Mermaid + PNG/SVG) | Done | architecture/ (system, request flow, data model, deployment); `final/Roomify - Architecture.pdf` |
| Web endpoint (live URL) | Owner | DEPLOYMENT.md: approve plan, provide Fly/Vercel/Neon accounts; then smoke test (Pending key for AI) |
| Demo video < 5 min | Done draft (2:55) · Pending key AI scenes · Owner intro, voice-over | `video/Roomify - Demo (draft).mp4`, video/SCRIPT.md |
| Evaluation: detection quality + latency | Done | EVALUATION.md §1 (60 COCO images) |
| Evaluation: plan budget compliance, AI latency, cost | Pending key | EVALUATION.md §2 |
| Failure cases with screenshots | Done detection · Pending key AI | evaluation/failures/ |
| Secrets: history scanned, none committed | Done | PROGRESS.md (Security) |

## Devpost form fields

| Field | Status | Source |
|---|---|---|
| Project name, tagline | Done draft | DEVPOST_STORY.md |
| Story (inspiration, what it does, how, challenges, accomplishments, learned, next) | Done draft · Owner review | DEVPOST_STORY.md |
| Built with | Done | DEVPOST_STORY.md |
| Image gallery | Done non-AI · Pending key plan/concept shots | gallery/ |
| Video link (YouTube/Vimeo) | Owner upload final video | video/ |
| "Try it out" links | Done repos · Owner live URL | REPOS.md, DEPLOYMENT.md |
| Team members added on Devpost | Owner | Devpost |
| Submit before the deadline | Owner | Devpost |

## Order once the key arrives
1. Add `OPENAI_API_KEY` to `roomify_backend/.env`; `_temp/stack.sh up`.
2. `LIVE_AI=1` live suite → fix anything found (first live call of `gpt-image-2.5-sunburst`).
3. Run the generation evaluation → fill EVALUATION.md §2, REPORT §6.2, rebuild PDF.
4. `LIVE_AI=1 LIVE_RECORD=1` → `assemble.sh` → final video.
5. develop → main, tag v1.0.0.

## Judge-ready documents

All in `final/`, rebuilt from the Markdown sources by `./build.sh`: Technical Report, Evaluation
Evidence, Architecture, Dependencies, Repositories and Deployment as PDF and DOCX, plus
`Roomify - Submission Pack.pdf` (cover, contents, page numbers).
