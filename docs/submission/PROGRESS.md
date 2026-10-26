# Roomify submission — progress log

Running log kept by the lead engineer. Newest entries at the bottom of each section.
Location rationale: `roomify_backend/docs/submission/` — the backend is the integration
point of the three services and already hosts `docs/`, so one place holds the cross-repo package.

## Environment notes (2026-10-24)

- `scope.md` was missing from the workspace; regenerated from the source Google Doc
  (the team's scope document) and saved as `docs/submission/scope.md`.
- No Jira connector is available. ROOM-87..101 were reconstructed from PR titles, branch
  names, commit messages and in-repo docs (see "Backlog mapping"); unknown titles are marked `?`.
- Local ports 5433 and 8080 are used by unrelated projects' containers; Roomify runs on
  Postgres `127.0.0.1:5439` (throwaway `postgres:17` container) and backend `:8081` locally.
- Local runs use `STORAGE_TYPE=local` and blank AWS credentials so tests never touch a real bucket.

## Security

- 2026-10-24 — Secret scan of full history (all refs + unreachable objects) in all three repos
  for AWS key IDs, AWS secrets, OpenAI/Google keys, private keys and credential-like property
  assignments: **no secrets found in git history**. `.env` is gitignored in all three repos;
  only `.env.example` has ever been committed.
- 2026-10-24 — **Flag:** `roomify_backend/.env` (untracked, local only) contains a full AWS access
  key pair and `STORAGE_TYPE=s3`. If this is the key that leaked earlier it must be rotated.
  Not used by any run in this work.

## Phase 1 / Step 1 — open PRs

Baseline before merging: backend develop 35/35 tests, CV 12/12, frontend 195/195 unit +
typecheck + lint + build. No open PRs in roomify_cv or roomify_frontend.

| PR | Ticket | Action | Tests after sync |
|---|---|---|---|
| backend#26 | ROOM-98 recommendation/DIY contracts | merged develop in (clean), merge commit | 63/63 |
| backend#27 | ROOM-101 recommendation persistence | retargeted to develop after #26, merged develop in (clean) | 87/87 |
| backend#25 | ROOM-94 image-generation adapter | merged develop in (clean) | 93/93 |
| backend#24 | ROOM-88/92/96 design lifecycle | conflicts with #23 (ROOM-90) resolved, see below | 113/113 |

Order rationale: #27 is stacked on #26; #25 is independent and small; #24 conflicted and
needed the others in develop first so the resolution could be tested against everything.

#24 resolution (documented on the PR): kept ROOM-88's versioned plan contract (the one
`DesignService`/persistence/assets use) and ported every ROOM-90 guardrail onto it, ported
ROOM-90's validator tests (5 kept + 2 added), renumbered its migration V7→V8 (V7 taken by
ROOM-101), and fixed one integration bug: recommendation sets could attach to PENDING/FAILED
designs once both PRs were combined. Merge commits only; no force-pushes.

No CI workflows exist in any repo (`.github/` holds only a PR template) — to be added.

## Backlog mapping (reconstructed, no Jira access)

| Ticket | Title (inferred) | Status |
|---|---|---|
| ROOM-87 | Structured budget-aware redesign plans | merged (#21) |
| ROOM-88 | Design input snapshot and output schema | merged (#24) |
| ROOM-89 | LLM provider adapter, secret-safe config | merged (#22) |
| ROOM-90 | Constraint-aware redesign prompt | merged (#23) |
| ROOM-91 | ? (redesign plan in UI) | not started |
| ROOM-92 | Persist design requests and results | merged (#24) |
| ROOM-93 | ? (visual prototype prompt from plan) | not started |
| ROOM-94 | Image-generation provider adapter | merged (#25), not wired to any endpoint |
| ROOM-95 | ? (generate + show visual prototype) | not started |
| ROOM-96 | Store generated prototypes | merged (#24) |
| ROOM-97 | ? (recommendation generation prompt/provider) | not started |
| ROOM-98 | Recommendation and DIY contracts | merged (#26) |
| ROOM-99/100 | Recommendation / DIY generation | not started (per #27 description) |
| ROOM-101 | Recommendation persistence and reads | merged (#27) |

## Gap analysis vs scope.md (MVP features)

| # | Feature | Backend | Frontend | Gap |
|---|---|---|---|---|
| F1 | Auth and project privacy | `users` table only; no endpoints, projects not owned | none | **missing** |
| F2 | Project setup + upload | create project takes no fields; upload + validation done | upload done | name / room type / purpose / rental status missing |
| F3 | Detection | done (CV + persistence) | done | — |
| F4 | Review + decisions | decision PATCH only | review done | manual add / edit / remove of objects missing |
| F5 | Style, budget, requirements | done | done | — |
| F6 | Redesign generator | plan generation done; image adapter not wired | none | prototype wiring + UI |
| F7 | Recommendations | contracts + persistence; no generation | none | generation + UI |
| F8 | Rental-friendly mode | enforced in plan prompt/validator | none | warnings + lease reminder in UI |
| F9 | Reuse + DIY | contracts + persistence; no generation | none | generation + UI |
| F10 | Budget summary | plan totals validated | none | summary UI, over-budget warning |
| F11 | Save + dashboard | get by id only | stub page | project list + dashboard |

Dead code noted: `OllamaClient`/`OllamaConfig` are not referenced by any service.

## Decisions from the project owner (2026-10-24)

- LLM/image provider: OpenAI; owner adds `OPENAI_API_KEY` to `roomify_backend/.env` locally.
- The AWS key in the local `.env` is a fresh key; the leaked key was already revoked.

## Phase 1 / Step 2 — features

| Date | Repo | PR | Feature | Result |
|---|---|---|---|---|
| 2026-10-24 | all | backend#29, cv#5, frontend#15 | First CI workflows (actions pinned by SHA) | merged, green |
| 2026-10-24 | backend | #28 | F1 auth: register/login/me, BCrypt, HS256 JWT, project ownership (foreign → 404) | merged, 119 tests, CI green |
| 2026-10-24 | frontend | #16 | F1 auth UI: login/register, httpOnly session cookie, proxy Bearer, protected pages | merged, 207 unit tests, CI green |

Fixes found on the way: `ai.api-key` ignored `OPENAI_API_KEY`; compose had a duplicate
`AI_MODEL` defaulting to empty; recommendation sets could attach to failed designs.

Pre-existing (on develop before any change): 7 mocked Playwright specs fail on chromium
(backendBridge ×3, roomFlow ×2, detectionReview ×2, the last flaky). Scheduled for step 3.
Local-dev note: Next normalizes `127.0.0.1` to `localhost` in redirects; use `http://localhost:3000`.

## Phase 1 / Step 2 — features (continued)

| Repo | PR | Feature | Tests |
|---|---|---|---|
| backend | #30 | F2/F11 project details + paginated owner-scoped list | 122 |
| backend | #31 | F4 add / relabel / remove items; continue manually after failed analysis | 124 |
| backend | #32 | ROOM-93/95 visual prototype prompt + generation endpoint | 122 |
| backend | #33 | ROOM-97/99/100 recommendation + DIY generation, server-computed totals, safety filter; contract gains `placement` | 131 |
| frontend | #17 | F2/F11 create-project form, dashboard, resume upload; fixed 7 pre-existing e2e failures | 215 unit, 39 e2e |
| frontend | #18 | F4 rename / remove / add in review; manual path on failed analysis | 221 |
| frontend | #19 | F6–F10 design workspace: plan, concept, suggestions, DIY, budget summary | 222 |

## Phase 1 / Step 3 — regression (2026-10-24)

- Real stack (Postgres 17 + CV + backend jar + `next start`) driven by Playwright (`tests/live`,
  frontend#20): journey, privacy, logout, CV-down manual entry **pass**; DB state verified by SQL.
- Bugs found and fixed: phones could not reach the dashboard (nav CSS); review button style leak;
  middleware host normalization; analyze helper race (test).
- **Blocked:** AI happy path (plan, concept, suggestions, DIY) needs `OPENAI_API_KEY` — still not set.

## Provider changes discovered (2026-10-24)

- OpenAI shut down `gpt-image-1` on 2026-10-23 → default switched to `gpt-image-2.5-sunburst`
  (backend#36, untested live). `gpt-4o-mini` shuts down 2027-01-14; text requests now support
  reasoning models (`gpt-6-luna`) by configuration.

## Phase 3 — hardening and package

| PR | Change |
|---|---|
| cv#6, backend#34, frontend#21 | exact pins, pinned images, non-root containers, CV Dockerfile |
| cv#7 | **security:** SSRF via `/v1/analyze image_url` blocked |
| backend#35, cv#8, frontend#22 | READMEs, accurate `.env.example` |
| backend#36 | retired image model, reasoning-model-ready requests, usage logging |
| frontend#23 | 1080p demo recording spec |

Docs in this folder: REPORT.md, EVALUATION.md (detection measured), DEPENDENCIES.md, DEPLOYMENT.md
(proposal, not deployed), REPOS.md, architecture/, video/.

## Open items (owner)

1. `OPENAI_API_KEY` in `roomify_backend/.env` → then: live AI regression, generation metrics, demo
   recording, release v1.0.0.
2. Approve (or not) the deployment proposal and provide hosting credentials.
3. Licensing: Ultralytics is AGPL-3.0; roomify_cv is labelled MIT.

## 2026-10-24 (afternoon) — release candidate without an OpenAI key

- Graceful no-key behaviour: placeholder/blank/rejected keys → `503 AI_CONFIGURATION_ERROR` everywhere,
  nothing half-stored (backend#39, frontend#24).
- roomify_cv relicensed AGPL-3.0, MIT notice kept in NOTICE (cv#9).
- Docs finalized with "Pending: requires OpenAI key" rows; draft report PDF (since replaced by `final/`); data-flow diagram.
- Non-AI demo scenes recorded on the real stack (frontend#25); draft cut 2:55 with pending slates;
  gallery screenshots; DEVPOST_STORY.md; CHECKLIST.md.
- ffmpeg 9.0.2 installed with Homebrew for video assembly.

## 2026-10-24 (evening) — judge-ready documents

- `build.sh` builds `final/`: six documents as PDF + DOCX and the combined Submission Pack PDF.
  pandoc is the single parser; PDFs are laid out by Paged.js in Chromium; DOCX uses a generated
  reference template, running header/footer and embedded Source Serif 4 / Source Code Pro (Adobe
  releases, SHA-256 pinned). Diagrams rendered from Mermaid in the same typeface.
- New ARCHITECTURE.md; diagrams redrawn for portrait pages; failure cases as captioned figures.
- Product name "Roomify" everywhere: UI wordmark (frontend#27), scope title, diagrams, video slates,
  file names. Clips, gallery and draft video re-recorded with the corrected UI.
- Tools installed with Homebrew for the build/QA: pandoc 3.12.1, LibreOffice (DOCX layout check).

## 2026-10-26 — demo mode and v1.0.0

- OpenAI unusable at submission: one key `401 invalid_api_key`, the other `429 credit_balance_exhausted`.
- Demo mode (backend#52): `AI_DEMO_MODE=true` serves plan, concept, suggestions and DIY from a bundled
  library (four room-purpose cases, CC0 photos); `AI_DEMO_FALLBACK=true` (default) answers from it when a
  live call fails. Results are marked `source: "demo"`; 25 new tests. Frontend labels them "Demo"
  (frontend#33).
- Live suite passes end to end in demo mode; fixed two stale live-test steps (a click that waited
  forever for a missing button, and the dashboard link after a budget edit).
- Screenshots of every main screen at desktop and phone width (`screenshots/`), gallery images 6–8,
  final video cut with the AI scenes recorded in demo mode (`video/Roomify - Demo.mp4`, 3:24).
- Docs: every "Pending: requires OpenAI key" row now reads "Demo mode (pre-generated results)"; no AI
  latency or cost numbers are reported.
- Deployment still blocked on Fly.io billing and a bucket-scoped AWS key.

