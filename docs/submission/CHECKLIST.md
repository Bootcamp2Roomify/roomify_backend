# Submission checklist (2026-10-24, release candidate v0.9.0-rc1)

The Devpost project page needs a login and the hackathon's rules page could not be read, so this maps
the requirements from the project brief plus Devpost's standard submission form. If the hackathon has
extra rules, add them here.

Legend: ✅ done · 🔑 pending: requires OpenAI key · 🙋 needs the project owner

## Required deliverables

| Requirement | Status | Where / what remains |
|---|---|---|
| Working app: all 11 MVP features implemented and merged | ✅ | All three repos' `develop` |
| Non-AI flows verified end to end in a real browser | ✅ | `roomify_frontend/tests/live` (journey, privacy, logout, CV-down) |
| AI flows (plan, concept, suggestions, DIY) verified live | 🔑 | Run `LIVE_AI=1` live suite once the key is in `.env` |
| Graceful behaviour without a key | ✅ | EVALUATION.md §3; `MissingAiKeyTest`, frontend tests |
| Release PRs develop → main, CI green | ✅ | backend#40, cv#10, frontend#26 — all checks pass |
| Merge to `main` | 🙋 | `main` requires 1 approval from someone other than the last pusher and linear history: a teammate approves, then **Squash and merge** (auto-merge is disabled in repo settings) |
| CI green on `main` | 🙋 then ✅ | runs automatically on the squash commit |
| Tag v0.9.0-rc1 on `main` | 🙋 | after the merges: `git tag -a v0.9.0-rc1 -m "Release candidate; AI features unverified" origin/main && git push origin v0.9.0-rc1` in each repo, then sync `main` back into `develop` (squash makes them diverge) |
| Tag v1.0.0 | 🔑 | after the live AI run |
| Technical report (Markdown + PDF) | ✅ draft · 🔑 final | REPORT.md / REPORT.pdf; §6.2 AI results pending |
| Repos with README, `.env.example`, run commands | ✅ | each repo's README |
| Repo visibility decision | ✅ | REPOS.md: all three public; no change needed |
| Licenses | ✅ | frontend/backend MIT; roomify_cv AGPL-3.0 (matches Ultralytics) |
| Pinned dependencies + DEPENDENCIES.md | ✅ | DEPENDENCIES.md |
| Architecture diagram (Mermaid + PNG/SVG) | ✅ | architecture/ (system + request flow) |
| Web endpoint (live URL) | 🙋 | DEPLOYMENT.md: approve plan, provide Fly/Vercel/Neon accounts; then smoke test (🔑 for AI) |
| Demo video < 5 min | ✅ draft (2:55) · 🔑 AI scenes · 🙋 intro, voice-over | video/roomify-demo-draft.mp4, video/SCRIPT.md |
| Evaluation: detection quality + latency | ✅ | EVALUATION.md §1 (60 COCO images) |
| Evaluation: plan budget compliance, AI latency, cost | 🔑 | EVALUATION.md §2 |
| Failure cases with screenshots | ✅ detection · 🔑 AI | evaluation/failures/ |
| Secrets: history scanned, none committed | ✅ | PROGRESS.md (Security) |

## Devpost form fields

| Field | Status | Source |
|---|---|---|
| Project name, tagline | ✅ draft | DEVPOST_STORY.md |
| Story (inspiration, what it does, how, challenges, accomplishments, learned, next) | ✅ draft · 🙋 review | DEVPOST_STORY.md |
| Built with | ✅ | DEVPOST_STORY.md |
| Image gallery | ✅ non-AI · 🔑 plan/concept shots | gallery/ |
| Video link (YouTube/Vimeo) | 🙋 upload final video | video/ |
| "Try it out" links | ✅ repos · 🙋 live URL | REPOS.md, DEPLOYMENT.md |
| Team members added on Devpost | 🙋 | Devpost |
| Submit before the deadline | 🙋 | Devpost |

## Order once the key arrives
1. Add `OPENAI_API_KEY` to `roomify_backend/.env`; `_temp/stack.sh up`.
2. `LIVE_AI=1` live suite → fix anything found (first live call of `gpt-image-2.5-sunburst`).
3. Run the generation evaluation → fill EVALUATION.md §2, REPORT §6.2, rebuild PDF.
4. `LIVE_AI=1 LIVE_RECORD=1` → `assemble.sh` → final video.
5. develop → main, tag v1.0.0.
